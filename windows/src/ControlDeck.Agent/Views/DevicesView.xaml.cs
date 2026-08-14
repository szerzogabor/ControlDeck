using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace ControlDeck.Agent.Views;

/// <summary>Lists every paired device with a live online/offline badge, driven by <see cref="Transport.DeviceStateManager"/>.</summary>
public partial class DevicesView : UserControl
{
    private readonly AppServices _services;

    public DevicesView(AppServices services)
    {
        InitializeComponent();
        _services = services;

        _services.ConnectionManager.DeviceStates.StateChanged += (_, _) => Dispatcher.Invoke(Refresh);
        _services.ConnectionManager.PeerConnectedAndAuthenticated += (_, _) => Dispatcher.Invoke(Refresh);
        _services.ConnectionManager.PeerDisconnected += (_, _) => Dispatcher.Invoke(Refresh);

        Refresh();
    }

    private void Refresh()
    {
        DeviceList.Items.Clear();

        foreach (var device in _services.PairedDeviceRepository.GetAll())
        {
            var state = _services.ConnectionManager.DeviceStates.Get(device.DeviceId);
            var isSelf = device.DeviceId == _services.Identity.DeviceId;
            var online = isSelf || state.Connection == Domain.ConnectionState.Online;

            var row = new DockPanel { Margin = new Thickness(4) };
            row.Children.Add(new Ellipse
            {
                Width = 10,
                Height = 10,
                Fill = online ? Brushes.LimeGreen : Brushes.Gray,
                Margin = new Thickness(0, 0, 8, 0),
                VerticalAlignment = VerticalAlignment.Center,
            });
            row.Children.Add(new TextBlock
            {
                Text = $"{device.DeviceName}  ({device.Platform})  —  {(online ? "Online" : "Offline")}",
                VerticalAlignment = VerticalAlignment.Center,
            });

            DeviceList.Items.Add(row);
        }

        if (DeviceList.Items.Count == 0)
        {
            DeviceList.Items.Add(new TextBlock { Text = "No paired devices yet. Use the Pairing tab to add one.", Foreground = Brushes.Gray });
        }
    }
}
