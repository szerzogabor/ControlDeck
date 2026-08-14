using System.Security.Cryptography;
using ControlDeck.Agent.Identity;
using ControlDeck.Agent.Persistence;
using ControlDeck.Agent.Transport;
using ControlDeck.Domain;
using ControlDeck.Protocol;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Pairing;

/// <summary>
/// Drives both pairing directions (protocol/PROTOCOL.md §3.2):
/// - Inbound: this device displays a QR/PIN; an incoming PAIR_REQUEST is
///   validated against the currently-displayed token and answered with a
///   freshly-generated shared secret.
/// - Outbound: the user enters a PIN shown on another device (symmetric to
///   the Android app's QR-scan flow, since Windows has no camera scanner in
///   the MVP); this device connects out and sends PAIR_REQUEST itself.
///
/// On success, both flows persist the peer's metadata
/// (<see cref="IPairedDeviceRepository"/>) and shared secret
/// (<see cref="ISecretStore"/>, DPAPI-protected) — the secret is never
/// logged (protocol/PROTOCOL.md §7).
/// </summary>
public sealed class PairingService
{
    private readonly DeviceIdentity _selfIdentity;
    private readonly ISecretStore _secretStore;
    private readonly IPairedDeviceRepository _pairedDevices;
    private readonly WebSocketClientConnector _clientConnector;
    private readonly ILogger<PairingService> _logger;

    private PairingToken? _activeIncomingToken;

    public event EventHandler<PairedDevice>? PeerPaired;

    public PairingService(
        DeviceIdentity selfIdentity,
        ISecretStore secretStore,
        IPairedDeviceRepository pairedDevices,
        WebSocketClientConnector clientConnector,
        ILogger<PairingService> logger)
    {
        _selfIdentity = selfIdentity;
        _secretStore = secretStore;
        _pairedDevices = pairedDevices;
        _clientConnector = clientConnector;
        _logger = logger;
    }

    /// <summary>Starts (or restarts) a 2-minute pairing window and returns the token to render as QR/PIN.</summary>
    public PairingToken BeginHostingPairingWindow()
    {
        _activeIncomingToken = PairingToken.GenerateSixDigitPin();
        _logger.LogInformation("Pairing window opened, expires at {ExpiresAtUtc:O}.", _activeIncomingToken.ExpiresAtUtc);
        return _activeIncomingToken;
    }

    public void CancelHostingPairingWindow() => _activeIncomingToken = null;

    /// <summary>Wire this up to every accepted <see cref="PeerConnection.PairRequestReceived"/>.</summary>
    public async Task HandleIncomingPairRequestAsync(PeerConnection connection, PairRequestPayload request)
    {
        if (_activeIncomingToken is null || _activeIncomingToken.IsExpired)
        {
            _logger.LogInformation("Rejected PAIR_REQUEST from {RequesterDeviceId}: no active/expired pairing window.", request.RequesterDeviceId);
            await connection.SendPairResponseAsync(false, "TOKEN_EXPIRED", null).ConfigureAwait(false);
            return;
        }

        if (request.PairingToken != _activeIncomingToken.Value)
        {
            _logger.LogInformation("Rejected PAIR_REQUEST from {RequesterDeviceId}: token mismatch.", request.RequesterDeviceId);
            await connection.SendPairResponseAsync(false, "TOKEN_INVALID", null).ConfigureAwait(false);
            return;
        }

        var secret = RandomNumberGenerator.GetBytes(32);
        await connection.SendPairResponseAsync(true, null, Convert.ToBase64String(secret)).ConfigureAwait(false);

        PersistPairing(request.RequesterDeviceId, request.RequesterDeviceName, ToDomainPlatform(request.RequesterPlatform), secret);

        // One-shot: the token can't be reused for a second requester in the same window.
        _activeIncomingToken = null;
    }

    /// <summary>Outbound: connect to a peer displaying a PIN and send PAIR_REQUEST. Returns true on PAIR_RESPONSE{accepted:true}.</summary>
    public async Task<bool> PairByPinAsync(string host, int port, string pin, CancellationToken cancellationToken)
    {
        var connection = await _clientConnector.ConnectAsync(host, port, cancellationToken).ConfigureAwait(false);
        if (connection is null)
        {
            return false;
        }

        var responseReceived = new TaskCompletionSource<PairResponsePayload?>(TaskCreationOptions.RunContinuationsAsynchronously);
        connection.MessageReceived += OnMessage;

        void OnMessage(object? sender, Envelope envelope)
        {
            if (envelope.Payload is PairResponsePayload response)
            {
                responseReceived.TrySetResult(response);
            }
        }

        try
        {
            // PeerConnection sends its own HELLO immediately as the
            // initiator; give the handshake a brief moment to settle before
            // layering PAIR_REQUEST on top. This is a one-shot,
            // user-initiated flow (not steady-state traffic), so a short
            // fixed delay is an acceptable simplification for the MVP.
            await Task.Delay(300, cancellationToken).ConfigureAwait(false);

            var request = new PairRequestPayload(_selfIdentity.DeviceId, _selfIdentity.DeviceName, WirePlatform.Windows, pin);
            await connection.SendAsync(Envelope.Create(MessageType.PairRequest, request, _selfIdentity.DeviceId)).ConfigureAwait(false);

            var response = await responseReceived.Task.WaitAsync(TimeSpan.FromSeconds(10), cancellationToken).ConfigureAwait(false);

            if (response is not { Accepted: true, SharedSecret: { } secretBase64 })
            {
                _logger.LogInformation("Pairing rejected by {Host}:{Port}: {Reason}", host, port, response?.Reason ?? "no response");
                return false;
            }

            var secret = Convert.FromBase64String(secretBase64);
            PersistPairing(response.DeviceId, response.DeviceName, ToDomainPlatform(response.Platform), secret);
            return true;
        }
        catch (TimeoutException)
        {
            _logger.LogInformation("Pairing with {Host}:{Port} timed out waiting for PAIR_RESPONSE.", host, port);
            return false;
        }
        finally
        {
            connection.MessageReceived -= OnMessage;
        }
    }

    private void PersistPairing(string deviceId, string deviceName, Platform platform, byte[] secret)
    {
        _secretStore.Set(deviceId, secret);
        var paired = new PairedDevice(deviceId, deviceName, platform, DateTimeOffset.UtcNow);
        _pairedDevices.Upsert(paired);

        // SECURITY: never log `secret` itself (protocol/PROTOCOL.md §7).
        _logger.LogInformation("Paired with {DeviceId} ({DeviceName}).", deviceId, deviceName);

        PeerPaired?.Invoke(this, paired);
    }

    private static Platform ToDomainPlatform(WirePlatform platform) =>
        platform == WirePlatform.Windows ? Platform.Windows : Platform.Android;
}
