using ControlDeck.Agent.Persistence;
using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Agent.Tests;

public class DashboardMapperTests
{
    [Fact]
    public void Dashboard_RoundTripsThroughWireDto()
    {
        var widget = new Widget(
            new WidgetId("w1"),
            WidgetType.SliderVolume,
            new GridPosition(1, 2),
            new GridSize(2, 1),
            new DeviceId("pc-1"),
            new VolumeSet(40),
            new Dictionary<string, string> { ["label"] = "PC Volume" });

        var group = new Group(
            new GroupId("g1"),
            "Speakers",
            GroupKind.RelativeSlider,
            new List<WidgetId> { widget.Id },
            ReconnectPolicy.SyncGroupState);

        var dashboard = new Dashboard(new DashboardId("d1"), "Gaming", 3, new List<Widget> { widget }, new List<Group> { group });

        var dto = DashboardMapper.ToDto(dashboard);
        var roundTripped = DashboardMapper.ToDomain(dto);

        Assert.Equal(dashboard.Id, roundTripped.Id);
        Assert.Equal(dashboard.Name, roundTripped.Name);
        Assert.Equal(dashboard.Version, roundTripped.Version);
        Assert.Single(roundTripped.Widgets);
        Assert.Single(roundTripped.Groups);

        var roundTrippedWidget = roundTripped.Widgets[0];
        Assert.Equal(widget.Type, roundTrippedWidget.Type);
        Assert.Equal(widget.TargetDeviceId, roundTrippedWidget.TargetDeviceId);
        Assert.Equal(widget.Action, roundTrippedWidget.Action);
        Assert.Equal("PC Volume", roundTrippedWidget.Configuration["label"]);

        var roundTrippedGroup = roundTripped.Groups[0];
        Assert.Equal(group.Kind, roundTrippedGroup.Kind);
        Assert.Equal(group.ReconnectPolicy, roundTrippedGroup.ReconnectPolicy);
        Assert.Equal(group.MemberWidgetIds, roundTrippedGroup.MemberWidgetIds);
    }

    [Theory]
    [InlineData(WidgetType.SliderBrightness)]
    [InlineData(WidgetType.SliderVolume)]
    [InlineData(WidgetType.ButtonMute)]
    [InlineData(WidgetType.ButtonMediaPlayPause)]
    [InlineData(WidgetType.ButtonMediaNext)]
    [InlineData(WidgetType.ButtonMediaPrevious)]
    [InlineData(WidgetType.AppLaunch)]
    public void EveryWidgetType_RoundTrips(WidgetType type)
    {
        ActionSpec action = type switch
        {
            WidgetType.SliderBrightness => new BrightnessSet(10),
            WidgetType.SliderVolume => new VolumeSet(10),
            WidgetType.ButtonMute => new SetMuted(false),
            WidgetType.ButtonMediaPlayPause => new MediaSetState(MediaState.Playing),
            WidgetType.ButtonMediaNext => new MediaNext(),
            WidgetType.ButtonMediaPrevious => new MediaPrevious(),
            WidgetType.AppLaunch => new AppLaunch("spotify"),
            _ => throw new ArgumentOutOfRangeException(nameof(type)),
        };

        var widget = new Widget(
            new WidgetId("w"), type, new GridPosition(0, 0), new GridSize(1, 1),
            new DeviceId("pc"), action, new Dictionary<string, string>());

        var dto = DashboardMapper.ToDto(widget);
        var roundTripped = DashboardMapper.ToDomain(dto);

        Assert.Equal(type, roundTripped.Type);
        Assert.Equal(action, roundTripped.Action);
    }

    [Theory]
    [InlineData(ReconnectPolicy.SyncGroupState)]
    [InlineData(ReconnectPolicy.KeepDeviceState)]
    [InlineData(ReconnectPolicy.NoAction)]
    public void EveryReconnectPolicy_RoundTrips(ReconnectPolicy policy)
    {
        var group = new Group(new GroupId("g"), "G", GroupKind.AbsoluteToggle, new List<WidgetId>(), policy);

        var dto = DashboardMapper.ToDto(group);
        var roundTripped = DashboardMapper.ToDomain(dto);

        Assert.Equal(policy, roundTripped.ReconnectPolicy);
    }
}
