using System.Windows;

namespace ControlDeck.Agent.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        var services = ((App)Application.Current).Services;

        DashboardHost.Content = new DashboardView(services);
        DevicesHost.Content = new DevicesView(services);
        PairingHost.Content = new PairingView(services);
        SettingsHost.Content = new SettingsView(services);
    }
}
