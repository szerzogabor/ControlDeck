using System.Security.Cryptography;

namespace ControlDeck.Agent.Pairing;

/// <summary>
/// A short-lived pairing token (protocol/PROTOCOL.md §3.2): either the
/// 6-digit PIN shown for manual entry, or the same value embedded in the QR
/// payload. Both flows share this one low-entropy, human-typeable token —
/// there's no separate "QR token" format.
/// </summary>
public sealed class PairingToken
{
    public string Value { get; }
    public DateTimeOffset ExpiresAtUtc { get; }

    private PairingToken(string value, DateTimeOffset expiresAtUtc)
    {
        Value = value;
        ExpiresAtUtc = expiresAtUtc;
    }

    public bool IsExpired => DateTimeOffset.UtcNow > ExpiresAtUtc;

    /// <summary>Generates a random 6-digit PIN, valid for 2 minutes (protocol/PROTOCOL.md §3.2).</summary>
    public static PairingToken GenerateSixDigitPin()
    {
        var value = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
        return new PairingToken(value, DateTimeOffset.UtcNow.AddMinutes(2));
    }
}
