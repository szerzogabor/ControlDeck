using System.Collections.Concurrent;
using ControlDeck.Agent.Dispatch;
using ControlDeck.Agent.Identity;
using ControlDeck.Agent.Persistence;
using ControlDeck.Agent.PlatformActions;
using ControlDeck.Domain;
using ControlDeck.Protocol;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Transport;

/// <summary>
/// The Device Runtime's transport orchestrator (docs/ARCHITECTURE.md §2):
/// owns every live <see cref="PeerConnection"/>, the <see cref="DeviceStateManager"/>,
/// dispatches incoming ACTION/STATE_UPDATE/DASHBOARD_SYNC traffic to the
/// right domain logic, and applies <see cref="DashboardSyncResolver"/> for
/// conflict resolution. UI code talks to this class, never to a socket
/// directly (docs/ARCHITECTURE.md §1 layering rule).
/// </summary>
public sealed class ConnectionManager
{
    private readonly DeviceIdentity _selfIdentity;
    private readonly ISecretStore _secretStore;
    private readonly IPairedDeviceRepository _pairedDevices;
    private readonly IDashboardRepository _dashboardRepository;
    private readonly IAppRegistryRepository _appRegistry;
    private readonly ActionDispatcher _actionDispatcher;
    private readonly CapabilityRegistry _capabilityRegistry;
    private readonly ILogger<ConnectionManager> _logger;

    private readonly ConcurrentDictionary<string, PeerConnection> _connections = new();
    private readonly ConcurrentDictionary<string, IReadOnlySet<Capability>> _peerCapabilities = new();

    public DeviceStateManager DeviceStates { get; } = new();

    public event EventHandler<string>? PeerConnectedAndAuthenticated;
    public event EventHandler<string>? PeerDisconnected;
    public event EventHandler<Dashboard>? DashboardUpdatedByPeer;

    public ConnectionManager(
        DeviceIdentity selfIdentity,
        ISecretStore secretStore,
        IPairedDeviceRepository pairedDevices,
        IDashboardRepository dashboardRepository,
        IAppRegistryRepository appRegistry,
        ActionDispatcher actionDispatcher,
        CapabilityRegistry capabilityRegistry,
        ILogger<ConnectionManager> logger)
    {
        _selfIdentity = selfIdentity;
        _secretStore = secretStore;
        _pairedDevices = pairedDevices;
        _dashboardRepository = dashboardRepository;
        _appRegistry = appRegistry;
        _actionDispatcher = actionDispatcher;
        _capabilityRegistry = capabilityRegistry;
        _logger = logger;
    }

    public void Attach(PeerConnection connection)
    {
        connection.Authenticated += (_, _) => OnAuthenticated(connection);
        connection.MessageReceived += (_, envelope) => _ = OnMessageReceivedAsync(connection, envelope);
        connection.Closed += (_, _) => OnClosed(connection);
    }

    public IReadOnlyList<string> ConnectedPeerDeviceIds => _connections.Keys.ToList();

    public IReadOnlySet<Capability> CapabilitiesOf(string deviceId) =>
        _peerCapabilities.TryGetValue(deviceId, out var caps) ? caps : new HashSet<Capability>();

    /// <summary>Sends a locally-originated ACTION to a (possibly remote) target device. No-op if the target is offline.</summary>
    public async Task<bool> SendActionAsync(string targetDeviceId, ActionSpec action)
    {
        if (targetDeviceId == _selfIdentity.DeviceId)
        {
            var (success, _, resultingState) = _actionDispatcher.Execute(DashboardMapper.ToDto(action));
            if (success && resultingState is not null)
            {
                ApplyResultingStateToLocalStateManager(_selfIdentity.DeviceId, resultingState);
            }

            return success;
        }

        if (!_connections.TryGetValue(targetDeviceId, out var connection))
        {
            _logger.LogInformation("Cannot send action; target {TargetDeviceId} is offline.", targetDeviceId);
            return false;
        }

        var envelope = Envelope.Create(
            MessageType.Action,
            new ActionPayload(DashboardMapper.ToDto(action)),
            _selfIdentity.DeviceId,
            targetDeviceId);

        await connection.SendAsync(envelope).ConfigureAwait(false);
        return true;
    }

    public async Task BroadcastDashboardAsync(Dashboard dashboard)
    {
        var envelope = Envelope.Create(
            MessageType.DashboardSync,
            new DashboardSyncPayload(DashboardMapper.ToDto(dashboard)),
            _selfIdentity.DeviceId);

        foreach (var connection in _connections.Values)
        {
            await connection.SendAsync(envelope).ConfigureAwait(false);
        }
    }

    private void OnAuthenticated(PeerConnection connection)
    {
        if (connection.PeerDeviceId is not { } peerDeviceId)
        {
            return;
        }

        _connections[peerDeviceId] = connection;
        DeviceStates.MarkOnline(peerDeviceId);

        _ = SendDeviceInfoAndCapabilitiesAsync(connection);
        _ = SendAllOwnedDashboardsAsync(connection);

        PeerConnectedAndAuthenticated?.Invoke(this, peerDeviceId);
    }

    private async Task SendDeviceInfoAndCapabilitiesAsync(PeerConnection connection)
    {
        var deviceInfo = new DeviceInfoPayload(_selfIdentity.DeviceId, _selfIdentity.DeviceName, WirePlatform.Windows, DeviceIdentityStore.AppVersion);
        await connection.SendAsync(Envelope.Create(MessageType.DeviceInfo, deviceInfo, _selfIdentity.DeviceId)).ConfigureAwait(false);

        var caps = _capabilityRegistry.CurrentCapabilities().Select(CapabilityRegistry.ToWireToken).ToList();
        var apps = _appRegistry.GetAll()
            .Where(e => !string.IsNullOrWhiteSpace(e.Target))
            .Select(e => new Protocol.AppRegistryEntry(e.AppId, e.DisplayName))
            .ToList();
        var capabilitiesPayload = new CapabilitiesPayload(_selfIdentity.DeviceId, caps, apps);
        await connection.SendAsync(Envelope.Create(MessageType.Capabilities, capabilitiesPayload, _selfIdentity.DeviceId)).ConfigureAwait(false);
    }

    private async Task SendAllOwnedDashboardsAsync(PeerConnection connection)
    {
        foreach (var dashboard in _dashboardRepository.GetAll())
        {
            var payload = new DashboardSyncPayload(DashboardMapper.ToDto(dashboard));
            await connection.SendAsync(Envelope.Create(MessageType.DashboardSync, payload, _selfIdentity.DeviceId)).ConfigureAwait(false);
        }
    }

    private async Task OnMessageReceivedAsync(PeerConnection connection, Envelope envelope)
    {
        if (!connection.IsAuthenticated)
        {
            return;
        }

        var peerDeviceId = connection.PeerDeviceId ?? envelope.SourceDeviceId;
        if (peerDeviceId is null)
        {
            return;
        }

        switch (envelope.Payload)
        {
            case CapabilitiesPayload caps:
                var known = caps.Capabilities
                    .Select(CapabilityRegistry.FromWireToken)
                    .Where(c => c is not null)
                    .Select(c => c!.Value)
                    .ToHashSet();
                _peerCapabilities[peerDeviceId] = known;
                return;

            case ActionPayload action when envelope.TargetDeviceId == _selfIdentity.DeviceId:
                await HandleIncomingActionAsync(connection, envelope, action).ConfigureAwait(false);
                return;

            case ActionResultPayload result:
                if (result.ResultingState is not null)
                {
                    ApplyResultingStateToLocalStateManager(peerDeviceId, result.ResultingState);
                }

                return;

            case StateUpdatePayload stateUpdate:
                ApplyResultingStateToLocalStateManager(peerDeviceId, stateUpdate.State);
                return;

            case DashboardSyncPayload dashboardSync:
                await HandleDashboardSyncAsync(connection, envelope, dashboardSync).ConfigureAwait(false);
                return;

            case DashboardAckPayload:
                // Diagnostics only per protocol/PROTOCOL.md §3.7 — nothing to do.
                return;
        }
    }

    private async Task HandleIncomingActionAsync(PeerConnection connection, Envelope envelope, ActionPayload action)
    {
        var (success, errorCode, resultingState) = _actionDispatcher.Execute(action.Action);
        if (success && resultingState is not null)
        {
            ApplyResultingStateToLocalStateManager(_selfIdentity.DeviceId, resultingState);
        }

        var resultPayload = new ActionResultPayload(envelope.MessageId, success, errorCode, resultingState);
        await connection.SendAsync(Envelope.Create(
            MessageType.ActionResult,
            resultPayload,
            _selfIdentity.DeviceId,
            envelope.SourceDeviceId)).ConfigureAwait(false);
    }

    private async Task HandleDashboardSyncAsync(PeerConnection connection, Envelope envelope, DashboardSyncPayload payload)
    {
        var incomingDashboardId = new DashboardId(payload.Dashboard.Id);
        var local = _dashboardRepository.GetById(incomingDashboardId);

        var outcome = DashboardSyncResolver.Resolve(
            localDashboardExists: local is not null,
            incomingVersion: payload.Dashboard.Version,
            localVersion: local?.Version ?? 0,
            incomingTimestamp: envelope.Timestamp,
            localTimestamp: envelope.Timestamp, // best-effort: we don't persist the original apply timestamp; ties are rare and this keeps the resolver call honest about what's available.
            incomingSourceDeviceId: envelope.SourceDeviceId ?? string.Empty,
            localSourceDeviceId: _selfIdentity.DeviceId);

        if (outcome == SyncOutcome.ApplyIncoming)
        {
            var domainDashboard = DashboardMapper.ToDomain(payload.Dashboard);
            _dashboardRepository.Upsert(domainDashboard);
            DashboardUpdatedByPeer?.Invoke(this, domainDashboard);

            await connection.SendAsync(Envelope.Create(
                MessageType.DashboardAck,
                new DashboardAckPayload(payload.Dashboard.Id, payload.Dashboard.Version),
                _selfIdentity.DeviceId)).ConfigureAwait(false);
        }
        else if (outcome == SyncOutcome.KeepLocalAndReplyWithLocal && local is not null)
        {
            await connection.SendAsync(Envelope.Create(
                MessageType.DashboardSync,
                new DashboardSyncPayload(DashboardMapper.ToDto(local)),
                _selfIdentity.DeviceId)).ConfigureAwait(false);
        }
    }

    private void ApplyResultingStateToLocalStateManager(string deviceId, ActionSpecDto resultingState)
    {
        switch (resultingState)
        {
            case BrightnessSetDto d:
                DeviceStates.ApplyBrightness(deviceId, d.Value);
                break;
            case VolumeSetDto d:
                DeviceStates.ApplyVolume(deviceId, d.Value);
                break;
            case SetMutedDto d:
                DeviceStates.ApplyMuted(deviceId, d.Muted);
                break;
            case MediaSetStateDto d:
                DeviceStates.ApplyMediaState(deviceId, d.State == WireMediaState.Playing ? MediaState.Playing : MediaState.Paused);
                break;
            // MediaNext/MediaPrevious/AppLaunch/UnknownActionDto carry no persistent state to record.
        }
    }

    private void OnClosed(PeerConnection connection)
    {
        if (connection.PeerDeviceId is not { } peerDeviceId)
        {
            return;
        }

        _connections.TryRemove(peerDeviceId, out _);
        DeviceStates.MarkOffline(peerDeviceId);
        PeerDisconnected?.Invoke(this, peerDeviceId);
    }
}
