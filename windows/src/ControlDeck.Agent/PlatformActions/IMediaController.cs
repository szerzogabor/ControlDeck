using ControlDeck.Domain;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>Controls whatever app currently owns the system media session, via simulated hardware media keys.</summary>
public interface IMediaController
{
    /// <summary>
    /// Best-effort: sends the play/pause key unconditionally. See
    /// <see cref="MediaKeyController"/>'s class doc for why MEDIA_SET_STATE
    /// can't be verified against real OS play/pause truth in this
    /// implementation.
    /// </summary>
    void SetState(MediaState desiredState);

    void Next();

    void Previous();
}
