package com.guardian.adblock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var blockRulesInput: EditText
    private lateinit var allowRulesInput: EditText
    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "GUARDIAN AdBlock"
            textSize = 26f
        })

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(18), 0, dp(18))
        }
        content.addView(status)

        content.addView(Button(this).apply {
            text = "보호 시작"
            setOnClickListener {
                val consent = VpnService.prepare(this@MainActivity)
                if (consent != null) startActivityForResult(consent, REQUEST_VPN)
                else startProtection()
            }
        }, matchWidth())
        content.addView(Button(this).apply {
            text = "보호 중지"
            setOnClickListener {
                startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_STOP))
            }
        }, matchWidth())

        content.addView(sectionTitle("사용자 차단 목록"))
        content.addView(helpText("차단할 도메인을 한 줄에 하나씩 입력하세요. 하위 도메인도 함께 차단됩니다."))
        blockRulesInput = domainEditor("예: ads.example.com\ntracker.example.net")
        content.addView(blockRulesInput, editorHeight())

        content.addView(sectionTitle("허용 목록"))
        content.addView(helpText("차단 목록에 포함된 사이트라도 여기에 등록하면 허용됩니다."))
        allowRulesInput = domainEditor("예: trusted.example.com")
        content.addView(allowRulesInput, editorHeight())

        content.addView(Button(this).apply {
            text = "사용자 규칙 저장 및 적용"
            setOnClickListener { saveUserRules() }
        }, matchWidth())

        content.addView(helpText("규칙은 기기 안에 저장됩니다. 차단 규칙과 허용 규칙은 Guardian이 제공하는 공통 목록에 추가로 적용됩니다."))

        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        loadUserRulesIntoEditors()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(GuardianVpnService.ACTION_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
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

    private fun startProtection() {
        val service = Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_START)
        startForegroundService(service)
        status.text = "보호 시작 요청을 전달했습니다. VPN 연결 상태를 확인하고 있습니다."
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
        prefs.edit()
            .putString(GuardianVpnService.KEY_USER_BLOCK, block.joinToString("\n"))
            .putString(GuardianVpnService.KEY_USER_ALLOW, allow.joinToString("\n"))
            .apply()

        if (prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)) {
            startService(Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
            status.text = "사용자 규칙을 저장했습니다. 현재 보호 서비스에 적용을 요청했습니다."
        } else {
            status.text = "사용자 규칙을 저장했습니다. 다음 보호 시작 시 적용됩니다."
        }
    }

    private fun parseEditor(editor: EditText, label: String): List<String>? {
        val domains = linkedSetOf<String>()
        val rawLines = editor.text.toString().lineSequence().toList()
        for ((index, raw) in rawLines.withIndex()) {
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("#")) continue
            val domain = DomainFilter.normalizeRuleDomain(value)
            if (domain == null) {
                status.text = "$label ${index + 1}번째 줄이 올바른 도메인이 아닙니다: $value"
                return null
            }
            domains.add(domain)
            if (domains.size > MAX_USER_RULES) {
                status.text = "$label은 최대 $MAX_USER_RULES개까지 등록할 수 있습니다."
                return null
            }
        }
        return domains.toList()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val active = prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        val message = prefs.getString(GuardianVpnService.KEY_STATUS, null)
        status.text = message ?: if (active) "DNS 보호가 실행 중입니다." else "기기 내 DNS 보호가 꺼져 있습니다. 보호 시작을 눌러 VPN 권한을 승인하세요."
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun helpText(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun domainEditor(hintText: String) = EditText(this).apply {
        hint = hintText
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(false)
        minLines = 3
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun editorHeight() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112))
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_VPN = 100
        private const val MAX_USER_RULES = 1000
    }
}