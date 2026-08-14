using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using ControlDeck.Domain;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Simulates hardware media keys via `SendInput` (VK_MEDIA_PLAY_PAUSE /
/// VK_MEDIA_NEXT_TRACK / VK_MEDIA_PREV_TRACK). This is the standard reliable
/// way to control whatever media app currently has focus/registered as the
/// system media session on Windows, without needing per-app integrations.
///
/// DESIGN DECISION / KNOWN LIMITATION: `MEDIA_SET_STATE(desired)`
/// (protocol/PROTOCOL.md §3.5) is implemented as *best-effort* — this class
/// always sends the play/pause key unconditionally, since Windows has no
/// cheap, synchronous way to ask "is something currently playing" from this
/// P/Invoke layer. The accurate alternative is
/// `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager`
/// (SMTC) via a CsWinRT/`Microsoft.Windows.SDK.Contracts` projection, which
/// would let us read the actual current `PlaybackStatus` and only send the
/// key if it disagrees with the requested state. That was deliberately left
/// out of this MVP to avoid pulling in the WinRT projection toolchain (and
/// its own set of packaging/activation quirks) for a single capability; the
/// key-press approach is simple, dependency-free, and matches what a
/// physical Fn+media key does, at the cost of a single potential
/// double-toggle if ControlDeck's belief about play/pause state has drifted
/// from reality (e.g. the user paused locally without ControlDeck knowing).
/// A future version should replace this with SMTC for exact confirmation.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class MediaKeyController : IMediaController
{
    private const ushort VK_MEDIA_NEXT_TRACK = 0xB0;
    private const ushort VK_MEDIA_PREV_TRACK = 0xB1;
    private const ushort VK_MEDIA_PLAY_PAUSE = 0xB3;

    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;

    private readonly ILogger<MediaKeyController> _logger;

    public MediaKeyController(ILogger<MediaKeyController> logger)
    {
        _logger = logger;
    }

    public void SetState(MediaState desiredState)
    {
        _logger.LogInformation("MEDIA_SET_STATE({DesiredState}) — sending play/pause key (best-effort, see class doc).", desiredState);
        SendKey(VK_MEDIA_PLAY_PAUSE);
    }

    public void Next() => SendKey(VK_MEDIA_NEXT_TRACK);

    public void Previous() => SendKey(VK_MEDIA_PREV_TRACK);

    private void SendKey(ushort virtualKeyCode)
    {
        try
        {
            var inputs = new INPUT[]
            {
                CreateKeyInput(virtualKeyCode, keyUp: false),
                CreateKeyInput(virtualKeyCode, keyUp: true),
            };

            var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
            if (sent != inputs.Length)
            {
                _logger.LogWarning(
                    "SendInput only accepted {Sent}/{Total} synthetic media key events (vk=0x{Vk:X2}); Win32 error {Error}.",
                    sent, inputs.Length, virtualKeyCode, Marshal.GetLastWin32Error());
            }
        }
        catch (Exception ex) when (ex is EntryPointNotFoundException or DllNotFoundException)
        {
            _logger.LogWarning(ex, "Failed to send synthetic media key vk=0x{Vk:X2}.", virtualKeyCode);
        }
    }

    private static INPUT CreateKeyInput(ushort vk, bool keyUp) => new()
    {
        type = INPUT_KEYBOARD,
        u = new InputUnion
        {
            ki = new KEYBDINPUT
            {
                wVk = vk,
                wScan = 0,
                dwFlags = keyUp ? KEYEVENTF_KEYUP : 0,
                time = 0,
                dwExtraInfo = IntPtr.Zero,
            },
        },
    };

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}
