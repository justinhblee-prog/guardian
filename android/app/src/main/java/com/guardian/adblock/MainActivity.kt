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
    private var filterStatusText: TextView? = null
    private var filterUpdateButton: Button? = null
    private var appFilterFeedback: TextView? = null
    private var receiverRegistered = false
    private var currentTab = "home"

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
        val securityEnabled = filterPrefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val securityStale = securityEnabled && (FilterListManager.cachedSecurityLines(this).isEmpty() ||
                System.currentTimeMillis() - FilterListManager.lastSecurityUpdatedAt(this) >= 24L * 60L * 60L * 1000L)
        if (autoUpdate && (FilterListManager.cachedLines(this).isEmpty() ||
                System.currentTimeMillis() - lastUpdate >= 24L * 60L * 60L * 1000L || securityStale)) {
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
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) startProtection()
    }

    private fun showTab(tab: String) {
        currentTab = tab
        startButton = null
        statsText = null
        recentText = null
        diagnosticText = null
        rulesFeedback = null
        filterStatusText = null
        filterUpdateButton = null
        appFilterFeedback = null
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
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
        body.addView(hero.withTop(22))

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
            addView(text("IPv4 / IPv6 DNS · UDP 53", 12f, muted, false).withTop(7))
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
        body.addView(infoRow("IPv4 / IPv6", "가상 DNS 엔드포인트", "지원", accent))
        body.addView(infoRow("사용자 규칙", "직접 등록한 차단·허용 도메인", "규칙 탭에서 관리", Color.rgb(116,167,255)))
        body.addView(infoRow("암호화 DNS", "DoH / DoT 트래픽", "미지원", red))
        body.addView(infoRow("앱별 제어", "앱별 허용 및 차단", "추후 제공", red))
        body.addView(text("보호 범위 안내", 17f, white, true).withTop(24))
        body.addView(text("현재 Guardian은 기기의 DNS 요청 중 IPv4/IPv6 UDP 53번 트래픽을 로컬 VPN으로 전달해 필터링합니다. 모든 광고 차단이나 HTTPS 내부 광고 제거를 보장하지 않습니다.", 13f, muted, false).withTop(8))
    }

    private fun buildRules(body: LinearLayout) {
        body.addView(text("필터 및 규칙", 25f, white, true))
        body.addView(text("나만의 차단 및 허용 도메인을 관리합니다.", 13f, muted, false).withTop(5))
        body.addView(text("차단 도메인", 16f, white, true).withTop(22))
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
        body.addView(infoRow("앱 버전", "Guardian AdBlock", "0.6.5-dev", accent))
        body.addView(infoRow("데이터 저장", "사용자 규칙은 기기 내부에 저장", "로컬", accent))
        body.addView(infoRow("개인정보", "외부 계정 및 분석 SDK 미사용", "로컬 우선", accent))
        body.addView(text("DNS 업스트림 서버", 18f, white, true).withTop(24))
        body.addView(text("차단되지 않은 DNS 질의를 전달할 서버를 선택합니다.", 12f, muted, false).withTop(5))
        val dnsOptions = listOf(
            "Cloudflare · 1.1.1.1" to "1.1.1.1",
            "Google · 8.8.8.8" to "8.8.8.8",
            "Quad9 · 9.9.9.9" to "9.9.9.9",
            "AdGuard DNS · 94.140.14.14" to "94.140.14.14"
        )
        val dnsSpinner = Spinner(this)
        dnsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dnsOptions.map { it.first })
        val dnsPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val selectedDns = dnsOptions.indexOfFirst { it.second == dnsPrefs.getString(GuardianVpnService.KEY_UPSTREAM_DNS, GuardianVpnService.DEFAULT_UPSTREAM_DNS) }.coerceAtLeast(0)
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
        body.addView(text("광고 차단 필터", 18f, white, true).withTop(24))
        body.addView(text("AdGuard DNS Filter · 광고, 모바일 광고, 추적 도메인 목록", 12f, muted, false).withTop(5))
        val filterCard = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        filterStatusText = text(filterStatusDescription(), 12f, muted, false)
        filterCard.addView(filterStatusText!!)
        val autoUpdateCheck = CheckBox(this).apply {
            text = "24시간 경과 시 앱 실행 중 자동 업데이트"
            textSize = 12f
            setTextColor(white)
            isChecked = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                .getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(FilterListManager.KEY_AUTO_UPDATE, checked).apply()
            }
        }
        filterCard.addView(autoUpdateCheck)
        val securityCheck = CheckBox(this).apply {
            text = "악성코드·피싱 보호 (HaGeZi Threat Intelligence)"
            textSize = 12f
            setTextColor(white)
            isChecked = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                .getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(FilterListManager.KEY_SECURITY_ENABLED, checked).apply()
                val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                if (ProtectionState.isActive(
                        prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                        GuardianVpnService.isTunnelActive()
                    )) {
                    startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
                if (checked && FilterListManager.cachedSecurityLines(this@MainActivity).isEmpty()) startFilterUpdate()
                else filterStatusText?.text = filterStatusDescription()
            }
        }
        filterCard.addView(securityCheck)
        filterUpdateButton = Button(this).apply {
            text = "필터 목록 업데이트"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 13)
            setOnClickListener { startFilterUpdate() }
        }
        filterCard.addView(filterUpdateButton!!, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        body.addView(filterCard.withTop(10))
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

    private fun buildAppExclusions(body: LinearLayout) {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val excluded = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty().toMutableSet()
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
            .take(150)
        val card = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        apps.forEach { (pkg, label) ->
            val check = CheckBox(this).apply {
                text = "$label\n$pkg"
                textSize = 12f
                setTextColor(white)
                isChecked = pkg in excluded
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setOnCheckedChangeListener { _, checked ->
                    val latest = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty().toMutableSet()
                    if (checked) latest.add(pkg) else latest.remove(pkg)
                    prefs.edit().putStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, latest.toSet()).apply()
                    appFilterFeedback?.text = "제외 앱 ${latest.size}개 저장됨 · 보호를 중지한 뒤 다시 시작해 적용하세요."
                }
            }
            card.addView(check)
        }
        body.addView(card.withTop(10))
        appFilterFeedback = text(
            "현재 제외 앱 ${excluded.size}개 · 최대 150개 표시",
            11f, muted, false
        ).withTop(8) as TextView
        body.addView(appFilterFeedback!!)
    }

    private fun filterStatusDescription(): String {
        val count = FilterListManager.cachedBlockCount(this)
        val updated = FilterListManager.lastUpdatedAt(this)
        val time = if (updated > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updated)) else "아직 업데이트되지 않음"
        val securityCount = FilterListManager.cachedSecurityBlockCount(this)
        val securityOn = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
            .getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        return "광고·추적 ${count}개 · 보안 ${if (securityOn) securityCount else 0}개 · 마지막 업데이트: $time"
    }

    private fun startFilterUpdate() {
        filterUpdateButton?.isEnabled = false
        filterUpdateButton?.text = "필터 다운로드 및 검증 중…"
        filterStatusText?.text = "공식 AdGuard DNS 필터를 내려받아 규칙을 검증합니다."
        thread(name = "guardian-filter-update") {
            try {
                val result = FilterListManager.update(this)
                val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                val securityResult = if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
                    try { FilterListManager.updateSecurity(this) } catch (e: Exception) { null }
                } else null
                val active = ProtectionState.isActive(
                    prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                    GuardianVpnService.isTunnelActive()
                )
                if (active) {
                    startService(Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
                runOnUiThread {
                    val securityMessage = if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
                        if (securityResult != null) " · 보안 ${securityResult.blocked}개" else " · 보안 목록 업데이트 실패(이전 목록 유지)"
                    } else ""
                    filterStatusText?.text = "광고·추적 ${result.blocked}개 검증 완료${securityMessage} · ${result.bytes / 1024}KB"
                    filterUpdateButton?.text = "필터 목록 업데이트"
                    filterUpdateButton?.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    filterStatusText?.text = "업데이트 실패: ${e.message ?: e.javaClass.simpleName} · 기존 필터 유지"
                    filterUpdateButton?.text = "다시 시도"
                    filterUpdateButton?.isEnabled = true
                }
            }
        }
    }

    private fun requestProtection() {
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
