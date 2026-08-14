using System.Net.WebSockets;
using ControlDeck.Agent.Identity;
using ControlDeck.Agent.Persistence;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Transport;

/// <summary>
/// Connects outward to a peer's WebSocket server via `ClientWebSocket`
/// (protocol/PROTOCOL.md §1) and wraps the result in a
/// <see cref="PeerConnection"/> in the <see cref="PeerConnectionRole.Initiator"/>
/// role. Used both for the initial pairing connection and for ordinary
/// reconnects to already-paired peers (docs/ARCHITECTURE.md §5's reconnect
/// flow starts here).
/// </summary>
public sealed class WebSocketClientConnector
{
    private readonly DeviceIdentity _selfIdentity;
    private readonly ISecretStore _secretStore;
    private readonly ILoggerFactory _loggerFactory;

    public WebSocketClientConnector(DeviceIdentity selfIdentity, ISecretStore secretStore, ILoggerFactory loggerFactory)
    {
        _selfIdentity = selfIdentity;
        _secretStore = secretStore;
        _loggerFactory = loggerFactory;
    }

    /// <summary>Opens ws://{host}:{port}/ and returns a running PeerConnection, or null if the connection couldn't be established.</summary>
    public async Task<PeerConnection?> ConnectAsync(string host, int port, CancellationToken cancellationToken)
    {
        var client = new ClientWebSocket();
        try
        {
            var uri = new Uri($"ws://{host}:{port}/");
            await client.ConnectAsync(uri, cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is WebSocketException or OperationCanceledException or ObjectDisposedException)
        {
            _loggerFactory.CreateLogger<WebSocketClientConnector>()
                .LogInformation(ex, "Failed to connect to peer at {Host}:{Port}.", host, port);
            client.Dispose();
            return null;
        }

        var connection = new PeerConnection(
            new WebSocketConnection(client),
            PeerConnectionRole.Initiator,
            _selfIdentity,
            _secretStore,
            _loggerFactory.CreateLogger<PeerConnection>());

        _ = connection.RunAsync();
        return connection;
    }
}
