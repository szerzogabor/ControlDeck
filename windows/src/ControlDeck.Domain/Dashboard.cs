namespace ControlDeck.Domain;

/// <summary>
/// A dashboard document. `Version` is monotonically increasing, incremented
/// by whichever device edits; see docs/ARCHITECTURE.md §6 and
/// <see cref="DashboardSyncResolver"/> for the sync/conflict rule.
/// </summary>
public sealed record Dashboard(
    DashboardId Id,
    string Name,
    long Version,
    IReadOnlyList<Widget> Widgets,
    IReadOnlyList<Group> Groups
);
