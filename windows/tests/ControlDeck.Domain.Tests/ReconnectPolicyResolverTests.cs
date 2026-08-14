using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Domain.Tests;

public class ReconnectPolicyResolverTests
{
    private static readonly DeviceId Device = new("device-drifted");
    private static readonly WidgetId Widget = new("widget-drifted");

    // docs/ARCHITECTURE.md §5: SYNC_GROUP_STATE corrects a drifted 35% back to the group's 60%.
    [Fact]
    public void SyncGroupState_CorrectsDriftedMemberToGroupAuthoritativeValue()
    {
        var groupAuthoritative = new VolumeSet(60); // member drifted to 35% while offline

        var dispatch = ReconnectPolicyResolver.Resolve(
            ReconnectPolicy.SyncGroupState, Device, Widget, groupAuthoritative);

        Assert.NotNull(dispatch);
        Assert.Equal(Device, dispatch!.TargetDeviceId);
        Assert.Equal(Widget, dispatch.WidgetId);
        Assert.Equal(new VolumeSet(60), dispatch.Action);
    }

    [Fact]
    public void KeepDeviceState_EmitsNoCorrectiveAction()
    {
        var dispatch = ReconnectPolicyResolver.Resolve(
            ReconnectPolicy.KeepDeviceState, Device, Widget, new VolumeSet(60));

        Assert.Null(dispatch);
    }

    [Fact]
    public void NoAction_EmitsNoCorrectiveAction()
    {
        var dispatch = ReconnectPolicyResolver.Resolve(
            ReconnectPolicy.NoAction, Device, Widget, new VolumeSet(60));

        Assert.Null(dispatch);
    }

    [Fact]
    public void SyncGroupState_WorksForAbsoluteMuteGroups()
    {
        var dispatch = ReconnectPolicyResolver.Resolve(
            ReconnectPolicy.SyncGroupState, Device, Widget, new SetMuted(true));

        Assert.NotNull(dispatch);
        Assert.Equal(new SetMuted(true), dispatch!.Action);
    }
}
