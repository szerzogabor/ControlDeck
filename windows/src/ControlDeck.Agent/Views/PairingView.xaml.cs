using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using ControlDeck.Agent.Discovery;
using ControlDeck.Agent.Pairing;
using ControlDeck.Agent.Persistence;

namespace ControlDeck.Agent.Views;

/// <summary>
/// Displays this device's QR code + PIN for inbound pairing, and a
/// host/port/PIN form to pair outward (protocol/PROTOCOL.md §3.2). Windows
/// has no camera scanner in the MVP, so outward pairing is PIN-only —
/// symmetric to how the Android app can type in a PIN shown here. Also lists
/// nearby unpaired devices found via mDNS for one-click quick-connect
/// (testing convenience — see PairingService.QuickConnectAsync).
/// </summary>
public partial class PairingView : UserControl
{
    private readonly AppServices _services;

    public PairingView(AppServices services)
    {
        InitializeComponent();
        _services = services;

        _services.PairingService.PeerPaired += (_, device) => Dispatcher.Invoke(() =>
        {
            PairResultText.Text = $"Paired with {device.DeviceName} ({device.Platform}).";
            RefreshNearbyDevices();
        });

        _services.Discovery.DeviceFound += (_, _) => Dispatcher.Invoke(RefreshNearbyDevices);
        _services.Discovery.DeviceLost += (_, _) => Dispatcher.Invoke(RefreshNearbyDevices);

        RefreshNearbyDevices();
    }

    private void RefreshNearbyDevices()
    {
        NearbyDeviceList.Items.Clear();

        var pairedIds = _services.PairedDeviceRepository.GetAll().Select(d => d.DeviceId).ToHashSet();

        foreach (var device in _services.Discovery.KnownDevices.Where(d => d.DeviceId != _services.Identity.DeviceId && !pairedIds.Contains(d.DeviceId)))
        {
            var row = new DockPanel { Margin = new Thickness(0, 0, 0, 4), Tag = device };
            row.Children.Add(new TextBlock
            {
                Text = $"{device.DeviceName}  ({device.Platform})",
                VerticalAlignment = VerticalAlignment.Center,
            });
            NearbyDeviceList.Items.Add(row);
        }

        if (NearbyDeviceList.Items.Count == 0)
        {
            NearbyDeviceList.Items.Add(new TextBlock { Text = "Searching for devices on your network...", Foreground = Brushes.Gray });
        }
    }

    private async void NearbyDeviceList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (NearbyDeviceList.SelectedItem is not DockPanel { Tag: DiscoveredDevice device })
        {
            return;
        }

        NearbyDeviceList.SelectedItem = null;
        PairResultText.Text = $"Connecting to {device.DeviceName}…";

        var success = await _services.PairingService.QuickConnectAsync(device.HostOrAddress, device.Port, CancellationToken.None).ConfigureAwait(true);
        PairResultText.Text = success
            ? $"Paired with {device.DeviceName}."
            : $"Quick connect to {device.DeviceName} failed — enable \"Auto-accept pairing\" in its Settings, or use QR/PIN pairing instead.";

        RefreshNearbyDevices();
    }

    private void ShowPairingCodeButton_Click(object sender, RoutedEventArgs e)
    {
        var token = _services.PairingService.BeginHostingPairingWindow();
        var prefs = _services.Preferences.Load();

        var payload = new QrPairingPayload(_services.Identity.DeviceId, token.Value, prefs.WebSocketPort);
        QrImage.Source = QrCodeRenderer.Render(payload);
        PinText.Text = token.Value;
        ExpiryText.Text = $"Expires at {token.ExpiresAtUtc.ToLocalTime():T}";
    }

    private async void PairButton_Click(object sender, RoutedEventArgs e)
    {
        var host = HostBox.Text.Trim();
        var pin = PinBox.Text.Trim();

        if (string.IsNullOrWhiteSpace(host) || !int.TryParse(PortBox.Text, out var port) || string.IsNullOrWhiteSpace(pin))
        {
            PairResultText.Text = "Enter a valid host, port, and PIN.";
            return;
        }

        PairButton.IsEnabled = false;
        PairResultText.Text = "Pairing…";

        try
        {
            var success = await _services.PairingService.PairByPinAsync(host, port, pin, CancellationToken.None).ConfigureAwait(true);
            PairResultText.Text = success ? "Paired successfully." : "Pairing failed — check the PIN and try again.";
        }
        finally
        {
            PairButton.IsEnabled = true;
        }
    }
}
