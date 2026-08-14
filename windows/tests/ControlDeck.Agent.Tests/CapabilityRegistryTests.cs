using ControlDeck.Agent.PlatformActions;
using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Agent.Tests;

public class CapabilityRegistryTests
{
    [Fact]
    public void AlwaysIncludesMediaAndAppLaunch()
    {
        var registry = new CapabilityRegistry(
            new FakeVolumeController { IsAvailable = false },
            new FakeBrightnessController { IsAvailable = false });

        var caps = registry.CurrentCapabilities();

        Assert.Contains(Capability.MediaPlayPause, caps);
        Assert.Contains(Capability.MediaNext, caps);
        Assert.Contains(Capability.MediaPrevious, caps);
        Assert.Contains(Capability.AppLaunch, caps);
        Assert.DoesNotContain(Capability.Volume, caps);
        Assert.DoesNotContain(Capability.Mute, caps);
        Assert.DoesNotContain(Capability.Brightness, caps);
    }

    [Fact]
    public void IncludesVolumeAndMute_WhenVolumeControllerAvailable()
    {
        var registry = new CapabilityRegistry(
            new FakeVolumeController { IsAvailable = true },
            new FakeBrightnessController { IsAvailable = false });

        var caps = registry.CurrentCapabilities();

        Assert.Contains(Capability.Volume, caps);
        Assert.Contains(Capability.Mute, caps);
    }

    [Fact]
    public void IncludesBrightness_OnlyWhenBrightnessControllerAvailable()
    {
        var unavailable = new CapabilityRegistry(new FakeVolumeController(), new FakeBrightnessController { IsAvailable = false });
        var available = new CapabilityRegistry(new FakeVolumeController(), new FakeBrightnessController { IsAvailable = true });

        Assert.DoesNotContain(Capability.Brightness, unavailable.CurrentCapabilities());
        Assert.Contains(Capability.Brightness, available.CurrentCapabilities());
    }

    [Theory]
    [InlineData(Capability.Brightness, "BRIGHTNESS")]
    [InlineData(Capability.Volume, "VOLUME")]
    [InlineData(Capability.Mute, "MUTE")]
    [InlineData(Capability.MediaPlayPause, "MEDIA_PLAY_PAUSE")]
    [InlineData(Capability.MediaNext, "MEDIA_NEXT")]
    [InlineData(Capability.MediaPrevious, "MEDIA_PREVIOUS")]
    [InlineData(Capability.AppLaunch, "APP_LAUNCH")]
    public void WireTokenRoundTrips(Capability capability, string wireToken)
    {
        Assert.Equal(wireToken, CapabilityRegistry.ToWireToken(capability));
        Assert.Equal(capability, CapabilityRegistry.FromWireToken(wireToken));
    }

    [Fact]
    public void FromWireToken_UnknownToken_ReturnsNull_InsteadOfThrowing()
    {
        Assert.Null(CapabilityRegistry.FromWireToken("HOLOGRAM_PROJECTION"));
    }
}
