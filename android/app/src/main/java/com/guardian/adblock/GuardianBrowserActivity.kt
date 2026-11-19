package com.guardian.adblock

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Optional in-app browser. Cosmetic filtering is intentionally limited to
 * pages rendered in this WebView; it does not decrypt or modify other apps'
 * HTTPS traffic.
 */
class GuardianBrowserActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var hideEmptyAdSlots = true
    @Volatile private var cosmeticRules = CosmeticFilterEngine.parse(CosmeticFilterEngine.DEFAULT_RULES)
    @Volatile private var cosmeticRuleCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideEmptyAdSlots = getPreferences(MODE_PRIVATE).getBoolean(KEY_HIDE_EMPTY_SLOTS, true)
        buildScreen()
        loadCosmeticRules()
        webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com")
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 16, 30))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 24f
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(44), dp(46)))
        address = EditText(this).apply {
            hint = "웹사이트 주소 입력"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(10), 0, dp(10), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { navigate(); true } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            leftMargin = dp(6); rightMargin = dp(6)
        })
        val go = Button(this).apply { text = "이동"; setOnClickListener { navigate() } }
        top.addView(go, LinearLayout.LayoutParams(dp(54), dp(46)))
        root.addView(top)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val toggle = Button(this).apply {
            text = if (hideEmptyAdSlots) "빈 광고 영역 숨김: 켬" else "빈 광고 영역 숨김: 끔"
            setOnClickListener {
                hideEmptyAdSlots = !hideEmptyAdSlots
                getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_HIDE_EMPTY_SLOTS, hideEmptyAdSlots).apply()
                text = if (hideEmptyAdSlots) "빈 광고 영역 숨김: 켬" else "빈 광고 영역 숨김: 끔"
                applyCosmeticStyles()
            }
        }
        actions.addView(toggle, LinearLayout.LayoutParams(0, dp(40), 1f))
        actions.addView(Button(this).apply {
            text = "새로고침"
            setOnClickListener { webView.reload() }
        }, LinearLayout.LayoutParams(dp(92), dp(40)).apply { leftMargin = dp(6) })
        root.addView(actions)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        status = TextView(this).apply {
            text = "Guardian 브라우저 · 필터는 이 화면에만 적용됩니다."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root.addView(status)
        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }
            override fun onPageFinished(view: WebView, url: String) {
                address.setText(url)
                status.text = if (hideEmptyAdSlots) {
                    "지원 CSS 규칙 ${cosmeticRuleCount}개 · Guardian 내부 브라우저 전용"
                } else {
                    "코스메틱 숨김 꺼짐 · Guardian 내부 브라우저"
                }
                applyCosmeticStyles()
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                status.text = "보안 인증서 오류로 페이지 연결을 차단했습니다."
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun navigate() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        val uri = runCatching { Uri.parse(candidate) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.userInfo != null) {
            Toast.makeText(this, "HTTP 또는 HTTPS 웹 주소만 입력할 수 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        webView.loadUrl(uri.toString())
    }

    /** Load the active, validated filter caches away from the WebView UI thread. */
    private fun loadCosmeticRules() {
        thread(name = "guardian-browser-cosmetic-rules") {
            val loaded = runCatching {
                val cached = FilterListManager.cachedCosmeticRules(applicationContext)
                // Keep the built-in ad-slot selectors active even when a downloaded
                // cosmetic list exists. Previously the cached feed replaced these
                // defaults, leaving common empty "Advertisement" boxes visible.
                CosmeticFilterEngine.withBuiltInDefaults(cached)
            }.getOrElse {
                CosmeticFilterEngine.parse(CosmeticFilterEngine.DEFAULT_RULES)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                cosmeticRules = loaded
                cosmeticRuleCount = loaded.genericHideRules.size + loaded.domainHideRules.size
                if (::webView.isInitialized && webView.url != null && hideEmptyAdSlots) {
                    status.text = "지원 CSS 규칙 ${cosmeticRuleCount}개 · Guardian 내부 브라우저 전용"
                    applyCosmeticStyles()
                }
            }
        }
    }

    private fun applyCosmeticStyles() {
        if (!::webView.isInitialized || !hideEmptyAdSlots) {
            if (::webView.isInitialized) webView.evaluateJavascript(
                "document.getElementById('guardian-cosmetic-style')?.remove();", null
            )
            return
        }
        val rules = cosmeticRules
        val host = runCatching { Uri.parse(webView.url ?: "").host.orEmpty() }.getOrDefault("")
        val css = CosmeticFilterEngine.cssForHost(rules, host)
        val encoded = org.json.JSONObject.quote(css)
        val script = """
            (function() {
              var old = document.getElementById('guardian-cosmetic-style');
              if (old) old.remove();
              var style = document.createElement('style');
              style.id = 'guardian-cosmetic-style';
              style.textContent = $encoded;
              (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "guardian.browser.url"
        private const val KEY_HIDE_EMPTY_SLOTS = "hide_empty_ad_slots"
    }
}
