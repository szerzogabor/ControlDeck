using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Domain.Tests;

public class GroupControllerTests
{
    private static DeviceId Device(string suffix) => new($"device-{suffix}");
    private static WidgetId Widget(string suffix) => new($"widget-{suffix}");

    // Worked example from docs/ARCHITECTURE.md §4.1:
    // relative slider 20,50,80 with PC +10 -> 30,60,90
    [Fact]
    public void RelativeSlider_AppliesDeltaToEveryMember()
    {
        var members = new[]
        {
            new RelativeSliderMember(Widget("pc"), Device("pc"), 20, new BrightnessSet(20)),
            new RelativeSliderMember(Widget("laptop"), Device("laptop"), 50, new BrightnessSet(50)),
            new RelativeSliderMember(Widget("tv"), Device("tv"), 80, new BrightnessSet(80)),
        };

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue: 20, newValue: 30);

        Assert.Equal(3, dispatches.Count);
        AssertBrightness(dispatches, Widget("pc"), 30);
        AssertBrightness(dispatches, Widget("laptop"), 60);
        AssertBrightness(dispatches, Widget("tv"), 90);
    }

    // Worked example from docs/ARCHITECTURE.md §4.1:
    // relative slider clamping 95,80,90 with Phone +20 -> 100,100,100 (independent clamp, no redistribution)
    [Fact]
    public void RelativeSlider_ClampsEachMemberIndependently()
    {
        var members = new[]
        {
            new RelativeSliderMember(Widget("phone"), Device("phone"), 95, new VolumeSet(95)),
            new RelativeSliderMember(Widget("pc"), Device("pc"), 80, new VolumeSet(80)),
            new RelativeSliderMember(Widget("tv"), Device("tv"), 90, new VolumeSet(90)),
        };

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue: 95, newValue: 115);

        Assert.Equal(3, dispatches.Count);
        AssertVolume(dispatches, Widget("phone"), 100);
        AssertVolume(dispatches, Widget("pc"), 100);
        AssertVolume(dispatches, Widget("tv"), 100);
    }

    [Fact]
    public void RelativeSlider_SkipsMembersAlreadySaturatedInDirectionOfTravel()
    {
        var members = new[]
        {
            new RelativeSliderMember(Widget("a"), Device("a"), 100, new VolumeSet(100)),
            new RelativeSliderMember(Widget("b"), Device("b"), 70, new VolumeSet(70)),
        };

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue: 70, newValue: 90);

        var dispatch = Assert.Single(dispatches);
        Assert.Equal(Widget("b"), dispatch.WidgetId);
        Assert.Equal(new VolumeSet(90), dispatch.Action);
    }

    [Fact]
    public void RelativeSlider_ClampsAtZeroLowerBound()
    {
        var members = new[]
        {
            new RelativeSliderMember(Widget("a"), Device("a"), 5, new BrightnessSet(5)),
            new RelativeSliderMember(Widget("b"), Device("b"), 0, new BrightnessSet(0)),
        };

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue: 5, newValue: -20);

        Assert.Single(dispatches); // "b" stays at 0, no-op skipped
        AssertBrightness(dispatches, Widget("a"), 0);
    }

    [Fact]
    public void RelativeSlider_SingleMemberGroup()
    {
        var members = new[]
        {
            new RelativeSliderMember(Widget("solo"), Device("solo"), 40, new VolumeSet(40)),
        };

        var dispatches = GroupController.ApplyRelativeSlider(members, oldValue: 40, newValue: 55);

        var dispatch = Assert.Single(dispatches);
        Assert.Equal(new VolumeSet(55), dispatch.Action);
    }

    [Fact]
    public void RelativeSlider_EmptyGroupProducesNoDispatches()
    {
        var dispatches = GroupController.ApplyRelativeSlider(Array.Empty<RelativeSliderMember>(), 10, 20);
        Assert.Empty(dispatches);
    }

    // docs/ARCHITECTURE.md §4.2: mute group toggle semantics.
    [Fact]
    public void AbsoluteToggle_UnmutedGroup_MutesAllMembers()
    {
        var members = new[]
        {
            new MuteMember(Widget("a"), Device("a"), false),
            new MuteMember(Widget("b"), Device("b"), false),
        };

        var dispatches = GroupController.ApplyAbsoluteToggle(members);

        Assert.All(dispatches, d => Assert.Equal(new SetMuted(true), d.Action));
    }

    [Fact]
    public void AbsoluteToggle_AllMutedGroup_UnmutesAllMembers()
    {
        var members = new[]
        {
            new MuteMember(Widget("a"), Device("a"), true),
            new MuteMember(Widget("b"), Device("b"), true),
        };

        var dispatches = GroupController.ApplyAbsoluteToggle(members);

        Assert.All(dispatches, d => Assert.Equal(new SetMuted(false), d.Action));
    }

    [Fact]
    public void AbsoluteToggle_PartiallyMutedGroup_MutesEverything()
    {
        var members = new[]
        {
            new MuteMember(Widget("a"), Device("a"), true),
            new MuteMember(Widget("b"), Device("b"), false),
        };

        var dispatches = GroupController.ApplyAbsoluteToggle(members);

        Assert.All(dispatches, d => Assert.Equal(new SetMuted(true), d.Action));
    }

    [Fact]
    public void AbsoluteToggle_EmptyGroupProducesNoDispatches()
    {
        var dispatches = GroupController.ApplyAbsoluteToggle(Array.Empty<MuteMember>());
        Assert.Empty(dispatches);
    }

    // Worked example from docs/ARCHITECTURE.md §4.3:
    // PC=Playing, Phone=Paused, Tablet=Playing (majority Playing) -> user picks Paused -> all get Pause.
    [Fact]
    public void AbsoluteMediaToggle_MajorityPlaying_BroadcastsPauseToAllMembers()
    {
        var members = new[]
        {
            new MediaMember(Widget("pc"), Device("pc"), MediaState.Playing),
            new MediaMember(Widget("phone"), Device("phone"), MediaState.Paused),
            new MediaMember(Widget("tablet"), Device("tablet"), MediaState.Playing),
        };

        var dispatches = GroupController.ApplyAbsoluteMediaToggle(members);

        Assert.Equal(3, dispatches.Count);
        Assert.All(dispatches, d => Assert.Equal(new MediaSetState(MediaState.Paused), d.Action));
    }

    [Fact]
    public void AbsoluteMediaToggle_MajorityPaused_BroadcastsPlayToAllMembers()
    {
        var members = new[]
        {
            new MediaMember(Widget("pc"), Device("pc"), MediaState.Paused),
            new MediaMember(Widget("phone"), Device("phone"), MediaState.Paused),
            new MediaMember(Widget("tablet"), Device("tablet"), MediaState.Playing),
        };

        var dispatches = GroupController.ApplyAbsoluteMediaToggle(members);

        Assert.All(dispatches, d => Assert.Equal(new MediaSetState(MediaState.Playing), d.Action));
    }

    [Fact]
    public void AbsoluteMedia_ExplicitDesiredState_BroadcastsUnconditionally()
    {
        var members = new[]
        {
            new MediaMember(Widget("pc"), Device("pc"), MediaState.Paused),
            new MediaMember(Widget("phone"), Device("phone"), MediaState.Paused),
        };

        var dispatches = GroupController.ApplyAbsoluteMedia(members, MediaState.Playing);

        Assert.All(dispatches, d => Assert.Equal(new MediaSetState(MediaState.Playing), d.Action));
    }

    [Fact]
    public void MediaEdge_NextIsBroadcastToEveryMemberUnconditionally()
    {
        var members = new[]
        {
            new MediaMember(Widget("pc"), Device("pc"), MediaState.Playing),
            new MediaMember(Widget("phone"), Device("phone"), MediaState.Paused),
        };

        var dispatches = GroupController.ApplyMediaEdge(members, isNext: true);

        Assert.Equal(2, dispatches.Count);
        Assert.All(dispatches, d => Assert.IsType<MediaNext>(d.Action));
    }

    [Fact]
    public void MediaEdge_PreviousIsBroadcastToEveryMemberUnconditionally()
    {
        var members = new[]
        {
            new MediaMember(Widget("pc"), Device("pc"), MediaState.Playing),
        };

        var dispatches = GroupController.ApplyMediaEdge(members, isNext: false);

        var dispatch = Assert.Single(dispatches);
        Assert.IsType<MediaPrevious>(dispatch.Action);
    }

    private static void AssertBrightness(IReadOnlyList<GroupDispatch> dispatches, WidgetId widgetId, int expected)
    {
        var dispatch = dispatches.Single(d => d.WidgetId == widgetId);
        Assert.Equal(new BrightnessSet(expected), dispatch.Action);
    }

    private static void AssertVolume(IReadOnlyList<GroupDispatch> dispatches, WidgetId widgetId, int expected)
    {
        var dispatch = dispatches.Single(d => d.WidgetId == widgetId);
        Assert.Equal(new VolumeSet(expected), dispatch.Action);
    }
}
