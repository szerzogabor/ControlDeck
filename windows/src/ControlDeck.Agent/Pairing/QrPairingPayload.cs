using System.Text.Json;
using System.Windows.Media.Imaging;
using QRCoder;

namespace ControlDeck.Agent.Pairing;

/// <summary>The JSON payload encoded into the pairing QR code (protocol/PROTOCOL.md §3.2).</summary>
public sealed record QrPairingPayload(string DeviceId, string PairingToken, int Port);

/// <summary>Renders <see cref="QrPairingPayload"/> as a WPF-displayable bitmap via QRCoder.</summary>
public static class QrCodeRenderer
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static string ToJson(QrPairingPayload payload) => JsonSerializer.Serialize(payload, JsonOptions);

    /// <summary>Renders the pairing payload as a QR code PNG, wrapped for WPF `Image.Source`.</summary>
    public static BitmapImage Render(QrPairingPayload payload, int pixelsPerModule = 10)
    {
        var json = ToJson(payload);

        using var qrGenerator = new QRCodeGenerator();
        using var qrCodeData = qrGenerator.CreateQrCode(json, QRCodeGenerator.ECCLevel.M);
        using var qrCode = new PngByteQRCode(qrCodeData);
        var pngBytes = qrCode.GetGraphic(pixelsPerModule);

        var bitmap = new BitmapImage();
        using var stream = new System.IO.MemoryStream(pngBytes);
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.StreamSource = stream;
        bitmap.EndInit();
        bitmap.Freeze();
        return bitmap;
    }
}
