namespace ControlDeck.Agent.PlatformActions;

public enum AppLaunchResult
{
    Launched,
    AppNotFound,
    PlatformError
}

/// <summary>Resolves an opaque `appId` (protocol/PROTOCOL.md §8) via the local app registry and launches it.</summary>
public interface IAppLauncher
{
    AppLaunchResult Launch(string appId);
}
