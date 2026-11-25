using System.Collections.ObjectModel;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows;
using System.Windows.Controls;

namespace GuardianLicenseManager;

public partial class MainWindow : Window
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private readonly ObservableCollection<LicenseRow> _rows = [];
    private string _token = "";
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public MainWindow()
    {
        InitializeComponent();
        LicenseGrid.ItemsSource = _rows;
    }

    private void ConfigureClient()
    {
        if (!Uri.TryCreate(ApiUrlBox.Text.Trim(), UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && !(uri.IsLoopback && uri.Scheme == Uri.UriSchemeHttp)))
            throw new InvalidOperationException("API 주소는 HTTPS여야 합니다. 로컬 테스트만 HTTP를 허용합니다.");
        if (string.IsNullOrWhiteSpace(_token)) throw new InvalidOperationException("관리자 토큰을 입력하세요.");
        _http.BaseAddress = new Uri(uri.ToString().TrimEnd('/') + "/");
        _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);
        _http.DefaultRequestHeaders.CacheControl = new CacheControlHeaderValue { NoStore = true };
    }

    private async Task<T> Get<T>(string path)
    {
        using var response = await _http.GetAsync(path);
        var body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException($"서버 오류 {(int)response.StatusCode}: {body}");
        return JsonSerializer.Deserialize<T>(body, JsonOptions) ?? throw new InvalidOperationException("서버 응답을 해석할 수 없습니다.");
    }

    private async Task<T> Post<T>(string path, object payload)
    {
        using var response = await _http.PostAsJsonAsync(path, payload);
        var body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException($"서버 오류 {(int)response.StatusCode}: {body}");
        return JsonSerializer.Deserialize<T>(body, JsonOptions) ?? throw new InvalidOperationException("서버 응답을 해석할 수 없습니다.");
    }

    private async void Connect_Click(object sender, RoutedEventArgs e)
    {
        _token = TokenBox.Password.Trim();
        try { ConfigureClient(); await RefreshData(); ConnectionLabel.Text = "● 연결됨"; ConnectionLabel.Foreground = System.Windows.Media.Brushes.LightGreen; StatusText.Text = "라이선스 서버에 안전하게 연결되었습니다."; }
        catch (Exception ex) { ConnectionLabel.Text = "연결 실패"; StatusText.Text = ex.Message; }
    }

    private void Updates_Click(object sender, RoutedEventArgs e)
    {
        if (_http.BaseAddress is null || string.IsNullOrWhiteSpace(_token))
        { StatusText.Text = "제품 업데이트를 관리하려면 먼저 License API에 연결하세요."; return; }
        new UpdateWindow(_http.BaseAddress, _token) { Owner = this }.ShowDialog();
    }

    private void Disconnect_Click(object sender, RoutedEventArgs e)
    {
        _token = ""; TokenBox.Clear(); _http.DefaultRequestHeaders.Authorization = null;
        _rows.Clear(); TotalCount.Text = ActiveCount.Text = ExpiredCount.Text = RevokedCount.Text = "—";
        ConnectionLabel.Text = "연결되지 않음"; StatusText.Text = "관리자 연결이 해제되었습니다.";
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e)
    {
        try { await RefreshData(); StatusText.Text = "목록을 최신 상태로 갱신했습니다."; }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private async Task RefreshData()
    {
        var result = await Get<LicenseListResponse>("v1/admin/licenses");
        _rows.Clear();
        foreach (var item in result.Licenses ?? []) _rows.Add(new LicenseRow(item));
        TotalCount.Text = _rows.Count.ToString("N0");
        ActiveCount.Text = _rows.Count(x => EffectiveStatus(x.Status, x.ExpiresAt) == "active").ToString("N0");
        ExpiredCount.Text = _rows.Count(x => EffectiveStatus(x.Status, x.ExpiresAt) == "expired").ToString("N0");
        RevokedCount.Text = _rows.Count(x => EffectiveStatus(x.Status, x.ExpiresAt) == "revoked").ToString("N0");
    }

    private void PlanBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var plan = (PlanBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "annual_365d";
        CustomDaysPanel.Visibility = plan == "custom" ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void Issue_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(CustomerNameBox.Text) || string.IsNullOrWhiteSpace(CustomerEmailBox.Text))
        { StatusText.Text = "고객명과 이메일을 입력하세요."; return; }
        try
        {
            var plan = (PlanBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "annual_365d";
            int? days;
            if (plan == "permanent")
            {
                days = null;
            }
            else if (plan == "custom")
            {
                if (!int.TryParse(CustomDaysBox.Text.Trim(), out var customDays) || customDays < 1 || customDays > 36500)
                { StatusText.Text = "직접 이용 기간은 1~36500일의 정수여야 합니다."; return; }
                days = customDays;
            }
            else
            {
                days = plan switch { "short_7d" => 7, "monthly_30d" => 30, "quarterly_90d" => 90, _ => 365 };
            }
            var devices = int.Parse((DeviceCountBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "1");
            var payload = new { validDays = days, maxDevices = devices, planCode = plan, customer = new { name = CustomerNameBox.Text.Trim(), email = CustomerEmailBox.Text.Trim(), orderRef = OrderRefBox.Text.Trim() } };
            var result = await Post<IssueResponse>("v1/admin/licenses", payload);
            IssuedKeyBox.Text = result.LicenseKey;
            IssuedPanel.Visibility = Visibility.Visible;
            StatusText.Text = "발급 완료. 키는 이 화면에서 한 번만 확인하고 안전한 채널로 전달하세요.";
            await RefreshData();
        }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private void CopyKey_Click(object sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrWhiteSpace(IssuedKeyBox.Text)) Clipboard.SetText(IssuedKeyBox.Text);
    }

    private async void Revoke_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string id } || MessageBox.Show("이 라이선스를 정지하고 활성 기기를 모두 해제하시겠습니까?", "라이선스 정지 확인", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try { await Post<JsonElement>($"v1/admin/licenses/{Uri.EscapeDataString(id)}/revoke", new { }); await RefreshData(); StatusText.Text = "라이선스가 정지되었습니다."; }
        catch (Exception ex) { StatusText.Text = ex.Message; }
    }

    private sealed class LicenseListResponse { [JsonPropertyName("licenses")] public List<LicenseDto>? Licenses { get; set; } }
    private sealed class IssueResponse { [JsonPropertyName("licenseKey")] public string LicenseKey { get; set; } = ""; }
    private sealed class LicenseDto
    {
        public string Id { get; set; } = "";
        [JsonPropertyName("license_ref")] public string LicenseRef { get; set; } = "";
        public string Status { get; set; } = "";
        [JsonPropertyName("expires_at")] public DateTimeOffset? ExpiresAt { get; set; }
        [JsonPropertyName("max_devices")] public int MaxDevices { get; set; }
        [JsonPropertyName("active_devices")] public int ActiveDevices { get; set; }
        [JsonPropertyName("created_at")] public DateTimeOffset CreatedAt { get; set; }
    }
    private static string EffectiveStatus(string status, DateTimeOffset? expiresAt)
    {
        if (string.Equals(status, "revoked", StringComparison.OrdinalIgnoreCase)) return "revoked";
        if (string.Equals(status, "expired", StringComparison.OrdinalIgnoreCase) || (expiresAt.HasValue && expiresAt.Value <= DateTimeOffset.UtcNow)) return "expired";
        return "active";
    }

    private sealed class LicenseRow
    {
        public LicenseRow(LicenseDto x) { Id=x.Id; Status=x.Status; ExpiresAt=x.ExpiresAt; DisplayRef=x.LicenseRef; ActiveDevices=x.ActiveDevices; MaxDevices=x.MaxDevices; }
        public string Id { get; }
        public string Status { get; }
        public DateTimeOffset? ExpiresAt { get; }
        public string DisplayRef { get; }
        public int ActiveDevices { get; }
        public int MaxDevices { get; }
        public string StatusLabel => EffectiveStatus(Status, ExpiresAt) switch { "active" => "사용 가능", "expired" => "만료", _ => "정지" };
        public string ExpiresLabel => ExpiresAt.HasValue ? ExpiresAt.Value.ToLocalTime().ToString("yyyy-MM-dd") : "영구 (PERMANENT)";
        public string DeviceLabel => $"{ActiveDevices}/{MaxDevices}";
    }
}