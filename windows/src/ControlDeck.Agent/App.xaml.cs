using System.Windows;
using ControlDeck.Agent.Dispatch;
using ControlDeck.Agent.Identity;
using ControlDeck.Agent.Pairing;
using ControlDeck.Agent.Persistence;
using ControlDeck.Agent.PlatformActions;
using ControlDeck.Agent.Transport;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent;

/// <summary>
/// Composition root: wires every service by hand (no DI container — the
/// object graph is small and static enough that a container would only add
/// indirection). <see cref="Services"/> is the one place the rest of the app
/// reaches into for shared instances.
/// </summary>
public partial class App : Application
{
    public AppServices Services { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        PersistencePaths.EnsureRootExists();
        Services = AppServices.BuildAndStart();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Services.Dispose();
        base.OnExit(e);
    }
}

/// <summary>The full Device Runtime object graph (docs/ARCHITECTURE.md §2), built once at startup.</summary>
public sealed class AppServices : IDisposable
{
    public required ILoggerFactory LoggerFactory { get; init; }
    public required DeviceIdentityStore IdentityStore { get; init; }
    public required DeviceIdentity Identity { get; init; }
    public required IPreferencesRepository Preferences { get; init; }
    public required IDashboardRepository DashboardRepository { get; init; }
    public required IPairedDeviceRepository PairedDeviceRepository { get; init; }
    public required IAppRegistryRepository AppRegistryRepository { get; init; }
    public required ISecretStore SecretStore { get; init; }

    public required IVolumeController VolumeController { get; init; }
    public required IBrightnessController BrightnessController { get; init; }
    public required IMediaController MediaController { get; init; }
    public required IAppLauncher AppLauncher { get; init; }
    public required CapabilityRegistry CapabilityRegistry { get; init; }
    public required ActionDispatcher ActionDispatcher { get; init; }

    public required WebSocketServerHost Server { get; init; }
    public required WebSocketClientConnector ClientConnector { get; init; }
    public required ConnectionManager ConnectionManager { get; init; }
    public required PairingService PairingService { get; init; }
    public required GroupActionCoordinator GroupActionCoordinator { get; init; }
    public required GroupReconnectCoordinator GroupReconnectCoordinator { get; init; }

    public required Discovery.IDiscoveryService Discovery { get; init; }

    public static AppServices BuildAndStart()
    {
        var loggerFactory = Logging.LoggingSetup.CreateFactory();

        var identityStore = new DeviceIdentityStore(loggerFactory.CreateLogger<DeviceIdentityStore>());
        var identity = identityStore.Load();

        var preferences = new JsonPreferencesRepository(loggerFactory.CreateLogger<JsonPreferencesRepository>());
        var prefs = preferences.Load();

        var dashboardRepository = new JsonDashboardRepository(loggerFactory.CreateLogger<JsonDashboardRepository>());
        var pairedDeviceRepository = new JsonPairedDeviceRepository(loggerFactory.CreateLogger<JsonPairedDeviceRepository>());
        var appRegistryRepository = new JsonAppRegistryRepository(loggerFactory.CreateLogger<JsonAppRegistryRepository>());
        var secretStore = new DpapiSecretStore(loggerFactory.CreateLogger<DpapiSecretStore>());

        var volumeController = new WindowsVolumeController(loggerFactory.CreateLogger<WindowsVolumeController>());
        var brightnessController = new WmiBrightnessController(loggerFactory.CreateLogger<WmiBrightnessController>());
        var mediaController = new MediaKeyController(loggerFactory.CreateLogger<MediaKeyController>());
        var appLauncher = new ProcessAppLauncher(appRegistryRepository, loggerFactory.CreateLogger<ProcessAppLauncher>());
        var capabilityRegistry = new CapabilityRegistry(volumeController, brightnessController);
        var actionDispatcher = new ActionDispatcher(
            volumeController, brightnessController, mediaController, appLauncher, capabilityRegistry,
            loggerFactory.CreateLogger<ActionDispatcher>());

        var connectionManager = new ConnectionManager(
            identity, secretStore, pairedDeviceRepository, dashboardRepository, appRegistryRepository,
            actionDispatcher, capabilityRegistry, loggerFactory.CreateLogger<ConnectionManager>());

        var server = new WebSocketServerHost(prefs.WebSocketPort, identity, secretStore, loggerFactory);
        var clientConnector = new WebSocketClientConnector(identity, secretStore, loggerFactory);
        var pairingService = new PairingService(identity, secretStore, pairedDeviceRepository, clientConnector, loggerFactory.CreateLogger<PairingService>());

        server.ConnectionAccepted += (_, connection) =>
        {
            connectionManager.Attach(connection);
            connection.PairRequestReceived += (_, request) => _ = pairingService.HandleIncomingPairRequestAsync(connection, request);
        };
        server.Start();

        var groupActionCoordinator = new GroupActionCoordinator(connectionManager, loggerFactory.CreateLogger<GroupActionCoordinator>());
        var groupReconnectCoordinator = new GroupReconnectCoordinator(connectionManager, dashboardRepository, loggerFactory.CreateLogger<GroupReconnectCoordinator>());

        var discovery = new Discovery.MdnsDiscoveryService(loggerFactory.CreateLogger<Discovery.MdnsDiscoveryService>());
        discovery.Start(identity.DeviceId, identity.DeviceName, Domain.Platform.Windows, DeviceIdentityStore.AppVersion, prefs.WebSocketPort);

        // Auto-reconnect to every already-paired device whenever it's discovered again.
        discovery.DeviceFound += (_, found) =>
        {
            if (pairedDeviceRepository.GetById(found.DeviceId) is not null &&
                !connectionManager.ConnectedPeerDeviceIds.Contains(found.DeviceId))
            {
                _ = ReconnectToPeerAsync(clientConnector, connectionManager, found.HostOrAddress, found.Port);
            }
        };

        return new AppServices
        {
            LoggerFactory = loggerFactory,
            IdentityStore = identityStore,
            Identity = identity,
            Preferences = preferences,
            DashboardRepository = dashboardRepository,
            PairedDeviceRepository = pairedDeviceRepository,
            AppRegistryRepository = appRegistryRepository,
            SecretStore = secretStore,
            VolumeController = volumeController,
            BrightnessController = brightnessController,
            MediaController = mediaController,
            AppLauncher = appLauncher,
            CapabilityRegistry = capabilityRegistry,
            ActionDispatcher = actionDispatcher,
            Server = server,
            ClientConnector = clientConnector,
            ConnectionManager = connectionManager,
            PairingService = pairingService,
            GroupActionCoordinator = groupActionCoordinator,
            GroupReconnectCoordinator = groupReconnectCoordinator,
            Discovery = discovery,
        };
    }

    private static async Task ReconnectToPeerAsync(WebSocketClientConnector connector, ConnectionManager connectionManager, string host, int port)
    {
        var connection = await connector.ConnectAsync(host, port, CancellationToken.None).ConfigureAwait(false);
        if (connection is not null)
        {
            connectionManager.Attach(connection);
        }
    }

    public void Dispose()
    {
        Discovery.Dispose();
        _ = Server.DisposeAsync();
        (VolumeController as IDisposable)?.Dispose();
        LoggerFactory.Dispose();
    }
}
