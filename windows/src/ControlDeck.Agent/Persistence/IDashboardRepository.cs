using ControlDeck.Domain;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// Dashboard CRUD + persistence, kept behind an interface (dependency
/// inversion) so the JSON-file-backed implementation is swappable and the
/// Agent.Tests project can substitute an in-memory fake without touching
/// disk.
/// </summary>
public interface IDashboardRepository
{
    IReadOnlyList<Dashboard> GetAll();

    Dashboard? GetById(DashboardId id);

    /// <summary>Inserts or overwrites a dashboard as-is (caller owns version bumping).</summary>
    void Upsert(Dashboard dashboard);

    void Delete(DashboardId id);
}
