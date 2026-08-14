using System.IO;
using System.Text;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Logging;

/// <summary>
/// Minimal file + debug-output logging sink. Used across discovery, pairing,
/// connection, protocol, action-dispatch, and dashboard-sync events (per the
/// spec). Deliberately hand-rolled instead of pulling in
/// Microsoft.Extensions.Logging.Debug/Console just to avoid two more
/// dependencies for a two-sink MVP logger.
///
/// SECURITY: callers must never pass `sharedSecret` or PIN values into any
/// log call (protocol/PROTOCOL.md §7) — this class does no redaction of its
/// own, the discipline lives at every call site.
/// </summary>
public sealed class FileLoggerProvider : ILoggerProvider
{
    private readonly string _logFilePath;
    private readonly object _writeLock = new();

    public FileLoggerProvider(string logFilePath)
    {
        _logFilePath = logFilePath;
        var directory = Path.GetDirectoryName(_logFilePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }

    public ILogger CreateLogger(string categoryName) => new FileLogger(categoryName, this);

    internal void Write(string line)
    {
        lock (_writeLock)
        {
            try
            {
                File.AppendAllText(_logFilePath, line + Environment.NewLine, Encoding.UTF8);
            }
            catch (IOException)
            {
                // Logging must never crash the app; drop the line on disk contention.
            }
        }

        System.Diagnostics.Debug.WriteLine(line);
    }

    public void Dispose()
    {
    }

    private sealed class FileLogger(string categoryName, FileLoggerProvider provider) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => logLevel != LogLevel.None;

        public void Log<TState>(
            LogLevel logLevel,
            EventId eventId,
            TState state,
            Exception? exception,
            Func<TState, Exception?, string> formatter)
        {
            if (!IsEnabled(logLevel))
            {
                return;
            }

            var message = formatter(state, exception);
            var line = $"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss.fff} [{logLevel}] {categoryName}: {message}";
            if (exception is not null)
            {
                line += Environment.NewLine + exception;
            }

            provider.Write(line);
        }
    }
}

public static class LoggingSetup
{
    /// <summary>Creates the shared <see cref="ILoggerFactory"/> writing to %LOCALAPPDATA%\ControlDeck\logs\agent.log.</summary>
    public static ILoggerFactory CreateFactory()
    {
        var logDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ControlDeck",
            "logs");
        var logFilePath = Path.Combine(logDirectory, "agent.log");

        return LoggerFactory.Create(builder =>
        {
            builder.SetMinimumLevel(LogLevel.Information);
            builder.AddProvider(new FileLoggerProvider(logFilePath));
        });
    }
}
