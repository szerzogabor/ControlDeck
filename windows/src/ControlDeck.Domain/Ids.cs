namespace ControlDeck.Domain;

/// <summary>
/// Strongly-typed identifier wrappers. All ControlDeck ids are UUID strings on
/// the wire (see protocol/PROTOCOL.md §2); wrapping them prevents accidentally
/// passing a DeviceId where a WidgetId is expected, etc.
/// </summary>
public readonly record struct DeviceId(string Value)
{
    public override string ToString() => Value;

    public static DeviceId NewId() => new(Guid.NewGuid().ToString());
}

public readonly record struct WidgetId(string Value)
{
    public override string ToString() => Value;

    public static WidgetId NewId() => new(Guid.NewGuid().ToString());
}

public readonly record struct DashboardId(string Value)
{
    public override string ToString() => Value;

    public static DashboardId NewId() => new(Guid.NewGuid().ToString());
}

public readonly record struct GroupId(string Value)
{
    public override string ToString() => Value;

    public static GroupId NewId() => new(Guid.NewGuid().ToString());
}
