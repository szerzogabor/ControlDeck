using System.Windows;

namespace ControlDeck.Agent.Views;

/// <summary>Minimal single-line text prompt (WPF has no built-in InputBox).</summary>
public partial class InputDialog : Window
{
    public string? ResultValue { get; private set; }

    public InputDialog(string prompt, string defaultValue = "")
    {
        InitializeComponent();
        PromptText.Text = prompt;
        ValueBox.Text = defaultValue;
        ValueBox.Focus();
        ValueBox.SelectAll();
    }

    private void Ok_Click(object sender, RoutedEventArgs e)
    {
        ResultValue = ValueBox.Text;
        DialogResult = true;
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
    }

    public static string? Prompt(Window owner, string prompt, string defaultValue = "")
    {
        var dialog = new InputDialog(prompt, defaultValue) { Owner = owner };
        return dialog.ShowDialog() == true ? dialog.ResultValue : null;
    }
}
