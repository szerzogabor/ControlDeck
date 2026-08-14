namespace ControlDeck.Agent.Persistence;

/// <summary>
/// One appId -> launch-mechanism mapping (protocol/PROTOCOL.md §8). `Target`
/// is either a full executable path or a shell invocation such as
/// `shell:AppsFolder\PackageFamilyName!AppId` (resolved via `Process.Start`
/// with `UseShellExecute = true`).
/// </summary>
public sealed record AppRegistryEntry(
    string AppId,
    string DisplayName,
    string Target
);

public interface IAppRegistryRepository
{
    IReadOnlyList<AppRegistryEntry> GetAll();

    AppRegistryEntry? GetByAppId(string appId);

    void Upsert(AppRegistryEntry entry);

    void Remove(string appId);
}
