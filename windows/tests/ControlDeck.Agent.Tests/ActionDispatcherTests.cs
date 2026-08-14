using ControlDeck.Agent.Dispatch;
using ControlDeck.Agent.PlatformActions;
using ControlDeck.Protocol;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ControlDeck.Agent.Tests;

public class ActionDispatcherTests
{
    private static ActionDispatcher BuildDispatcher(
        FakeVolumeController? volume = null,
        FakeBrightnessController? brightness = null,
        FakeMediaController? media = null,
        FakeAppLauncher? appLauncher = null,
        out CapabilityRegistry capabilityRegistry)
    {
        volume ??= new FakeVolumeController();
        brightness ??= new FakeBrightnessController();
        media ??= new FakeMediaController();
        appLauncher ??= new FakeAppLauncher();
        capabilityRegistry = new CapabilityRegistry(volume, brightness);

        return new ActionDispatcher(
            volume, brightness, media, appLauncher, capabilityRegistry, NullLogger<ActionDispatcher>.Instance);
    }

    [Fact]
    public void VolumeSet_WithinRange_Succeeds()
    {
        var volume = new FakeVolumeController();
        var dispatcher = BuildDispatcher(volume: volume, out _);

        var (success, errorCode, resultingState) = dispatcher.Execute(new VolumeSetDto(70));

        Assert.True(success);
        Assert.Null(errorCode);
        Assert.Equal(70, volume.Volume);
        var state = Assert.IsType<VolumeSetDto>(resultingState);
        Assert.Equal(70, state.Value);
    }

    [Theory]
    [InlineData(-1)]
    [InlineData(101)]
    public void VolumeSet_OutOfRange_ReturnsInvalidValue(int value)
    {
        var dispatcher = BuildDispatcher(out _);

        var (success, errorCode, _) = dispatcher.Execute(new VolumeSetDto(value));

        Assert.False(success);
        Assert.Equal("INVALID_VALUE", errorCode);
    }

    [Fact]
    public void BrightnessSet_WhenBrightnessUnavailable_ReturnsUnsupportedCapability()
    {
        var brightness = new FakeBrightnessController { IsAvailable = false };
        var dispatcher = BuildDispatcher(brightness: brightness, out _);

        var (success, errorCode, _) = dispatcher.Execute(new BrightnessSetDto(50));

        Assert.False(success);
        Assert.Equal("UNSUPPORTED_CAPABILITY", errorCode);
    }

    [Fact]
    public void VolumeSet_WhenVolumeUnavailable_ReturnsUnsupportedCapability()
    {
        var volume = new FakeVolumeController { IsAvailable = false };
        var dispatcher = BuildDispatcher(volume: volume, out var registry);

        Assert.DoesNotContain(Domain.Capability.Volume, registry.CurrentCapabilities());

        var (success, errorCode, _) = dispatcher.Execute(new VolumeSetDto(50));

        Assert.False(success);
        Assert.Equal("UNSUPPORTED_CAPABILITY", errorCode);
    }

    [Fact]
    public void SetMuted_TogglesFakeController()
    {
        var volume = new FakeVolumeController { Muted = false };
        var dispatcher = BuildDispatcher(volume: volume, out _);

        var (success, _, resultingState) = dispatcher.Execute(new SetMutedDto(true));

        Assert.True(success);
        Assert.True(volume.Muted);
        Assert.Equal(new SetMutedDto(true), resultingState);
    }

    [Fact]
    public void MediaSetState_DelegatesToMediaController()
    {
        var media = new FakeMediaController();
        var dispatcher = BuildDispatcher(media: media, out _);

        var (success, _, _) = dispatcher.Execute(new MediaSetStateDto(WireMediaState.Paused));

        Assert.True(success);
        Assert.Equal(Domain.MediaState.Paused, media.LastSetState);
    }

    [Fact]
    public void MediaNext_DelegatesToMediaController()
    {
        var media = new FakeMediaController();
        var dispatcher = BuildDispatcher(media: media, out _);

        var (success, _, _) = dispatcher.Execute(new MediaNextDto());

        Assert.True(success);
        Assert.Equal(1, media.NextCount);
    }

    [Fact]
    public void AppLaunch_Found_Succeeds()
    {
        var launcher = new FakeAppLauncher();
        var dispatcher = BuildDispatcher(appLauncher: launcher, out _);

        var (success, errorCode, _) = dispatcher.Execute(new AppLaunchDto("spotify"));

        Assert.True(success);
        Assert.Null(errorCode);
        Assert.Contains("spotify", launcher.LaunchedAppIds);
    }

    [Fact]
    public void AppLaunch_NotFound_ReturnsAppNotFound()
    {
        var launcher = new FakeAppLauncher();
        launcher.ConfigureResult("unknown-app", AppLaunchResult.AppNotFound);
        var dispatcher = BuildDispatcher(appLauncher: launcher, out _);

        var (success, errorCode, _) = dispatcher.Execute(new AppLaunchDto("unknown-app"));

        Assert.False(success);
        Assert.Equal("APP_NOT_FOUND", errorCode);
    }

    [Fact]
    public void UnknownActionType_ReturnsUnsupportedCapability_WithoutThrowing()
    {
        var dispatcher = BuildDispatcher(out _);

        var (success, errorCode, _) = dispatcher.Execute(new UnknownActionDto("FUTURE_ACTION"));

        Assert.False(success);
        Assert.Equal("UNSUPPORTED_CAPABILITY", errorCode);
    }

    [Fact]
    public void PlatformException_IsCaughtAndReturnsPlatformError_NotThrown()
    {
        var volume = new ThrowingVolumeController();
        var brightness = new FakeBrightnessController();
        var media = new FakeMediaController();
        var appLauncher = new FakeAppLauncher();
        var registry = new CapabilityRegistry(volume, brightness);
        var dispatcher = new ActionDispatcher(volume, brightness, media, appLauncher, registry, NullLogger<ActionDispatcher>.Instance);

        var (success, errorCode, _) = dispatcher.Execute(new VolumeSetDto(50));

        Assert.False(success);
        Assert.Equal("PLATFORM_ERROR", errorCode);
    }
}
