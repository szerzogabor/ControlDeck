using ControlDeck.Agent.Persistence;
using ControlDeck.Agent.Transport;
using ControlDeck.Domain;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Dispatch;

/// <summary>
/// Applies docs/ARCHITECTURE.md §5 whenever a device transitions
/// OFFLINE -&gt; ONLINE: for every group the reconnecting device is a member
/// of (across every locally-known dashboard), resolve the group's current
/// authoritative value from its other online members and hand off to
/// <see cref="ReconnectPolicyResolver"/>.
/// </summary>
public sealed class GroupReconnectCoordinator
{
    private readonly ConnectionManager _connectionManager;
    private readonly IDashboardRepository _dashboardRepository;
    private readonly ILogger<GroupReconnectCoordinator> _logger;

    public GroupReconnectCoordinator(
        ConnectionManager connectionManager,
        IDashboardRepository dashboardRepository,
        ILogger<GroupReconnectCoordinator> logger)
    {
        _connectionManager = connectionManager;
        _dashboardRepository = dashboardRepository;
        _logger = logger;

        _connectionManager.PeerConnectedAndAuthenticated += (_, deviceId) => _ = OnPeerReconnectedAsync(deviceId);
    }

    private async Task OnPeerReconnectedAsync(string reconnectedDeviceId)
    {
        foreach (var dashboard in _dashboardRepository.GetAll())
        {
            foreach (var group in dashboard.Groups)
            {
                var memberWidgets = group.MemberWidgetIds
                    .Select(id => dashboard.Widgets.FirstOrDefault(w => w.Id == id))
                    .Where(w => w is not null)
                    .Select(w => w!)
                    .ToList();

                var reconnectingWidget = memberWidgets.FirstOrDefault(w => w.TargetDeviceId.Value == reconnectedDeviceId);
                if (reconnectingWidget is null)
                {
                    continue;
                }

                var authoritative = ResolveGroupAuthoritativeAction(group, memberWidgets, reconnectingWidget);
                if (authoritative is null)
                {
                    continue;
                }

                var dispatch = ReconnectPolicyResolver.Resolve(
                    group.ReconnectPolicy,
                    reconnectingWidget.TargetDeviceId,
                    reconnectingWidget.Id,
                    authoritative);

                if (dispatch is null)
                {
                    continue;
                }

                _logger.LogInformation(
                    "Reconnect policy {Policy} for group {GroupId}: correcting {DeviceId}.",
                    group.ReconnectPolicy, group.Id, reconnectedDeviceId);

                await _connectionManager.SendActionAsync(dispatch.TargetDeviceId.Value, dispatch.Action).ConfigureAwait(false);
            }
        }
    }

    /// <summary>
    /// Approximates "the group's current authoritative value" as the current
    /// value of another online member of the same group — for
    /// RELATIVE_SLIDER groups this is the shared post-delta baseline in
    /// practice (every online member should already share it, since
    /// RelativeSlider dispatches keep all online members in lockstep); for
    /// absolute kinds it's simply that member's absolute state. Returns null
    /// if no other member is currently online to derive a value from.
    /// </summary>
    private ActionSpec? ResolveGroupAuthoritativeAction(Group group, IReadOnlyList<Widget> members, Widget reconnectingWidget)
    {
        var otherOnlineMembers = members
            .Where(w => w.Id != reconnectingWidget.Id)
            .Where(w => _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).Connection == ConnectionState.Online)
            .ToList();

        if (otherOnlineMembers.Count == 0)
        {
            return null;
        }

        return group.Kind switch
        {
            GroupKind.RelativeSlider => reconnectingWidget.Action switch
            {
                BrightnessSet => new BrightnessSet(_connectionManager.DeviceStates.Get(otherOnlineMembers[0].TargetDeviceId.Value).Brightness ?? 0),
                VolumeSet => new VolumeSet(_connectionManager.DeviceStates.Get(otherOnlineMembers[0].TargetDeviceId.Value).Volume ?? 0),
                _ => null
            },
            GroupKind.AbsoluteToggle => new SetMuted(MajorityMuted(otherOnlineMembers)),
            GroupKind.AbsoluteMedia => new MediaSetState(MajorityMediaState(otherOnlineMembers)),
            _ => null
        };
    }

    private bool MajorityMuted(IReadOnlyList<Widget> members)
    {
        var mutedCount = members.Count(w => _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).Muted == true);
        return mutedCount * 2 >= members.Count;
    }

    private MediaState MajorityMediaState(IReadOnlyList<Widget> members)
    {
        var playingCount = members.Count(w => _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).MediaState == MediaState.Playing);
        return playingCount * 2 >= members.Count ? MediaState.Playing : MediaState.Paused;
    }
}
