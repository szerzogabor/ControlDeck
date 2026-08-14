using System.Windows;
using System.Windows.Controls;
using ControlDeck.Agent.Persistence;
using ControlDeck.Domain;

namespace ControlDeck.Agent.Views;

public partial class SettingsView : UserControl
{
    private readonly AppServices _services;
    private bool _suppressReconnectPolicyEvent;

    public SettingsView(AppServices services)
    {
        InitializeComponent();
        _services = services;

        DeviceNameBox.Text = _services.Identity.DeviceName;

        ReconnectPolicyCombo.ItemsSource = Enum.GetValues<ReconnectPolicy>();
        _suppressReconnectPolicyEvent = true;
        ReconnectPolicyCombo.SelectedItem = _services.Preferences.Load().DefaultReconnectPolicy;
        _suppressReconnectPolicyEvent = false;

        RefreshAppRegistry();
    }

    private void SaveDeviceName_Click(object sender, RoutedEventArgs e)
    {
        var name = DeviceNameBox.Text.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            return;
        }

        _services.IdentityStore.SaveDeviceName(name);
        MessageBox.Show("Device name saved. Reconnect to peers to propagate the change.", "ControlDeck");
    }

    private void ReconnectPolicyCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_suppressReconnectPolicyEvent || ReconnectPolicyCombo.SelectedItem is not ReconnectPolicy policy)
        {
            return;
        }

        var prefs = _services.Preferences.Load();
        _services.Preferences.Save(prefs with { DefaultReconnectPolicy = policy });
    }

    private void RefreshAppRegistry()
    {
        AppRegistryHost.Items.Clear();

        foreach (var entry in _services.AppRegistryRepository.GetAll())
        {
            var row = new DockPanel { Margin = new Thickness(0, 0, 0, 6) };

            var appIdText = new TextBlock { Text = entry.AppId, Width = 90, VerticalAlignment = VerticalAlignment.Center };
            DockPanel.SetDock(appIdText, Dock.Left);

            var removeButton = new Button { Content = "Remove", Margin = new Thickness(8, 0, 0, 0) };
            DockPanel.SetDock(removeButton, Dock.Right);
            removeButton.Click += (_, _) =>
            {
                _services.AppRegistryRepository.Remove(entry.AppId);
                RefreshAppRegistry();
            };

            var targetBox = new TextBox { Text = entry.Target, ToolTip = "Executable path or shell:AppsFolder\\... target" };
            targetBox.LostFocus += (_, _) =>
            {
                _services.AppRegistryRepository.Upsert(entry with { Target = targetBox.Text.Trim() });
            };

            row.Children.Add(appIdText);
            row.Children.Add(removeButton);
            row.Children.Add(targetBox);
            AppRegistryHost.Items.Add(row);
        }
    }

    private void AddAppButton_Click(object sender, RoutedEventArgs e)
    {
        var appId = InputDialog.Prompt(Window.GetWindow(this)!, "New app id (e.g. \"vlc\"):");
        if (string.IsNullOrWhiteSpace(appId))
        {
            return;
        }

        var displayName = InputDialog.Prompt(Window.GetWindow(this)!, "Display name:", appId) ?? appId;
        _services.AppRegistryRepository.Upsert(new AppRegistryEntry(appId, displayName, string.Empty));
        RefreshAppRegistry();
    }
}
