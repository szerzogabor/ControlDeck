namespace ControlDeck.Domain;

/// <summary>
/// One outbound command produced by group math: which device to send it to,
/// which widget it logically corresponds to (for UI feedback/correlation),
/// and the resolved absolute/edge action.
/// </summary>
public sealed record GroupDispatch(DeviceId TargetDeviceId, WidgetId WidgetId, ActionSpec Action);

/// <summary>A RELATIVE_SLIDER group member's current absolute value and the widget's own action template.</summary>
public sealed record RelativeSliderMember(WidgetId WidgetId, DeviceId TargetDeviceId, int CurrentValue, ActionSpec ActionTemplate);

/// <summary>An ABSOLUTE_TOGGLE (mute) group member's current mute state.</summary>
public sealed record MuteMember(WidgetId WidgetId, DeviceId TargetDeviceId, bool CurrentlyMuted);

/// <summary>An ABSOLUTE_MEDIA group member's current playback state.</summary>
public sealed record MediaMember(WidgetId WidgetId, DeviceId TargetDeviceId, MediaState CurrentState);

/// <summary>
/// Pure group math, centralized per docs/ARCHITECTURE.md §4. UI layers call
/// these functions and never compute deltas or target states themselves —
/// no I/O happens here; callers are responsible for supplying current state
/// and for actually sending the resulting <see cref="GroupDispatch"/>es over
/// the transport.
/// </summary>
public static class GroupController
{
    /// <summary>
    /// §4.1 RELATIVE_SLIDER: delta is applied to every member's current value,
    /// then each member is independently clamped to [0,100] — no redistribution
    /// of the delta across the group. A member whose clamped value doesn't
    /// change (already saturated in the direction of travel) is skipped so no
    /// redundant no-op ACTION is sent.
    /// </summary>
    public static IReadOnlyList<GroupDispatch> ApplyRelativeSlider(
        IReadOnlyList<RelativeSliderMember> members,
        int oldValue,
        int newValue)
    {
        var delta = newValue - oldValue;
        var dispatches = new List<GroupDispatch>();

        foreach (var member in members)
        {
            var clamped = Math.Clamp(member.CurrentValue + delta, 0, 100);
            if (clamped == member.CurrentValue)
            {
                continue;
            }

            ActionSpec action = member.ActionTemplate switch
            {
                BrightnessSet => new BrightnessSet(clamped),
                VolumeSet => new VolumeSet(clamped),
                _ => throw new InvalidOperationException(
                    $"Relative slider group member {member.WidgetId} has an action template " +
                    $"({member.ActionTemplate.GetType().Name}) that is not a value-bearing slider action.")
            };

            dispatches.Add(new GroupDispatch(member.TargetDeviceId, member.WidgetId, action));
        }

        return dispatches;
    }

    /// <summary>
    /// §4.2 ABSOLUTE_TOGGLE (mute groups): mute everything unless everything is
    /// already muted, in which case unmute everything. A member's local state
    /// is never inverted independently of the others — the desired absolute
    /// state is computed once and broadcast to every member.
    /// </summary>
    public static IReadOnlyList<GroupDispatch> ApplyAbsoluteToggle(IReadOnlyList<MuteMember> members)
    {
        if (members.Count == 0)
        {
            return Array.Empty<GroupDispatch>();
        }

        var allMuted = members.All(m => m.CurrentlyMuted);
        var desiredMuted = !allMuted;

        return members
            .Select(m => new GroupDispatch(m.TargetDeviceId, m.WidgetId, new SetMuted(desiredMuted)))
            .ToList();
    }

    /// <summary>
    /// §4.3 ABSOLUTE_MEDIA, explicit variant: the user picked a desired state
    /// directly via a play/pause button pair. Broadcast unconditionally to
    /// every member — no ambiguity to resolve.
    /// </summary>
    public static IReadOnlyList<GroupDispatch> ApplyAbsoluteMedia(
        IReadOnlyList<MediaMember> members,
        MediaState desiredState)
    {
        return members
            .Select(m => new GroupDispatch(m.TargetDeviceId, m.WidgetId, new MediaSetState(desiredState)))
            .ToList();
    }

    /// <summary>
    /// §4.3 ABSOLUTE_MEDIA, single-button toggle variant: when the UI exposes
    /// only one play/pause button (no explicit pair), the desired state is the
    /// opposite of the majority current state across members. Ties (equal
    /// playing/paused counts) resolve to PAUSED, i.e. "not unanimously playing"
    /// is treated the same as "majority paused".
    /// </summary>
    public static IReadOnlyList<GroupDispatch> ApplyAbsoluteMediaToggle(IReadOnlyList<MediaMember> members)
    {
        var desiredState = MajorityState(members) == MediaState.Playing ? MediaState.Paused : MediaState.Playing;
        return ApplyAbsoluteMedia(members, desiredState);
    }

    /// <summary>
    /// §4.3: MEDIA_NEXT/MEDIA_PREVIOUS are edge-triggered and always
    /// absolute-broadcast to every member unconditionally — there is no
    /// "state" to reconcile for a skip event.
    /// </summary>
    public static IReadOnlyList<GroupDispatch> ApplyMediaEdge(IReadOnlyList<MediaMember> members, bool isNext)
    {
        ActionSpec action = isNext ? new MediaNext() : new MediaPrevious();
        return members
            .Select(m => new GroupDispatch(m.TargetDeviceId, m.WidgetId, action))
            .ToList();
    }

    private static MediaState MajorityState(IReadOnlyList<MediaMember> members)
    {
        if (members.Count == 0)
        {
            return MediaState.Paused;
        }

        var playingCount = members.Count(m => m.CurrentState == MediaState.Playing);
        var pausedCount = members.Count - playingCount;
        return playingCount > pausedCount ? MediaState.Playing : MediaState.Paused;
    }
}
