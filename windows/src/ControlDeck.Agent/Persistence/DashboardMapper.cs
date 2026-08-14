using ControlDeck.Domain;
using ControlDeck.Protocol;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// Maps between the pure <see cref="Domain"/> model and the
/// <see cref="Protocol"/> wire DTOs. Used both for persistence (the wire DTO
/// shape doubles as the on-disk JSON shape, reusing
/// <see cref="ProtocolCodec"/>'s already-correct ActionSpec (de)serialization
/// rather than duplicating a second JSON converter for Domain's ActionSpec
/// hierarchy) and for actually sending DASHBOARD_SYNC over the wire.
///
/// Unknown/unparseable wire enum strings (widget type, group kind, reconnect
/// policy) are surfaced as exceptions here rather than silently dropped,
/// since a locally-persisted or freshly-synced dashboard should never
/// contain a value this build doesn't understand — if it does, that's a
/// data problem worth surfacing loudly rather than corrupting the dashboard
/// on next save.
/// </summary>
public static class DashboardMapper
{
    public static DashboardDto ToDto(Dashboard dashboard) => new(
        dashboard.Id.Value,
        dashboard.Name,
        dashboard.Version,
        dashboard.Widgets.Select(ToDto).ToList(),
        dashboard.Groups.Select(ToDto).ToList());

    public static Dashboard ToDomain(DashboardDto dto) => new(
        new DashboardId(dto.Id),
        dto.Name,
        dto.Version,
        dto.Widgets.Select(ToDomain).ToList(),
        dto.Groups.Select(ToDomain).ToList());

    public static WidgetDto ToDto(Widget widget) => new(
        widget.Id.Value,
        WidgetTypeToWire(widget.Type),
        new GridPositionDto(widget.Position.X, widget.Position.Y),
        new GridSizeDto(widget.Size.Width, widget.Size.Height),
        widget.TargetDeviceId.Value,
        ToDto(widget.Action),
        widget.Configuration.Count == 0 ? null : new Dictionary<string, string>(widget.Configuration));

    public static Widget ToDomain(WidgetDto dto) => new(
        new WidgetId(dto.Id),
        WidgetTypeFromWire(dto.Type),
        new GridPosition(dto.Position.X, dto.Position.Y),
        new GridSize(dto.Size.Width, dto.Size.Height),
        new DeviceId(dto.TargetDeviceId),
        ToDomain(dto.Action),
        dto.Configuration ?? new Dictionary<string, string>());

    public static GroupDto ToDto(Group group) => new(
        group.Id.Value,
        group.Name,
        GroupKindToWire(group.Kind),
        group.MemberWidgetIds.Select(w => w.Value).ToList(),
        ReconnectPolicyToWire(group.ReconnectPolicy));

    public static Group ToDomain(GroupDto dto) => new(
        new GroupId(dto.Id),
        dto.Name,
        GroupKindFromWire(dto.Kind),
        dto.MemberWidgetIds.Select(id => new WidgetId(id)).ToList(),
        ReconnectPolicyFromWire(dto.ReconnectPolicy));

    public static ActionSpecDto ToDto(ActionSpec action) => action switch
    {
        BrightnessSet a => new BrightnessSetDto(a.Value),
        VolumeSet a => new VolumeSetDto(a.Value),
        SetMuted a => new SetMutedDto(a.Muted),
        MediaSetState a => new MediaSetStateDto(a.State == MediaState.Playing ? WireMediaState.Playing : WireMediaState.Paused),
        Domain.MediaNext => new MediaNextDto(),
        Domain.MediaPrevious => new MediaPreviousDto(),
        AppLaunch a => new AppLaunchDto(a.AppId),
        _ => throw new ArgumentOutOfRangeException(nameof(action), action, "Unknown ActionSpec subtype")
    };

    public static ActionSpec ToDomain(ActionSpecDto dto) => dto switch
    {
        BrightnessSetDto d => new BrightnessSet(d.Value),
        VolumeSetDto d => new VolumeSet(d.Value),
        SetMutedDto d => new SetMuted(d.Muted),
        MediaSetStateDto d => new MediaSetState(d.State == WireMediaState.Playing ? MediaState.Playing : MediaState.Paused),
        Protocol.MediaNextDto => new Domain.MediaNext(),
        Protocol.MediaPreviousDto => new Domain.MediaPrevious(),
        AppLaunchDto d => new AppLaunch(d.AppId),
        UnknownActionDto d => throw new InvalidOperationException(
            $"Cannot map unrecognized action type \"{d.RawType}\" into the Domain model."),
        _ => throw new ArgumentOutOfRangeException(nameof(dto), dto, "Unknown ActionSpecDto subtype")
    };

    private static string WidgetTypeToWire(WidgetType type) => type switch
    {
        WidgetType.SliderBrightness => "SLIDER_BRIGHTNESS",
        WidgetType.SliderVolume => "SLIDER_VOLUME",
        WidgetType.ButtonMute => "BUTTON_MUTE",
        WidgetType.ButtonMediaPlayPause => "BUTTON_MEDIA_PLAY_PAUSE",
        WidgetType.ButtonMediaNext => "BUTTON_MEDIA_NEXT",
        WidgetType.ButtonMediaPrevious => "BUTTON_MEDIA_PREVIOUS",
        WidgetType.AppLaunch => "APP_LAUNCH",
        _ => throw new ArgumentOutOfRangeException(nameof(type), type, "Unknown WidgetType")
    };

    private static WidgetType WidgetTypeFromWire(string wire) => wire switch
    {
        "SLIDER_BRIGHTNESS" => WidgetType.SliderBrightness,
        "SLIDER_VOLUME" => WidgetType.SliderVolume,
        "BUTTON_MUTE" => WidgetType.ButtonMute,
        "BUTTON_MEDIA_PLAY_PAUSE" => WidgetType.ButtonMediaPlayPause,
        "BUTTON_MEDIA_NEXT" => WidgetType.ButtonMediaNext,
        "BUTTON_MEDIA_PREVIOUS" => WidgetType.ButtonMediaPrevious,
        "APP_LAUNCH" => WidgetType.AppLaunch,
        _ => throw new InvalidOperationException($"Unknown widget type \"{wire}\".")
    };

    private static string GroupKindToWire(GroupKind kind) => kind switch
    {
        GroupKind.RelativeSlider => "RELATIVE_SLIDER",
        GroupKind.AbsoluteToggle => "ABSOLUTE_TOGGLE",
        GroupKind.AbsoluteMedia => "ABSOLUTE_MEDIA",
        _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, "Unknown GroupKind")
    };

    private static GroupKind GroupKindFromWire(string wire) => wire switch
    {
        "RELATIVE_SLIDER" => GroupKind.RelativeSlider,
        "ABSOLUTE_TOGGLE" => GroupKind.AbsoluteToggle,
        "ABSOLUTE_MEDIA" => GroupKind.AbsoluteMedia,
        _ => throw new InvalidOperationException($"Unknown group kind \"{wire}\".")
    };

    private static string ReconnectPolicyToWire(ReconnectPolicy policy) => policy switch
    {
        ReconnectPolicy.SyncGroupState => "SYNC_GROUP_STATE",
        ReconnectPolicy.KeepDeviceState => "KEEP_DEVICE_STATE",
        ReconnectPolicy.NoAction => "NO_ACTION",
        _ => throw new ArgumentOutOfRangeException(nameof(policy), policy, "Unknown ReconnectPolicy")
    };

    private static ReconnectPolicy ReconnectPolicyFromWire(string wire) => wire switch
    {
        "SYNC_GROUP_STATE" => ReconnectPolicy.SyncGroupState,
        "KEEP_DEVICE_STATE" => ReconnectPolicy.KeepDeviceState,
        "NO_ACTION" => ReconnectPolicy.NoAction,
        _ => throw new InvalidOperationException($"Unknown reconnect policy \"{wire}\".")
    };
}
