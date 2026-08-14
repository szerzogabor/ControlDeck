namespace ControlDeck.Protocol;

/// <summary>Wire shape of a grid position, e.g. inside a WidgetDto.</summary>
public sealed record GridPositionDto(int X, int Y);

/// <summary>Wire shape of a grid footprint, e.g. inside a WidgetDto.</summary>
public sealed record GridSizeDto(int Width, int Height);

/// <summary>
/// Wire shape of a Widget (protocol/PROTOCOL.md §3.7, mirrors
/// docs/ARCHITECTURE.md §3's domain Widget as a distinct wire type).
/// `Type` and (for groups) `ReconnectPolicy` are carried as raw strings on
/// the wire so unrecognized future values can be tolerated by the mapper
/// rather than failing JSON decode outright.
/// </summary>
public sealed record WidgetDto(
    string Id,
    string Type,
    GridPositionDto Position,
    GridSizeDto Size,
    string TargetDeviceId,
    ActionSpecDto Action,
    IReadOnlyDictionary<string, string>? Configuration
);

/// <summary>Wire shape of a Group (docs/ARCHITECTURE.md §3).</summary>
public sealed record GroupDto(
    string Id,
    string Name,
    string Kind,
    IReadOnlyList<string> MemberWidgetIds,
    string ReconnectPolicy
);

/// <summary>Wire shape of a Dashboard document, as carried inside DASHBOARD_SYNC.</summary>
public sealed record DashboardDto(
    string Id,
    string Name,
    long Version,
    IReadOnlyList<WidgetDto> Widgets,
    IReadOnlyList<GroupDto> Groups
);
