using System.Collections.ObjectModel;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows;
using System.Windows.Controls;

namespace GuardianLicenseManager;

public partial class UpdateWindow : Window
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly ObservableCollection<UpdateRow> _rows = [];
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public UpdateWindow(Uri baseAddress, string token)
    {
        InitializeComponent();
        _http.BaseAddress = baseAddress;
        _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        _http.DefaultRequestHeaders.CacheControl = new CacheControlHeaderValue { NoStore = true };
        UpdateGrid.ItemsSource = _rows;
        Loaded += async (_, _) => await LoadUpdates();
    }

    private async Task<T> Get<T>(string path)
    {
        using var response = await _http.GetAsync(path);
        var body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException($"서버 오류 {(int)response.StatusCode}: {body}");
        return JsonSerializer.Deserialize<T>(body, JsonOptions) ?? throw new InvalidOperationException("서버 응답을 해석할 수 없습니다.");
    }

    private async Task LoadUpdates()
    {
        try
        {
            var result = await Get<UpdateListResponse>("v1/admin/updates");
            _rows.Clear();
            foreach (var update in result.Updates ?? []) _rows.Add(new UpdateRow(update));
            StatusText.Text = $"등록된 업데이트 { _rows.Count:N0}건을 불러왔습니다.";
        }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await LoadUpdates();

    private async void Publish_Click(object sender, RoutedEventArgs e)
    {
        var platform = (PlatformBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "";
        var channel = (ChannelBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "";
        var version = VersionBox.Text.Trim();
        var url = DownloadUrlBox.Text.Trim();
        var hash = Sha256Box.Text.Trim().ToLowerInvariant();
        if (string.IsNullOrWhiteSpace(version) || !Uri.TryCreate(url, UriKind.Absolute, out var download) ||
            download.Scheme != Uri.UriSchemeHttps || string.IsNullOrWhiteSpace(download.Host) || hash.Length != 64 || hash.Any(c => !Uri.IsHexDigit(c)) || (MandatoryBox.IsChecked == true && string.IsNullOrWhiteSpace(MinVersionBox.Text.Trim())))
        { StatusText.Text = "버전, HTTPS 다운로드 주소, 64자리 SHA-256을 확인하세요."; return; }

        var payload = new
        {
            platform, channel, version, downloadUrl = url, sha256 = hash,
            minSupportedVersion = MinVersionBox.Text.Trim(),
            mandatory = MandatoryBox.IsChecked == true,
            releaseNotes = NotesBox.Text.Trim()
        };
        try
        {
            using var response = await _http.PostAsJsonAsync("v1/admin/updates", payload);
            var body = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException($"서버 오류 {(int)response.StatusCode}: {body}");
            StatusText.Text = $"{platform} {channel} {version} 업데이트를 게시했습니다.";
            await LoadUpdates();
        }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private sealed class UpdateListResponse { [JsonPropertyName("updates")] public List<UpdateDto>? Updates { get; set; } }
    private sealed class UpdateDto
    {
        public string Platform { get; set; } = "";
        public string Channel { get; set; } = "";
        public string Version { get; set; } = "";
        [JsonPropertyName("download_url")] public string DownloadUrl { get; set; } = "";
        [JsonPropertyName("min_supported_version")] public string? MinSupportedVersion { get; set; }
        public bool Mandatory { get; set; }
        [JsonPropertyName("published_at")] public DateTimeOffset PublishedAt { get; set; }
    }
    private sealed class UpdateRow(UpdateDto x)
    {
        public string PlatformLabel => x.Platform == "android" ? "Android" : "Windows";
        public string ChannelLabel => x.Channel == "stable" ? "정식" : "베타";
        public string Version => x.Version;
        public string MinVersionLabel => string.IsNullOrWhiteSpace(x.MinSupportedVersion) ? "—" : x.MinSupportedVersion;
        public bool Mandatory => x.Mandatory;
        public string PublishedLabel => x.PublishedAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm");
        public string DownloadUrl => x.DownloadUrl;
    }
}