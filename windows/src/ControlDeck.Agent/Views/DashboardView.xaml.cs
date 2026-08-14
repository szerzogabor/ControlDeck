using System.Windows;
using System.Windows.Controls;
using ControlDeck.Domain;

namespace ControlDeck.Agent.Views;

/// <summary>
/// Main dashboard screen: dashboard list (create/rename/delete/switch) on
/// the left, a UniformGrid-style widget layout on the right — sliders and
/// buttons matching the dark dashboard mock. Offline targets render
/// disabled with an "(offline)" badge without blocking the rest of the
/// dashboard (docs/ARCHITECTURE.md §7).
/// </summary>
public partial class DashboardView : UserControl
{
    private readonly AppServices _services;
    private Dashboard? _selected;

    public DashboardView(AppServices services)
    {
        InitializeComponent();
        _services = services;

        _services.ConnectionManager.DeviceStates.StateChanged += (_, _) => Dispatcher.Invoke(RenderWidgets);
        _services.ConnectionManager.DashboardUpdatedByPeer += (_, _) => Dispatcher.Invoke(ReloadDashboards);

        ReloadDashboards();
    }

    private void ReloadDashboards()
    {
        var selectedId = _selected?.Id;
        var all = _services.DashboardRepository.GetAll();
        DashboardList.ItemsSource = all;

        var toSelect = all.FirstOrDefault(d => d.Id == selectedId) ?? all.FirstOrDefault();
        DashboardList.SelectedItem = toSelect;
    }

    private void DashboardList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _selected = DashboardList.SelectedItem as Dashboard;
        DashboardTitle.Text = _selected?.Name ?? "(no dashboard selected)";
        RenderWidgets();
    }

    private void RenderWidgets()
    {
        WidgetsHost.Items.Clear();
        if (_selected is null)
        {
            return;
        }

        foreach (var widget in _selected.Widgets)
        {
            WidgetsHost.Items.Add(BuildWidgetTile(widget));
        }
    }

    private FrameworkElement BuildWidgetTile(Widget widget)
    {
        var state = _services.ConnectionManager.DeviceStates.Get(widget.TargetDeviceId.Value);
        var isOnline = state.Connection == ConnectionState.Online || widget.TargetDeviceId.Value == _services.Identity.DeviceId;

        var border = new Border
        {
            Width = 220,
            Margin = new Thickness(8),
            Padding = new Thickness(12),
            CornerRadius = new CornerRadius(8),
            Background = (System.Windows.Media.Brush)Application.Current.Resources["SurfaceBrush"],
            Opacity = isOnline ? 1.0 : 0.5,
        };

        var stack = new StackPanel();

        var header = new DockPanel();
        header.Children.Add(new TextBlock
        {
            Text = widget.Configuration.GetValueOrDefault("label", DefaultLabel(widget)),
            FontWeight = FontWeights.SemiBold,
        });
        if (!isOnline)
        {
            var badge = new TextBlock { Text = "OFFLINE", Foreground = (System.Windows.Media.Brush)Application.Current.Resources["OfflineBrush"], FontSize = 10 };
            DockPanel.SetDock(badge, Dock.Right);
            header.Children.Add(badge);
        }

        stack.Children.Add(header);

        FrameworkElement control = widget.Type switch
        {
            WidgetType.SliderBrightness or WidgetType.SliderVolume => BuildSlider(widget, state, isOnline),
            _ => BuildButton(widget, isOnline),
        };
        control.Margin = new Thickness(0, 8, 0, 0);
        stack.Children.Add(control);

        border.Child = stack;
        return border;
    }

    private static string DefaultLabel(Widget widget) => widget.Type switch
    {
        WidgetType.SliderBrightness => "Brightness",
        WidgetType.SliderVolume => "Volume",
        WidgetType.ButtonMute => "Mute",
        WidgetType.ButtonMediaPlayPause => "Play / Pause",
        WidgetType.ButtonMediaNext => "Next",
        WidgetType.ButtonMediaPrevious => "Previous",
        WidgetType.AppLaunch => "Launch App",
        _ => widget.Type.ToString(),
    };

    private FrameworkElement BuildSlider(Widget widget, DeviceState state, bool isOnline)
    {
        var current = widget.Type == WidgetType.SliderBrightness ? state.Brightness : state.Volume;
        var slider = new Slider
        {
            Minimum = 0,
            Maximum = 100,
            Value = current ?? 0,
            IsEnabled = isOnline,
            IsMoveToPointEnabled = true,
        };

        var oldValue = (int)slider.Value;
        slider.PreviewMouseUp += async (_, _) =>
        {
            var newValue = (int)slider.Value;
            if (newValue == oldValue)
            {
                return;
            }

            await DispatchWidgetActionAsync(widget, oldValue, newValue).ConfigureAwait(false);
            oldValue = newValue;
        };

        return slider;
    }

    private Button BuildButton(Widget widget, bool isOnline)
    {
        var button = new Button
        {
            Content = DefaultLabel(widget),
            IsEnabled = isOnline,
            HorizontalAlignment = HorizontalAlignment.Stretch,
        };

        button.Click += async (_, _) => await DispatchWidgetActionAsync(widget, 0, 0).ConfigureAwait(false);
        return button;
    }

    private async Task DispatchWidgetActionAsync(Widget widget, int oldValue, int newValue)
    {
        var group = _selected?.Groups.FirstOrDefault(g => g.MemberWidgetIds.Contains(widget.Id));

        if (group is null || _selected is null)
        {
            var action = ResolveDirectAction(widget, newValue);
            if (action is not null)
            {
                await _services.ConnectionManager.SendActionAsync(widget.TargetDeviceId.Value, action).ConfigureAwait(false);
            }

            return;
        }

        switch (group.Kind)
        {
            case GroupKind.RelativeSlider:
                await _services.GroupActionCoordinator.ApplyRelativeSliderAsync(group, _selected.Widgets, oldValue, newValue).ConfigureAwait(false);
                break;
            case GroupKind.AbsoluteToggle:
                await _services.GroupActionCoordinator.ApplyAbsoluteToggleAsync(group, _selected.Widgets).ConfigureAwait(false);
                break;
            case GroupKind.AbsoluteMedia:
                if (widget.Type is WidgetType.ButtonMediaNext or WidgetType.ButtonMediaPrevious)
                {
                    await _services.GroupActionCoordinator.ApplyMediaEdgeAsync(group, _selected.Widgets, widget.Type == WidgetType.ButtonMediaNext).ConfigureAwait(false);
                }
                else
                {
                    await _services.GroupActionCoordinator.ApplyAbsoluteMediaToggleAsync(group, _selected.Widgets).ConfigureAwait(false);
                }

                break;
        }
    }

    private static ActionSpec? ResolveDirectAction(Widget widget, int newValue) => widget.Action switch
    {
        BrightnessSet => new BrightnessSet(newValue),
        VolumeSet => new VolumeSet(newValue),
        SetMuted m => new SetMuted(!m.Muted),
        MediaSetState m => new MediaSetState(m.State == MediaState.Playing ? MediaState.Paused : MediaState.Playing),
        Domain.MediaNext => new Domain.MediaNext(),
        Domain.MediaPrevious => new Domain.MediaPrevious(),
        AppLaunch a => new AppLaunch(a.AppId),
        _ => null,
    };

    private void NewDashboardButton_Click(object sender, RoutedEventArgs e)
    {
        var name = InputDialog.Prompt(Window.GetWindow(this)!, "New dashboard name:", "New Dashboard");
        if (string.IsNullOrWhiteSpace(name))
        {
            return;
        }

        var dashboard = new Dashboard(DashboardId.NewId(), name, 1, Array.Empty<Widget>(), Array.Empty<Group>());
        _services.DashboardRepository.Upsert(dashboard);
        _ = _services.ConnectionManager.BroadcastDashboardAsync(dashboard);
        ReloadDashboards();
    }

    private void RenameDashboardButton_Click(object sender, RoutedEventArgs e)
    {
        if (_selected is null)
        {
            return;
        }

        var name = InputDialog.Prompt(Window.GetWindow(this)!, "Rename dashboard:", _selected.Name);
        if (string.IsNullOrWhiteSpace(name))
        {
            return;
        }

        var updated = _selected with { Name = name, Version = _selected.Version + 1 };
        _services.DashboardRepository.Upsert(updated);
        _ = _services.ConnectionManager.BroadcastDashboardAsync(updated);
        ReloadDashboards();
    }

    private void DeleteDashboardButton_Click(object sender, RoutedEventArgs e)
    {
        if (_selected is null)
        {
            return;
        }

        if (MessageBox.Show($"Delete dashboard \"{_selected.Name}\"?", "ControlDeck", MessageBoxButton.YesNo) != MessageBoxResult.Yes)
        {
            return;
        }

        _services.DashboardRepository.Delete(_selected.Id);
        _selected = null;
        ReloadDashboards();
    }

    private void AddSliderWidgetButton_Click(object sender, RoutedEventArgs e) => AddWidget(isSlider: true);

    private void AddButtonWidgetButton_Click(object sender, RoutedEventArgs e) => AddWidget(isSlider: false);

    private void AddWidget(bool isSlider)
    {
        if (_selected is null)
        {
            return;
        }

        var targetDeviceId = InputDialog.Prompt(Window.GetWindow(this)!, "Target device id (use this device's id for a local widget):", _services.Identity.DeviceId);
        if (string.IsNullOrWhiteSpace(targetDeviceId))
        {
            return;
        }

        var widget = isSlider
            ? new Widget(
                WidgetId.NewId(), WidgetType.SliderVolume, new GridPosition(0, 0), new GridSize(1, 1),
                new DeviceId(targetDeviceId), new VolumeSet(50), new Dictionary<string, string> { ["label"] = "Volume" })
            : new Widget(
                WidgetId.NewId(), WidgetType.ButtonMediaPlayPause, new GridPosition(0, 0), new GridSize(1, 1),
                new DeviceId(targetDeviceId), new MediaSetState(MediaState.Playing), new Dictionary<string, string> { ["label"] = "Play / Pause" });

        var updated = _selected with { Widgets = _selected.Widgets.Append(widget).ToList(), Version = _selected.Version + 1 };
        _services.DashboardRepository.Upsert(updated);
        _selected = updated;
        _ = _services.ConnectionManager.BroadcastDashboardAsync(updated);
        RenderWidgets();
    }
}
