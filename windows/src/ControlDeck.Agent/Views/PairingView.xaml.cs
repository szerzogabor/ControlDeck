using System.Windows;
using System.Windows.Controls;
using ControlDeck.Agent.Pairing;
using ControlDeck.Agent.Persistence;

namespace ControlDeck.Agent.Views;

/// <summary>
/// Displays this device's QR code + PIN for inbound pairing, and a
/// host/port/PIN form to pair outward (protocol/PROTOCOL.md §3.2). Windows
/// has no camera scanner in the MVP, so outward pairing is PIN-only —
/// symmetric to how the Android app can type in a PIN shown here.
/// </summary>
public partial class PairingView : UserControl
{
    private readonly AppServices _services;

    public PairingView(AppServices services)
    {
        InitializeComponent();
        _services = services;

        _services.PairingService.PeerPaired += (_, device) => Dispatcher.Invoke(() =>
            PairResultText.Text = $"Paired with {device.DeviceName} ({device.Platform}).");
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
