namespace ControlDeck.Domain;

/// <summary>Widget position within a dashboard's uniform grid.</summary>
public readonly record struct GridPosition(int X, int Y);

/// <summary>Widget footprint within a dashboard's uniform grid, in cells.</summary>
public readonly record struct GridSize(int Width, int Height);
