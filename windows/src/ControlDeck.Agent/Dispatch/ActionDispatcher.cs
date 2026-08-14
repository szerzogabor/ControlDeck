using ControlDeck.Agent.PlatformActions;
using ControlDeck.Domain;
using ControlDeck.Protocol;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Dispatch;

/// <summary>
/// The Action Engine (docs/ARCHITECTURE.md §2): receives an incoming
/// protocol ACTION, maps it to a Domain ActionSpec, checks capability
/// support, calls the appropriate platform-action interface, and returns an
/// ACTION_RESULT. Every platform call is wrapped so an exception becomes a
/// clean `success:false` result (protocol/PROTOCOL.md §3.5) rather than
/// propagating and taking down the connection (docs/ARCHITECTURE.md §7).
/// </summary>
public sealed class ActionDispatcher
{
    private readonly IVolumeController _volume;
    private readonly IBrightnessController _brightness;
    private readonly IMediaController _media;
    private readonly IAppLauncher _appLauncher;
    private readonly CapabilityRegistry _capabilityRegistry;
    private readonly ILogger<ActionDispatcher> _logger;

    public ActionDispatcher(
        IVolumeController volume,
        IBrightnessController brightness,
        IMediaController media,
        IAppLauncher appLauncher,
        CapabilityRegistry capabilityRegistry,
        ILogger<ActionDispatcher> logger)
    {
        _volume = volume;
        _brightness = brightness;
        _media = media;
        _appLauncher = appLauncher;
        _capabilityRegistry = capabilityRegistry;
        _logger = logger;
    }

    /// <summary>
    /// Executes a locally-received ACTION and returns the ActionResultPayload
    /// to send back (with `correlatesTo` set by the caller, which knows the
    /// originating envelope's `messageId`).
    /// </summary>
    public (bool Success, string? ErrorCode, ActionSpecDto? ResultingState) Execute(ActionSpecDto actionDto)
    {
        if (actionDto is UnknownActionDto unknown)
        {
            _logger.LogWarning("Received ACTION with unrecognized type \"{Type}\".", unknown.RawType);
            return (false, "UNSUPPORTED_CAPABILITY", null);
        }

        ActionSpec action;
        try
        {
            action = MapToDomain(actionDto);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to map incoming action to domain model.");
            return (false, "INVALID_ACTION", null);
        }

        var requiredCapability = CapabilityValidator.RequiredCapability(action);
        if (!_capabilityRegistry.CurrentCapabilities().Contains(requiredCapability))
        {
            return (false, "UNSUPPORTED_CAPABILITY", null);
        }

        try
        {
            return ExecuteOnPlatform(action);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Platform action {Action} threw unexpectedly.", action.GetType().Name);
            return (false, "PLATFORM_ERROR", null);
        }
    }

    private (bool, string?, ActionSpecDto?) ExecuteOnPlatform(ActionSpec action)
    {
        switch (action)
        {
            case BrightnessSet a:
                if (a.Value is < 0 or > 100)
                {
                    return (false, "INVALID_VALUE", null);
                }

                _brightness.SetBrightness(a.Value);
                return (true, null, new BrightnessSetDto(_brightness.GetBrightness()));

            case VolumeSet a:
                if (a.Value is < 0 or > 100)
                {
                    return (false, "INVALID_VALUE", null);
                }

                _volume.SetVolume(a.Value);
                return (true, null, new VolumeSetDto(_volume.GetVolume()));

            case SetMuted a:
                _volume.SetMuted(a.Muted);
                return (true, null, new SetMutedDto(_volume.GetMuted()));

            case MediaSetState a:
                _media.SetState(a.State);
                return (true, null, new MediaSetStateDto(a.State == MediaState.Playing ? WireMediaState.Playing : WireMediaState.Paused));

            case Domain.MediaNext:
                _media.Next();
                return (true, null, new MediaNextDto());

            case Domain.MediaPrevious:
                _media.Previous();
                return (true, null, new MediaPreviousDto());

            case AppLaunch a:
                var result = _appLauncher.Launch(a.AppId);
                return result switch
                {
                    AppLaunchResult.Launched => (true, null, new AppLaunchDto(a.AppId)),
                    AppLaunchResult.AppNotFound => (false, "APP_NOT_FOUND", null),
                    _ => (false, "PLATFORM_ERROR", null)
                };

            default:
                return (false, "UNSUPPORTED_CAPABILITY", null);
        }
    }

    private static ActionSpec MapToDomain(ActionSpecDto dto) => dto switch
    {
        BrightnessSetDto d => new BrightnessSet(d.Value),
        VolumeSetDto d => new VolumeSet(d.Value),
        SetMutedDto d => new SetMuted(d.Muted),
        MediaSetStateDto d => new MediaSetState(d.State == WireMediaState.Playing ? MediaState.Playing : MediaState.Paused),
        Protocol.MediaNextDto => new Domain.MediaNext(),
        Protocol.MediaPreviousDto => new Domain.MediaPrevious(),
        AppLaunchDto d => new AppLaunch(d.AppId),
        _ => throw new InvalidOperationException($"Cannot map {dto.GetType().Name} to a Domain ActionSpec.")
    };
}
