using ControlDeck.Agent.Transport;
using ControlDeck.Domain;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Dispatch;

/// <summary>
/// The UI-facing entry point for grouped interactions
/// (docs/ARCHITECTURE.md §4: "UI layers only ever call
/// `GroupController.apply(group, originWidgetId, userInput)` — they never
/// compute deltas or target states themselves"). This class is that call
/// site: it resolves each member's current state from
/// <see cref="DeviceStateManager"/>, calls the appropriate pure
/// <see cref="GroupController"/> function, and fans the resulting
/// <see cref="GroupDispatch"/>es out over <see cref="ConnectionManager"/>.
/// </summary>
public sealed class GroupActionCoordinator
{
    private readonly ConnectionManager _connectionManager;
    private readonly ILogger<GroupActionCoordinator> _logger;

    public GroupActionCoordinator(ConnectionManager connectionManager, ILogger<GroupActionCoordinator> logger)
    {
        _connectionManager = connectionManager;
        _logger = logger;
    }

    public async Task ApplyRelativeSliderAsync(Group group, IReadOnlyList<Widget> widgets, int oldValue, int newValue)
    {
        var members = ResolveMembers(group, widgets)
            .Select(w => new RelativeSliderMember(
                w.Id,
                w.TargetDeviceId,
                CurrentValueOf(w),
                w.Action))
            .ToList();

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue, newValue);
        await SendAllAsync(dispatches).ConfigureAwait(false);
    }

    public async Task ApplyAbsoluteToggleAsync(Group group, IReadOnlyList<Widget> widgets)
    {
        var members = ResolveMembers(group, widgets)
            .Select(w => new MuteMember(w.Id, w.TargetDeviceId, _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).Muted ?? false))
            .ToList();

        var dispatches = GroupController.ApplyAbsoluteToggle(members);
        await SendAllAsync(dispatches).ConfigureAwait(false);
    }

    public async Task ApplyAbsoluteMediaAsync(Group group, IReadOnlyList<Widget> widgets, MediaState desiredState)
    {
        var members = ResolveMembers(group, widgets)
            .Select(w => new MediaMember(w.Id, w.TargetDeviceId, _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).MediaState ?? MediaState.Paused))
            .ToList();

        var dispatches = GroupController.ApplyAbsoluteMedia(members, desiredState);
        await SendAllAsync(dispatches).ConfigureAwait(false);
    }

    public async Task ApplyAbsoluteMediaToggleAsync(Group group, IReadOnlyList<Widget> widgets)
    {
        var members = ResolveMembers(group, widgets)
            .Select(w => new MediaMember(w.Id, w.TargetDeviceId, _connectionManager.DeviceStates.Get(w.TargetDeviceId.Value).MediaState ?? MediaState.Paused))
            .ToList();

        var dispatches = GroupController.ApplyAbsoluteMediaToggle(members);
        await SendAllAsync(dispatches).ConfigureAwait(false);
    }

    public async Task ApplyMediaEdgeAsync(Group group, IReadOnlyList<Widget> widgets, bool isNext)
    {
        var members = ResolveMembers(group, widgets)
            .Select(w => new MediaMember(w.Id, w.TargetDeviceId, MediaState.Paused))
            .ToList();

        var dispatches = GroupController.ApplyMediaEdge(members, isNext);
        await SendAllAsync(dispatches).ConfigureAwait(false);
    }

    private int CurrentValueOf(Widget widget)
    {
        var state = _connectionManager.DeviceStates.Get(widget.TargetDeviceId.Value);
        return widget.Action switch
        {
            BrightnessSet => state.Brightness ?? 0,
            VolumeSet => state.Volume ?? 0,
            _ => 0
        };
    }

    private static IEnumerable<Widget> ResolveMembers(Group group, IReadOnlyList<Widget> widgets) =>
        group.MemberWidgetIds
            .Select(id => widgets.FirstOrDefault(w => w.Id == id))
            .Where(w => w is not null)
            .Select(w => w!);

    private async Task SendAllAsync(IReadOnlyList<GroupDispatch> dispatches)
    {
        foreach (var dispatch in dispatches)
        {
            var sent = await _connectionManager.SendActionAsync(dispatch.TargetDeviceId.Value, dispatch.Action).ConfigureAwait(false);
            if (!sent)
            {
                _logger.LogInformation("Skipped group dispatch to offline target {TargetDeviceId}.", dispatch.TargetDeviceId);
            }
        }
    }
}
