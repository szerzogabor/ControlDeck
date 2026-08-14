using System.Net;
using System.Net.WebSockets;
using System.Runtime.Versioning;
using ControlDeck.Agent.Identity;
using ControlDeck.Agent.Persistence;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Transport;

/// <summary>
/// LAN-bound WebSocket server via `HttpListener.AcceptWebSocketAsync` — no
/// extra dependency, built into .NET (protocol/PROTOCOL.md §1). Listens on
/// `http://+:{port}/` (all interfaces) so peers on the LAN can connect in.
/// Each accepted connection becomes a <see cref="PeerConnection"/> in the
/// <see cref="PeerConnectionRole.Acceptor"/> role.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WebSocketServerHost : IAsyncDisposable
{
    private readonly int _port;
    private readonly DeviceIdentity _selfIdentity;
    private readonly ISecretStore _secretStore;
    private readonly ILoggerFactory _loggerFactory;
    private readonly ILogger<WebSocketServerHost> _logger;
    private readonly HttpListener _listener = new();
    private readonly CancellationTokenSource _cts = new();
    private Task? _acceptLoop;

    public event EventHandler<PeerConnection>? ConnectionAccepted;

    public WebSocketServerHost(
        int port,
        DeviceIdentity selfIdentity,
        ISecretStore secretStore,
        ILoggerFactory loggerFactory)
    {
        _port = port;
        _selfIdentity = selfIdentity;
        _secretStore = secretStore;
        _loggerFactory = loggerFactory;
        _logger = loggerFactory.CreateLogger<WebSocketServerHost>();
    }

    public void Start()
    {
        // "+ " binds to all interfaces (LAN-bound per protocol/PROTOCOL.md §1).
        // Requires either running elevated or a prior `netsh http add urlacl`
        // for this port/user — documented as a first-run setup note; falls
        // back to localhost-only if the wildcard bind is denied so the app
        // still starts (loopback-only is clearly worse than not running, but
        // strictly better than crashing on startup).
        try
        {
            _listener.Prefixes.Add($"http://+:{_port}/");
            _listener.Start();
        }
        catch (HttpListenerException ex)
        {
            _logger.LogWarning(ex, "Failed to bind ws server to all interfaces on port {Port}; falling back to localhost only.", _port);
            _listener.Prefixes.Clear();
            _listener.Prefixes.Add($"http://localhost:{_port}/");
            _listener.Start();
        }

        _acceptLoop = Task.Run(AcceptLoopAsync);
        _logger.LogInformation("WebSocket server listening on port {Port}.", _port);
    }

    private async Task AcceptLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            HttpListenerContext context;
            try
            {
                context = await _listener.GetContextAsync().WaitAsync(_cts.Token).ConfigureAwait(false);
            }
            catch (Exception ex) when (ex is HttpListenerException or ObjectDisposedException or OperationCanceledException)
            {
                return; // listener stopped
            }

            _ = HandleContextAsync(context);
        }
    }

    private async Task HandleContextAsync(HttpListenerContext context)
    {
        if (!context.Request.IsWebSocketRequest)
        {
            context.Response.StatusCode = (int)HttpStatusCode.BadRequest;
            context.Response.Close();
            return;
        }

        try
        {
            var wsContext = await context.AcceptWebSocketAsync(subProtocol: null).ConfigureAwait(false);
            var connection = new PeerConnection(
                new WebSocketConnection(wsContext.WebSocket),
                PeerConnectionRole.Acceptor,
                _selfIdentity,
                _secretStore,
                _loggerFactory.CreateLogger<PeerConnection>());

            ConnectionAccepted?.Invoke(this, connection);
            _ = connection.RunAsync();
        }
        catch (WebSocketException ex)
        {
            _logger.LogWarning(ex, "Failed to accept incoming WebSocket connection from {RemoteEndPoint}.", context.Request.RemoteEndPoint);
        }
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        try
        {
            _listener.Stop();
        }
        catch (ObjectDisposedException)
        {
        }

        if (_acceptLoop is not null)
        {
            try
            {
                await _acceptLoop.ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
        }

        _listener.Close();
        _cts.Dispose();
    }
}
