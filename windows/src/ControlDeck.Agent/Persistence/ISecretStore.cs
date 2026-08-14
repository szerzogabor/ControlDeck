namespace ControlDeck.Agent.Persistence;

/// <summary>
/// Stores each paired peer's shared secret (protocol/PROTOCOL.md §3.2/§7 —
/// "never transmitted again in plaintext ... never logged"). Kept behind an
/// interface so pairing/auth code never has to know it's backed by DPAPI on
/// disk, and so tests can substitute an in-memory fake.
/// </summary>
public interface ISecretStore
{
    void Set(string deviceId, byte[] sharedSecret);

    bool TryGet(string deviceId, out byte[]? sharedSecret);

    void Remove(string deviceId);
}
