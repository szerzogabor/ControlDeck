namespace ControlDeck.Domain;

/// <summary>
/// A single dashboard control. See docs/ARCHITECTURE.md §3. `Action` mirrors
/// the protocol Action shape (appId for APP_LAUNCH widgets); `Configuration`
/// carries widget-specific display metadata (e.g. a custom label) that never
/// needs to be interpreted by group/sync logic.
/// </summary>
public sealed record Widget(
    WidgetId Id,
    WidgetType Type,
    GridPosition Position,
    GridSize Size,
    DeviceId TargetDeviceId,
    ActionSpec Action,
    IReadOnlyDictionary<string, string> Configuration
);
