using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Domain.Tests;

public class CapabilityValidatorTests
{
    [Fact]
    public void ActionSupported_WhenTargetHasRequiredCapability()
    {
        var caps = new HashSet<Capability> { Capability.Volume, Capability.Mute };

        Assert.True(CapabilityValidator.IsSupported(new VolumeSet(50), caps));
    }

    [Fact]
    public void ActionUnsupported_WhenTargetLacksRequiredCapability()
    {
        var caps = new HashSet<Capability> { Capability.Volume, Capability.Mute };

        Assert.False(CapabilityValidator.IsSupported(new BrightnessSet(50), caps));
    }

    [Fact]
    public void WidgetFlaggedUnsupported_WhenTargetDeviceLacksCapability()
    {
        var widget = new Widget(
            new WidgetId("w1"),
            WidgetType.SliderBrightness,
            new GridPosition(0, 0),
            new GridSize(1, 1),
            new DeviceId("desktop-no-brightness-sensor"),
            new BrightnessSet(50),
            new Dictionary<string, string>());

        var targetCapabilities = new HashSet<Capability> { Capability.Volume, Capability.Mute };

        Assert.False(CapabilityValidator.IsSupported(widget, targetCapabilities));
    }

    [Fact]
    public void WidgetSupported_WhenTargetDeviceHasCapability()
    {
        var widget = new Widget(
            new WidgetId("w1"),
            WidgetType.ButtonMute,
            new GridPosition(0, 0),
            new GridSize(1, 1),
            new DeviceId("pc"),
            new SetMuted(true),
            new Dictionary<string, string>());

        var targetCapabilities = new HashSet<Capability> { Capability.Mute };

        Assert.True(CapabilityValidator.IsSupported(widget, targetCapabilities));
    }

    [Theory]
    [InlineData(WidgetType.SliderBrightness, Capability.Brightness)]
    [InlineData(WidgetType.SliderVolume, Capability.Volume)]
    [InlineData(WidgetType.ButtonMute, Capability.Mute)]
    [InlineData(WidgetType.ButtonMediaPlayPause, Capability.MediaPlayPause)]
    [InlineData(WidgetType.ButtonMediaNext, Capability.MediaNext)]
    [InlineData(WidgetType.ButtonMediaPrevious, Capability.MediaPrevious)]
    [InlineData(WidgetType.AppLaunch, Capability.AppLaunch)]
    public void RequiredCapability_MapsEveryWidgetTypeCorrectly(WidgetType widgetType, Capability expected)
    {
        Assert.Equal(expected, CapabilityValidator.RequiredCapability(widgetType));
    }
}
