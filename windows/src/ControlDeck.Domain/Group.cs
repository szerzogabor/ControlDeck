namespace ControlDeck.Domain;

/// <summary>
/// A group re-interprets how the same widget actions are dispatched to
/// multiple targets; it never invents new action semantics. See
/// docs/ARCHITECTURE.md §3-4.
/// </summary>
public sealed record Group(
    GroupId Id,
    string Name,
    GroupKind Kind,
    IReadOnlyList<WidgetId> MemberWidgetIds,
    ReconnectPolicy ReconnectPolicy
);
