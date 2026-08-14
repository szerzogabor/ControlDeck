using System.IO;
using System.Net.WebSockets;

namespace ControlDeck.Agent.Transport;

/// <summary>
/// Thin abstraction over a live WebSocket so <see cref="PeerConnection"/>
/// doesn't care whether the socket came from `HttpListener.AcceptWebSocketAsync`
/// (server side) or `ClientWebSocket` (client side) — protocol/PROTOCOL.md §1
/// treats both directions identically once the socket is open.
/// </summary>
public interface IWebSocketConnection : IAsyncDisposable
{
    WebSocketState State { get; }

    Task SendTextAsync(string text, CancellationToken cancellationToken);

    /// <summary>Returns null if the connection was closed by the remote side.</summary>
    Task<string?> ReceiveTextAsync(CancellationToken cancellationToken);

    Task CloseAsync(WebSocketCloseStatus status, string? description, CancellationToken cancellationToken);
}

/// <summary>Wraps a raw <see cref="WebSocket"/> (from either <see cref="ClientWebSocket"/> or an accepted server context).</summary>
public sealed class WebSocketConnection : IWebSocketConnection
{
    private readonly WebSocket _socket;
    private const int MaxMessageBytes = 4 * 1024 * 1024; // 4 MiB — generous headroom over any real dashboard payload

    public WebSocketConnection(WebSocket socket)
    {
        _socket = socket;
    }

    public WebSocketState State => _socket.State;

    public async Task SendTextAsync(string text, CancellationToken cancellationToken)
    {
        var bytes = System.Text.Encoding.UTF8.GetBytes(text);
        await _socket.SendAsync(bytes, WebSocketMessageType.Text, endOfMessage: true, cancellationToken).ConfigureAwait(false);
    }

    public async Task<string?> ReceiveTextAsync(CancellationToken cancellationToken)
    {
        using var buffer = new MemoryStream();
        var segment = new byte[8192];

        while (true)
        {
            WebSocketReceiveResult result;
            try
            {
                result = await _socket.ReceiveAsync(segment, cancellationToken).ConfigureAwait(false);
            }
            catch (WebSocketException)
            {
                return null;
            }

            if (result.MessageType == WebSocketMessageType.Close)
            {
                return null;
            }

            buffer.Write(segment, 0, result.Count);

            if (buffer.Length > MaxMessageBytes)
            {
                throw new InvalidOperationException("Incoming WebSocket message exceeded the maximum allowed size.");
            }

            if (result.EndOfMessage)
            {
                return System.Text.Encoding.UTF8.GetString(buffer.ToArray());
            }
        }
    }

    public async Task CloseAsync(WebSocketCloseStatus status, string? description, CancellationToken cancellationToken)
    {
        if (_socket.State is WebSocketState.Open or WebSocketState.CloseReceived)
        {
            try
            {
                await _socket.CloseAsync(status, description, cancellationToken).ConfigureAwait(false);
            }
            catch (WebSocketException)
            {
                // Remote already gone; nothing more to do.
            }
        }
    }

    public ValueTask DisposeAsync()
    {
        _socket.Dispose();
        return ValueTask.CompletedTask;
    }
}
