package com.guardian.adblock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDot: TextView
    private var startButton: Button? = null
    private lateinit var blockRulesInput: EditText
    private lateinit var allowRulesInput: EditText
    private var rulesFeedback: TextView? = null
    private var statsText: TextView? = null
    private var recentText: TextView? = null
    private var diagnosticText: TextView? = null
    private var selfTestText: TextView? = null
    private var filterStatusText: TextView? = null
    private var filterUpdateButton: Button? = null
    private var appFilterFeedback: TextView? = null
    private var receiverRegistered = false
    private var currentTab = "home"
    private val filterUpdateRunning = AtomicBoolean(false)

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshStatus()
    }

    private val bg = Color.rgb(10, 16, 30)
    private val panel = Color.rgb(20, 29, 48)
    private val panel2 = Color.rgb(27, 39, 61)
    private val accent = Color.rgb(72, 220, 174)
    private val muted = Color.rgb(151, 166, 190)
    private val white = Color.rgb(241, 246, 255)
    private val red = Color.rgb(255, 112, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        showTab("home")
        val filterPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val autoUpdate = filterPrefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
        val lastUpdate = FilterListManager.lastUpdatedAt(this)
        val updateInterval = filterPrefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, 24L * 60L * 60L * 1000L)
        val securityEnabled = filterPrefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val profileChanged = FilterListManager.hasCachedFile(this) &&
                FilterListManager.selectedProfile(this).key != FilterListManager.cachedProfile(this).key
        val securityStale = securityEnabled && (!FilterListManager.hasCachedSecurityList(this) ||
                System.currentTimeMillis() - FilterListManager.lastSecurityUpdatedAt(this) >= updateInterval)
        val androidAdsEnabled = filterPrefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false)
        val androidAdsStale = androidAdsEnabled && (!FilterListManager.hasCachedAndroidAdsList(this) ||
                System.currentTimeMillis() - FilterListManager.lastAndroidAdsUpdatedAt(this) >= updateInterval)
        if (autoUpdate && (!FilterListManager.hasCachedList(this) ||
                System.currentTimeMillis() - lastUpdate >= updateInterval || securityStale || androidAdsStale || profileChanged)) {
            startFilterUpdate()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(GuardianVpnService.ACTION_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(stateReceiver, filter)
        receiverRegistered = true
        refreshStatus()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startProtection()
            else {
                statusTitle.text = "VPN 권한이 필요합니다"
                status.text = "Android VPN 연결 요청이 승인되지 않아 DNS 보호를 시작하지 않았습니다."
            }
        }
    }

    private fun showTab(tab: String) {
        currentTab = tab
        startButton = null
        statsText = null
        recentText = null
        diagnosticText = null
        selfTestText = null
        rulesFeedback = null
        filterStatusText = null
        filterUpdateButton = null
        appFilterFeedback = null
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(24))
        }
        body.addView(header())
        when (tab) {
            "home" -> buildHome(body)
            "protection" -> buildProtection(body)
            "rules" -> buildRules(body)
            "activity" -> buildActivity(body)
            else -> buildSettings(body)
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav(tab))
        setContentView(root)
        root.requestApplyInsets()
        refreshStatus()
    }

    private fun header(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
        }
        val mark = TextView(this).apply {
            text = "G"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(bg)
            background = shape(accent, 15)
        }
        wrap.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        labels.addView(text("GUARDIAN", 18f, white, true))
        labels.addView(text("PRIVATE DNS PROTECTION", 9f, muted, true))
        wrap.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        wrap.addView(text("●  LOCAL VPN", 10f, accent, true))
        return wrap
    }

    private fun buildHome(body: LinearLayout) {
        body.addView(text("보호 대시보드", 25f, white, true))
        body.addView(text("기기의 DNS 연결을 실시간으로 보호합니다.", 13f, muted, false).withTop(5))
        val hero = card(panel, 22).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }
        hero.addView(text("GUARDIAN SHIELD", 10f, muted, true))
        val shield = TextView(this).apply {
            text = "⬡"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(0, dp(10), 0, dp(2))
        }
        hero.addView(shield, LinearLayout.LayoutParams(-1, dp(88)))
        statusDot = text("●  PROTECTION OFF", 10f, red, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        hero.addView(statusDot)
        statusTitle = text("보호 상태 확인 중", 21f, white, true).apply { gravity = Gravity.CENTER }
        hero.addView(statusTitle)
        status = text("", 12f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }
        hero.addView(status)
        val start = Button(this).apply {
            text = "보호 시작"
            isAllCaps = false
            textSize = 15f
            setTextColor(bg)
            background = shape(accent, 14)
            setOnClickListener { requestProtection() }
        }
        startButton = start
        hero.addView(start, LinearLayout.LayoutParams(-1, dp(52)))
        val stop = Button(this).apply {
            text = "보호 중지"
            isAllCaps = false
            textSize = 13f
            setTextColor(white)
            background = shape(panel2, 14)
            setOnClickListener { startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_STOP)) }
        }
        hero.addView(stop, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(9) })
        val selfTestButton = Button(this).apply {
            text = "DNS 차단 기능 테스트"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 13)
            setOnClickListener { runDnsSelfTest() }
        }
        hero.addView(selfTestButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(9) })
        selfTestText = text("테스트를 실행하면 차단 도메인과 정상 도메인의 DNS 응답을 확인합니다.", 11f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        hero.addView(selfTestText!!)
        body.addView(hero.withTop(22))
        body.addView(card(panel, 18).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text("광고 차단 필터", 14f, white, true))
            val filterReady = isFilterConfigurationReady()
            addView(text(
                if (filterReady) filterStatusDescription() else "선택한 공식 필터 구성이 아직 준비되지 않았습니다. 목록을 내려받고 검증해야 광고 차단을 시작할 수 있습니다.",
                11f, if (filterReady) accent else red, false
            ).withTop(8))
            addView(Button(this@MainActivity).apply {
                text = "필터 및 차단 옵션 관리"
                isAllCaps = false
                setTextColor(white)
                background = shape(panel2, 12)
                setOnClickListener { showTab("rules") }
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(10) })
        }.withTop(12))

        body.addView(text("보호 구성", 18f, white, true).withTop(24))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        grid.addView(infoCard("01", "DNS 필터링", "광고 · 추적 도메인", accent), LinearLayout.LayoutParams(0, dp(112), 1f))
        grid.addView(space(10), LinearLayout.LayoutParams(dp(10), 1))
        grid.addView(infoCard("02", "사용자 규칙", "차단 · 허용 목록", Color.rgb(116, 167, 255)), LinearLayout.LayoutParams(0, dp(112), 1f))
        body.addView(grid.withTop(12))
        body.addView(card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text("실시간 DNS 통계", 14f, white, true))
            val stats = text("질의 0 · 차단 0 · 전달 0 · 실패 0", 12f, accent, true).withTop(9) as TextView
            statsText = stats
            addView(stats)
            val recent = text("아직 기록된 DNS 활동이 없습니다.", 11f, muted, false).withTop(8) as TextView
            recentText = recent
            addView(recent)
        }.withTop(12))
        body.addView(card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text("현재 보호 범위", 14f, white, true))
            addView(text("IPv4 / IPv6 · UDP/53 DNS", 12f, muted, false).withTop(7))
            addView(text("DNS 이외 트래픽, DoH/DoT는 아직 필터링하지 않습니다.", 11f, muted, false).withTop(5))
        }.withTop(12))
    }

    private fun buildActivity(body: LinearLayout) {
        body.addView(text("활동 및 통계", 25f, white, true))
        body.addView(text("VPN DNS 엔진이 실제 처리한 요청만 기록합니다.", 13f, muted, false).withTop(5))
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val summary = card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("실시간 DNS 통계", 14f, white, true))
            statsText = text(
                "질의 ${prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0L)} · 차단 ${prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L)} · 전달 ${prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L)} · 실패 ${prefs.getLong(GuardianVpnService.KEY_DNS_FAILED, 0L)} · 무시 ${prefs.getLong(GuardianVpnService.KEY_DNS_IGNORED, 0L)}",
                12f, accent, true
            ).withTop(10) as TextView
            addView(statsText!!)
        }
        body.addView(summary.withTop(18))
        body.addView(Button(this).apply {
            text = "DNS 차단 기능 테스트"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 12)
            setOnClickListener { runDnsSelfTest() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        selfTestText = text("8.8.8.8 직접 질의도 VPN에서 가로채는지 확인합니다.", 11f, muted, false).withTop(8) as TextView
        body.addView(selfTestText!!)
        body.addView(Button(this).apply {
            text = "활동 기록 초기화"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 12)
            setOnClickListener {
                prefs.edit()
                    .putLong(GuardianVpnService.KEY_DNS_QUERIES, 0L)
                    .putLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L)
                    .putLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L)
                    .putLong(GuardianVpnService.KEY_DNS_FAILED, 0L)
                    .putLong(GuardianVpnService.KEY_DNS_IGNORED, 0L)
                    .putString(GuardianVpnService.KEY_DNS_RECENT, "")
                    .putString(GuardianVpnService.KEY_DNS_DIAGNOSTIC, "")
                    .apply()
                refreshStatus()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        body.addView(text("최근 DNS 요청", 18f, white, true).withTop(24))
        recentText = text(
            prefs.getString(GuardianVpnService.KEY_DNS_RECENT, null)
                ?: "아직 DNS 요청 기록이 없습니다. 보호를 시작한 뒤 앱이나 웹사이트를 이용해 주세요.",
            12f, muted, false
        ).withTop(10) as TextView
        body.addView(card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(recentText!!)
        }.withTop(10))
        diagnosticText = text(
            "마지막 패킷 진단: ${prefs.getString(GuardianVpnService.KEY_DNS_DIAGNOSTIC, "아직 기록 없음")}",
            11f, muted, false
        ).withTop(12) as TextView
        body.addView(diagnosticText!!)
        body.addView(text("이 화면은 DNS 암호화 또는 VPN 우회 트래픽을 집계하지 않습니다.", 11f, muted, false).withTop(14))
    }

    private fun buildProtection(body: LinearLayout) {
        body.addView(text("보호 관리", 25f, white, true))
        body.addView(text("Guardian의 보호 엔진과 적용 범위를 확인합니다.", 13f, muted, false).withTop(5))
        body.addView(infoRow("DNS 보호", "광고 및 추적 도메인 질의 필터링", "활성화는 홈에서 시작", accent))
        body.addView(infoRow("IPv4 / IPv6", "가상 DNS + 주요 공용 DNS 주소", "UDP/53", accent))
        body.addView(infoRow("사용자 규칙", "직접 등록한 차단·허용 도메인", "필터 탭에서 관리", Color.rgb(116,167,255)))
        body.addView(infoRow("암호화 DNS", "DoH / DoT 트래픽", "미지원", red))
        body.addView(infoRow("앱별 보호 제외", "선택한 앱을 VPN 필터에서 제외", "설정에서 관리", accent))
        body.addView(text("보호 범위 안내", 17f, white, true).withTop(24))
        body.addView(text("현재 Guardian은 Android 가상 DNS와 주요 공용 DNS 주소로 향하는 IPv4/IPv6 UDP 53번 질의를 로컬 VPN으로 전달해 필터링합니다. DoH/DoT, TCP DNS, 목록에 없는 DNS 주소, HTTPS 내부 광고 요소는 필터링하지 못할 수 있습니다.", 13f, muted, false).withTop(8))
    }

    private fun buildRules(body: LinearLayout) {
        body.addView(text("필터 및 규칙", 25f, white, true))
        body.addView(text("활성 필터 목록과 나만의 차단·허용 규칙을 관리합니다.", 13f, muted, false).withTop(5))
        val officialFilterCard = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("DNS FILTER PROFILE", 10f, accent, true))
            addView(text(FilterListManager.selectedProfile(this@MainActivity).label, 14f, white, true).withTop(7))
            filterStatusText = text(filterStatusDescription(), 11f, muted, false).withTop(7) as TextView
            addView(filterStatusText!!)
            addView(buildFilterProfileSelector().withTop(10))
            addView(buildFilterOptions().withTop(12))
            filterUpdateButton = Button(this@MainActivity).apply {
                text = "필터 목록 업데이트"
                isAllCaps = false
                setTextColor(bg)
                background = shape(accent, 12)
                setOnClickListener { startFilterUpdate() }
            }
            addView(filterUpdateButton!!, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }
        body.addView(officialFilterCard.withTop(18))
        body.addView(text("사용자 규칙", 16f, white, true).withTop(22))
        body.addView(text("차단 도메인", 14f, white, true).withTop(12))
        body.addView(text("도메인 하나를 한 줄에 입력하세요. 하위 도메인도 함께 차단됩니다.", 12f, muted, false).withTop(5))
        blockRulesInput = domainEditor("ads.example.com\ntracker.example.net")
        body.addView(blockRulesInput, editorHeight().apply { topMargin = dp(10) })
        body.addView(text("허용 도메인", 16f, white, true).withTop(20))
        body.addView(text("차단 목록보다 허용 규칙이 우선 적용됩니다.", 12f, muted, false).withTop(5))
        allowRulesInput = domainEditor("trusted.example.com")
        body.addView(allowRulesInput, editorHeight().apply { topMargin = dp(10) })
        body.addView(Button(this).apply {
            text = "규칙 저장 및 적용"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 14)
            setOnClickListener { saveUserRules() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
        val feedback = text("규칙은 기기 내부에 안전하게 저장됩니다.", 12f, muted, false).withTop(10) as TextView
        rulesFeedback = feedback
        body.addView(feedback)
        loadUserRulesIntoEditors()
    }

    private fun buildSettings(body: LinearLayout) {
        body.addView(text("설정", 25f, white, true))
        body.addView(text("Guardian 앱 정보와 보호 정책", 13f, muted, false).withTop(5))
        body.addView(infoRow("앱 버전", "Guardian AdBlock", "0.7.0-dev", accent))
        body.addView(infoRow("데이터 저장", "사용자 규칙은 기기 내부에 저장", "로컬", accent))
        body.addView(infoRow("개인정보", "외부 계정 및 분석 SDK 미사용", "로컬 우선", accent))
        body.addView(text("DNS 업스트림 서버", 18f, white, true).withTop(24))
        body.addView(text("차단되지 않은 DNS 질의를 전달할 서버를 선택합니다.", 12f, muted, false).withTop(5))
        val dnsPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val knownDnsOptions = listOf(
            "Cloudflare · 1.1.1.1" to "1.1.1.1",
            "Google · 8.8.8.8" to "8.8.8.8",
            "Quad9 · 9.9.9.9" to "9.9.9.9",
            "AdGuard DNS · 광고/추적 차단 · 94.140.14.14" to "94.140.14.14",
            "AdGuard DNS 가족 보호 · 성인 콘텐츠 차단 · 94.140.14.15" to "94.140.14.15"
        )
        val currentDns = dnsPrefs.getString(GuardianVpnService.KEY_UPSTREAM_DNS, GuardianVpnService.DEFAULT_UPSTREAM_DNS)
            ?: GuardianVpnService.DEFAULT_UPSTREAM_DNS
        val dnsOptions = knownDnsOptions.toMutableList()
        if (knownDnsOptions.none { it.second == currentDns }) {
            dnsOptions.add(0, "사용자 지정 · $currentDns" to currentDns)
        }
        val dnsSpinner = Spinner(this)
        val dnsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dnsOptions.map { it.first })
        dnsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dnsSpinner.adapter = dnsAdapter
        val selectedDns = dnsOptions.indexOfFirst { it.second == currentDns }.coerceAtLeast(0)
        dnsSpinner.setSelection(selectedDns)
        dnsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_DNS, dnsOptions[position].second).apply()
                if (ProtectionState.isActive(
                        dnsPrefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                        GuardianVpnService.isTunnelActive()
                    )) {
                    startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
            }
        }
        body.addView(card(panel, 14).apply {
            setPadding(dp(12), dp(5), dp(12), dp(5))
            addView(dnsSpinner)
        }.withTop(10))
        body.addView(text(
            "시스템 가상 DNS의 업스트림은 즉시 변경됩니다. 공용 DNS 직접 포착을 사용하는 경우 새 서버 경로는 보호를 중지하고 다시 시작해야 적용됩니다.",
            10f, muted, false
        ).withTop(7))
        val customDnsInput = EditText(this).apply {
            hint = "사용자 지정 IPv4/IPv6 주소"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = shape(panel2, 12)
            if (knownDnsOptions.none { it.second == currentDns }) setText(currentDns)
        }
        body.addView(customDnsInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        val customDnsFeedback = text("IP 주소만 입력할 수 있습니다. 호스트 이름은 허용하지 않습니다.", 11f, muted, false).withTop(6) as TextView
        body.addView(customDnsFeedback)
        body.addView(Button(this).apply {
            text = "사용자 지정 DNS 저장"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 12)
            setOnClickListener {
                val normalized = DnsAddressValidator.normalize(customDnsInput.text.toString())
                if (normalized == null) {
                    customDnsFeedback.text = "올바른 IPv4 또는 IPv6 주소를 입력하세요."
                    customDnsFeedback.setTextColor(red)
                } else {
                    dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_DNS, normalized).apply()
                    var index = dnsOptions.indexOfFirst { it.second == normalized }
                    if (index < 0) {
                        dnsOptions.add(0, "사용자 지정 · $normalized" to normalized)
                        dnsAdapter.clear()
                        dnsAdapter.addAll(dnsOptions.map { it.first })
                        index = 0
                    }
                    dnsSpinner.setSelection(index)
                    customDnsInput.setText(normalized)
                    customDnsFeedback.text = "DNS 서버 저장 완료: $normalized"
                    customDnsFeedback.setTextColor(accent)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        body.addView(text("앱별 보호 제외", 18f, white, true).withTop(24))
        body.addView(text("선택한 앱은 Guardian VPN 필터링에서 제외됩니다. 변경 사항은 보호를 다시 시작하면 적용됩니다.", 12f, muted, false).withTop(5))
        buildAppExclusions(body)
        body.addView(text("현재 DNS 필터는 UDP/53 요청을 기준으로 동작합니다. 앱 내부 광고, DoH/DoT, 목록에 없는 사용자 지정 DNS 서버는 별도 지원이 필요합니다.", 12f, muted, false).withTop(14))
    }

    private fun bottomNav(selected: String): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15, 23, 39))
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        val items = listOf(
            Triple("home", "⌂", "홈"),
            Triple("protection", "◈", "보호"),
            Triple("activity", "▤", "활동"),
            Triple("rules", "≡", "필터"),
            Triple("settings", "⚙", "설정")
        )
        items.forEach { (id, icon, label) ->
            val active = selected == id
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { showTab(id) }
            }
            item.addView(text(icon, 21f, if (active) accent else muted, true).apply { gravity = Gravity.CENTER })
            item.addView(text(label, 10f, if (active) white else muted, active).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        return nav
    }

    private fun runDnsSelfTest() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        if (!ProtectionState.isActive(
                prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                GuardianVpnService.isTunnelActive()
            )) {
            selfTestText?.text = "테스트할 수 없습니다 · 먼저 DNS 보호를 시작하세요."
            selfTestText?.setTextColor(red)
            return
        }
        selfTestText?.text = "DNS 테스트 실행 중…"
        val directCapture = prefs.getBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, true)
        val blockedDnsServer = if (directCapture) "8.8.8.8" else "10.13.37.2"
        thread(name = "guardian-dns-self-test") {
            try {
                val blocked = DnsProbe.query("doubleclick.net", dnsServer = blockedDnsServer)
                val normal = DnsProbe.query("example.com")
                val passed = blocked.responseCode == 3 && normal.responseCode == 0
                val routeLabel = if (directCapture) "공용 DNS 직접 질의" else "시스템 가상 DNS"
                val result = if (passed) {
                    "통과 · $routeLabel 차단(NXDOMAIN) / example.com 정상(NOERROR)"
                } else {
                    "실패 · 차단 도메인 RCODE=${blocked.responseCode}, 정상 도메인 RCODE=${normal.responseCode}"
                }
                runOnUiThread {
                    selfTestText?.text = result
                    selfTestText?.setTextColor(if (passed) accent else red)
                    refreshStatus()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    selfTestText?.text = "실패 · ${e.message ?: e.javaClass.simpleName} · VPN 연결 및 DNS 처리 로그를 확인하세요."
                    selfTestText?.setTextColor(red)
                }
            }
        }
    }

    private fun isFilterConfigurationReady(): Boolean {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        return FilterListManager.isProfileCacheCurrent(
            FilterListManager.hasCachedList(this),
            FilterListManager.selectedProfile(this).key,
            FilterListManager.cachedProfile(this).key,
            prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false),
            FilterListManager.hasCachedSecurityList(this),
            prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false),
            FilterListManager.hasCachedAndroidAdsList(this)
        )
    }

    private fun buildFilterOptions(): View {
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val autoUpdateCheck = CheckBox(this).apply {
            text = "앱 실행 시 자동으로 최신 필터 확인"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(FilterListManager.KEY_AUTO_UPDATE, checked).apply()
            }
        }
        options.addView(autoUpdateCheck)
        options.addView(text("필터 업데이트 주기", 12f, muted, false).withTop(8))
        val intervals = listOf(
            "6시간마다" to 6L * 60L * 60L * 1000L,
            "12시간마다" to 12L * 60L * 60L * 1000L,
            "24시간마다" to 24L * 60L * 60L * 1000L,
            "7일마다" to 7L * 24L * 60L * 60L * 1000L
        )
        val intervalSpinner = Spinner(this)
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals.map { it.first })
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        intervalSpinner.adapter = intervalAdapter
        val currentInterval = prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, 24L * 60L * 60L * 1000L)
        intervalSpinner.setSelection(intervals.indexOfFirst { it.second == currentInterval }.coerceAtLeast(2))
        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, intervals[position].second).apply()
            }
        }
        options.addView(intervalSpinner)
        options.addView(text(
            "업데이트 확인은 앱을 실행할 때 수행됩니다. 앱을 닫은 동안의 백그라운드 자동 업데이트는 아직 지원하지 않습니다.",
            10f, muted, false
        ).withTop(3))
        val securityCheck = CheckBox(this).apply {
            text = "악성코드·피싱 보호 (HaGeZi Threat Intelligence)"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(FilterListManager.KEY_SECURITY_ENABLED, checked).apply()
                if (ProtectionState.isActive(
                        prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                        GuardianVpnService.isTunnelActive()
                    )) {
                    startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
                if (checked && !FilterListManager.hasCachedSecurityList(this@MainActivity)) startFilterUpdate()
                else filterStatusText?.text = filterStatusDescription()
            }
        }
        options.addView(securityCheck)
        val directDnsCheck = CheckBox(this).apply {
            text = "공용 DNS 직접 질의 포착 (고급)"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, checked).apply()
                filterStatusText?.text = if (checked) {
                    "공용 DNS 직접 질의 포착 설정 저장됨 · 보호를 중지한 뒤 다시 시작하면 적용됩니다."
                } else {
                    "시스템 가상 DNS만 포착하도록 설정됨 · 보호를 중지한 뒤 다시 시작하면 적용됩니다."
                }
            }
        }
        options.addView(directDnsCheck)
        options.addView(text(
            "고급 포착은 IP 주소 단위 경로를 사용하므로 해당 DNS 서버 IP로 향하는 DNS 이외의 연결도 VPN에 들어올 수 있습니다. DNS 외 트래픽은 중계하지 않으므로 연결 문제가 생기면 이 옵션을 끄세요.",
            10f, muted, false
        ).withTop(2))
        return options
    }

    private fun buildFilterProfileSelector(): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(text("차단 목록 프로필", 12f, muted, false))
        val profilePrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val filterProfiles = FilterListManager.availableProfiles()
        val profileSpinner = Spinner(this)
        val profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterProfiles.map { it.label })
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = profileAdapter
        val selectedProfileKey = FilterListManager.selectedProfile(this).key
        profileSpinner.setSelection(filterProfiles.indexOfFirst { it.key == selectedProfileKey }.coerceAtLeast(0))
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = filterProfiles[position]
                val current = profilePrefs.getString(FilterListManager.KEY_FILTER_PROFILE, "adguard") ?: "adguard"
                if (selected.key == current) return
                if (filterUpdateRunning.get()) {
                    val currentIndex = filterProfiles.indexOfFirst { it.key == current }.coerceAtLeast(0)
                    profileSpinner.setSelection(currentIndex)
                    filterStatusText?.text = "필터 업데이트 중에는 프로필을 변경할 수 없습니다."
                    return
                }
                profilePrefs.edit()
                    .putString(FilterListManager.KEY_FILTER_PROFILE, selected.key)
                    .putLong(FilterListManager.KEY_UPDATED_AT, 0L)
                    .apply()
                filterStatusText?.text = "선택한 프로필: ${selected.label} · 새 목록을 내려받습니다."
                startFilterUpdate()
            }
        }
        wrapper.addView(profileSpinner)
        wrapper.addView(text(
            "강력한 프로필은 차단 범위가 넓지만 일부 정상 서비스가 차단될 수 있습니다.",
            10f, muted, false
        ).withTop(4))
        return wrapper
    }

    private fun buildAppExclusions(body: LinearLayout) {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val excluded = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty().toMutableSet()
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
        val search = EditText(this).apply {
            hint = "앱 이름 또는 패키지명 검색"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = shape(panel2, 12)
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        val card = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(appList)
        body.addView(card.withTop(10))
        appFilterFeedback = text(
            "설치 앱 ${apps.size}개 · 보호 제외 앱 ${excluded.size}개",
            11f, muted, false
        ).withTop(8) as TextView
        body.addView(appFilterFeedback!!)
        fun renderApps(rawQuery: String) {
            appList.removeAllViews()
            val query = rawQuery.trim().lowercase(Locale.ROOT)
            val visible = apps.filter { (pkg, label) ->
                query.isEmpty() || label.lowercase(Locale.ROOT).contains(query) || pkg.lowercase(Locale.ROOT).contains(query)
            }.take(250)
            visible.forEach { (pkg, label) ->
                val check = CheckBox(this).apply {
                    text = "$label\n$pkg"
                    textSize = 12f
                    setTextColor(white)
                    isChecked = pkg in excluded
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) excluded.add(pkg) else excluded.remove(pkg)
                        prefs.edit().putStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, excluded.toSet()).apply()
                        appFilterFeedback?.text = "설치 앱 ${apps.size}개 · 제외 앱 ${excluded.size}개 저장됨 · 보호 재시작 후 적용"
                    }
                }
                appList.addView(check)
            }
            if (visible.isEmpty()) {
                appList.addView(text("검색 결과가 없습니다.", 12f, muted, false).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) })
            }
            appFilterFeedback?.text = "검색 결과 ${visible.size}개 / 전체 ${apps.size}개 · 제외 앱 ${excluded.size}개"
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderApps(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        renderApps("")
    }

    private fun filterStatusDescription(): String {
        val count = FilterListManager.cachedBlockCount(this)
        val updated = FilterListManager.lastUpdatedAt(this)
        val time = if (updated > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updated)) else "아직 업데이트되지 않음"
        val securityCount = FilterListManager.cachedSecurityBlockCount(this)
        val securityOn = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
            .getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val ignored = FilterListManager.cachedIgnoredCount(this)
        val securityIgnored = if (securityOn) FilterListManager.cachedSecurityIgnoredCount(this) else 0
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val lastError = prefs.getString(FilterListManager.KEY_LAST_ERROR, null)
        val securityError = if (securityOn) prefs.getString(FilterListManager.KEY_SECURITY_LAST_ERROR, null) else null
        val selectedProfile = FilterListManager.selectedProfile(this)
        val cachedProfile = FilterListManager.cachedProfile(this)
        val profileLabel = if (FilterListManager.hasCachedList(this) && selectedProfile.key != cachedProfile.key) {
            "선택: ${selectedProfile.label} · 적용 중: ${cachedProfile.label}"
        } else selectedProfile.label
        val summary = "$profileLabel · 광고·추적 ${count}개 · 보안 ${if (securityOn) securityCount else 0}개 · 미지원 규칙 ${ignored + securityIgnored}개 제외 · 업데이트: $time"
        return listOfNotNull(
            summary,
            lastError?.takeIf { it.isNotBlank() }?.let { "광고 필터 업데이트 실패(이전 목록 유지): $it" },
            securityError?.takeIf { it.isNotBlank() }?.let { "보안 필터 업데이트 실패(이전 목록 유지): $it" }
        ).joinToString(" · ")
    }

    private fun startFilterUpdate() {
        if (!filterUpdateRunning.compareAndSet(false, true)) {
            filterStatusText?.text = "필터 목록을 이미 다운로드하고 있습니다."
            return
        }
        filterUpdateButton?.isEnabled = false
        filterUpdateButton?.text = "필터 다운로드 및 검증 중…"
        filterStatusText?.text = "${FilterListManager.selectedProfile(this).label} 목록을 내려받아 규칙을 검증합니다."
        val hadPreviousMainCache = FilterListManager.hasCachedList(this)
        thread(name = "guardian-filter-update") {
            try {
                val result = FilterListManager.update(this)
                val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()
                val securityWasCached = FilterListManager.hasCachedSecurityList(this)
                val securityResult = if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
                    try {
                        FilterListManager.updateSecurity(this).also {
                            prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                        }
                    } catch (e: Exception) {
                        prefs.edit().putString(
                            FilterListManager.KEY_SECURITY_LAST_ERROR,
                            e.message ?: e.javaClass.simpleName
                        ).apply()
                        null
                    }
                } else null
                val active = ProtectionState.isActive(
                    prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                    GuardianVpnService.isTunnelActive()
                )
                if (active) {
                    startService(Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
                filterUpdateRunning.set(false)
                runOnUiThread {
                    val securityMessage = if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
                        if (securityResult != null) " · 보안 ${securityResult.blocked}개" else if (securityWasCached) " · 보안 업데이트 실패(이전 목록 유지)" else " · 보안 목록 다운로드 실패(보안 필터 미적용)"
                    } else ""
                    filterStatusText?.text = "${FilterListManager.selectedProfile(this).label} · 광고·추적 ${result.blocked}개 · 미지원 ${result.ignored}개 제외${securityMessage} · ${result.bytes / 1024}KB"
                    filterUpdateButton?.text = "필터 목록 업데이트"
                    filterUpdateButton?.isEnabled = true
                }
            } catch (e: Exception) {
                filterUpdateRunning.set(false)
                getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE).edit()
                    .putString(FilterListManager.KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName).apply()
                runOnUiThread {
                    val cacheMessage = if (hadPreviousMainCache) {
                        "이전 정상 필터를 계속 사용합니다."
                    } else {
                        "정상 필터가 없어 보호를 시작할 수 없습니다."
                    }
                    filterStatusText?.text = "업데이트 실패: ${e.message ?: e.javaClass.simpleName} · $cacheMessage"
                    filterUpdateButton?.text = "다시 시도"
                    filterUpdateButton?.isEnabled = true
                }
            }
        }
    }

    private fun requestProtection() {
        val hasCachedList = FilterListManager.hasCachedList(this)
        val selectedProfile = FilterListManager.selectedProfile(this)
        val cachedProfile = FilterListManager.cachedProfile(this)
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val hasSecurityCache = FilterListManager.hasCachedSecurityList(this)
        val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false)
        val hasAndroidAdsCache = FilterListManager.hasCachedAndroidAdsList(this)
        if (!FilterListManager.isProfileCacheCurrent(
                hasCachedList, selectedProfile.key, cachedProfile.key,
                securityEnabled, hasSecurityCache, androidAdsEnabled, hasAndroidAdsCache
            )) {
            statusTitle.text = "광고 필터 준비 필요"
            status.text = when {
                !hasCachedList -> "공식 광고 차단 목록을 아직 내려받지 못했습니다. 인터넷 연결 후 필터를 준비해야 정상적인 광고 차단이 가능합니다."
                selectedProfile.key != cachedProfile.key -> "선택한 ${selectedProfile.label} 목록이 아직 적용되지 않았습니다. 이전 프로필로 보호를 시작하지 않도록 다운로드 완료 후 다시 시작해 주세요."
                securityEnabled && !hasSecurityCache -> "악성코드·피싱 보호 목록이 준비되지 않았습니다. 보안 목록 다운로드를 완료한 후 다시 시작해 주세요."
                androidAdsEnabled && !hasAndroidAdsCache -> "Android 앱 광고 SDK 필터가 준비되지 않았습니다. 앱 광고 목록 다운로드를 완료한 후 다시 시작해 주세요."
                else -> "선택한 필터 구성을 아직 준비하지 못했습니다. 필터 업데이트를 완료한 후 다시 시작해 주세요."
            }
            showTab("rules")
            startFilterUpdate()
            return
        }
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, REQUEST_VPN) else startProtection()
    }

    private fun startProtection() {
        val service = Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
            status.text = "보호 시작 요청을 전달했습니다. VPN 연결을 확인하고 있습니다."
            statusTitle.text = "연결 확인 중"
        } catch (e: Exception) {
            statusTitle.text = "시작하지 못했습니다"
            status.text = e.message ?: "Android가 VPN 서비스를 시작하지 못했습니다."
        }
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        // A persisted flag can outlive an Android process kill. Confirm this
        // process still owns the VPN tunnel before showing protection as active.
        val persistedActive = prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        val active = ProtectionState.isActive(persistedActive, GuardianVpnService.isTunnelActive())
        val staleSession = persistedActive && !active
        val message = if (staleSession) {
            "앱 프로세스가 재시작되어 VPN 보호가 중단되었습니다. 다시 시작해 주세요."
        } else prefs.getString(GuardianVpnService.KEY_STATUS, null)
        if (staleSession) {
            // Clear stale persisted state so subsequent rule saves do not try to
            // reload a service that no longer owns a TUN descriptor.
            prefs.edit().putBoolean(GuardianVpnService.KEY_ACTIVE, false)
                .putString(GuardianVpnService.KEY_STATUS, message).apply()
        }
        // Not every tab creates the dashboard status views. Update only views
        // that exist so opening Rules/Protection/Settings cannot crash the app.
        if (::statusTitle.isInitialized) {
            statusTitle.text = if (active) "보호가 실행 중입니다" else "보호가 꺼져 있습니다"
            statusDot.text = if (active) "●  PROTECTION ACTIVE" else "●  PROTECTION OFF"
            statusDot.setTextColor(if (active) accent else red)
            startButton?.text = if (active) "보호 실행 중" else "보호 시작"
            startButton?.isEnabled = !active
            startButton?.alpha = if (active) 0.58f else 1f
        }
        if (::status.isInitialized) {
            status.text = message ?: if (active) "DNS 보호 서비스가 실행 중입니다." else "보호 시작을 눌러 VPN 권한을 승인하세요."
        }
        statsText?.text = "질의 ${prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0)} · 차단 ${prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0)} · 전달 ${prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0)} · 실패 ${prefs.getLong(GuardianVpnService.KEY_DNS_FAILED, 0)} · 무시 ${prefs.getLong(GuardianVpnService.KEY_DNS_IGNORED, 0)}"
        recentText?.text = prefs.getString(GuardianVpnService.KEY_DNS_RECENT, null) ?: "아직 기록된 DNS 활동이 없습니다."
        diagnosticText?.text = "마지막 패킷 진단: ${prefs.getString(GuardianVpnService.KEY_DNS_DIAGNOSTIC, "아직 기록 없음")}"
    }

    private fun loadUserRulesIntoEditors() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        blockRulesInput.setText(prefs.getString(GuardianVpnService.KEY_USER_BLOCK, "") ?: "")
        allowRulesInput.setText(prefs.getString(GuardianVpnService.KEY_USER_ALLOW, "") ?: "")
    }

    private fun saveUserRules() {
        val block = parseEditor(blockRulesInput, "차단 목록") ?: return
        val allow = parseEditor(allowRulesInput, "허용 목록") ?: return
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        prefs.edit().putString(GuardianVpnService.KEY_USER_BLOCK, block.joinToString("\n"))
            .putString(GuardianVpnService.KEY_USER_ALLOW, allow.joinToString("\n")).apply()
        val protectionActive = ProtectionState.isActive(
            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
            GuardianVpnService.isTunnelActive()
        )
        if (protectionActive) {
            startService(Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
            rulesFeedback?.text = "규칙을 저장하고 적용을 요청했습니다."
        } else rulesFeedback?.text = "규칙을 저장했습니다. 다음 보호 시작 시 적용됩니다."
    }

    private fun parseEditor(editor: EditText, label: String): List<String>? {
        val domains = linkedSetOf<String>()
        for ((index, raw) in editor.text.toString().lineSequence().withIndex()) {
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("#")) continue
            val domain = DomainFilter.normalizeRuleDomain(value)
            if (domain == null) {
                rulesFeedback?.text = "$label ${index + 1}번째 줄의 도메인을 확인하세요: $value"
                return null
            }
            domains.add(domain)
            if (domains.size > MAX_USER_RULES) {
                rulesFeedback?.text = "${label}은 최대 ${MAX_USER_RULES}개까지 등록할 수 있습니다."
                return null
            }
        }
        return domains.toList()
    }

    private fun infoCard(number: String, title: String, subtitle: String, tint: Int) = card(panel2, 16).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(12), dp(12))
        addView(text(number, 10f, tint, true))
        addView(text(title, 14f, white, true).withTop(7))
        addView(text(subtitle, 10f, muted, false).withTop(4))
    }

    private fun infoRow(title: String, subtitle: String, value: String, tint: Int) = card(panel, 16).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 14f, white, true))
        labels.addView(text(subtitle, 11f, muted, false).withTop(4))
        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(value, 11f, tint, true))
    }.withTop(10)

    private fun card(color: Int, radius: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(color, radius)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun domainEditor(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(muted)
        setTextColor(white)
        textSize = 13f
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(false)
        minLines = 3
        setPadding(dp(13), dp(13), dp(13), dp(13))
        background = shape(panel2, 13)
    }

    private fun editorHeight() = LinearLayout.LayoutParams(-1, dp(120))
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(width, 1) }
    private fun View.withTop(value: Int): View {
        val p = layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(value)
        layoutParams = p
        return this
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_VPN = 100
        private const val MAX_USER_RULES = 1000
    }
}
