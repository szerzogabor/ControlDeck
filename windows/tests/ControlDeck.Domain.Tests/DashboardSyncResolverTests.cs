using ControlDeck.Domain;
using Xunit;

namespace ControlDeck.Domain.Tests;

public class DashboardSyncResolverTests
{
    [Fact]
    public void NoLocalDashboard_AlwaysAppliesIncoming()
    {
        var outcome = DashboardSyncResolver.Resolve(
            localDashboardExists: false,
            incomingVersion: 1, localVersion: 0,
            incomingTimestamp: 100, localTimestamp: 0,
            incomingSourceDeviceId: "device-a", localSourceDeviceId: "device-b");

        Assert.Equal(SyncOutcome.ApplyIncoming, outcome);
    }

    // docs/ARCHITECTURE.md §6 worked example: v12 base, concurrent v13/v14 edits -> v14 wins.
    [Fact]
    public void HigherVersion_WinsOutright()
    {
        var outcome = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 14, localVersion: 13,
            incomingTimestamp: 1000, localTimestamp: 5000, // timestamp irrelevant when versions differ
            incomingSourceDeviceId: "device-a", localSourceDeviceId: "device-b");

        Assert.Equal(SyncOutcome.ApplyIncoming, outcome);
    }

    [Fact]
    public void LowerVersion_IsDiscardedAndRepliesWithLocal()
    {
        var outcome = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 13, localVersion: 14,
            incomingTimestamp: 5000, localTimestamp: 1000,
            incomingSourceDeviceId: "device-a", localSourceDeviceId: "device-b");

        Assert.Equal(SyncOutcome.KeepLocalAndReplyWithLocal, outcome);
    }

    [Fact]
    public void EqualVersion_TieBreaksByTimestampFirst()
    {
        var incomingNewer = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 14, localVersion: 14,
            incomingTimestamp: 2000, localTimestamp: 1000,
            incomingSourceDeviceId: "device-a", localSourceDeviceId: "device-z");

        Assert.Equal(SyncOutcome.ApplyIncoming, incomingNewer);

        var localNewer = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 14, localVersion: 14,
            incomingTimestamp: 1000, localTimestamp: 2000,
            incomingSourceDeviceId: "device-z", localSourceDeviceId: "device-a");

        Assert.Equal(SyncOutcome.KeepLocalAndReplyWithLocal, localNewer);
    }

    // Deterministic final fallback when version AND timestamp are equal.
    [Fact]
    public void EqualVersionAndTimestamp_TieBreaksBySourceDeviceIdOrdinalOrdering()
    {
        var incomingWins = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 5, localVersion: 5,
            incomingTimestamp: 1000, localTimestamp: 1000,
            incomingSourceDeviceId: "zzz-device", localSourceDeviceId: "aaa-device");

        Assert.Equal(SyncOutcome.ApplyIncoming, incomingWins);

        var localWins = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 5, localVersion: 5,
            incomingTimestamp: 1000, localTimestamp: 1000,
            incomingSourceDeviceId: "aaa-device", localSourceDeviceId: "zzz-device");

        Assert.Equal(SyncOutcome.KeepLocalAndReplyWithLocal, localWins);
    }

    [Fact]
    public void EqualVersionTimestampAndSourceDeviceId_IsDeterministicNoOp()
    {
        var outcome = DashboardSyncResolver.Resolve(
            localDashboardExists: true,
            incomingVersion: 5, localVersion: 5,
            incomingTimestamp: 1000, localTimestamp: 1000,
            incomingSourceDeviceId: "same-device", localSourceDeviceId: "same-device");

        Assert.Equal(SyncOutcome.KeepLocalAndReplyWithLocal, outcome);
    }
}
