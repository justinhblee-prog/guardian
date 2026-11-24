package com.guardian.adblock

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.BitmapFactory
import android.util.Base64
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Switch
import androidx.core.content.ContextCompat
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDot: TextView
    private var startButton: Button? = null
    private var protectionStatusText: TextView? = null
    private var protectionStatusDetail: TextView? = null
    private var protectionFeedback: TextView? = null
    private var securityFilterSwitch: Switch? = null
    private var androidAdsFilterSwitch: Switch? = null
    private var securityFilterCheckBox: CheckBox? = null
    private var androidAdsFilterCheckBox: CheckBox? = null
    private lateinit var blockRulesInput: EditText
    private lateinit var allowRulesInput: EditText
    private var rulesFeedback: TextView? = null
    private var statsText: TextView? = null
    private var recentText: TextView? = null
    private var diagnosticText: TextView? = null
    private var selfTestText: TextView? = null
    private var upstreamTestText: TextView? = null
    private var upstreamTestButton: Button? = null
    private var filterStatusText: TextView? = null
    private var filterUpdateButton: Button? = null
    private var homeFilterUpdateButton: Button? = null
    private var homeFilterStatusText: TextView? = null
    private var appFilterFeedback: TextView? = null
    private var appExclusionsAppliedStatus: TextView? = null
    private var appExclusionsSearchInput: EditText? = null
    private var compatibilityStatusText: TextView? = null
    private var compatibilityAppsButton: Button? = null
    private var compatibilityPauseSwitch: Switch? = null
    private var usageAccessSettingsOpened = false
    private var syncingCompatibilitySwitch = false
    private var focusAppExclusionsOnBuild = false
    private var blockRateValue: TextView? = null
    private var blockedCountValue: TextView? = null
    private var queryCountValue: TextView? = null
    private var adBlockedValue: TextView? = null
    private var trackerBlockedValue: TextView? = null
    private var savedDataValue: TextView? = null
    private var activityChart: ActivityStatsChart? = null
    private var securityProfileAutoSwitch: CheckBox? = null
    private var securityProfileStatusText: TextView? = null
    private var securityProfileUpdateStatusText: TextView? = null
    private var securityProfileSpinner: Spinner? = null
    private var securityProfileIds: List<SecurityProfilePolicy.Id>? = null
    private var syncingSecurityProfileSwitch = false
    private var receiverRegistered = false
    private var currentTab = "home"
    private var selectedSettingsCategory = "settings-general"
    private var settingsSidebarCollapsed = false
    private var phoneSettingsShowingDetail = false
    // Reused across tab rebuilds to keep navigation taps responsive.
    private var companyLogoBitmap: android.graphics.Bitmap? = null
    private var contentScrollView: ScrollView? = null
    private var focusLicenseActivationOnBuild = false
    private var licenseKeyInput: EditText? = null
    private val filterUpdateRunning = AtomicBoolean(false)
    private val startupFilterCheckStarted = AtomicBoolean(false)
    private val protectionCheckRunning = AtomicBoolean(false)
    @Volatile private var pendingFilterStartNotice: String? = null
    private var syncingFilterOptions = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatus()
            refreshAppExclusionsAppliedStatus()
            refreshSecurityProfilePreview()
            when (intent?.action) {
                FilterUpdateWorker.ACTION_FILTERS_CHANGED -> {
                    syncFilterOptionControls()
                    filterStatusText?.text = filterStatusDescription()
                }
                GuardianVpnService.ACTION_UPSTREAM_TEST_RESULT -> {
                    upstreamTestText?.text = upstreamTestDescription()
                    upstreamTestButton?.isEnabled = true
                    upstreamTestButton?.text = "업스트림 연결 테스트"
                }
            }
        }
    }

    private val bg = Color.rgb(9, 17, 29)
    private val panel = Color.rgb(17, 31, 47)
    private val panel2 = Color.rgb(25, 48, 65)
    private val accent = Color.rgb(55, 211, 190)
    private val muted = Color.rgb(151, 174, 192)
    private val white = Color.rgb(241, 246, 255)
    private val red = Color.rgb(255, 112, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.decorView.systemUiVisibility = 0
        val persistedTab = getSharedPreferences(UI_STATE_PREFS, MODE_PRIVATE)
            .getString(PREF_LAST_SELECTED_TAB, null)
        val restoredTab = ActivityScreenState.restoreTab(
            savedInstanceState?.getString(STATE_CURRENT_TAB),
            persistedTab
        )
        val restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y, 0) ?: 0
        val restoredBlockDraft = savedInstanceState?.getString(STATE_BLOCK_RULES_DRAFT)
        val restoredAllowDraft = savedInstanceState?.getString(STATE_ALLOW_RULES_DRAFT)
        val hasRuleDraft = restoredBlockDraft != null && restoredAllowDraft != null
        if (savedInstanceState != null) {
            android.util.Log.i(
                "GuardianUI",
                "Activity restored after configuration change; tab=$restoredTab scrollY=$restoredScrollY hasRuleDraft=$hasRuleDraft"
            )
        }
        val restoreLicenseFocus = savedInstanceState?.getBoolean(STATE_FOCUS_LICENSE_ACTIVATION, false) == true
        val savedSettingsCategory = savedInstanceState?.getString(STATE_SETTINGS_CATEGORY)
        selectedSettingsCategory = when (savedSettingsCategory) {
            "settings-general" -> "settings-general"
            "settings-filtering" -> "settings-filtering"
            "settings-premium" -> "settings-premium"
            "settings-support" -> "settings-support"
            "settings-about" -> "settings-about"
            "settings-exit" -> "settings-exit"
            else -> restoredTab.takeIf { it.startsWith("settings-") } ?: "settings-general"
        }
        val screenTab = if (
            ResponsiveLayoutPolicy.usesSettingsSplitPane(resources.configuration.screenWidthDp) &&
            restoredTab.startsWith("settings-") &&
            !restoreLicenseFocus
        ) {
            selectedSettingsCategory = restoredTab
            "settings"
        } else {
            restoredTab
        }
        showTab(screenTab, focusLicenseActivation = restoreLicenseFocus)
        if (restoredTab == "settings-filtering") {
            if (hasRuleDraft) {
                blockRulesInput.setText(restoredBlockDraft)
                allowRulesInput.setText(restoredAllowDraft)
            }
        }
        if (restoredScrollY > 0 && !restoreLicenseFocus) {
            contentScrollView?.let { scroll ->
                scroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        if (restoredTab == "settings-filtering" && hasRuleDraft) {
                            val editorBounds = android.graphics.Rect()
                            blockRulesInput.getDrawingRect(editorBounds)
                            scroll.offsetDescendantRectToMyCoords(blockRulesInput, editorBounds)
                            scroll.scrollTo(0, (editorBounds.top - dp(24)).coerceAtLeast(0))
                        } else {
                            scroll.scrollTo(0, restoredScrollY)
                        }
                        val maximumScroll = (scroll.getChildAt(0)?.height ?: 0) - scroll.height
                        android.util.Log.i(
                            "GuardianUI",
                            "Restored Activity scroll position; requested=$restoredScrollY actual=${scroll.scrollY} max=${maximumScroll.coerceAtLeast(0)}"
                        )
                    }
                })
            }
        }
        requestNotificationPermissionIfNeeded()
        val filterPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        // One-time migration: turn on the Android app-ad SDK list for existing
        // installations too, not just fresh installs whose preference is absent.
        if (!filterPrefs.getBoolean("android_ads_default_enabled_v1", false)) {
            filterPrefs.edit()
                .putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
                .putBoolean("android_ads_default_enabled_v1", true)
                .apply()
        }
        // Temporary rollout: enable the malware/phishing list for existing and
        // new installations. Its cache is fetched by the startup update check.
        if (!filterPrefs.getBoolean("security_filter_default_enabled_v1", false)) {
            filterPrefs.edit()
                .putBoolean(FilterListManager.KEY_SECURITY_ENABLED, true)
                .putBoolean("security_filter_default_enabled_v1", true)
                .apply()
        }
        // Enable recurring filter refresh by default. Preserve an explicit user
        // choice made in Protection settings; migrate installations that only
        // received the old forced-off default to the safer current default.
        if (!filterPrefs.getBoolean("filter_auto_update_default_on_v2", false)) {
            if (!filterPrefs.getBoolean("filter_auto_update_user_choice_v1", false)) {
                filterPrefs.edit().putBoolean(FilterListManager.KEY_AUTO_UPDATE, true).apply()
            }
            filterPrefs.edit().putBoolean("filter_auto_update_default_on_v2", true).apply()
        }
        // Update discovery is user-initiated from Settings. Do not run a
        // background development/stable update check or show an install prompt
        // automatically on every app launch.
        checkStartupFilterReadiness()
        // WorkManager initialization/cancellation is not part of first-run filter
        // readiness. Run scheduler reconciliation away from the UI thread so a
        // slow WorkManager database cannot delay Settings/navigation or the
        // initial filter download request.
        thread(name = "guardian-background-scheduler-sync") {
            runCatching { LicenseRefreshScheduler.sync(applicationContext) }
                .onFailure { android.util.Log.e("Guardian", "License refresh scheduling failed: ${SafeFailureSummary.codeFor(it)}") }
            runCatching { FilterUpdateScheduler.sync(applicationContext) }
                .onFailure { android.util.Log.e("Guardian", "Filter refresh scheduling failed: ${SafeFailureSummary.codeFor(it)}") }
        }
    }

    /**
     * A missing first-run filter cache is setup, not an automatic update check.
     * Run the potentially expensive recovery/hash work on a background thread.
     */
    private fun checkStartupFilterReadiness() {
        if (!startupFilterCheckStarted.compareAndSet(false, true)) return
        val filterPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        thread(name = "guardian-startup-filter-check") {
            android.util.Log.i("Guardian", "Startup filter readiness check started")
            try {
                val autoUpdate = filterPrefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
                val lastUpdate = FilterListManager.lastUpdatedAt(this)
                val updateInterval = filterPrefs.getLong(
                    FilterListManager.KEY_UPDATE_INTERVAL_MS,
                    24L * 60L * 60L * 1000L
                )
                val securityEnabled = filterPrefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
                val profileChanged = FilterListManager.hasCachedFile(this) &&
                    FilterListManager.selectedProfile(this).key != FilterListManager.cachedProfile(this).key
                val securityStale = securityEnabled && (
                    !FilterListManager.hasCachedSecurityList(this) ||
                        System.currentTimeMillis() - FilterListManager.lastSecurityUpdatedAt(this) >= updateInterval
                    )
                val androidAdsEnabled = filterPrefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
                val androidAdsStale = androidAdsEnabled && (
                    !FilterListManager.hasCachedAndroidAdsList(this) ||
                        System.currentTimeMillis() - FilterListManager.lastAndroidAdsUpdatedAt(this) >= updateInterval
                    )
                val initialMainMissing = !FilterListManager.hasCachedList(this)
                val initialSecurityMissing = securityEnabled && !FilterListManager.hasCachedSecurityList(this)
                val initialAndroidAdsMissing = androidAdsEnabled && !FilterListManager.hasCachedAndroidAdsList(this)
                val scheduledRefreshDue = autoUpdate && (
                    System.currentTimeMillis() - lastUpdate >= updateInterval ||
                        securityStale || androidAdsStale || profileChanged
                    )
                val needsUpdate = FilterStartupPolicy.shouldRefresh(
                    mainCacheMissing = initialMainMissing,
                    securityCacheMissing = initialSecurityMissing,
                    securityEnabled = securityEnabled,
                    androidAdsCacheMissing = initialAndroidAdsMissing,
                    androidAdsEnabled = androidAdsEnabled,
                    scheduledRefreshDue = scheduledRefreshDue
                )
                android.util.Log.i(
                    "Guardian",
                    "Startup filter readiness: mainMissing=$initialMainMissing securityMissing=$initialSecurityMissing " +
                        "androidAdsMissing=$initialAndroidAdsMissing autoUpdate=$autoUpdate scheduledDue=$scheduledRefreshDue"
                )
                if (needsUpdate) runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        startFilterUpdate()
                    } else {
                        startupFilterCheckStarted.set(false)
                    }
                }
            } catch (error: Exception) {
                startupFilterCheckStarted.set(false)
                android.util.Log.e("Guardian", "Startup filter readiness check failed: ${SafeFailureSummary.codeFor(error)}")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val savedScrollY = contentScrollView?.scrollY ?: 0
        android.util.Log.i("GuardianUI", "Saving Activity state; tab=$currentTab scrollY=$savedScrollY")
        outState.putString(STATE_CURRENT_TAB, currentTab)
        outState.putString(STATE_SETTINGS_CATEGORY, selectedSettingsCategory)
        outState.putBoolean(STATE_FOCUS_LICENSE_ACTIVATION, focusLicenseActivationOnBuild)
        outState.putInt(STATE_SCROLL_Y, savedScrollY)
        if (currentTab == "settings-filtering" && ::blockRulesInput.isInitialized && ::allowRulesInput.isInitialized) {
            outState.putString(STATE_BLOCK_RULES_DRAFT, blockRulesInput.text.toString())
            outState.putString(STATE_ALLOW_RULES_DRAFT, allowRulesInput.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentTab.startsWith("settings-")) {
            showTab("settings")
            return
        }
        if (currentTab != "home") {
            showTab("home")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Guardian 종료")
            .setMessage("앱을 종료하시겠습니까?\n\n종료해도 이미 실행 중인 DNS 보호 VPN은 별도로 중지하지 않는 한 계속 실행됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("종료") { _, _ -> finish() }
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(red)
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
            }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(GuardianVpnService.ACTION_STATUS_CHANGED).apply {
            addAction(FilterUpdateWorker.ACTION_FILTERS_CHANGED)
            addAction(GuardianVpnService.ACTION_UPSTREAM_TEST_RESULT)
        }
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        refreshStatus()
        refreshSecurityProfilePreview()
        // A background worker may disable an optional source while this Activity
        // is stopped. Reconcile any already-created controls with persisted state.
        syncFilterOptionControls()
        // A filter update may finish while this Activity is stopped, when its
        // package-scoped broadcast receiver is not registered. Re-read persisted
        // cache metadata whenever the screen becomes visible again.
        filterStatusText?.text = filterStatusDescription()
        upstreamTestText?.text = upstreamTestDescription()
    }

    override fun onResume() {
        super.onResume()
        // Retry the first-run readiness check if Activity startup was interrupted
        // by a permission/settings transition. The atomic gate prevents duplicate
        // downloads when the onCreate check is already running.
        checkStartupFilterReadiness()
        if (usageAccessSettingsOpened) {
            usageAccessSettingsOpened = false
            if (currentTab.startsWith("settings")) {
                showTab(currentTab)
                refreshStatus()
            }
        }
        restoreProtectionAfterUpdateIfNeeded()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    /**
     * MY_PACKAGE_REPLACED is the primary restore signal. This UI-resume fallback
     * covers devices that defer or reject the receiver's foreground-service start.
     * It never overrides an explicit user stop or a deliberate compatibility pause.
     */
    private fun restoreProtectionAfterUpdateIfNeeded() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(GuardianVpnService.KEY_RESTORE_REQUESTED, false) ||
            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false) ||
            prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        ) return

        val now = System.currentTimeMillis()
        val pending = prefs.getBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, false)
        val pendingAt = prefs.getLong("protection_recovery_requested_at", 0L)
        if (pending && now - pendingAt < 30_000L) return

        val lastAttempt = prefs.getLong("protection_ui_restore_retry_at", 0L)
        if (now - lastAttempt < 15_000L) return
        prefs.edit()
            .putLong("protection_ui_restore_retry_at", now)
            .putBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, true)
            .putLong("protection_recovery_requested_at", now)
            .putString(GuardianVpnService.KEY_STATUS, "업데이트 후 VPN 보호를 자동으로 다시 연결하고 있습니다.")
            .commit()
        try {
            val service = Intent(this, GuardianVpnService::class.java)
                .setAction(GuardianVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }
        } catch (error: Exception) {
            android.util.Log.e("GuardianProtection", "UI fallback could not restart protection after update: ${SafeFailureSummary.codeFor(error)}")
            prefs.edit()
                .putBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, false)
                .putString(GuardianVpnService.KEY_STATUS, "업데이트 후 VPN 자동 복구 요청에 실패했습니다. 다시 시도합니다.")
                .commit()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startProtection()
            else {
                pendingFilterStartNotice = null
                val message = "Android VPN 연결 요청이 승인되지 않아 DNS 보호를 시작하지 않았습니다."
                if (currentTab == "home" && ::statusTitle.isInitialized && ::status.isInitialized) {
                    statusTitle.text = "VPN 권한이 필요합니다"
                    status.text = message
                } else if (currentTab == "protection") {
                    protectionStatusText?.text = "VPN 권한이 필요합니다"
                    protectionFeedback?.text = message
                } else {
                    filterStatusText?.text = message
                }
            }
        }
    }

    /**
     * Give Settings an immediate visual response before constructing its many controls.
     * The previous synchronous render built the entire long settings form on the tap
     * callback, so slower devices could appear to ignore the button.
     */
    private fun navigateToTab(tab: String) {
        if (tab != "settings") {
            showTab(tab)
            return
        }
        // A tap on the Settings bottom-navigation destination is an explicit
        // request to return to the category index. Do this before assigning
        // currentTab: showTab() uses the previous destination to decide whether
        // a phone should start on the category index or retain a detail page.
        // Without this reset, returning to Settings from an already-open detail
        // page leaves the user stranded in that detail screen and automation
        // cannot reach sibling categories such as App Exclusions.
        if (!ResponsiveLayoutPolicy.usesSettingsSplitPane(
                resources.configuration.screenWidthDp.coerceAtLeast(1)
            ) && (currentTab != "settings" || phoneSettingsShowingDetail)
        ) {
            phoneSettingsShowingDetail = false
        }
        currentTab = tab
        val loadingRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            addView(android.widget.ProgressBar(this@MainActivity).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(accent)
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(TextView(this@MainActivity).apply {
                text = "설정을 여는 중…"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(white)
                setPadding(dp(24), dp(12), dp(24), dp(16))
            })
        }
        setContentView(loadingRoot)
        // Let Android draw the immediate loading state before constructing the
        // large settings hierarchy on the UI thread. A plain post() can run in
        // the same traversal and make the tap appear to have been ignored.
        loadingRoot.postDelayed({
            if (!isFinishing && currentTab == "settings" && loadingRoot.isAttachedToWindow) {
                showTab("settings")
            }
        }, 96L)
    }

    private fun showTab(
        tab: String,
        focusLicenseActivation: Boolean = false,
        focusAppExclusions: Boolean = false
    ) {
        val previousTab = currentTab
        val entryWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(1)
        if (tab == "settings" && previousTab != "settings" &&
            !ResponsiveLayoutPolicy.usesSettingsSplitPane(entryWidthDp)
        ) {
            // Phones use a full-width category list, then a full-width detail page.
            phoneSettingsShowingDetail = false
        }
        currentTab = tab
        getSharedPreferences(UI_STATE_PREFS, MODE_PRIVATE).edit()
            .putString(PREF_LAST_SELECTED_TAB, tab)
            .apply()
        focusLicenseActivationOnBuild = focusLicenseActivation && tab == "settings-premium"
        focusAppExclusionsOnBuild = focusAppExclusions && tab == "app-exclusions"
        licenseKeyInput = null
        startButton = null
        protectionStatusText = null
        protectionStatusDetail = null
        protectionFeedback = null
        securityFilterSwitch = null
        androidAdsFilterSwitch = null
        securityFilterCheckBox = null
        androidAdsFilterCheckBox = null
        statsText = null
        recentText = null
        diagnosticText = null
        selfTestText = null
        upstreamTestText = null
        upstreamTestButton = null
        rulesFeedback = null
        filterStatusText = null
        filterUpdateButton = null
        homeFilterUpdateButton = null
        homeFilterStatusText = null
        appFilterFeedback = null
        appExclusionsSearchInput = null
        appExclusionsAppliedStatus = null
        compatibilityStatusText = null
        compatibilityAppsButton = null
        compatibilityPauseSwitch = null
        blockRateValue = null
        blockedCountValue = null
        queryCountValue = null
        adBlockedValue = null
        trackerBlockedValue = null
        savedDataValue = null
        activityChart = null
        securityProfileAutoSwitch = null
        securityProfileStatusText = null
        securityProfileUpdateStatusText = null
        securityProfileSpinner = null
        securityProfileIds = null
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
        contentScrollView = scroll
        val screenWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(1)
        val horizontalPadding = dp(ResponsiveLayoutPolicy.horizontalPaddingDp(screenWidthDp))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, dp(20), horizontalPadding, dp(24))
        }
        body.addView(header())
        when (tab) {
            "home" -> buildHome(body)
            "protection" -> buildProtection(body)
            "activity" -> buildActivity(body)
            "premium" -> buildSettingsPremium(body)
            "updates" -> buildUpdates(body)
            "settings-general" -> buildSettingsGeneral(body)
            "settings-filtering" -> buildSettingsFiltering(body)
            "settings-premium" -> buildSettingsPremium(body)
            "settings-support" -> {
                body.addView(text("고객 지원", 25f, white, true))
                body.addView(buildSupportCenterCard().withTop(12))
            }
            "settings-about" -> buildSettingsAbout(body)
            "settings-exit" -> {
                buildSettingsLanding(body)
                AlertDialog.Builder(this).setTitle("Guardian 종료")
                    .setMessage("앱을 종료하시겠습니까? 실행 중인 DNS 보호는 종료 후에도 유지됩니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("종료") { _, _ -> finish() }
                    .show()
            }
            "app-exclusions" -> {
                body.addView(text("앱 제외", 25f, white, true))
                body.addView(text("선택한 앱의 네트워크를 Guardian DNS 필터링에서 제외합니다.", 13f, muted, false).withTop(5))
                buildAppExclusions(body)
            }
            else -> buildSettingsLanding(body)
        }
        val navSelection = when {
            tab.startsWith("settings-") || tab in setOf("premium", "updates", "rules") -> "settings"
            else -> tab
        }
        val usesNavigationRail = ResponsiveLayoutPolicy.usesNavigationRail(screenWidthDp)
        val contentWidth = ResponsiveLayoutPolicy.contentWidthPx(
            ResponsiveLayoutPolicy.contentAreaWidthDp(screenWidthDp),
            resources.displayMetrics.density
        )
        scroll.addView(
            body,
            FrameLayout.LayoutParams(
                contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )
        if (focusLicenseActivationOnBuild && tab == "settings-premium") {
            scroll.post {
                licenseKeyInput?.let { input ->
                    val bounds = android.graphics.Rect()
                    input.getDrawingRect(bounds)
                    scroll.offsetDescendantRectToMyCoords(input, bounds)
                    scroll.smoothScrollTo(0, (bounds.top - dp(28)).coerceAtLeast(0))
                    input.requestFocus()
                    input.post {
                        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        keyboard.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                focusLicenseActivationOnBuild = false
            }
        }

        if (focusAppExclusionsOnBuild && tab == "app-exclusions") {
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            scroll.post {
                appExclusionsSearchInput?.let { input ->
                    val bounds = android.graphics.Rect()
                    input.getDrawingRect(bounds)
                    scroll.offsetDescendantRectToMyCoords(input, bounds)
                    scroll.smoothScrollTo(0, (bounds.top - dp(18)).coerceAtLeast(0))
                    input.requestFocus()
                    input.post {
                        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        keyboard.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                focusAppExclusionsOnBuild = false
            }
        }
        if (usesNavigationRail) {
            val workspace = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(bg)
            }
            workspace.addView(bottomNav(navSelection, asRail = true), LinearLayout.LayoutParams(dp(88), -1))
            workspace.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(bottomNav(navSelection))
        }
        setContentView(root)
        root.requestApplyInsets()
        refreshStatus()
    }


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_requested", false)) {
                prefs.edit().putBoolean("notification_permission_requested", true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7101)
            }
        }
    }

    private fun header(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val companyMark = ImageView(this).apply {
            contentDescription = "주식회사 유진컨설팅 로고"
            scaleType = ImageView.ScaleType.FIT_CENTER
            // The header is rebuilt on every tab change. Decode and recolor the
            // company wordmark only once per Activity instead of doing a full
            // bitmap pixel pass on every Settings/navigation tap.
            val cachedLogo = companyLogoBitmap ?: run {
                val encoded = assets.open("eugene_consulting_logo.b64").bufferedReader().use { it.readText() }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                // Recolor only the Korean wordmark. Keep the symbol and English subtitle untouched.
                val adjusted = source.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                for (y in 0 until adjusted.height.coerceAtMost(90)) {
                    for (x in 125 until adjusted.width) {
                        val pixel = adjusted.getPixel(x, y)
                        val alpha = android.graphics.Color.alpha(pixel)
                        val red = android.graphics.Color.red(pixel)
                        val green = android.graphics.Color.green(pixel)
                        val blue = android.graphics.Color.blue(pixel)
                        if (alpha > 0 && red < 90 && green < 90 && blue < 180) {
                            adjusted.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
                        }
                    }
                }
                adjusted
            }.also { companyLogoBitmap = it }
            setImageBitmap(cachedLogo)
        }
        val guardianMark = ImageView(this).apply {
            contentDescription = "GUARDIAN 보호 로고"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.guardian_launcher_art)
        }
        wrap.addView(guardianMark, LinearLayout.LayoutParams(dp(46), dp(48)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), 0, 0, 0)
        }
        labels.addView(text("GUARDIAN", 17f, white, true))
        labels.addView(text("AD BLOCK", 8f, muted, true))
        wrap.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val headerSpacer = View(this)
        wrap.addView(headerSpacer, LinearLayout.LayoutParams(0, 1, 1f))
        wrap.addView(companyMark, LinearLayout.LayoutParams(dp(124), dp(42)))
        return wrap
    }

    private fun buildHome(body: LinearLayout) {
        val homeBg = Color.rgb(14, 29, 43)
        val homePanel = Color.rgb(19, 38, 55)
        val homeGreen = Color.rgb(55, 211, 190)
        val homeOrange = Color.rgb(239, 164, 91)
        val wideDashboard = ResponsiveLayoutPolicy.usesTwoColumnDashboard(resources.configuration.screenWidthDp)

        body.addView(text("보호 대시보드", 24f, white, true))
        body.addView(text("기기의 DNS 연결과 차단 활동을 한눈에 확인합니다.", 12f, muted, false).withTop(4))

        val hero = card(homeBg, 22).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(13, 39, 55), Color.rgb(19, 54, 68))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(48, 112, 126))
            }
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(text("GUARDIAN", 17f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        brandRow.addView(text("SYSTEM-WIDE DNS PROTECTION", 9f, homeGreen, true))
        hero.addView(brandRow)

        statusDot = text("●  보호 꺼짐", 11f, homeOrange, true).apply {
            gravity = Gravity.START
            setPadding(0, dp(16), 0, dp(5))
        }
        hero.addView(statusDot)
        statusTitle = text("보호 상태 확인 중", 23f, white, true).apply { gravity = Gravity.START }
        hero.addView(statusTitle)
        status = text("", 13f, muted, false).apply {
            gravity = Gravity.START
            setPadding(0, dp(8), 0, dp(18))
        }
        hero.addView(status)

        val power = Button(this).apply {
            text = "보호 시작"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = shape(homeOrange, 28)
            minHeight = dp(58)
            contentDescription = "DNS 보호 시작 또는 중지"
            setOnClickListener { onHomeProtectionButtonTapped() }
        }
        startButton = power
        hero.addView(power, LinearLayout.LayoutParams(-1, dp(58)))
        body.addView(hero.withTop(18))

        val metrics = LinearLayout(this).apply {
            orientation = if (wideDashboard) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        blockRateValue = text("0%", 22f, homeGreen, true)
        blockedCountValue = text("0", 22f, homeOrange, true)
        queryCountValue = text("0", 22f, white, true)
        val rateCard = metricCard("차단률", blockRateValue!!, homeGreen)
        val blockedCard = metricCard("차단된 질의", blockedCountValue!!, homeOrange)
        val queryCard = metricCard("전체 DNS", queryCountValue!!, white)
        if (wideDashboard) {
            metrics.addView(rateCard, LinearLayout.LayoutParams(0, dp(104), 1f))
            metrics.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
            metrics.addView(blockedCard, LinearLayout.LayoutParams(0, dp(104), 1f))
            metrics.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
            metrics.addView(queryCard, LinearLayout.LayoutParams(0, dp(104), 1f))
        } else {
            val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            firstRow.addView(rateCard, LinearLayout.LayoutParams(0, dp(104), 1f))
            firstRow.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
            firstRow.addView(blockedCard, LinearLayout.LayoutParams(0, dp(104), 1f))
            metrics.addView(firstRow)
            metrics.addView(queryCard, LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(8) })
        }
        body.addView(metrics.withTop(16))

        val versionName = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (_: Exception) { "확인 불가" }
        body.addView(card(homePanel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text("설치 버전", 15f, white, true))
            addView(text("설치된 앱 버전  $versionName", 12f, homeGreen, true).withTop(9))
        }.withTop(14))

        val columns = LinearLayout(this).apply {
            orientation = if (wideDashboard) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(card(homePanel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            addView(text("실시간 DNS 통계", 14f, white, true))
            statsText = text("질의 0 · 차단 0 · 전달 0 · 실패 0", 12f, homeGreen, true).withTop(9) as TextView
            addView(statsText)
            recentText = text("아직 기록된 DNS 활동이 없습니다.", 11f, muted, false).withTop(8) as TextView
            addView(recentText)
        })
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(card(homePanel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            addView(text("보호 범위", 14f, white, true))
            addView(text("IPv4 / IPv6 · UDP/53 + TCP/53 DNS", 12f, muted, false).withTop(8))
            addView(text("앱 자체 DoH/DoT 요청은 가로채 차단하지 않습니다. 다만 아래 설정에서 Guardian의 DNS 업스트림 전송을 DoH/DoT로 암호화할 수 있습니다.", 11f, muted, false).withTop(6))
        })
        val privateDns = privateDnsCompatibilityStatus()
        right.addView(card(homePanel, 14).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(text("ANDROID 비공개 DNS", 10f, muted, true))
            addView(text(privateDns.first, 11f, privateDns.second, false).withTop(6))
        }.withTop(10))
        left.addView(card(homePanel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            addView(text("DNS 필터 엔진", 14f, white, true))
            addView(text("광고·추적 도메인 필터와 사용자 차단·허용 규칙을 관리합니다.", 11f, muted, false).withTop(7))
            homeFilterStatusText = text(filterStatusDescription(), 10f, muted, false).withTop(8) as TextView
            addView(homeFilterStatusText!!)
            homeFilterUpdateButton = Button(this@MainActivity).apply {
                text = if (filterUpdateRunning.get()) "필터 다운로드 및 검증 중…" else "필터 목록 업데이트"
                isAllCaps = false
                setTextColor(bg)
                background = shape(accent, 12)
                isEnabled = !filterUpdateRunning.get()
                contentDescription = "필터 목록 업데이트"
                setOnClickListener { startFilterUpdate() }
            }
            addView(homeFilterUpdateButton!!, LinearLayout.LayoutParams(-1, dp(42)).apply {
                topMargin = dp(10)
            })
        }.withTop(10))
        if (wideDashboard) {
            columns.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            columns.addView(space(12), LinearLayout.LayoutParams(dp(12), 1))
            columns.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            columns.addView(left, LinearLayout.LayoutParams(-1, -2))
            columns.addView(right, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        body.addView(columns.withTop(14))
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

        val adCount = prefs.getLong(GuardianVpnService.KEY_DNS_AD_BLOCKED, 0L)
        val trackerCount = prefs.getLong(GuardianVpnService.KEY_DNS_TRACKER_BLOCKED, 0L)
        val otherCount = prefs.getLong(GuardianVpnService.KEY_DNS_OTHER_BLOCKED, 0L)
        val chartCard = card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("차단 활동 그래프", 14f, white, true))
            activityChart = ActivityStatsChart()
            addView(activityChart, LinearLayout.LayoutParams(-1, dp(178)).apply { topMargin = dp(12) })
            val categories = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            adBlockedValue = text(String.format(Locale.getDefault(), "%,d", adCount), 17f, Color.rgb(255, 166, 94), true)
            trackerBlockedValue = text(String.format(Locale.getDefault(), "%,d", trackerCount), 17f, Color.rgb(116, 167, 255), true)
            categories.addView(metricCard("광고 차단 건수", adBlockedValue!!, Color.rgb(255, 166, 94)), LinearLayout.LayoutParams(0, dp(76), 1f))
            categories.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
            categories.addView(metricCard("추적 차단 건수", trackerBlockedValue!!, Color.rgb(116, 167, 255)), LinearLayout.LayoutParams(0, dp(76), 1f))
            addView(categories.withTop(10))
            addView(text("예상 절약 데이터", 11f, muted, false).withTop(14))
            savedDataValue = text(estimatedSavedDataLabel(prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L)), 18f, accent, true).withTop(4) as TextView
            addView(savedDataValue!!)
            addView(text("광고/추적 구분은 도메인 이름 기반 추정 분류입니다. 절약 데이터는 차단 1건당 24KB를 가정한 추정치이며 실제 전송량 측정값이 아닙니다.", 10f, muted, false).withTop(8))
        }
        body.addView(chartCard.withTop(12))
        body.addView(Button(this).apply {
            text = "DNS 규칙 엔진 테스트"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 12)
            setOnClickListener { runDnsSelfTest() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        selfTestText = text("VPN 내부 규칙 엔진을 검사합니다. 실기기 네트워크 경로 검증은 별도입니다.", 11f, muted, false).withTop(8) as TextView
        body.addView(selfTestText!!)
        upstreamTestButton = Button(this).apply {
            text = "업스트림 연결 테스트"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 12)
            setOnClickListener {
                isEnabled = false
                text = "연결 확인 중..."
                upstreamTestText?.text = "선택한 DNS 서버와 전송 방식으로 실제 질의를 보내 연결을 확인합니다."
                try {
                    startService(Intent(this@MainActivity, GuardianVpnService::class.java)
                        .setAction(GuardianVpnService.ACTION_TEST_UPSTREAM))
                } catch (e: Exception) {
                    isEnabled = true
                    text = "업스트림 연결 테스트"
                    upstreamTestText?.text = "테스트 시작 실패: " + e.javaClass.simpleName
                }
            }
        }
        body.addView(upstreamTestButton!!, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(14) })
        upstreamTestText = text(upstreamTestDescription(), 11f, muted, false).withTop(8) as TextView
        body.addView(upstreamTestText!!)
        body.addView(Button(this).apply {
            text = "차단 기록 초기화"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 12)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("차단 기록을 초기화할까요?")
                    .setMessage("DNS 질의·차단 통계와 최근 요청 기록이 이 기기에서 삭제됩니다. 이 작업은 되돌릴 수 없습니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("초기화") { _, _ ->
                        prefs.edit()
                            .putLong(GuardianVpnService.KEY_DNS_QUERIES, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_FAILED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_IGNORED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_AD_BLOCKED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_TRACKER_BLOCKED, 0L)
                            .putLong(GuardianVpnService.KEY_DNS_OTHER_BLOCKED, 0L)
                            .putString(GuardianVpnService.KEY_DNS_RECENT, "")
                            .putString(GuardianVpnService.KEY_DNS_DIAGNOSTIC, "")
                            .apply()
                        refreshStatus()
                        Toast.makeText(this@MainActivity, "차단 기록을 초기화했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
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
        body.addView(Button(this).apply {
            text = "개인정보 제외 진단 정보 복사"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = shape(panel2, 12)
            setOnClickListener { copySupportDiagnostics(this) }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(14) })
        body.addView(text("진단 보고서에는 필터 파일/캐시 상태와 앱 환경만 포함됩니다. 최근 방문 도메인, 사용자 규칙, 라이선스 비밀값은 복사하지 않습니다.", 10f, muted, false).withTop(7))
        body.addView(text("이 화면은 DNS 암호화 또는 VPN 우회 트래픽을 집계하지 않습니다.", 11f, muted, false).withTop(14))
    }

    private fun copySupportDiagnostics(button: Button) {
        button.isEnabled = false
        button.text = "진단 정보 준비 중..."
        thread(name = "guardian-support-diagnostics") {
            val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
            fun cacheBytes(name: String): Long = File(filesDir, name)
                .takeIf { it.isFile && it.length() > 0L }
                ?.length() ?: 0L
            var assetBytes = -1L
            val assetAvailable = try {
                assetBytes = assets.open("filter_rules.txt").use { input ->
                    var total = 0L
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                    }
                    total
                }
                assetBytes > 0L
            } catch (_: Exception) {
                false
            } catch (_: LinkageError) {
                false
            }
            val nativeLibraryDirectory = File(applicationInfo.nativeLibraryDir)
            val nativeLibraryFiles = try {
                nativeLibraryDirectory.listFiles()
                    ?.filter { it.isFile && it.name.matches(Regex("lib[A-Za-z0-9_+.-]+\\.so")) }
                    ?.map { it.name }
                    .orEmpty()
            } catch (_: SecurityException) {
                emptyList()
            }
            val nativeLibraryDirectoryReadable = nativeLibraryDirectory.isDirectory &&
                nativeLibraryDirectory.canRead()
            val selectedProfile = try {
                FilterListManager.selectedProfile(this).key
            } catch (_: Exception) {
                "unknown"
            }
            val cachedProfile = try {
                FilterListManager.cachedProfile(this).key
            } catch (_: Exception) {
                null
            }
            val baseCacheReady = runCatching { FilterListManager.hasCachedList(this) }.getOrNull()
            val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            val securityCacheReady = runCatching { FilterListManager.hasCachedSecurityList(this) }.getOrNull()
            val androidAdsCacheReady = runCatching { FilterListManager.hasCachedAndroidAdsList(this) }.getOrNull()
            val intervalMs = FilterUpdateScheduler.normalizeInterval(
                prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, FilterUpdateScheduler.DEFAULT_INTERVAL_MS)
            )
            val report = ProtectionDiagnostics.supportReport(
                appVersion = BuildConfig.VERSION_NAME,
                androidRelease = Build.VERSION.RELEASE ?: "unknown",
                apiLevel = Build.VERSION.SDK_INT,
                protectionActive = ProtectionState.isActive(
                    prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                    GuardianVpnService.isTunnelActive()
                ),
                bundledRuleAssetAvailable = assetAvailable,
                selectedProfile = selectedProfile,
                baseCacheBytes = cacheBytes("adguard_dns_filter.txt"),
                securityFilterEnabled = securityEnabled,
                securityCacheBytes = cacheBytes("hagezi_threat_intelligence.txt"),
                androidAdsFilterEnabled = androidAdsEnabled,
                androidAdsCacheBytes = cacheBytes("awavenue_android_ads.txt"),
                autoUpdateEnabled = prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true),
                updateIntervalHours = intervalMs / 3_600_000L,
                hasBaseUpdateTimestamp = FilterListManager.lastUpdatedAt(this) > 0L,
                signedFilterDeliveryConfigured = FilterDistributionSettings.requiresSignedDelivery(
                    FilterDistributionSettings.configuredBaseUrl(this),
                    BuildConfig.FILTER_MANIFEST_PUBLIC_KEY
                ),
                licenseApiConfigured = BuildConfig.LICENSE_API_BASE_URL.isNotBlank() &&
                    BuildConfig.LICENSE_PUBLIC_KEY.isNotBlank(),
                filterUpdateErrorPresent = listOf(
                    FilterListManager.KEY_LAST_ERROR,
                    FilterListManager.KEY_SECURITY_LAST_ERROR,
                    FilterListManager.KEY_ANDROID_ADS_LAST_ERROR
                ).any { !prefs.getString(it, null).isNullOrBlank() },
                lastServiceStatus = prefs.getString(GuardianVpnService.KEY_STATUS, null),
                lastStartupTrace = prefs.getString(GuardianVpnService.KEY_STARTUP_TRACE, null),
                bundledRuleAssetBytes = assetBytes,
                nativeLibraryDirectoryReadable = nativeLibraryDirectoryReadable,
                nativeLibraryNames = nativeLibraryFiles,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                baseCacheReady = baseCacheReady,
                cachedProfile = cachedProfile,
                cachedRuleCount = FilterListManager.cachedBlockCount(this),
                securityCacheReady = securityCacheReady,
                securityRuleCount = FilterListManager.cachedSecurityBlockCount(this),
                androidAdsCacheReady = androidAdsCacheReady,
                androidAdsRuleCount = FilterListManager.cachedAndroidAdsBlockCount(this),
                filterReadiness = prefs.getString(GuardianVpnService.KEY_FILTER_READINESS, null)
            )
            runOnUiThread {
                try {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Guardian diagnostics", report))
                    android.widget.Toast.makeText(this, "개인정보를 제외한 진단 정보를 복사했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    android.widget.Toast.makeText(this, "진단 정보 복사 실패: ${error.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    button.isEnabled = true
                    button.text = "개인정보 제외 진단 정보 복사"
                }
            }
        }
    }

    private fun upstreamTestDescription(): String {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val testedAt = prefs.getLong(GuardianVpnService.KEY_UPSTREAM_TEST_AT, 0L)
        if (testedAt <= 0L) return "선택한 DNS 서버와 전송 방식으로 실제 질의를 보내 연결을 확인합니다."
        val success = prefs.getBoolean(GuardianVpnService.KEY_UPSTREAM_TEST_SUCCESS, false)
        val latency = prefs.getLong(GuardianVpnService.KEY_UPSTREAM_TEST_LATENCY_MS, 0L)
        val transport = when (prefs.getString(GuardianVpnService.KEY_UPSTREAM_TEST_TRANSPORT, "udp")) {
            "tcp" -> "TCP/53"
            "dot" -> "DNS-over-TLS/853"
            "doh" -> "DNS-over-HTTPS/443"
            else -> "UDP/53"
        }
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(testedAt))
        return if (success) "연결 성공 · $transport · " + latency + "ms · $time"
        else "연결 실패 · $transport · 서버 주소, 네트워크 또는 TLS 호스트 설정을 확인하세요 · $time"
    }

    private fun privateDnsMode(): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        return android.provider.Settings.Global.getString(
            contentResolver, "private_dns_mode"
        )?.lowercase(Locale.ROOT)
    }

    private fun privateDnsCompatibilityStatus(): Pair<String, Int> {
        if (Build.VERSION.SDK_INT < 28) {
            return "Android 비공개 DNS 상태 확인은 Android 9 이상에서 지원됩니다." to muted
        }
        val mode = privateDnsMode()
        val provider = android.provider.Settings.Global.getString(
            contentResolver, "private_dns_specifier"
        )?.takeIf { it.isNotBlank() }
        return when (mode) {
            "off" -> "비공개 DNS 꺼짐 — Guardian 시스템 DNS 필터링에 적합한 상태입니다." to accent
            "opportunistic" -> "비공개 DNS 자동 모드입니다. 네트워크에 따라 암호화 DNS가 Guardian 필터를 우회할 수 있습니다. 차단이 누락되면 Android 설정에서 비공개 DNS를 '사용 안 함'으로 변경해 주세요." to Color.rgb(238, 176, 82)
            "hostname" -> ("비공개 DNS 공급자" + (provider?.let { " ($it)" } ?: "") + "가 지정되어 있습니다. DoT/853 요청이 Guardian 필터를 우회할 수 있습니다. 완전한 DNS 필터링을 원하시면 Android 설정에서 비공개 DNS를 '사용 안 함'으로 변경해 주세요.") to red
            else -> "비공개 DNS 상태를 확인할 수 없습니다. DNS 차단이 작동하지 않으면 Android 비공개 DNS 설정을 확인해 주세요." to muted
        }
    }

    private fun buildProtection(body: LinearLayout) {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val active = ProtectionState.isActive(
            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
            GuardianVpnService.isTunnelActive()
        )
        body.addView(text("보호 설정", 25f, white, true))
        body.addView(text("실시간 DNS 보호와 주요 차단 기능을 한곳에서 관리합니다.", 13f, muted, false).withTop(5))

        val control = card(panel, 20).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(text("GUARDIAN PROTECTION", 10f, accent, true))
            protectionStatusText = text(
                if (active) "보호가 실행 중입니다" else "보호가 꺼져 있습니다",
                21f, white, true
            ).withTop(12) as TextView
            addView(protectionStatusText!!)
            protectionStatusDetail = text(
                ProtectionDiagnostics.statusDetail(
                    active,
                    prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0L),
                    prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L),
                    prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L),
                    prefs.getLong(GuardianVpnService.KEY_DNS_FAILED, 0L),
                    privateDnsMode()
                ),
                12f, muted, false
            ).withTop(6) as TextView
            addView(protectionStatusDetail!!)
            val privateDns = privateDnsCompatibilityStatus()
            addView(text(privateDns.first, 11f, privateDns.second, false).withTop(10))
            addView(text(
                "보호 시작과 중지는 홈 화면의 단일 제어 버튼에서 실행합니다.",
                11f, muted, false
            ).withTop(14))
        }
        body.addView(control.withTop(18))

        body.addView(text("보호 기능", 18f, white, true).withTop(24))
        body.addView(text("필터 기능을 켜거나 끄고 업데이트 정책을 조정합니다.", 12f, muted, false).withTop(4))

        val feedback = text("변경한 필터 옵션은 현재 보호 상태에 맞춰 적용됩니다.", 11f, muted, false).withTop(8) as TextView
        protectionFeedback = feedback

        body.addView(protectionSwitchCard(
            "자동 필터 업데이트",
            "네트워크 연결 시 설정한 주기에 따라 광고·추적 필터를 갱신합니다.",
            prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
        ) { checked ->
            prefs.edit().putBoolean(FilterListManager.KEY_AUTO_UPDATE, checked).putBoolean("filter_auto_update_user_choice_v1", true).apply()
            FilterUpdateScheduler.sync(this)
            feedback.text = if (checked) "자동 업데이트를 켰습니다." else "자동 업데이트를 껐습니다."
        }.withTop(10))

        val securitySwitch = protectionSwitchCard(
            "악성코드 · 피싱 보호",
            "HaGeZi Threat Intelligence 도메인 목록을 추가 적용합니다. 현재 모든 사용자가 사용할 수 있습니다.",
            prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false),
            onToggleCreated = {
                securityFilterSwitch = it
                it.isEnabled = !filterUpdateRunning.get()
            }
        ) { checked ->
            prefs.edit().putBoolean(FilterListManager.KEY_SECURITY_ENABLED, checked).apply()
            if (checked && !FilterListManager.hasCachedSecurityList(this)) {
                feedback.text = "보안 필터 목록을 다운로드합니다."
                startFilterUpdate()
            } else {
                reloadProtectionRulesIfActive(prefs)
                feedback.text = if (checked) "악성코드 · 피싱 필터를 활성화했습니다." else "보안 필터를 비활성화했습니다."
            }
        }
        body.addView(securitySwitch.withTop(8))

        body.addView(protectionSwitchCard(
            "Android 앱 광고 SDK 필터",
            "AWAvenue 목록으로 알려진 앱 광고 SDK 도메인을 추가 차단합니다. 모든 앱 내부 광고를 제거하는 기능은 아닙니다.",
            prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true),
            onToggleCreated = {
                androidAdsFilterSwitch = it
                it.isEnabled = !filterUpdateRunning.get()
            }
        ) { checked ->
            prefs.edit().putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, checked).apply()
            if (checked && !FilterListManager.hasCachedAndroidAdsList(this)) {
                feedback.text = "Android 광고 SDK 목록을 다운로드합니다."
                startFilterUpdate()
            } else {
                reloadProtectionRulesIfActive(prefs)
                feedback.text = if (checked) "앱 광고 SDK 필터를 활성화했습니다." else "앱 광고 SDK 필터를 비활성화했습니다."
            }
        }.withTop(8))

        body.addView(protectionSwitchCard(
            "공용 DNS 직접 포착 · 고급",
            "Cloudflare·Google·Quad9·AdGuard 등 알려진 DNS 서버 IP를 포착합니다. 같은 IP로 가는 비DNS 연결에도 영향을 줄 수 있어 기본값은 꺼져 있습니다.",
            prefs.getBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, GuardianVpnService.DEFAULT_DIRECT_DNS_CAPTURE)
        ) { checked ->
            prefs.edit().putBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, checked).apply()
            feedback.text = if (checked) {
                "고급 DNS 포착 설정을 저장했습니다. 보호를 중지한 뒤 다시 시작해야 적용됩니다."
            } else {
                "시스템 가상 DNS만 포착하도록 설정했습니다. 보호를 중지한 뒤 다시 시작해야 적용됩니다."
            }
        }.withTop(8))
        body.addView(feedback)

        val profile = FilterListManager.selectedProfile(this)
        body.addView(card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("현재 필터 프로필", 11f, muted, true))
            addView(text(profile.label, 15f, white, true).withTop(6))
            addView(text(filterStatusDescription(), 11f, muted, false).withTop(6))
            addView(text(
                "필터 규칙과 앱별 보호 제외는 하단 메뉴에서 각각 관리합니다.",
                11f, muted, false
            ).withTop(12))
        }.withTop(18))

        body.addView(text("현재 보호 범위", 18f, white, true).withTop(24))
        body.addView(infoRow("DNS 프로토콜", "시스템 가상 DNS의 IPv4 / IPv6 요청", "UDP/53 · TCP/53", accent))
        body.addView(infoRow("앱 자체 암호화 DNS", "앱 내부 DoH / DoT 요청 가로채기", "미지원", red))
        body.addView(text("참고: Guardian이 필터링한 DNS 질의를 업스트림 서버로 전달할 때 사용하는 DoH / DoT 전송은 설정에서 지원합니다. 앱이 자체적으로 직접 연결하는 암호화 DNS를 가로채는 기능과는 다릅니다.", 11f, muted, false).withTop(6))
        body.addView(text(
            "DNS 이외의 네트워크 트래픽은 중계하지 않습니다. IPv6 확장 헤더와 IP 조각화, HTTPS 내부 광고 요소, 앱에 직접 내장된 광고 콘텐츠는 필터링하지 못할 수 있습니다.",
            11f, muted, false
        ).withTop(8))
    }

    private fun protectionSwitchCard(
        title: String,
        description: String,
        checked: Boolean,
        onToggleCreated: ((Switch) -> Unit)? = null,
        onChanged: (Boolean) -> Unit
    ): View {
        val card = card(panel, 14).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text(title, 13f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = checked
            val toggleStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(toggleStates, intArrayOf(accent, muted))
            trackTintList = ColorStateList(toggleStates, intArrayOf(Color.rgb(38, 112, 92), Color.rgb(78, 91, 112)))
            contentDescription = title
            setOnCheckedChangeListener { _, enabled -> onChanged(enabled) }
        }
        onToggleCreated?.invoke(toggle)
        row.addView(toggle, LinearLayout.LayoutParams(dp(58), dp(42)))
        card.addView(row)
        card.addView(text(description, 10f, muted, false).withTop(4))
        return card
    }

    /** Keep visible filter controls aligned when a background worker disables an unavailable optional source. */
    private fun syncFilterOptionControls() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        syncingFilterOptions = true
        try {
            val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            securityFilterSwitch?.isChecked = securityEnabled
            securityFilterCheckBox?.isChecked = securityEnabled
            androidAdsFilterSwitch?.isChecked = androidAdsEnabled
            androidAdsFilterCheckBox?.isChecked = androidAdsEnabled
        } finally {
            syncingFilterOptions = false
        }
    }

    private fun reloadProtectionRulesIfActive(prefs: android.content.SharedPreferences) {
        if (ProtectionState.isActive(
                prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                GuardianVpnService.isTunnelActive()
            )) {
            startService(Intent(this, GuardianVpnService::class.java)
                .setAction(GuardianVpnService.ACTION_RELOAD_RULES))
        }
    }

    private fun buildRules(body: LinearLayout) {
        body.addView(text("필터 규칙", 25f, white, true))
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
                text = if (filterUpdateRunning.get()) "필터 다운로드 및 검증 중…" else "필터 목록 업데이트"
                isAllCaps = false
                setTextColor(bg)
                background = shape(accent, 12)
                isEnabled = !filterUpdateRunning.get()
                contentDescription = "필터 목록 업데이트"
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

    private fun buildUpdates(body: LinearLayout) {
        body.addView(text("프로그램 업데이트", 25f, white, true))
        body.addView(text("Guardian 앱 자체 업데이트입니다. 광고·보안 필터 목록 갱신과 별개입니다.", 13f, muted, false).withTop(5))
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "확인 불가"
        } catch (_: Exception) { "확인 불가" }
        body.addView(infoRow("설치 버전", "Guardian AdBlock", versionName, accent).withTop(14))

        val developmentBuild = BuildConfig.DEBUG
        val stableUpdaterEnabled = BuildConfig.GUARDIAN_STABLE_UPDATER_ENABLED
        val feedback = text(
            when {
                developmentBuild -> "개발 버전 업데이트 확인 전"
                stableUpdaterEnabled -> "정식 업데이트 확인 전"
                else -> "이 배포 유형에서는 인앱 업데이트가 비활성화되어 있습니다."
            },
            12f, muted, false
        ).withTop(12) as TextView
        body.addView(feedback)
        var downloaded: File? = null
        val installButton = Button(this).apply {
            text = "업데이트 설치"
            isAllCaps = false
            isEnabled = false
            setTextColor(bg)
            background = shape(accent, 12)
        }
        val checkButton = Button(this).apply {
            text = if (developmentBuild) "개발 업데이트 확인" else "정식 업데이트 확인"
            isAllCaps = false
            isEnabled = developmentBuild || stableUpdaterEnabled
            setTextColor(bg)
            background = shape(accent, 12)
            setOnClickListener {
                isEnabled = false
                installButton.isEnabled = false
                downloaded = null
                feedback.text = if (developmentBuild) "공식 개발 릴리스 확인 중…" else "공식 정식 릴리스 확인 중…"
                thread(name = "guardian-app-update-menu") {
                    try {
                        if (developmentBuild) {
                            val update = DevUpdateManager.checkForUpdate()
                            if (update == null) {
                                runOnUiThread {
                                    feedback.text = "현재 설치된 개발 버전이 최신입니다."
                                    isEnabled = true
                                }
                            } else {
                                runOnUiThread {
                                    feedback.text = "새 개발 버전 ${update.versionName} 발견 · APK 다운로드 및 검증 중…"
                                }
                                val apk = DevUpdateManager.download(this@MainActivity, update)
                                runOnUiThread {
                                    downloaded = apk
                                    feedback.text = "${update.versionName} 다운로드 완료 · SHA-256, 패키지 및 개발 서명 인증서 검증 통과."
                                    installButton.isEnabled = true
                                    isEnabled = true
                                }
                            }
                        } else {
                            val update = GuardianStableUpdateManager.checkForUpdate()
                            if (update == null) {
                                runOnUiThread {
                                    feedback.text = "현재 설치된 정식 버전이 최신입니다."
                                    isEnabled = true
                                }
                            } else {
                                runOnUiThread {
                                    feedback.text = "새 정식 버전 ${update.versionName} 발견 · APK 다운로드 및 검증 중…"
                                }
                                val apk = GuardianStableUpdateManager.download(this@MainActivity, update)
                                runOnUiThread {
                                    downloaded = apk
                                    feedback.text = "${update.versionName} 다운로드 완료 · SHA-256, 패키지 및 운영 서명 인증서 검증 통과."
                                    installButton.isEnabled = true
                                    isEnabled = true
                                }
                            }
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            downloaded = null
                            installButton.isEnabled = false
                            feedback.text = "업데이트 확인 또는 다운로드 실패: " + SafeFailureSummary.describe(error)
                            isEnabled = true
                        }
                    }
                }
            }
        }
        body.addView(checkButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        installButton.setOnClickListener {
            val apk = downloaded ?: return@setOnClickListener
            try {
                val launched = if (developmentBuild) {
                    DevUpdateManager.install(this@MainActivity, apk)
                } else {
                    GuardianStableUpdateManager.install(this@MainActivity, apk)
                }
                feedback.text = if (launched) {
                    "Android 시스템 설치 확인 화면을 열었습니다."
                } else {
                    "알 수 없는 앱 설치 권한을 허용한 뒤 다시 설치를 누르세요."
                }
            } catch (error: Exception) {
                feedback.text = "설치 화면을 열지 못했습니다: " + SafeFailureSummary.describe(error)
            }
        }
        body.addView(installButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        body.addView(text(
            if (developmentBuild) {
                "개발판은 공식 개발 릴리스의 APK SHA-256, 패키지 ID, 버전 및 개발 서명 인증서를 검증합니다. 업데이트는 사용자가 직접 확인하고 설치합니다."
            } else {
                "정식판은 공식 HTTPS Release manifest, APK SHA-256, 패키지 ID, 버전 및 운영 서명 인증서를 검증한 뒤 Android 설치 화면을 엽니다."
            },
            11f, muted, false
        ).withTop(14))
    }
    private fun buildPremium(body: LinearLayout) {
        val entitlement = LicenseEntitlementStore.current(this)
        val retainedLicense = LicenseEntitlementStore.currentForRefresh(this)
        val proActive = entitlement != null
        // Development builds expose the complete Premium preview independently
        // from real signed-license entitlement. Never use this flag to authorize
        // production features or alter LicenseEntitlementStore.
        val developmentPreview = BuildConfig.DEBUG
        val apiReady = BuildConfig.LICENSE_API_BASE_URL.isNotBlank() &&
            BuildConfig.LICENSE_PUBLIC_KEY.isNotBlank()

        body.addView(text("Guardian Premium", 26f, white, true))
        body.addView(text(
            "프리미엄 보안 기능과 PRO 라이선스를 한곳에서 관리합니다.",
            13f, muted, false
        ).withTop(5))

        body.addView(card(panel, 20).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(text("GUARDIAN  /  PRO MEMBERSHIP", 10f, accent, true))
            addView(text(
                when {
                    proActive -> "PRO 프리미엄 라이선스가 활성화되어 있습니다"
                    developmentPreview -> "개발 미리보기 · 프리미엄 전체 화면 열림"
                    else -> "Guardian PRO로 보안 경험을 확장하세요"
                },
                19f, white, true
            ).withTop(12))
            addView(text(
                when {
                    proActive -> "서버 서명과 이 기기의 등록 정보를 확인했습니다."
                    retainedLicense != null -> "이전에 등록한 라이선스가 만료되었습니다. 상태를 갱신하거나 새 라이선스를 활성화하세요."
                    developmentPreview -> "개발 빌드에서는 라이선스 등록 없이 프리미엄 화면과 기능 구성을 미리 볼 수 있습니다. 실제 라이선스 권한은 별도로 검증됩니다."
                    else -> "현재 FREE 플랜을 사용 중입니다. 발급받은 PRO 라이선스 코드로 이 기기를 등록할 수 있습니다."
                },
                11f, muted, false
            ).withTop(7))
            addView(text(
                when {
                    proActive -> "PRO ACTIVE"
                    retainedLicense != null -> "LICENSE EXPIRED"
                    developmentPreview -> "PRO PREVIEW · 개발 전용"
                    else -> "FREE PLAN"
                },
                11f, if (proActive) accent else if (retainedLicense != null) Color.rgb(238, 176, 82) else muted, true
            ).withTop(14))
            if (proActive && entitlement != null) {
                val expiry = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                    .format(Date(entitlement.expiresAt * 1000L))
                addView(text("라이선스 유효 기간 · $expiry 까지", 11f, muted, false).withTop(6))
            }
            addView(Button(this@MainActivity).apply {
                text = if (proActive || retainedLicense != null) "PRO 라이선스 관리" else "PRO 라이선스 활성화"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(bg)
                background = shape(accent, 12)
                setOnClickListener {
                    showTab("settings-premium", focusLicenseActivation = true)
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
        }.withTop(16))

        body.addView(text("멤버십 상태", 18f, white, true).withTop(22))
        body.addView(infoRow(
            "현재 이용 플랜",
            if (proActive) "기기 바인딩 및 서버 서명 검증 완료" else "PRO 라이선스 미활성화",
            if (proActive) "PRO" else "FREE",
            if (proActive) accent else muted
        ))
        body.addView(infoRow(
            "라이선스 서버",
            if (apiReady) "앱에 검증용 공개키와 API 주소가 설정되어 있습니다." else "운영 서버 또는 서명 공개키 설정이 필요합니다.",
            if (apiReady) "연결 설정됨" else "미설정",
            if (apiReady) accent else Color.rgb(238, 176, 82)
        ))
        body.addView(infoRow(
            "기기 등록",
            if (proActive) "이 기기에 발급된 서명 토큰을 확인했습니다." else "활성화 시 이 기기에 연결된 라이선스를 발급합니다.",
            if (proActive) "확인됨" else "대기",
            if (proActive) accent else muted
        ))

        if (developmentPreview) {
            body.addView(buildSecurityProfilePreview().withTop(14))
        }
        body.addView(text("PRO 기능 미리보기", 18f, white, true).withTop(24))
        body.addView(text(
            if (developmentPreview) "개발 미리보기 모드 · 라이선스 권한을 부여하지 않으며, 실제 엔진 연결 상태를 각 항목에 표시합니다."
            else "라이선스 활성화 후 이용할 수 있는 프리미엄 기능입니다.",
            11f, muted, false
        ).withTop(5))
        body.addView(card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("앱 코드 피싱 보호", 15f, white, true))
            addView(text(
                "PRO 전용 기능으로 분류할 수 있도록 메뉴를 마련했습니다. 다만 현재 앱에는 앱 코드·링크를 판별하는 검사 엔진이 연결되어 있지 않아 아직 실제 탐지나 차단은 수행하지 않습니다.",
                11f, muted, false
            ).withTop(6))
            addView(text("검사 엔진 연동 필요", 10f, Color.rgb(238, 176, 82), true).withTop(9))
        }.withTop(10))
        body.addView(card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("서명 기반 PRO 라이선스", 15f, white, true))
            addView(text(
                "서버가 발급한 기기 바인딩 토큰을 앱에 고정된 RSA 공개키로 검증합니다. 만료된 라이선스는 PRO 권한을 부여하지 않습니다.",
                11f, muted, false
            ).withTop(6))
            addView(text(
                if (proActive) "현재 이 기기에서 검증 완료" else "라이선스 활성화 후 검증",
                10f, if (proActive) accent else muted, true
            ).withTop(9))
        }.withTop(8))

        val previewFeatures = listOf(
            Triple("고급 광고·추적 차단", "필터 엔진 기반 차단 정책과 사용자 규칙을 확장하는 기능", "필터 엔진 기반 · 적용 범위 검증 필요"),
            Triple("앱별 세밀한 보호 제어", "앱 단위 보호 제외와 예외 정책을 관리하는 기능", "현재 개발 중 · 기기별 VPN 검증 필요"),
            Triple("실시간 위협·DNS 활동 분석", "차단 이벤트와 DNS 요청을 분류해 활동 내역으로 제공", "기본 DNS 처리 존재 · 고급 분석 개발 필요"),
            Triple("보안 프로필 및 자동화", "네트워크 유형·검증 상태에 따른 보호 프로필 자동 전환", "DEBUG NetworkCallback 연결 · Android 검증 및 PRO 권한 등록 대기"),
            Triple("피싱 링크·앱 코드 검사", "의심 URL과 앱 코드의 위험도를 검사하는 기능", "검사 엔진 미연결 · 실제 탐지 안 함"),
            Triple("프리미엄 리포트 및 통계", "기간별 차단량과 보호 상태를 요약하는 기능", "리포트 엔진 개발 필요")
        )
        previewFeatures.forEach { (name, description, status) ->
            body.addView(card(panel, 14).apply {
                setPadding(dp(15), dp(14), dp(15), dp(14))
                addView(text(name, 14f, white, true))
                addView(text(description, 11f, muted, false).withTop(5))
                addView(text(status, 10f, Color.rgb(238, 176, 82), true).withTop(8))
            }.withTop(8))
        }

        body.addView(text(
            "개발 미리보기는 UI와 기능 구성을 검토하기 위한 모드입니다. 실제 보안 동작은 각 기능의 엔진 구현과 테스트가 끝난 뒤에만 활성 상태로 표시합니다. 정식 배포 빌드에서는 서버 서명 라이선스로 이용 권한을 판정합니다.",
            10f, muted, false
        ).withTop(14))
    }

    private fun buildSettingsLanding(body: LinearLayout) {
        data class SettingsEntry(val symbol: String, val title: String, val description: String, val target: String)
        val sections = listOf(
            SettingsEntry("◉", "일반", "앱 동작, DNS 연결 및 기본 환경", "settings-general"),
            SettingsEntry("◈", "필터링", "광고·추적 차단, 보호 정책과 사용자 규칙", "settings-filtering"),
            SettingsEntry("◇", "라이선스 및 프리미엄", "이용 기간, 허용 기기와 프리미엄 기능", "settings-premium"),
            SettingsEntry("✉", "고객 지원", "도움말, 시스템 진단과 오류 내용 복사", "settings-support"),
            SettingsEntry("ⓘ", "정보", "제품 정보, 개인정보 및 앱 업데이트", "settings-about"),
            SettingsEntry("⏻", "종료", "앱 종료 및 백그라운드 보호 안내", "settings-exit")
        )
        val screenWidthDp = resources.configuration.screenWidthDp
        body.addView(text("설정", 27f, white, true))
        body.addView(text("Guardian을 원하는 방식으로 관리하세요.", 13f, muted, false).withTop(5))
        if (ResponsiveLayoutPolicy.usesSettingsSplitPane(screenWidthDp)) {
            val split = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }.withTop(18) as LinearLayout
            val categoryPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = shape(Color.rgb(17, 31, 47), 20)
            }
            val detailPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(20))
                background = shape(Color.rgb(27, 27, 30), 20)
            }
            split.addView(categoryPanel, LinearLayout.LayoutParams(
                dp(if (settingsSidebarCollapsed) 68 else 260),
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dp(14) })
            val sidebarToggle = Button(this).apply {
                text = if (settingsSidebarCollapsed) ">" else "<  메뉴 접기"
                isAllCaps = false
                textSize = if (settingsSidebarCollapsed) 20f else 12f
                setTextColor(accent)
                background = shape(panel2, 12)
                contentDescription = if (settingsSidebarCollapsed) "설정 메뉴 펼치기" else "설정 메뉴 접기"
                setOnClickListener {
                    settingsSidebarCollapsed = !settingsSidebarCollapsed
                    showTab("settings")
                }
            }
            categoryPanel.addView(sidebarToggle, LinearLayout.LayoutParams(-1, dp(42)).apply {
                bottomMargin = dp(8)
            })
            sections.forEachIndexed { index, (symbol, title, description, target) ->
                val selected = selectedSettingsCategory == target
                categoryPanel.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(14), dp(10), dp(14))
                    background = shape(if (selected) Color.rgb(27, 72, 81) else Color.TRANSPARENT, 16)
                    addView(text(symbol, 19f, if (selected) white else muted, true),
                        LinearLayout.LayoutParams(dp(30), -2))
                    if (!settingsSidebarCollapsed) {
                        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                        labels.addView(text(title, 14f, white, true))
                        labels.addView(text(description, 10f, muted, false).withTop(3))
                        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    } else {
                        gravity = Gravity.CENTER
                    }
                    contentDescription = title
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedSettingsCategory = target
                        showTab("settings")
                    }
                }.apply {
                    if (index > 0) setPadding(paddingLeft, dp(12), paddingRight, dp(12))
                }.withTop(3))
            }
            when (selectedSettingsCategory) {
                "settings-general" -> buildSettingsGeneral(detailPanel)
                "settings-filtering" -> buildSettingsFiltering(detailPanel)
                "settings-premium" -> buildSettingsPremium(detailPanel)
                "settings-support" -> {
                    detailPanel.addView(text("고객 지원", 25f, white, true))
                    detailPanel.addView(buildSupportCenterCard().withTop(12))
                    detailPanel.addView(text("진단 정보와 오류 내용은 직접 확인하고 복사할 수 있습니다.", 11f, muted, false).withTop(10))
                }
                "settings-about" -> buildSettingsAbout(detailPanel)
                "settings-exit" -> {
                    detailPanel.addView(text("종료", 25f, white, true))
                    detailPanel.addView(text("앱을 닫아도 DNS 보호는 명시적으로 중지하지 않는 한 유지됩니다.", 12f, muted, false).withTop(8))
                    detailPanel.addView(Button(this).apply {
                        text = "Guardian 종료"
                        isAllCaps = false
                        setTextColor(white)
                        background = shape(Color.rgb(139, 57, 64), 12)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity).setTitle("Guardian 종료")
                                .setMessage("앱을 종료하시겠습니까? 실행 중인 DNS 보호는 종료 후에도 유지됩니다.")
                                .setNegativeButton("취소", null)
                                .setPositiveButton("종료") { _, _ -> finish() }
                                .show()
                        }
                    }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(16) })
                }
                else -> buildSettingsGeneral(detailPanel)
            }
            val detailScroll = ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(detailPanel, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            split.addView(detailScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            body.addView(split, LinearLayout.LayoutParams(-1, dp(660)))
            return
        }

        // Phone layout deliberately avoids a cramped side-by-side rail.
        // Show the six settings categories at full width, then open the selected
        // category as a full-width detail page with a clear back-to-menu action.
        if (!phoneSettingsShowingDetail) {
            sections.forEach { (symbol, title, description, target) ->
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(15), dp(14), dp(15))
                    background = shape(panel, 15)
                    addView(text(symbol, 21f, accent, true),
                        LinearLayout.LayoutParams(dp(38), -2))
                    val labels = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(title, 14f, white, true))
                        addView(text(description, 11f, muted, false).withTop(4))
                    }
                    addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(text("›", 24f, muted, true))
                    isClickable = true
                    isFocusable = true
                    contentDescription = "$title 설정 열기"
                    setOnClickListener {
                        selectedSettingsCategory = target
                        phoneSettingsShowingDetail = true
                        showTab("settings")
                    }
                }.withTop(9))
            }
            return
        }

        body.addView(Button(this).apply {
            text = "‹  설정 메뉴"
            isAllCaps = false
            textSize = 13f
            setTextColor(accent)
            background = shape(panel2, 11)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                phoneSettingsShowingDetail = false
                showTab("settings")
            }
        }.withTop(12))
        val phoneDetail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(16), dp(15), dp(18))
            background = shape(Color.rgb(27, 27, 30), 16)
        }
        when (selectedSettingsCategory) {
            "settings-general" -> buildSettingsGeneral(phoneDetail)
            "settings-filtering" -> buildSettingsFiltering(phoneDetail)
            "settings-premium" -> buildSettingsPremium(phoneDetail)
            "settings-support" -> {
                phoneDetail.addView(text("고객 지원", 21f, white, true))
                phoneDetail.addView(buildSupportCenterCard().withTop(10))
                phoneDetail.addView(text("진단 정보와 오류 내용은 직접 확인하고 복사할 수 있습니다.", 11f, muted, false).withTop(8))
            }
            "settings-about" -> buildSettingsAbout(phoneDetail)
            "settings-exit" -> {
                phoneDetail.addView(text("종료", 21f, white, true))
                phoneDetail.addView(text("앱을 닫아도 DNS 보호는 명시적으로 중지하지 않는 한 유지됩니다.", 11f, muted, false).withTop(8))
                phoneDetail.addView(Button(this).apply {
                    text = "Guardian 종료"
                    isAllCaps = false
                    setTextColor(white)
                    background = shape(Color.rgb(139, 57, 64), 12)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("Guardian 종료")
                            .setMessage("앱을 종료하시겠습니까? 실행 중인 DNS 보호는 종료 후에도 유지됩니다.")
                            .setNegativeButton("취소", null)
                            .setPositiveButton("종료") { _, _ -> finish() }
                            .show()
                    }
                }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
            }
            else -> buildSettingsGeneral(phoneDetail)
        }
        body.addView(phoneDetail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun buildSettingsGeneral(body: LinearLayout) {
        body.addView(text("일반", 25f, white, true))
        body.addView(text("앱 표시와 기본 동작, 알림 권한을 관리합니다.", 13f, muted, false).withTop(5))
        body.addView(card(panel, 14).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("표시 및 언어", 15f, white, true))
            addView(infoRow("표시 언어", "현재 앱 인터페이스 언어", "한국어", accent))
            addView(infoRow("화면 테마", "Guardian 기본 화면 테마", "다크", accent))
            addView(infoRow("백그라운드 동작", "앱 창을 닫아도 실행 중인 DNS 보호 유지", "유지", accent))
        }.withTop(12))
        body.addView(card(panel, 14).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("알림 권한", 15f, white, true))
            addView(text("보호 상태와 업데이트 알림은 Android 시스템 알림 권한을 사용합니다.", 11f, muted, false).withTop(5))
            addView(Button(this@MainActivity).apply {
                text = "Android 알림 설정 열기"
                isAllCaps = false
                setTextColor(white)
                background = shape(panel2, 12)
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }.withTop(10))
    }

    private fun buildSettingsFiltering(body: LinearLayout) {
        body.addView(text("필터링", 25f, white, true))
        body.addView(text("보호 정책, DNS 연결, 필터 서버와 사용자 규칙을 관리합니다.", 13f, muted, false).withTop(5))
        buildProtection(body)
        buildRules(body)
                body.addView(card(panel, 14).apply {
                    setPadding(dp(15), dp(15), dp(15), dp(15))
                    addView(text("서명 필터 배포 서버", 15f, white, true))
                    addView(text(
                        "필터 파일은 앱에 포함된 공개키로 서명을 검증합니다. 노트북 서버의 HTTPS 주소를 바꾸면 다음 업데이트부터 새 주소를 사용합니다.",
                        11f, muted, false
                    ).withTop(5))
                    val filterServerInput = EditText(this@MainActivity).apply {
                        hint = "https://filters.example.com"
                        setHintTextColor(muted)
                        setTextColor(white)
                        textSize = 13f
                        setSingleLine(true)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        background = shape(panel2, 11)
                        setText(FilterDistributionSettings.configuredBaseUrl(this@MainActivity))
                    }
                    addView(filterServerInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                    val filterServerFeedback = text(
                        "현재 주소: " + FilterDistributionSettings.configuredBaseUrl(this@MainActivity)
                            .ifBlank { "미설정 · 서명 배포 서버 연결 전에는 기존 필터 공급원을 사용합니다." },
                        10f, muted, false
                    ).withTop(7) as TextView
                    addView(filterServerFeedback)
                    addView(Button(this@MainActivity).apply {
                        text = "필터 서버 주소 저장"
                        isAllCaps = false
                        setTextColor(bg)
                        background = shape(accent, 12)
                        setOnClickListener {
                            try {
                                FilterDistributionSettings.saveBaseUrl(this@MainActivity, filterServerInput.text.toString())
                                filterServerFeedback.text = "저장 완료 · 다음 필터 업데이트부터 새 HTTPS 서버와 앱 내 서명 공개키를 사용합니다."
                            } catch (error: Exception) {
                                filterServerFeedback.text = "필터 서버 설정 실패: ${SafeFailureSummary.describe(error)}"
                            }
                        }
                    }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
                    addView(Button(this@MainActivity).apply {
                        text = "빌드 기본 주소로 되돌리기"
                        isAllCaps = false
                        setTextColor(white)
                        background = shape(panel2, 12)
                        setOnClickListener {
                            FilterDistributionSettings.resetBaseUrl(this@MainActivity)
                            filterServerInput.setText(BuildConfig.FILTER_MANIFEST_BASE_URL)
                            filterServerFeedback.text = "빌드 기본 주소로 되돌렸습니다."
                        }
                    }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
                }.withTop(12))
        body.addView(text("DNS 업스트림 서버 · 암호화 연결", 18f, white, true).withTop(24))
                body.addView(text("차단되지 않은 DNS 질의를 전달할 서버를 선택합니다.", 12f, muted, false).withTop(5))
                val dnsPrefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                val knownDnsOptions = listOf(
                    "Cloudflare · IPv4 · 1.1.1.1" to "1.1.1.1",
                    "Cloudflare · IPv6 · 2606:4700:4700::1111" to "2606:4700:4700::1111",
                    "Google · IPv4 · 8.8.8.8" to "8.8.8.8",
                    "Google · IPv6 · 2001:4860:4860::8888" to "2001:4860:4860::8888",
                    "Quad9 · IPv4 · 9.9.9.9" to "9.9.9.9",
                    "Quad9 · IPv6 · 2620:fe::fe" to "2620:fe::fe",
                    "AdGuard DNS · IPv4 · 광고/추적 차단" to "94.140.14.14",
                    "AdGuard DNS · IPv6 · 광고/추적 차단" to "2a10:50c0::ad1:ff",
                    "AdGuard DNS 가족 보호 · IPv4 · 성인 콘텐츠 차단" to "94.140.14.15",
                    "AdGuard DNS 가족 보호 · IPv6 · 성인 콘텐츠 차단" to "2a10:50c0::bad1:ff"
                )
                val currentDns = dnsPrefs.getString(GuardianVpnService.KEY_UPSTREAM_DNS, GuardianVpnService.DEFAULT_UPSTREAM_DNS)
                    ?: GuardianVpnService.DEFAULT_UPSTREAM_DNS
                var tlsHostInput: EditText? = null
                val dnsOptions = knownDnsOptions.toMutableList()
                if (knownDnsOptions.none { it.second == currentDns }) {
                    dnsOptions.add(0, "사용자 지정 · $currentDns" to currentDns)
                }
                val dnsSpinner = Spinner(this)
                val dnsAdapter = darkSpinnerAdapter(dnsOptions.map { it.first })
                dnsSpinner.background = shape(panel2, 12)
                dnsSpinner.setPadding(dp(8), dp(3), dp(8), dp(3))
                dnsSpinner.setPopupBackgroundDrawable(shape(panel, 12))
                dnsSpinner.adapter = dnsAdapter
                val selectedDns = dnsOptions.indexOfFirst { it.second == currentDns }.coerceAtLeast(0)
                dnsSpinner.setSelection(selectedDns)
                dnsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedAddress = dnsOptions[position].second
                        val previousAddress = dnsPrefs.getString(
                            GuardianVpnService.KEY_UPSTREAM_DNS,
                            GuardianVpnService.DEFAULT_UPSTREAM_DNS
                        )
                        // Spinner emits an initial selection when Settings is rendered.
                        // Do not overwrite a user's custom TLS hostname or reload filter
                        // rules unless the resolver address actually changed.
                        if (previousAddress != selectedAddress) {
                            dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_DNS, selectedAddress).apply()
                            defaultTlsHostForDns(selectedAddress)?.let { host ->
                                dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_TLS_HOST, host).apply()
                                tlsHostInput?.setText(host)
                            }
                        }
                    }
                }
                body.addView(card(panel, 14).apply {
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    addView(dnsSpinner)
                }.withTop(10))
                val transportOptions = listOf(
                    "UDP · 일반 DNS/53" to "udp",
                    "TCP · DNS/53" to "tcp",
                    "DNS-over-TLS · 암호화/853" to "dot",
                    "DNS-over-HTTPS · 암호화/443" to "doh"
                )
                val currentTransport = dnsPrefs.getString(
                    GuardianVpnService.KEY_UPSTREAM_TRANSPORT,
                    GuardianVpnService.DEFAULT_UPSTREAM_TRANSPORT
                ) ?: GuardianVpnService.DEFAULT_UPSTREAM_TRANSPORT
                val transportSpinner = Spinner(this).apply {
                    background = shape(panel2, 12)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    setPopupBackgroundDrawable(shape(panel, 12))
                    adapter = darkSpinnerAdapter(transportOptions.map { it.first })
                    setSelection(transportOptions.indexOfFirst { it.second == currentTransport }.coerceAtLeast(0))
                }
                body.addView(text("암호화 DNS · 업스트림 전송 방식", 15f, white, true).withTop(16))
                body.addView(card(panel, 14).apply {
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    addView(transportSpinner)
                }.withTop(8))
        
                val currentTlsHost = dnsPrefs.getString(
                    GuardianVpnService.KEY_UPSTREAM_TLS_HOST,
                    defaultTlsHostForDns(currentDns) ?: GuardianVpnService.DEFAULT_UPSTREAM_TLS_HOST
                ) ?: GuardianVpnService.DEFAULT_UPSTREAM_TLS_HOST
                tlsHostInput = EditText(this).apply {
                    hint = "TLS 인증서 호스트 이름 (예: cloudflare-dns.com)"
                    setHintTextColor(muted)
                    setTextColor(white)
                    textSize = 13f
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    background = shape(panel2, 12)
                    setText(currentTlsHost)
                    visibility = if (currentTransport == "dot" || currentTransport == "doh") View.VISIBLE else View.GONE
                }
                body.addView(tlsHostInput!!, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                val tlsHostFeedback = text(
                    "DoT/DoH는 TLS 인증서와 호스트 이름을 검증합니다. 사용자 지정 DNS IP를 선택했다면 해당 IP에 맞는 인증서 호스트를 입력하세요.",
                    10f, muted, false
                ).withTop(6) as TextView
                tlsHostFeedback.visibility = if (currentTransport == "dot" || currentTransport == "doh") View.VISIBLE else View.GONE
                body.addView(tlsHostFeedback)
                val tlsHostSaveButton = Button(this).apply {
                    text = "TLS 호스트 저장"
                    isAllCaps = false
                    setTextColor(white)
                    background = shape(panel2, 12)
                    visibility = if (currentTransport == "dot" || currentTransport == "doh") View.VISIBLE else View.GONE
                    setOnClickListener {
                        val normalized = DnsTlsHostValidator.normalize(tlsHostInput?.text?.toString().orEmpty())
                        if (normalized == null) {
                            tlsHostFeedback.text = "올바른 DNS 호스트 이름을 입력하세요. IP 주소나 밑줄은 사용할 수 없습니다."
                            tlsHostFeedback.setTextColor(red)
                        } else {
                            dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_TLS_HOST, normalized).apply()
                            tlsHostInput?.setText(normalized)
                            tlsHostFeedback.text = "TLS 인증서 호스트 저장 완료: $normalized"
                            tlsHostFeedback.setTextColor(accent)
                            if (ProtectionState.isActive(
                                    dnsPrefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                                    GuardianVpnService.isTunnelActive()
                                )) {
                                startService(Intent(this@MainActivity, GuardianVpnService::class.java)
                                    .setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                            }
                        }
                    }
                }
                body.addView(tlsHostSaveButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
                transportSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val mode = transportOptions[position].second
                        val previous = dnsPrefs.getString(
                            GuardianVpnService.KEY_UPSTREAM_TRANSPORT,
                            GuardianVpnService.DEFAULT_UPSTREAM_TRANSPORT
                        )
                        if (previous != mode) {
                            dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_TRANSPORT, mode).apply()
                            val showTls = mode == "dot" || mode == "doh"
                            tlsHostInput?.visibility = if (showTls) View.VISIBLE else View.GONE
                            tlsHostFeedback.visibility = if (showTls) View.VISIBLE else View.GONE
                            tlsHostSaveButton.visibility = if (showTls) View.VISIBLE else View.GONE
                            if (ProtectionState.isActive(
                                    dnsPrefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                                    GuardianVpnService.isTunnelActive()
                                )) {
                                startService(Intent(this@MainActivity, GuardianVpnService::class.java)
                                    .setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                            }
                        }
                    }
                }
        
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
                            defaultTlsHostForDns(normalized)?.let { host ->
                                dnsPrefs.edit().putString(GuardianVpnService.KEY_UPSTREAM_TLS_HOST, host).apply()
                                tlsHostInput?.setText(host)
                            }
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
                // Keep the device-wide compatibility option ahead of the large installed-app
                // inventory so it remains discoverable without scrolling through every app.
                buildCompatibilityPauseSection(body)
                body.addView(text("현재 DNS 필터는 UDP/53 및 TCP/53 요청을 처리합니다. 업스트림은 UDP/TCP, DNS-over-TLS 또는 DNS-over-HTTPS를 선택할 수 있습니다. 앱 자체 DoH/DoT 우회 요청, IPv6 확장 헤더·IP 조각화, 앱 내부 광고는 필터링하지 못할 수 있습니다.", 12f, muted, false).withTop(14))
    }

    private fun buildSecurityProfilePreview(): LinearLayout {
        val active = SecurityProfilePolicy.profile(SecurityProfileStore.activeProfileId(this))
        return card(panel, 16).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("보안 프로필 자동 전환 · 개발 미리보기", 15f, white, true))
            addView(text(
                "네트워크의 연결 유형·검증 상태·종량제 여부를 확인해 보호 필터 프로필을 전환합니다. SSID/BSSID는 저장하지 않으며 알 수 없는 Wi-Fi는 공용 네트워크로 취급합니다.",
                10f, muted, false
            ).withTop(5))
            val autoSwitch = CheckBox(this@MainActivity).apply {
                text = "보호 중 네트워크 변경 시 프로필 자동 전환"
                setTextColor(white)
                buttonTintList = ColorStateList.valueOf(accent)
                isChecked = SecurityProfileStore.automaticSwitchEnabled(this@MainActivity)
                setOnCheckedChangeListener { _, checked ->
                    if (syncingSecurityProfileSwitch) return@setOnCheckedChangeListener
                    SecurityProfileStore.setAutomaticSwitchEnabled(this@MainActivity, checked)
                    SecurityProfileMonitor.refresh(this@MainActivity)
                    refreshSecurityProfilePreview()
                    Toast.makeText(
                        this@MainActivity,
                        if (checked) "보호가 실행 중이면 네트워크 상태에 따라 프로필을 전환합니다."
                        else "자동 프로필 전환을 중지했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            securityProfileAutoSwitch = autoSwitch
            addView(autoSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

            addView(text("수동 보안 프로필", 13f, white, true).withTop(16))
            addView(text(
                "자동 전환을 끄고 보호 정책을 직접 선택합니다. 수동 적용은 다음에 네트워크가 바뀌어도 유지됩니다.",
                10f, muted, false
            ).withTop(4))
            val profileIds = SecurityProfilePolicy.Id.values().toList()
            securityProfileIds = profileIds
            var selectedManualProfile = SecurityProfileStore.activeProfileId(this@MainActivity)
            val profileSpinner = Spinner(this@MainActivity).apply {
                adapter = darkSpinnerAdapter(profileIds.map {
                    SecurityProfilePolicy.profile(it).label
                })
                background = shape(panel2, 10)
                setPopupBackgroundDrawable(shape(panel, 10))
                setSelection(profileIds.indexOf(selectedManualProfile).coerceAtLeast(0))
            }
            addView(profileSpinner, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(8)
            })
            securityProfileSpinner = profileSpinner
            val profileStatus = text(
                "현재 적용 프로필: ${active.label}",
                11f, accent, true
            ).withTop(8) as TextView
            securityProfileStatusText = profileStatus
            profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    selectedManualProfile = profileIds[position]
                }
            }
            addView(Button(this@MainActivity).apply {
                text = "선택한 보안 프로필 적용"
                isAllCaps = false
                setTextColor(bg)
                background = shape(accent, 9)
                setOnClickListener {
                    SecurityProfileStore.applyManualProfile(this@MainActivity, selectedManualProfile)
                    refreshSecurityProfilePreview()
                    Toast.makeText(
                        this@MainActivity,
                        "${selected.label} 보안 프로필을 적용했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
            addView(profileStatus)
            val updatePolicyStatus = text("", 10f, muted, false).withTop(4) as TextView
            securityProfileUpdateStatusText = updatePolicyStatus
            addView(updatePolicyStatus)
            addView(text(
                "제한: 로밍 자동 감지와 필터 자동 다운로드 스케줄러는 아직 연결되지 않았습니다. 이 개발 미리보기는 PRO 권한을 부여하지 않으며 Release 빌드에서는 자동 전환이 비활성입니다.",
                10f, Color.rgb(238, 176, 82), false
            ).withTop(8))
        }
        refreshSecurityProfilePreview()
        return card
    }

    /** Keep the visible preview consistent with service-driven network profile changes. */
    private fun refreshSecurityProfilePreview() {
        if (!BuildConfig.DEBUG) return
        val switchView = securityProfileAutoSwitch ?: return
        val statusView = securityProfileStatusText ?: return
        val updateView = securityProfileUpdateStatusText ?: return
        val automatic = SecurityProfileStore.automaticSwitchEnabled(this)
        if (switchView.isChecked != automatic) {
            syncingSecurityProfileSwitch = true
            switchView.isChecked = automatic
            syncingSecurityProfileSwitch = false
        }
        val activeId = SecurityProfileStore.activeProfileId(this)
        val activeProfile = SecurityProfilePolicy.profile(activeId)
        val mode = if (automatic) "자동 전환" else "수동/고정"
        statusView.text = "현재 적용 프로필: ${activeProfile.label} · $mode"
        val profileIds = securityProfileIds.orEmpty()
        val spinner = securityProfileSpinner
        val selectedIndex = profileIds.indexOf(activeId)
        if (spinner != null && selectedIndex >= 0 && spinner.selectedItemPosition != selectedIndex) {
            spinner.setSelection(selectedIndex)
        }
        updateView.text = "요금제 연결 자동 필터 업데이트 허용 정책: " +
            if (SecurityProfileStore.automaticUpdatesAllowed(this)) "허용" else "제한"
    }

    private fun buildSettingsPremium(body: LinearLayout) {
        val releasableLicense = LicenseEntitlementStore.currentForRefresh(this)
        buildPremium(body)
                body.addView(card(panel, 14).apply {
                    setPadding(dp(15), dp(15), dp(15), dp(15))
                    addView(text("운영 서버 주소 (노트북)", 15f, white, true))
                    addView(text(
                        "노트북에서 Guardian 운영서버를 실행한 뒤 HTTPS 주소를 입력하세요. 주소가 바뀌면 여기서 변경할 수 있습니다.",
                        11f, muted, false
                    ).withTop(5))
                    val serverUrlInput = EditText(this@MainActivity).apply {
                        hint = "https://guardian.example.com"
                        setHintTextColor(muted)
                        setTextColor(white)
                        textSize = 13f
                        setSingleLine(true)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        background = shape(panel2, 11)
                        setText(LicenseApiClient.configuredBaseUrl(this@MainActivity))
                    }
                    addView(serverUrlInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                    val serverFeedback = text(
                        "현재 주소: " + LicenseApiClient.configuredBaseUrl(this@MainActivity).ifBlank { "미설정 (운영서버 미연결)" },
                        10f, muted, false
                    ).withTop(7) as TextView
                    addView(serverFeedback)
                    addView(Button(this@MainActivity).apply {
                        text = "운영 서버 주소 저장"
                        isAllCaps = false
                        setTextColor(bg)
                        background = shape(accent, 12)
                        setOnClickListener {
                            try {
                                LicenseApiClient.saveBaseUrl(this@MainActivity, serverUrlInput.text.toString())
                                LicenseRefreshScheduler.sync(this@MainActivity)
                                serverFeedback.text = "저장 완료 · 라이선스 자동 갱신 일정에 새 서버 주소를 반영했습니다."
                            } catch (error: Exception) {
                                serverFeedback.text = "운영 서버 설정 실패: ${SafeFailureSummary.describe(error)}"
                            }
                        }
                    }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
                    addView(Button(this@MainActivity).apply {
                        text = "빌드 기본 주소로 되돌리기"
                        isAllCaps = false
                        setTextColor(white)
                        background = shape(panel2, 12)
                        setOnClickListener {
                            LicenseApiClient.resetBaseUrl(this@MainActivity)
                            LicenseRefreshScheduler.sync(this@MainActivity)
                            serverUrlInput.setText(BuildConfig.LICENSE_API_BASE_URL)
                            serverFeedback.text = "빌드 기본 주소로 되돌렸습니다."
                        }
                    }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
                }.withTop(12))
                body.addView(card(panel, 14).apply {
                    setPadding(dp(15), dp(15), dp(15), dp(15))
                    addView(text("PRO 라이선스 활성화", 15f, white, true))
                    addView(text(
                        "발급받은 라이선스 키를 입력하면 서버에서 기기 등록 후 서명 토큰을 발급합니다.",
                        11f, muted, false
                    ).withTop(5))
                    val licenseKeyInput = EditText(this@MainActivity).apply {
                        hint = "GDN-XXXX-XXXX-..."
                        contentDescription = "PRO 라이선스 활성화 코드 입력"
                        setHintTextColor(muted)
                        setTextColor(white)
                        textSize = 13f
                        setSingleLine(true)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        background = shape(panel2, 11)
                    }
                    this@MainActivity.licenseKeyInput = licenseKeyInput
                    addView(licenseKeyInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
                    val licenseFeedback = text(
                        if (LicenseApiClient.configuredBaseUrl(this@MainActivity).isBlank()) "운영 라이선스 서버 주소가 아직 설정되지 않았습니다."
                        else "라이선스 키는 서버에서 확인하며 앱에는 원문을 저장하지 않습니다.",
                        10f, muted, false
                    ).withTop(8) as TextView
                    addView(licenseFeedback)
                    val activateButton = Button(this@MainActivity).apply {
                        text = "라이선스 활성화"
                        isAllCaps = false
                        setTextColor(bg)
                        background = shape(accent, 12)
                        setOnClickListener {
                            val key = licenseKeyInput.text.toString().trim()
                            if (key.isBlank()) {
                                licenseFeedback.text = "라이선스 키를 입력하세요."
                                return@setOnClickListener
                            }
                            isEnabled = false
                            licenseFeedback.text = "서버에 라이선스 활성화를 요청하고 있습니다."
                            thread {
                                try {
                                    LicenseApiClient.activate(this@MainActivity, key, BuildConfig.VERSION_NAME)
                                    runOnUiThread { showTab("settings-premium") }
                                } catch (error: Exception) {
                                    runOnUiThread {
                                        licenseFeedback.text = "라이선스 활성화 실패: ${SafeFailureSummary.describe(error)}"
                                        isEnabled = true
                                    }
                                }
                            }
                        }
                    }
                    addView(activateButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
                    if (releasableLicense != null) {
                        val refreshButton = Button(this@MainActivity).apply {
                            text = "라이선스 상태 갱신"
                            isAllCaps = false
                            setTextColor(white)
                            background = shape(panel2, 12)
                            setOnClickListener {
                                isEnabled = false
                                licenseFeedback.text = "서버에서 라이선스 상태를 확인하고 있습니다."
                                thread {
                                    try {
                                        LicenseApiClient.refresh(this@MainActivity)
                                        runOnUiThread { showTab("settings-premium") }
                                    } catch (error: Exception) {
                                        runOnUiThread {
                                            if (error is LicenseApiException && error.invalidatesEntitlement) {
                                                // Rebuild settings after the local signed token was cleared.
                                                showTab("settings-premium")
                                            } else {
                                                licenseFeedback.text = "라이선스 갱신 실패: ${SafeFailureSummary.describe(error)}"
                                                isEnabled = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        addView(refreshButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
                        val deactivateButton = Button(this@MainActivity).apply {
                            text = "이 기기 라이선스 해제"
                            isAllCaps = false
                            setTextColor(white)
                            background = shape(panel2, 12)
                            setOnClickListener {
                                isEnabled = false
                                thread {
                                    try {
                                        LicenseApiClient.deactivate(this@MainActivity)
                                        runOnUiThread { showTab("settings-premium") }
                                    } catch (error: Exception) {
                                        runOnUiThread {
                                            licenseFeedback.text = "기기 등록 해제 실패: ${SafeFailureSummary.describe(error)}"
                                            isEnabled = true
                                        }
                                    }
                                }
                            }
                        }
                        addView(deactivateButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
                    }
                }.withTop(12))
    }

    private fun buildSettingsAbout(body: LinearLayout) {
        body.addView(text("정보", 25f, white, true))
        body.addView(infoRow("앱 버전", "Guardian AdBlock", BuildConfig.VERSION_NAME, accent).withTop(12))
        body.addView(infoRow("버전 코드", "Android 업데이트 식별 번호", BuildConfig.VERSION_CODE.toString(), accent))
        body.addView(infoRow("데이터 저장", "사용자 규칙 및 설정 저장 위치", "기기 내부", accent))
        body.addView(infoRow("개인정보", "외부 계정 및 분석 SDK 미사용", "로컬 우선", accent))
        body.addView(Button(this).apply {
            text = "앱 업데이트 확인"
            isAllCaps = false
            setTextColor(bg)
            background = shape(accent, 12)
            contentDescription = "Guardian 앱 업데이트 확인 및 설치"
            setOnClickListener { showTab("updates") }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        body.addView(text("앱 업데이트는 여기에서 확인합니다. 필터 목록 갱신은 설정 > 필터링에서 별도로 관리합니다.", 11f, muted, false).withTop(7))
        body.addView(text("정책·오픈소스 및 라이선스 고지는 배포 문서와 함께 관리합니다.", 11f, muted, false).withTop(12))
                body.addView(card(panel, 14).apply {
                    setPadding(dp(15), dp(13), dp(15), dp(13))
                    addView(text("제작자 및 개발사", 13f, white, true))
                    addView(text("(주)유진컨설팅", 13f, accent, true).withTop(8))
                    addView(text("제작자: 이학범", 12f, white, false).withTop(4))
                    addView(text("연락처: 010-5201-3121", 12f, muted, false).withTop(3))
                }.withTop(12))
                val verifiedLicense = LicenseEntitlementStore.current(this)
                // Keep an expired-but-valid signed token available for device-slot release.
                // It must never be presented as an active Pro entitlement.
                val releasableLicense = LicenseEntitlementStore.currentForRefresh(this)
                body.addView(infoRow(
                    "라이선스",
                    when {
                        verifiedLicense != null -> "서버 서명 및 이 기기 등록을 확인했습니다"
                        releasableLicense != null -> "라이선스가 만료되었습니다 · 이 기기 등록 해제 가능"
                        else -> "서명 라이선스 미등록 · 무료 보호 기능 사용 중"
                    },
                    if (verifiedLicense != null) "PRO" else "FREE",
                    if (verifiedLicense != null) accent else muted
                ))
    }


    private fun buildSupportCenterCard(): View {
        return card(panel, 14).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(text("고객지원 센터", 16f, white, true))
            addView(text(
                "자주 묻는 질문을 확인하고, 기능 제안·오류·결제·라이선스 문의를 접수하거나 답변을 조회할 수 있습니다.",
                11f, muted, false
            ).withTop(5))
            addView(text(
                "문의 접수 시 입력한 내용과 선택 이메일은 답변 처리를 위해 서버로 전송됩니다. 진단 정보는 별도 동의 없이는 전송하지 않습니다.",
                11f, muted, false
            ).withTop(6))
            addView(Button(this@MainActivity).apply {
                text = "자주 묻는 질문 (Q&A)"
                isAllCaps = false
                setTextColor(white)
                background = shape(panel2, 11)
                setOnClickListener { showSupportFaq() }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
            addView(Button(this@MainActivity).apply {
                text = "문의 및 의견 보내기"
                isAllCaps = false
                setTextColor(bg)
                background = shape(accent, 11)
                setOnClickListener { showSupportSubmitDialog() }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
            addView(Button(this@MainActivity).apply {
                text = "내 문의 답변 확인"
                isAllCaps = false
                setTextColor(white)
                background = shape(panel2, 11)
                setOnClickListener { showSupportLookupDialog() }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
    }

    private fun showSupportFaq() {
        val questions = arrayOf(
            "광고 차단이 되지 않아요",
            "VPN 보호가 연결되지 않아요",
            "특정 앱을 보호에서 제외하고 싶어요",
            "Samsung Wallet에서 VPN 경고가 나와요",
            "필터 업데이트는 어떻게 하나요?",
            "Pro 라이선스는 어떻게 활성화하나요?",
            "기기 변경 또는 라이선스 만료"
        )
        val answers = arrayOf(
            "Guardian은 DNS 기반 광고·추적 도메인을 차단합니다. 앱 내부 광고, 자체 DNS 암호화(DoH/DoT), 이미 내려받은 콘텐츠 광고는 차단되지 않을 수 있습니다. 보호 상태와 필터 업데이트 시각을 확인하세요.",
            "Android VPN 권한을 허용하고 다른 VPN 앱과 충돌하는지 확인하세요. 계속 실패하면 오류 신고에 보호 진단 정보를 직접 첨부해 주세요.",
            "설정의 앱 제외에서 앱 이름 또는 패키지명을 검색하고 선택한 뒤 변경사항 저장을 누르세요. 저장 후 VPN에 적용된 앱 수와 실패 수를 확인하세요.",
            "앱별 트래픽 제외와 기기 전체 VPN 연결 해제는 다릅니다. 금융 앱이 VPN 연결 자체를 감지하면 자동 일시중지 호환성 설정을 사용해야 할 수 있으며, 일시중지 중에는 다른 앱도 VPN DNS 보호를 받지 못합니다.",
            "필터 규칙 또는 보호 설정에서 필터 업데이트를 실행하세요. 자동 업데이트는 설정한 주기에 따라 동작합니다.",
            "프리미엄 화면에서 발급받은 라이선스 키를 입력하고 활성화하세요. 활성화에는 운영 라이선스 서버 연결이 필요합니다.",
            "라이선스 관리자는 등록 기기를 해제할 수 있습니다. 만료·정지된 키는 관리자에게 갱신 또는 상태 확인을 요청하세요."
        )
        AlertDialog.Builder(this)
            .setTitle("Guardian 자주 묻는 질문")
            .setItems(questions) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle(questions[which])
                    .setMessage(answers[which])
                    .setPositiveButton("확인", null)
                    .show()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showSupportSubmitDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val categories = listOf(
            "질문 / Q&A" to "question",
            "오류 신고" to "bug",
            "기능 제안 / 의견" to "suggestion",
            "결제 문의" to "billing",
            "라이선스 문의" to "license"
        )
        val category = Spinner(this).apply {
            adapter = darkSpinnerAdapter(categories.map { it.first })
            background = shape(panel2, 10)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        fun field(hintText: String, multiline: Boolean = false): EditText = EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(panel2, 10)
            if (multiline) {
                minLines = 4
                maxLines = 7
                gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT
            }
        }
        val subject = field("제목 (3~120자)")
        val email = field("연락 이메일 (선택)")
        val message = field("문의 또는 의견을 자세히 입력하세요 (10~5,000자)", true)
        form.addView(text("문의 유형", 11f, muted, true))
        form.addView(category, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(5) })
        form.addView(subject, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(9) })
        form.addView(email, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        form.addView(message, LinearLayout.LayoutParams(-1, dp(140)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("문의 및 의견 보내기")
            .setView(form)
            .setNegativeButton("취소", null)
            .setPositiveButton("접수", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = subject.text.toString().trim()
                val body = message.text.toString().trim()
                if (title.length !in 3..120 || body.length !in 10..5000) {
                    android.widget.Toast.makeText(this, "제목은 3~120자, 내용은 10~5,000자로 입력하세요.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submit.isEnabled = false
                submit.text = "접수 중…"
                val selectedCategory = categories[category.selectedItemPosition].second
                thread(name = "guardian-support-submit") {
                    val result = runCatching {
                        LicenseApiClient.submitSupportTicket(
                            this@MainActivity, selectedCategory, title, body, email.text.toString().trim()
                        )
                    }
                    runOnUiThread {
                        result.onSuccess { response ->
                            val receipt = "접수 번호: ${response.optString("ticketRef")}\n조회 코드: ${response.optString("accessToken")}\n\n이 정보는 다시 표시되지 않으므로 안전한 곳에 보관하세요."
                            dialog.dismiss()
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("문의 접수 완료")
                                .setMessage(receipt)
                                .setPositiveButton("접수 정보 복사") { _, _ ->
                                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Guardian 문의 접수 정보", receipt))
                                    android.widget.Toast.makeText(this@MainActivity, "접수 정보가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("닫기", null)
                                .show()
                        }.onFailure { error ->
                            submit.isEnabled = true
                            submit.text = "접수"
                            android.widget.Toast.makeText(this@MainActivity, "문의 접수 실패: ${SafeFailureSummary.describe(error)}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showSupportLookupDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun input(hintText: String): EditText = EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            setSingleLine(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(panel2, 10)
        }
        val ticketRef = input("접수 번호 (GDN-...)")
        val accessToken = input("접수 시 발급된 조회 코드")
        form.addView(ticketRef)
        form.addView(accessToken, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("내 문의 답변 확인")
            .setView(form)
            .setNegativeButton("취소", null)
            .setPositiveButton("조회", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (ticketRef.text.isNullOrBlank() || accessToken.text.isNullOrBlank()) {
                    android.widget.Toast.makeText(this, "접수 번호와 조회 코드를 입력하세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                button.text = "조회 중…"
                thread(name = "guardian-support-lookup") {
                    val result = runCatching {
                        LicenseApiClient.lookupSupportTicket(this@MainActivity, ticketRef.text.toString(), accessToken.text.toString())
                    }
                    runOnUiThread {
                        result.onSuccess { response ->
                            val ticket = response.optJSONObject("ticket")
                            val details = if (ticket == null) "문의 정보를 읽을 수 없습니다." else
                                "상태: ${ticket.optString("status")}\n유형: ${ticket.optString("category")}\n제목: ${ticket.optString("subject")}\n\n접수 내용:\n${ticket.optString("message")}\n\n답변:\n${ticket.optString("admin_reply").ifBlank { "아직 답변이 등록되지 않았습니다." }}"
                            dialog.dismiss()
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("문의 처리 현황")
                                .setMessage(details)
                                .setPositiveButton("확인", null)
                                .show()
                        }.onFailure { error ->
                            button.isEnabled = true
                            button.text = "조회"
                            android.widget.Toast.makeText(this@MainActivity, "문의 조회 실패: ${SafeFailureSummary.describe(error)}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun defaultTlsHostForDns(address: String): String? = when (address.lowercase()) {
        "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001" -> "cloudflare-dns.com"
        "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844" -> "dns.google"
        "9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9" -> "dns.quad9.net"
        "94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff" -> "dns.adguard-dns.com"
        "94.140.14.15", "94.140.15.16", "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff" -> "family.dns.adguard.com"
        else -> null
    }

    private fun bottomNav(selected: String, asRail: Boolean = false): View {
        val horizontal = !asRail
        val nav = LinearLayout(this).apply {
            orientation = if (asRail) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15, 23, 39))
            setPadding(if (asRail) dp(7) else dp(4), dp(6), if (asRail) dp(7) else dp(4), dp(6))
            if (horizontal) minimumHeight = dp(72)
            // Keep labels above Android's gesture/navigation area on edge-to-edge devices.
            if (horizontal) {
                setOnApplyWindowInsetsListener { view, insets ->
                    val bottomInset = if (Build.VERSION.SDK_INT >= 30) {
                        insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    } else {
                        @Suppress("DEPRECATION")
                        insets.systemWindowInsetBottom
                    }
                    view.setPadding(dp(4), dp(8), dp(4), dp(8) + bottomInset)
                    insets
                }
            }
        }
        val items = listOf(
            Triple("home", "⌂", "홈"),
            Triple("protection", "◈", "보호"),
            Triple("app-exclusions", "▣", "앱 제외"),
            Triple("activity", "▤", "활동"),
            Triple("settings", "⚙", "설정")
        )
        items.forEach { (id, icon, label) ->
            val active = selected == id
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(if (asRail) dp(4) else dp(2), if (asRail) dp(12) else dp(5), if (asRail) dp(4) else dp(2), if (asRail) dp(12) else dp(5))
                contentDescription = label
                isSelected = active
                setOnClickListener { navigateToTab(id) }
                if (active) background = shape(Color.rgb(31, 54, 76), 14)
            }
            item.addView(text(icon, if (asRail) 24f else 20f, if (active) accent else muted, true).apply { gravity = Gravity.CENTER })
            item.addView(text(label, if (asRail) 11f else 10f, if (active) white else muted, active).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
                maxLines = 1
                includeFontPadding = false
            })
            if (asRail) {
                nav.addView(item, LinearLayout.LayoutParams(-1, dp(68)))
            } else {
                nav.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f))
            }
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
        selfTestText?.text = "DNS 규칙 엔진 테스트 실행 중…"
        thread(name = "guardian-dns-self-test") {
            try {
                val result = DnsProbe.evaluateRules()
                val message = if (result.passed) {
                    "규칙 엔진 통과 · ${result.blockedDomain} 차단 규칙 일치 및 NXDOMAIN 생성 · ${result.normalDomain} 정상 허용"
                } else {
                    "실패 · 차단 일치=${result.blockedRuleMatched}, RCODE=${result.blockedResponseCode}, 정상 도메인 차단=${result.normalDomainBlocked}"
                }
                runOnUiThread {
                    selfTestText?.text = message
                    selfTestText?.setTextColor(if (result.passed) accent else red)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    selfTestText?.text = "실패 · ${SafeFailureSummary.codeFor(e)}"
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
            prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true),
            FilterListManager.hasCachedAndroidAdsList(this)
        )
    }

    private fun buildFilterOptions(): View {
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val autoUpdateCheck = CheckBox(this).apply {
            applyToggleTint(this)
            applyToggleTint(this)
            text = "백그라운드에서 필터 자동 업데이트"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(FilterListManager.KEY_AUTO_UPDATE, checked).apply()
                FilterUpdateScheduler.sync(this@MainActivity)
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
        val intervalAdapter = darkSpinnerAdapter(intervals.map { it.first })
        intervalSpinner.background = shape(panel2, 12)
        intervalSpinner.setPadding(dp(8), dp(3), dp(8), dp(3))
        intervalSpinner.setPopupBackgroundDrawable(shape(panel, 12))
        intervalSpinner.adapter = intervalAdapter
        val currentInterval = prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, 24L * 60L * 60L * 1000L)
        intervalSpinner.setSelection(
            FilterUpdateScheduler.selectionIndex(currentInterval, intervals.map { it.second }, defaultIndex = 2)
        )
        // Spinner may emit the initial selection while the filters screen is being rendered.
        // Only persist and reschedule when the user actually changes the interval.
        var selectedInterval = currentInterval
        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val nextInterval = intervals[position].second
                if (nextInterval == selectedInterval) return
                selectedInterval = nextInterval
                prefs.edit().putLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, nextInterval).apply()
                FilterUpdateScheduler.sync(this@MainActivity)
            }
        }
        options.addView(intervalSpinner)
        options.addView(text(
            "설정한 주기에 따라 백그라운드에서 네트워크 연결 시 자동 갱신합니다. 앱 실행 시에도 만료된 목록을 확인합니다.",
            10f, muted, false
        ).withTop(3))
        val securityCheck = CheckBox(this).apply {
            applyToggleTint(this)
            applyToggleTint(this)
            text = "악성코드·피싱 보호 (HaGeZi Threat Intelligence · 전체 사용자)"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                if (syncingFilterOptions) return@setOnCheckedChangeListener
                prefs.edit().putBoolean(FilterListManager.KEY_SECURITY_ENABLED, checked).apply()
                if (checked && !FilterListManager.hasCachedSecurityList(this@MainActivity)) {
                    filterStatusText?.text = "보안 필터 목록을 다운로드합니다."
                    startFilterUpdate()
                } else {
                    // Apply both enabling and disabling immediately when protection is already active.
                    reloadProtectionRulesIfActive(prefs)
                    filterStatusText?.text = filterStatusDescription()
                }
            }
        }
        securityCheck.isEnabled = !filterUpdateRunning.get()
        securityFilterCheckBox = securityCheck
        options.addView(securityCheck)
        val androidAdsCheck = CheckBox(this).apply {
            applyToggleTint(this)
            applyToggleTint(this)
            text = "Android 앱 광고 SDK 차단 (AWAvenue Ads Rule)"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            setOnCheckedChangeListener { _, checked ->
                if (syncingFilterOptions) return@setOnCheckedChangeListener
                prefs.edit().putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, checked).apply()
                if (checked && !FilterListManager.hasCachedAndroidAdsList(this@MainActivity)) {
                    filterStatusText?.text = "Android 앱 광고 SDK 필터를 다운로드합니다."
                    startFilterUpdate()
                } else {
                    if (ProtectionState.isActive(
                            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                            GuardianVpnService.isTunnelActive()
                        )) {
                        startService(Intent(this@MainActivity, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                    }
                    filterStatusText?.text = filterStatusDescription()
                }
            }
        }
        androidAdsCheck.isEnabled = !filterUpdateRunning.get()
        androidAdsFilterCheckBox = androidAdsCheck
        options.addView(androidAdsCheck)
        options.addView(text(
            "Android 앱 광고 SDK 도메인을 추가로 차단합니다. DNS 수준 필터이므로 모든 앱 내부 광고가 제거되는 것은 아닙니다.",
            10f, muted, false
        ).withTop(2))
        val directDnsCheck = CheckBox(this).apply {
            applyToggleTint(this)
            applyToggleTint(this)
            text = "공용 DNS 직접 질의 포착 (고급 · 일부 비DNS 연결 영향 가능)"
            textSize = 12f
            setTextColor(white)
            isChecked = prefs.getBoolean(GuardianVpnService.KEY_DIRECT_DNS_CAPTURE, GuardianVpnService.DEFAULT_DIRECT_DNS_CAPTURE)
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
        val profileAdapter = darkSpinnerAdapter(filterProfiles.map { it.label })
        profileSpinner.background = shape(panel2, 12)
        profileSpinner.setPadding(dp(8), dp(3), dp(8), dp(3))
        profileSpinner.setPopupBackgroundDrawable(shape(panel, 12))
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

    private fun buildCompatibilityPauseSection(body: LinearLayout) {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val selected = prefs.getStringSet(AppForegroundPauseSettings.KEY_PACKAGES, emptySet()).orEmpty()
        val enabled = prefs.getBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
        val usageAccess = ForegroundAppMonitor.hasUsageAccess(this)

        val section = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(text("앱 실행 중 VPN 자동 일시중지", 15f, white, true))
            addView(
                text(
                    "선택한 앱이 화면에 표시되는 동안 Guardian VPN 연결을 기기 전체에서 일시 중지하고, 앱을 닫으면 자동으로 다시 연결합니다. 일시중지 중에는 다른 앱도 DNS 보호를 받지 못합니다.",
                    11f, muted, false
                ).withTop(6)
            )
        }

        val statusText = text("", 11f, muted, false).withTop(9) as TextView
        compatibilityStatusText = statusText
        section.addView(statusText)

        val accessButton = Button(this).apply {
            text = if (usageAccess) "사용 정보 접근 권한 허용됨" else "사용 정보 접근 권한 허용"
            setTextColor(if (usageAccess) muted else white)
            background = shape(panel2, 10)
            isEnabled = !usageAccess
            contentDescription = "사용 정보 접근 권한 설정"
            setOnClickListener { requestUsageAccessForCompatibilityPause() }
        }
        section.addView(accessButton, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(9)
        })

        val appsButton = Button(this).apply {
            text = "일시중지 앱 선택 (${selected.size}개)"
            setTextColor(white)
            background = shape(panel2, 10)
            contentDescription = "VPN 자동 일시중지 앱 선택"
            setOnClickListener { showCompatibilityPauseAppsDialog() }
        }
        compatibilityAppsButton = appsButton
        section.addView(appsButton, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(8)
        })

        val pauseSwitch = Switch(this).apply {
            text = "선택 앱 실행 중 VPN 전체 일시중지"
            contentDescription = "선택 앱 실행 중 VPN 자동 일시중지"
            setTextColor(white)
            textSize = 13f
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                if (syncingCompatibilitySwitch) return@setOnCheckedChangeListener
                if (!checked) {
                    prefs.edit()
                        .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                        .remove(AppForegroundPauseSettings.KEY_ERROR)
                        .commit()
                    notifyCompatibilitySettingsChanged()
                    updateCompatibilityPauseStatus()
                    return@setOnCheckedChangeListener
                }

                syncingCompatibilitySwitch = true
                isChecked = false
                syncingCompatibilitySwitch = false

                if (!ForegroundAppMonitor.hasUsageAccess(this@MainActivity)) {
                    requestUsageAccessForCompatibilityPause()
                    return@setOnCheckedChangeListener
                }
                val currentSelected = prefs.getStringSet(
                    AppForegroundPauseSettings.KEY_PACKAGES, emptySet()
                ).orEmpty()
                if (currentSelected.isEmpty()) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "먼저 VPN을 일시중지할 앱을 선택해 주세요.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setOnCheckedChangeListener
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("기기 전체 VPN 일시중지")
                    .setMessage(
                        "선택한 앱이 화면에 표시되는 동안 Guardian VPN 연결을 완전히 중지합니다. 이 시간에는 기기 전체가 DNS 보호를 받지 못합니다. 선택 앱을 닫으면 자동으로 다시 연결됩니다. 사용 중인 앱 이름은 현재 실행 앱 확인에만 기기 안에서 사용하며 사용 이력을 저장하거나 서버로 전송하지 않습니다."
                    )
                    .setNegativeButton("취소", null)
                    .setPositiveButton("활성화") { _, _ ->
                        val saved = prefs.edit()
                            .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, true)
                            .remove(AppForegroundPauseSettings.KEY_ERROR)
                            .commit()
                        if (!saved) {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "호환성 설정을 저장하지 못했습니다.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            syncingCompatibilitySwitch = true
                            compatibilityPauseSwitch?.isChecked = true
                            syncingCompatibilitySwitch = false
                            notifyCompatibilitySettingsChanged()
                        }
                        updateCompatibilityPauseStatus()
                    }
                    .show()
            }
        }
        compatibilityPauseSwitch = pauseSwitch
        section.addView(pauseSwitch, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
        })

        body.addView(section.withTop(12))
        updateCompatibilityPauseStatus()
    }

    private fun requestUsageAccessForCompatibilityPause() {
        AlertDialog.Builder(this)
            .setTitle("사용 정보 접근 권한")
            .setMessage(
                "Guardian은 선택한 앱이 현재 화면에 표시되는지만 확인해 VPN을 일시중지하거나 다시 연결합니다. 앱 사용 기록을 저장하거나 서버로 전송하지 않습니다. Android 설정에서 Guardian의 사용 정보 접근을 허용해야 합니다."
            )
            .setNegativeButton("취소", null)
            .setPositiveButton("설정 열기") { _, _ ->
                usageAccessSettingsOpened = true
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (error: Exception) {
                    usageAccessSettingsOpened = false
                    android.widget.Toast.makeText(
                        this,
                        "사용 정보 접근 설정을 열지 못했습니다: ${SafeFailureSummary.describe(error)}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun showCompatibilityPauseAppsDialog() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED, false)) {
            AlertDialog.Builder(this)
                .setTitle("설치 앱 목록 접근 안내")
                .setMessage(
                    "앱별 보호 제외 및 선택 앱 자동 VPN 일시중지 기능을 제공하기 위해 Guardian이 기기에 설치된 앱의 이름과 패키지명을 조회합니다. " +
                        "이 목록과 선택 설정은 이 기기에서만 사용하며 Guardian 서버로 전송하거나 광고·분석에 사용하지 않습니다. " +
                        "동의하면 두 기능의 앱 선택 목록을 표시합니다."
                )
                .setNegativeButton("취소", null)
                .setPositiveButton("동의하고 계속") { _, _ ->
                    val saved = prefs.edit()
                        .putBoolean(KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED, true)
                        .commit()
                    if (saved) {
                        showTab("settings")
                        showCompatibilityPauseAppsDialog()
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "동의 상태를 저장하지 못했습니다. 저장 공간을 확인해 주세요.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .show()
            return
        }
        val selected = prefs.getStringSet(
            AppForegroundPauseSettings.KEY_PACKAGES, emptySet()
        ).orEmpty().toMutableSet()
        var apps: List<Pair<String, String>> = emptyList()
        var inventoryReady = false
        var inventoryMessage = "설치 앱 목록을 불러오는 중…"
        var renderGeneration = 0
        val search = EditText(this).apply {
            hint = "앱 이름 또는 패키지명 검색"
            contentDescription = "VPN 자동 일시중지 앱 검색 입력"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            minHeight = dp(48)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = shape(panel2, 10)
        }
        val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val inventorySummary = text("설치 앱 목록을 불러오는 중…", 11f, muted, false)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(appList, ViewGroup.LayoutParams(-1, -2))
        }
        val retryButton = Button(this).apply {
            text = "앱 목록 다시 불러오기"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 10)
            isEnabled = false
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(search, LinearLayout.LayoutParams(-1, dp(52)))
            addView(inventorySummary, LinearLayout.LayoutParams(-1, dp(36)))
            addView(scroll, LinearLayout.LayoutParams(-1, dp(314)).apply {
                topMargin = dp(8)
            })
            addView(retryButton, LinearLayout.LayoutParams(-1, dp(42)).apply {
                topMargin = dp(8)
            })
        }
        lateinit var dialog: AlertDialog
        fun renderApps(queryText: String) {
            appList.removeAllViews()
            if (!inventoryReady) {
                appList.addView(text("설치 앱 목록을 불러오는 중…", 12f, muted, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return
            }
            if (apps.isEmpty()) {
                appList.addView(text(inventoryMessage, 12f, red, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return
            }
            val query = queryText.trim().lowercase(Locale.ROOT)
            val visible = apps.filter { (pkg, label) ->
                query.isEmpty() ||
                    pkg.lowercase(Locale.ROOT).contains(query) ||
                    label.lowercase(Locale.ROOT).contains(query)
            }
            val generation = ++renderGeneration
            var nextIndex = 0
            fun appendBatch() {
                if (generation != renderGeneration || !dialog.isShowing) return
                val batchEnd = minOf(nextIndex + 40, visible.size)
                for (index in nextIndex until batchEnd) {
                    val (pkg, label) = visible[index]
                    appList.addView(CheckBox(this).apply {
                        applyAppSelectionTint(this)
                        text = "$label\n$pkg"
                        contentDescription = "자동 일시중지 앱 $pkg"
                        textSize = 12f
                        setTextColor(white)
                        isChecked = pkg in selected
                        setPadding(dp(4), dp(6), dp(4), dp(6))
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) selected.add(pkg) else selected.remove(pkg)
                        }
                    })
                }
                nextIndex = batchEnd
                if (nextIndex < visible.size) appList.post { appendBatch() }
            }
            if (visible.isEmpty()) {
                appList.addView(text("검색 결과가 없습니다.", 12f, muted, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
            } else {
                appList.post { appendBatch() }
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        dialog = AlertDialog.Builder(this)
            .setTitle("VPN 자동 일시중지 앱 선택")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
            .create()
        dialog.setOnShowListener {
            // Force the entire app-picker surface to use the same dark palette as
            // its rows. AlertDialog otherwise inherits a light window background
            // on some Samsung/One UI theme combinations, reducing label contrast.
            dialog.window?.setBackgroundDrawable(shape(panel, 18))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(white)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
                setOnClickListener {
                    if (!inventoryReady) return@setOnClickListener
                    val editor = prefs.edit()
                        .putStringSet(AppForegroundPauseSettings.KEY_PACKAGES, selected.toSet())
                    if (selected.isEmpty()) {
                        editor.putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                    }
                    val saved = editor.commit()
                    if (!saved) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "일시중지 앱 목록을 저장하지 못했습니다.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
                    compatibilityPauseSwitch?.let { control ->
                        syncingCompatibilitySwitch = true
                        control.isChecked = prefs.getBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                        syncingCompatibilitySwitch = false
                    }
                    updateCompatibilityPauseStatus()
                    notifyCompatibilitySettingsChanged()
                    dialog.dismiss()
                }
            }
        }
        fun loadInventory() {
            inventoryReady = false
            inventoryMessage = "설치 앱 목록을 불러오는 중…"
            inventorySummary.text = inventoryMessage
            retryButton.isEnabled = false
            retryButton.text = "앱 목록 조회 중…"
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            renderApps(search.text?.toString().orEmpty())
            thread(name = "guardian-compatibility-app-inventory") {
                val result = runCatching { InstalledAppInventory.load(this@MainActivity) }
                runOnUiThread {
                    if (!dialog.isShowing) return@runOnUiThread
                    inventoryReady = true
                    result.onSuccess { snapshot ->
                        apps = snapshot.apps
                        inventoryMessage = snapshot.statusMessage()
                        inventorySummary.text = inventoryMessage
                        android.util.Log.i(
                            "GuardianApps",
                            "Compatibility picker loaded ${apps.size} apps; queryAllPackages=${snapshot.queryAllPackagesGranted}, launcherFallback=${snapshot.launcherFallbackCount}"
                        )
                    }.onFailure { error ->
                        apps = emptyList()
                        inventoryMessage = "설치 앱 조회 실패: ${SafeFailureSummary.describe(error)}"
                        inventorySummary.text = inventoryMessage
                        android.util.Log.e("GuardianApps", "Could not load compatibility app inventory: ${SafeFailureSummary.codeFor(error)}")
                    }
                    retryButton.text = if (apps.isEmpty()) "다시 조회" else "목록 새로고침"
                    retryButton.isEnabled = true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = apps.isNotEmpty()
                    renderApps(search.text?.toString().orEmpty())
                }
            }
        }
        retryButton.setOnClickListener { loadInventory() }
        dialog.show()
        renderApps(search.text?.toString().orEmpty())
        loadInventory()
    }
    private fun notifyCompatibilitySettingsChanged() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(GuardianVpnService.KEY_RESTORE_REQUESTED, false)) {
            try {
                startService(
                    Intent(this, GuardianVpnService::class.java)
                        .setAction(AppForegroundPauseSettings.ACTION_SETTINGS_CHANGED)
                )
            } catch (error: Exception) {
                compatibilityStatusText?.text =
                    "자동 재연결 설정 전달 실패: ${SafeFailureSummary.describe(error)}"
            }
        }
    }

    private fun updateCompatibilityPauseStatus() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val selected = prefs.getStringSet(
            AppForegroundPauseSettings.KEY_PACKAGES, emptySet()
        ).orEmpty()
        var enabled = prefs.getBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
        val paused = prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        val pausedPackage = prefs.getString(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE, null)
        val usageAccess = ForegroundAppMonitor.hasUsageAccess(this)
        var error = prefs.getString(AppForegroundPauseSettings.KEY_ERROR, null)
        if (usageAccess && error != null) {
            prefs.edit().remove(AppForegroundPauseSettings.KEY_ERROR).apply()
            error = null
        }
        if (selected.isEmpty() && enabled) {
            prefs.edit().putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false).apply()
            enabled = false
        }
        if (!usageAccess && enabled) {
            prefs.edit().putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false).apply()
            enabled = false
        }
        compatibilityPauseSwitch?.let { control ->
            if (control.isChecked != enabled) {
                syncingCompatibilitySwitch = true
                control.isChecked = enabled
                syncingCompatibilitySwitch = false
            }
        }
        val pausedLabel = pausedPackage?.let { packageId ->
            try {
                packageManager.getApplicationInfo(packageId, 0).loadLabel(packageManager).toString()
            } catch (_: Exception) {
                packageId
            }
        }
        compatibilityStatusText?.text = when {
            paused -> "${pausedLabel ?: "선택 앱"} 실행 중 · 기기 전체 VPN 보호가 일시 중지되었습니다."
            error != null -> error
            !usageAccess -> "사용 정보 접근 권한을 허용해야 선택 앱의 실행 상태를 감지할 수 있습니다."
            selected.isEmpty() -> "일시중지할 앱을 선택하면 Wallet 등 호환성 문제가 있는 앱에서 자동 전환할 수 있습니다."
            enabled -> "자동 일시중지 활성화 · 선택 앱 종료 후 VPN 자동 재연결"
            else -> "선택 앱 ${selected.size}개 저장됨 · 자동 일시중지 꺼짐"
        }
        compatibilityAppsButton?.text = "일시중지 앱 선택 (${selected.size}개)"
    }

    private fun refreshAppExclusionsAppliedStatus() {
        val target = appExclusionsAppliedStatus ?: return
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val requested = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty()
        val applied = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS_APPLIED, emptySet()).orEmpty()
        val rejected = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS_ERROR, emptySet()).orEmpty()
        val active = ProtectionState.isActive(
            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
            GuardianVpnService.isTunnelActive()
        )
        target.text = when {
            !active -> "VPN 미연결 · 저장된 제외 ${requested.size}개 · 다음 보호 연결 때 적용"
            rejected.isNotEmpty() -> "VPN 연결됨 · 제외 적용 ${applied.size}/${requested.size}개 · 적용 실패 ${rejected.size}개"
            else -> "VPN 연결됨 · 앱별 보호 제외 ${applied.size}/${requested.size}개 적용"
        }
        target.setTextColor(if (active && rejected.isNotEmpty()) red else muted)
    }

    private fun buildAppExclusions(body: LinearLayout) {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED, false)) {
            AlertDialog.Builder(this)
                .setTitle("설치 앱 목록 접근 안내")
                .setMessage(
                    "앱별 보호 제외 기능을 제공하기 위해 Guardian이 기기에 설치된 앱의 이름과 패키지명을 조회합니다. " +
                        "이 목록과 선택한 제외 설정은 이 기기에서만 사용하며 Guardian 서버로 전송하거나 광고·분석에 사용하지 않습니다. " +
                        "동의하면 전체 설치 앱 목록을 표시합니다."
                )
                .setNegativeButton("취소", null)
                .setPositiveButton("동의하고 계속") { _, _ ->
                    val saved = prefs.edit()
                        .putBoolean(KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED, true)
                        .commit()
                    if (saved && prefs.getBoolean(KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED, false)) {
                        // Keep the user in Settings and render the inventory immediately.
                        // Returning to Protection discarded the user's current task and
                        // forced a second navigation through the consent-gated screen.
                        showTab("app-exclusions")
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "동의 상태를 저장하지 못했습니다. 저장 공간을 확인해 주세요.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .show()
            return
        }
        val savedExclusions = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty().toMutableSet()
        val excluded = savedExclusions.toMutableSet()
        // QUERY_ALL_PACKAGES is required here because this control must list every
        // installed app, including non-launcher and system apps. The inventory is
        // used only to render this on-device exclusion picker; it is never uploaded.
        // Include disabled preinstalled packages (some OEM builds ship companion
        // apps such as Android Auto disabled until the user enables them).
        var apps: List<Pair<String, String>> = emptyList()
        var inventoryReady = false
        var inventoryMessage = "설치 앱 목록을 불러오는 중…"
        var renderGeneration = 0
        val search = EditText(this).apply {
            hint = "앱 이름 또는 패키지명 입력"
            contentDescription = "앱별 보호 제외 검색 입력"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            minHeight = dp(50)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = shape(panel2, 12).apply { setStroke(dp(1), accent) }
            if (Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply {
                    setColor(accent)
                    setSize(dp(2), dp(20))
                }
            }
        }
        appExclusionsSearchInput = search
        search.setOnFocusChangeListener { view, focused ->
            view.background = shape(panel2, 12).apply {
                setStroke(dp(if (focused) 2 else 1), if (focused) accent else muted)
            }
            if (focused) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                contentScrollView?.post {
                    val bounds = android.graphics.Rect()
                    search.getDrawingRect(bounds)
                    contentScrollView?.offsetDescendantRectToMyCoords(search, bounds)
                    contentScrollView?.smoothScrollTo(0, (bounds.top - dp(18)).coerceAtLeast(0))
                }
            }
        }
        val inventorySummary = text("설치 앱 목록을 불러오는 중…", 11f, muted, false).withTop(4) as TextView
        val inventoryRetryButton = Button(this).apply {
            text = "앱 목록 다시 불러오기"
            contentDescription = "설치 앱 목록 다시 불러오기"
            isAllCaps = false
            setTextColor(white)
            background = shape(panel2, 10)
            isEnabled = false
        }
        val card = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(text("설치된 앱 검색", 13f, white, true))
            addView(inventorySummary)
            addView(text(
                "제외한 앱의 네트워크는 Android 시스템으로 우회됩니다. Guardian VPN 연결 자체는 계속 유지되므로, 기기 전체의 VPN 상태를 확인하는 앱은 경고를 표시할 수 있습니다.",
                10f, muted, false
            ).withTop(5))
            addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(9) })
            addView(inventoryRetryButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7) })
        }
        val appListCard = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appListCard.addView(appList)
        body.addView(card.withTop(10))
        body.addView(appListCard.withTop(8))
        appFilterFeedback = text(
            "설치 앱 ${apps.size}개 · 보호 제외 앱 ${excluded.size}개",
            11f, muted, false
        ).withTop(8) as TextView
        body.addView(appFilterFeedback!!)
        appExclusionsAppliedStatus = text("", 11f, muted, false).withTop(5) as TextView
        body.addView(appExclusionsAppliedStatus!!)
        refreshAppExclusionsAppliedStatus()
        val saveButton = android.widget.Button(this).apply {
            text = "변경사항 저장"
            contentDescription = "앱별 보호 제외 변경사항 저장"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            minHeight = dp(50)
            background = shape(accent, 12)
            isEnabled = false
            setOnClickListener {
                val snapshot = excluded.toSet()
                val saved = prefs.edit()
                    .putStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, snapshot)
                    .commit()
                if (!saved || prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty() != snapshot) {
                    appFilterFeedback?.text = "제외 설정 저장에 실패했습니다. 기존 설정은 유지됩니다."
                    isEnabled = true
                    return@setOnClickListener
                }
                savedExclusions.clear()
                savedExclusions.addAll(snapshot)
                isEnabled = false
                text = "저장 완료"
                val protectionActive = ProtectionState.isActive(
                    prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                    GuardianVpnService.isTunnelActive()
                )
                if (protectionActive) {
                    appFilterFeedback?.text = "제외 설정 저장 완료 · VPN에 새 제외 목록을 적용하고 있습니다."
                    try {
                        startService(Intent(this@MainActivity, GuardianVpnService::class.java)
                            .setAction(GuardianVpnService.ACTION_RECONFIGURE_EXCLUDED_APPS))
                    } catch (error: Exception) {
                        appFilterFeedback?.text = "설정은 저장됐지만 VPN 재적용 요청 실패: ${SafeFailureSummary.describe(error)}"
                    }
                } else {
                    appFilterFeedback?.text = "제외 설정 저장 완료 · 다음 보호 연결 때 적용됩니다."
                }
            }
        }
        body.addView(saveButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
        fun updateSaveState() {
            val dirty = excluded != savedExclusions
            saveButton.isEnabled = dirty
            saveButton.text = if (dirty) "변경사항 저장 (${excluded.size}개 제외 선택)" else "변경사항 저장"
            if (dirty) {
                appFilterFeedback?.text = "저장되지 않은 변경사항 · 제외 앱 ${excluded.size}개"
            }
        }
        fun renderApps(rawQuery: String) {
            appList.removeAllViews()
            if (!inventoryReady) {
                appList.addView(text("설치 앱 목록을 불러오는 중…", 12f, muted, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return
            }
            if (apps.isEmpty()) {
                appList.addView(text(inventoryMessage, 12f, red, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                appFilterFeedback?.text = inventoryMessage
                return
            }
            val query = rawQuery.trim().lowercase(Locale.ROOT)
            val visible = apps.filter { (pkg, label) ->
                query.isEmpty() || label.lowercase(Locale.ROOT).contains(query) || pkg.lowercase(Locale.ROOT).contains(query)
            }
            val generation = ++renderGeneration
            var nextIndex = 0
            fun appendBatch() {
                if (generation != renderGeneration) return
                val batchEnd = minOf(nextIndex + 40, visible.size)
                for (index in nextIndex until batchEnd) {
                    val (pkg, label) = visible[index]
                    appList.addView(CheckBox(this).apply {
                        applyAppSelectionTint(this)
                        text = "$label\n$pkg"
                        contentDescription = "앱별 보호 제외 앱 $pkg"
                        textSize = 12f
                        setTextColor(white)
                        isChecked = pkg in excluded
                        isEnabled = pkg != packageName
                        setPadding(dp(4), dp(5), dp(4), dp(5))
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) excluded.add(pkg) else excluded.remove(pkg)
                            updateSaveState()
                        }
                    })
                }
                nextIndex = batchEnd
                if (nextIndex < visible.size) appList.post { appendBatch() }
            }
            if (visible.isEmpty()) {
                appList.addView(text("검색 결과가 없습니다.", 12f, muted, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
            } else {
                appList.post { appendBatch() }
            }
            updateSaveState()
            if (excluded == savedExclusions) {
                appFilterFeedback?.text = "검색 결과 ${visible.size}개 / 전체 ${apps.size}개 · 저장된 제외 앱 ${excluded.size}개"
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, before: Int, count: Int, after: Int) = renderApps(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        fun loadAppExclusionInventory() {
            inventoryReady = false
            inventoryMessage = "설치 앱 목록을 불러오는 중…"
            inventorySummary.text = inventoryMessage
            inventoryRetryButton.text = "앱 목록 조회 중…"
            inventoryRetryButton.isEnabled = false
            renderApps(search.text?.toString().orEmpty())
            thread(name = "guardian-app-exclusion-inventory") {
                val result = runCatching { InstalledAppInventory.load(this@MainActivity) }
                runOnUiThread {
                    inventoryReady = true
                    result.onSuccess { snapshot ->
                        apps = snapshot.apps
                        inventoryMessage = snapshot.statusMessage()
                        inventorySummary.text = if (apps.isEmpty()) inventoryMessage else
                            "${inventoryMessage} · 앱 이름 또는 패키지명으로 검색하세요."
                        appFilterFeedback?.contentDescription = "앱별 보호 제외 앱 목록 준비 완료"
                        android.util.Log.i(
                            "GuardianApps",
                            "Exclusion picker loaded ${apps.size} apps; queryAllPackages=${snapshot.queryAllPackagesGranted}, launcherFallback=${snapshot.launcherFallbackCount}"
                        )
                    }.onFailure { error ->
                        apps = emptyList()
                        inventoryMessage = "설치 앱 조회 실패: ${SafeFailureSummary.describe(error)}"
                        inventorySummary.text = inventoryMessage
                        android.util.Log.e("GuardianApps", "Could not load app exclusion inventory: ${SafeFailureSummary.codeFor(error)}")
                    }
                    inventoryRetryButton.text = if (apps.isEmpty()) "다시 조회" else "목록 새로고침"
                    inventoryRetryButton.isEnabled = true
                    renderApps(search.text?.toString().orEmpty())
                }
            }
        }
        inventoryRetryButton.setOnClickListener { loadAppExclusionInventory() }
        renderApps("")
        loadAppExclusionInventory()
    }

    private fun filterStatusDescription(): String {
        val count = FilterListManager.cachedBlockCount(this)
        val updated = FilterListManager.lastUpdatedAt(this)
        val time = if (updated > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updated)) else "아직 업데이트되지 않음"
        val securityCount = FilterListManager.cachedSecurityBlockCount(this)
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val securityOn = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val androidAdsOn = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
        val androidAdsCount = FilterListManager.cachedAndroidAdsBlockCount(this)
        val ignored = FilterListManager.cachedIgnoredCount(this)
        val securityIgnored = if (securityOn) FilterListManager.cachedSecurityIgnoredCount(this) else 0
        val androidAdsIgnored = if (androidAdsOn) FilterListManager.cachedAndroidAdsIgnoredCount(this) else 0
        val lastError = prefs.getString(FilterListManager.KEY_LAST_ERROR, null)
        val securityError = if (securityOn) prefs.getString(FilterListManager.KEY_SECURITY_LAST_ERROR, null) else null
        val androidAdsError = if (androidAdsOn) prefs.getString(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR, null) else null
        val selectedProfile = FilterListManager.selectedProfile(this)
        val cachedProfile = FilterListManager.cachedProfile(this)
        val profileLabel = if (FilterListManager.hasCachedList(this) && selectedProfile.key != cachedProfile.key) {
            "선택: ${selectedProfile.label} · 적용 중: ${cachedProfile.label}"
        } else selectedProfile.label
        val summary = "$profileLabel · 광고·추적 ${count}개 · 보안 ${if (securityOn) securityCount else 0}개 · 앱 광고 ${if (androidAdsOn) androidAdsCount else 0}개 · 미지원 규칙 ${ignored + securityIgnored + androidAdsIgnored}개 제외 · 업데이트: $time"
        val signedManifestStatus = FilterListManager.signedManifestStatus(this)
        return listOfNotNull(
            summary,
            signedManifestStatus,
            lastError?.takeIf { it.isNotBlank() }?.let { "광고 필터 업데이트 실패(이전 목록 유지): ${SafeFailureSummary.describeCode(it)}" },
            securityError?.takeIf { it.isNotBlank() }?.let { "보안 필터 업데이트 실패(이전 목록 유지): ${SafeFailureSummary.describeCode(it)}" },
            androidAdsError?.takeIf { it.isNotBlank() }?.let { "Android 앱 광고 필터 업데이트 실패(이전 목록 유지): ${SafeFailureSummary.describeCode(it)}" }
        ).joinToString(" · ")
    }

    private fun setFilterUpdateStatus(message: String) {
        filterStatusText?.text = message
        homeFilterStatusText?.text = message
        protectionFeedback?.text = message
    }

    private fun startFilterUpdate() {
        if (!filterUpdateRunning.compareAndSet(false, true)) {
            setFilterUpdateStatus("필터 목록을 이미 다운로드하고 있습니다.")
            return
        }
        filterUpdateButton?.isEnabled = false
        filterUpdateButton?.text = "필터 다운로드 및 검증 중…"
        homeFilterUpdateButton?.isEnabled = false
        homeFilterUpdateButton?.text = "필터 다운로드 및 검증 중…"
        // Freeze optional-filter choices while their enabled/cache snapshot is
        // being processed. Otherwise a user could enable a list mid-update,
        // after the snapshot, leaving protection configured with no cache.
        securityFilterSwitch?.isEnabled = false
        androidAdsFilterSwitch?.isEnabled = false
        securityFilterCheckBox?.isEnabled = false
        androidAdsFilterCheckBox?.isEnabled = false
        setFilterUpdateStatus("${FilterListManager.selectedProfile(this).label} 목록을 내려받아 규칙을 검증합니다.")
        android.util.Log.i("Guardian", "Starting filter update for ${FilterListManager.selectedProfile(this).key}")
        thread(name = "guardian-filter-update") {
            val hadPreviousMainCache = try { FilterListManager.hasCachedList(this) } catch (_: Exception) { false }
            try {
                android.util.Log.i("Guardian", "Base filter update request entered")
                val result = FilterListManager.update(this)
                android.util.Log.i("Guardian", "Base filter downloaded: ${result.blocked} rules, ${result.bytes} bytes")
                val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
                prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()
                val securityWasEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
                val androidAdsWasEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
                val securityWasCached = FilterListManager.hasCachedSecurityList(this)
                val androidAdsWasCached = FilterListManager.hasCachedAndroidAdsList(this)
                val securityResult = if (securityWasEnabled) {
                    try {
                        FilterListManager.updateSecurity(this).also {
                            prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Guardian", "Security filter download failed: ${SafeFailureSummary.codeFor(e)}")
                        val editor = prefs.edit().putString(
                            FilterListManager.KEY_SECURITY_LAST_ERROR,
                            SafeFailureSummary.codeFor(e)
                        )
                        if (FilterUpdatePolicy.shouldDisableOptionalFilter(securityWasEnabled, securityWasCached)) {
                            editor.putBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
                        }
                        editor.apply()
                        null
                    }
                } else null
                val androidAdsResult = if (androidAdsWasEnabled) {
                    try {
                        FilterListManager.updateAndroidAds(this).also {
                            prefs.edit().remove(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR).apply()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Guardian", "Android ads filter download failed: ${SafeFailureSummary.codeFor(e)}")
                        prefs.edit().putString(
                            FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                            SafeFailureSummary.codeFor(e)
                        ).apply()
                        if (!androidAdsWasCached) prefs.edit()
                            .putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false).apply()
                        null
                    }
                } else null
                val active = ProtectionState.isActive(
                    prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                    GuardianVpnService.isTunnelActive()
                )
                // The previous readiness note describes the old cache snapshot.
                // Clear it before asking the live service to rebuild rules so support
                // diagnostics never present stale filter availability as current.
                prefs.edit().remove(GuardianVpnService.KEY_FILTER_READINESS).apply()
                if (active) {
                    startService(Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_RELOAD_RULES))
                }
                android.util.Log.i(
                    "Guardian",
                    "Filter update completed; security=${securityResult != null}, androidAds=${androidAdsResult != null}"
                )
                filterUpdateRunning.set(false)
                runOnUiThread {
                    if (securityWasEnabled && securityResult == null && !securityWasCached) {
                        securityFilterSwitch?.isChecked = false
                        securityFilterCheckBox?.isChecked = false
                    }
                    if (androidAdsWasEnabled && androidAdsResult == null && !androidAdsWasCached) {
                        androidAdsFilterSwitch?.isChecked = false
                        androidAdsFilterCheckBox?.isChecked = false
                    }
                    val securityMessage = if (securityWasEnabled) {
                        if (securityResult != null) " · 보안 ${securityResult.blocked}개" else if (securityWasCached) " · 보안 업데이트 실패(이전 목록 유지)" else " · 보안 목록 다운로드 실패(캐시 없음 · 자동 비활성화)"
                    } else ""
                    val androidAdsMessage = if (androidAdsWasEnabled) {
                        if (androidAdsResult != null) " · Android 앱 광고 ${androidAdsResult.blocked}개" else if (androidAdsWasCached) " · 앱 광고 필터 업데이트 실패(이전 목록 유지)" else " · 앱 광고 필터 다운로드 실패(캐시 없음 · 자동 비활성화)"
                    } else ""
                    val signedStatus = FilterListManager.signedManifestStatus(this)?.let { " · $it" } ?: ""
                    setFilterUpdateStatus("${FilterListManager.selectedProfile(this).label} · 광고·추적 ${result.blocked}개 · 미지원 ${result.ignored}개 제외${securityMessage}${androidAdsMessage} · ${result.bytes / 1024}KB$signedStatus")
                    filterUpdateButton?.text = "필터 목록 업데이트"
                    filterUpdateButton?.isEnabled = true
                    homeFilterUpdateButton?.text = "필터 목록 업데이트"
                    homeFilterUpdateButton?.isEnabled = true
                    securityFilterSwitch?.isEnabled = true
                    androidAdsFilterSwitch?.isEnabled = true
                    securityFilterCheckBox?.isEnabled = true
                    androidAdsFilterCheckBox?.isEnabled = true
                }
            } catch (e: Exception) {
                android.util.Log.e("Guardian", "Base filter update failed: ${SafeFailureSummary.codeFor(e)}; causeTypes=${SafeFailureSummary.diagnosticTypeChain(e)}")
                filterUpdateRunning.set(false)
                getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE).edit()
                    .putString(FilterListManager.KEY_LAST_ERROR, SafeFailureSummary.codeFor(e)).apply()
                runOnUiThread {
                    val cacheMessage = if (hadPreviousMainCache) {
                        "이전 정상 필터를 계속 사용합니다."
                    } else {
                        "앱 내 기본 DNS 규칙으로 보호를 시작할 수 있지만, 추가 광고·추적 필터는 다운로드 후 적용됩니다."
                    }
                    setFilterUpdateStatus("업데이트 실패: ${SafeFailureSummary.describe(e)} · $cacheMessage")
                    filterUpdateButton?.text = "다시 시도"
                    filterUpdateButton?.isEnabled = true
                    homeFilterUpdateButton?.text = "다시 시도"
                    homeFilterUpdateButton?.isEnabled = true
                    securityFilterSwitch?.isEnabled = true
                    androidAdsFilterSwitch?.isEnabled = true
                    securityFilterCheckBox?.isEnabled = true
                    androidAdsFilterCheckBox?.isEnabled = true
                }
            }
        }
    }

    private fun onHomeProtectionButtonTapped() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        val active = ProtectionState.isActive(
            prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
            GuardianVpnService.isTunnelActive()
        )
        val compatibilityPaused = prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        if (active || compatibilityPaused) {
            try {
                startService(
                    Intent(this, GuardianVpnService::class.java)
                        .setAction(GuardianVpnService.ACTION_STOP)
                )
            } catch (error: Exception) {
                android.util.Log.e("GuardianProtection", "Could not stop protection from the home toggle: ${SafeFailureSummary.codeFor(error)}")
                status.text = "보호 중지 요청 실패: ${SafeFailureSummary.describe(error)}"
            }
            return
        }
        requestProtection()
    }

    private fun requestProtection() {
        android.util.Log.i("GuardianProtection", "Protection button tapped; bundled-rule readiness check starting")
        if (!protectionCheckRunning.compareAndSet(false, true)) {
            android.util.Log.w("GuardianProtection", "Readiness check already running; duplicate tap ignored")
            protectionFeedback?.text = "기본 보호 규칙 상태를 확인하고 있습니다. 잠시만 기다려 주세요."
            return
        }
        startButton?.isEnabled = false
        protectionFeedback?.text = "앱에 포함된 기본 DNS 규칙을 확인하고 있습니다."
        thread(name = "guardian-protection-readiness") {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
            val readiness = try {
                // The bundled rules are sufficient to bring up the DNS VPN safely.
                // Downloaded lists are optional enhancements and must never be a hard
                // prerequisite for creating the tunnel on a first install/offline device.
                val bundled = assets.open("filter_rules.txt").bufferedReader().use(FilterRuleParser::parse)
                val baselineReady = FilterStartupGate.hasUsableBundledBaseline(
                    bundled.blocked.size,
                    bundled.importantBlocked.size
                )
                val unavailable = mutableListOf<String>()
                val mainReady = runCatching { FilterListManager.hasCachedList(this) }.getOrDefault(false)
                val selected = FilterListManager.selectedProfile(this)
                val cached = FilterListManager.cachedProfile(this)
                when {
                    !mainReady -> unavailable.add("광고·추적")
                    selected.key != cached.key -> unavailable.add("선택한 ${selected.label}")
                }
                if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false) &&
                    !runCatching { FilterListManager.hasCachedSecurityList(this) }.getOrDefault(false)
                ) unavailable.add("악성코드·피싱")
                if (prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true) &&
                    !runCatching { FilterListManager.hasCachedAndroidAdsList(this) }.getOrDefault(false)
                ) unavailable.add("Android 앱 광고")
                val notice = if (unavailable.isEmpty()) null else
                    "기본 DNS 규칙으로 먼저 연결합니다. 추가 필터는 아직 준비되지 않았습니다: ${unavailable.joinToString()}."
                android.util.Log.i(
                    "GuardianProtection",
                    "Bundled-rule readiness: ready=$baselineReady optionalFiltersMissing=${unavailable.joinToString()} elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
                )
                baselineReady to if (baselineReady) notice else "앱 내 기본 DNS 규칙이 비어 있어 보호를 시작할 수 없습니다."
            } catch (error: Exception) {
                android.util.Log.e("GuardianProtection", "Bundled filter asset could not be loaded: ${SafeFailureSummary.codeFor(error)}")
                prefs.edit().putString(GuardianVpnService.KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error)).apply()
                false to "앱 내 기본 필터 라이브러리를 읽지 못했습니다. 진단 정보를 확인해 주세요. (${ProtectionDiagnostics.exceptionTypeChain(error)})"
            } catch (error: LinkageError) {
                android.util.Log.e("GuardianProtection", "Bundled filter component could not be loaded: ${SafeFailureSummary.codeFor(error)}")
                prefs.edit().putString(GuardianVpnService.KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error)).apply()
                false to "필터 구성요소를 불러오지 못했습니다. 진단 정보를 확인해 주세요. (${ProtectionDiagnostics.exceptionTypeChain(error)})"
            }
            runOnUiThread {
                protectionCheckRunning.set(false)
                startButton?.isEnabled = true
                if (!readiness.first) {
                    pendingFilterStartNotice = null
                    protectionFeedback?.text = readiness.second
                    setFilterUpdateStatus(readiness.second ?: "기본 필터를 확인할 수 없습니다.")
                    return@runOnUiThread
                }
                pendingFilterStartNotice = readiness.second
                readiness.second?.let(::setFilterUpdateStatus)
                val consent = VpnService.prepare(this)
                android.util.Log.i("GuardianProtection", "VPN consent check complete; consentRequired=${consent != null}")
                if (consent != null) startActivityForResult(consent, REQUEST_VPN) else startProtection()
            }
        }
    }

    private fun startProtection() {
        try {
            android.util.Log.i("GuardianProtection", "Starting GuardianVpnService after VPN consent")
            // Keep class resolution inside the diagnostic boundary too: a loader
            // failure can happen while evaluating GuardianVpnService::class.java.
            val service = Intent(this, GuardianVpnService::class.java).setAction(GuardianVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
            val baseMessage = "보호 시작 요청을 전달했습니다. VPN 연결을 확인하고 있습니다."
            val notice = pendingFilterStartNotice.also { pendingFilterStartNotice = null }
            val message = listOfNotNull(baseMessage, notice).joinToString(" ")
            if (currentTab == "home" && ::status.isInitialized && ::statusTitle.isInitialized) {
                status.text = message
                statusTitle.text = "연결 확인 중"
            } else if (currentTab == "protection") {
                protectionStatusText?.text = "연결 확인 중"
                protectionFeedback?.text = message
            }
        } catch (error: Exception) {
            pendingFilterStartNotice = null
            reportProtectionStartRequestFailure(error)
        } catch (error: LinkageError) {
            // Class/native runtime loading failures are Errors, not Exceptions.
            // Keep the safe caller trace so handset-only loader failures are diagnosable.
            pendingFilterStartNotice = null
            reportProtectionStartRequestFailure(error)
        }
    }

    private fun reportProtectionStartRequestFailure(error: Throwable) {
        android.util.Log.e("GuardianProtection", "Could not dispatch GuardianVpnService start request: ${SafeFailureSummary.codeFor(error)}")
        val message = "보호 시작 중 예기치 않은 오류가 발생했습니다 " +
            "(${ProtectionDiagnostics.exceptionTypeChain(error)}): " +
            SafeFailureSummary.describe(error)
        getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE).edit()
            .putString(GuardianVpnService.KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error, 16))
            .putString(GuardianVpnService.KEY_STATUS, message)
            .apply()
        if (currentTab == "home" && ::status.isInitialized && ::statusTitle.isInitialized) {
            statusTitle.text = "시작하지 못했습니다"
            status.text = message
        } else if (currentTab == "protection") {
            protectionStatusText?.text = "시작하지 못했습니다"
            protectionFeedback?.text = message
        } else {
            filterStatusText?.text = "보호를 시작하지 못했습니다: $message"
        }
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences(GuardianVpnService.PREFS, MODE_PRIVATE)
        // A persisted flag can outlive an Android process kill. Confirm this
        // process still owns the VPN tunnel before showing protection as active.
        val persistedActive = prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        val active = ProtectionState.isActive(persistedActive, GuardianVpnService.isTunnelActive())
        appFilterFeedback?.let { feedback ->
            val requested = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS, emptySet()).orEmpty()
            val applied = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS_APPLIED, emptySet()).orEmpty()
            val stale = prefs.getStringSet(GuardianVpnService.KEY_EXCLUDED_APPS_ERROR, emptySet()).orEmpty()
            feedback.text = when {
                stale.isNotEmpty() -> "제외 적용 실패 또는 미설치 앱 ${stale.size}개 · 적용 ${applied.size}/${requested.size}"
                active && requested != applied -> "제외 적용 확인 필요 · 적용 ${applied.size}/${requested.size}"
                active -> "앱별 보호 제외 적용 완료 · ${applied.size}개"
                else -> "제외 앱 ${requested.size}개 저장됨 · 보호 연결 시 적용"
            }
        }
        val recoveryPending = prefs.getBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, false) && !active
        val staleSession = persistedActive && !active && !recoveryPending
        val compatibilityPaused = prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        val compatibilityPackage = prefs.getString(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE, null)
        val compatibilityLabel = compatibilityPackage?.let { packageId ->
            try {
                packageManager.getApplicationInfo(packageId, 0).loadLabel(packageManager).toString()
            } catch (_: Exception) {
                packageId
            }
        }
        val message = when {
            compatibilityPaused -> "${compatibilityLabel ?: "선택 앱"} 실행 중 · 기기 전체 DNS 보호가 일시 중지되었습니다. 앱을 닫으면 자동으로 다시 연결됩니다."
            recoveryPending -> "VPN 보호를 자동으로 다시 연결하고 있습니다."
            staleSession -> "VPN 서비스가 다시 연결되는 중입니다. 자동 복구가 되지 않으면 '다시 연결'을 눌러 주세요."
            else -> prefs.getString(GuardianVpnService.KEY_STATUS, null)
        }
        // Not every tab creates the dashboard status views. Update only views
        // that exist so opening Rules/Protection/Settings cannot crash the app.
        if (currentTab == "home" && ::statusTitle.isInitialized) {
            statusTitle.text = when {
                active -> "보호가 실행 중입니다"
                compatibilityPaused -> "호환 앱 실행 중 · 보호 일시 중지"
                recoveryPending -> "보호 자동 복구 중"
                staleSession -> "보호 복구 대기 중"
                else -> "보호가 꺼져 있습니다"
            }
            statusDot.text = when {
                active -> "●  보호 중"
                compatibilityPaused -> "●  호환 앱으로 일시 중지"
                recoveryPending || staleSession -> "●  자동 복구 중"
                else -> "●  보호 꺼짐"
            }
            statusDot.setTextColor(
                when {
                    active || recoveryPending || staleSession -> accent
                    compatibilityPaused -> Color.rgb(255, 190, 80)
                    else -> red
                }
            )
            startButton?.text = when {
                active -> "보호 중지"
                compatibilityPaused -> "보호 완전 중지"
                recoveryPending -> "보호 복구 중"
                staleSession -> "다시 연결"
                else -> "보호 시작"
            }
            // The main protection control is a real toggle: while the tunnel is
            // active it remains tappable and stops protection instead of looking
            // disabled. Only suppress taps while an automatic recovery is running.
            startButton?.isEnabled = !recoveryPending
            startButton?.background = shape(
                if (active || compatibilityPaused) panel2 else accent, 28
            )
            startButton?.alpha = if (recoveryPending) 0.58f else 1f
        }
        if (currentTab == "home" && ::status.isInitialized) {
            status.text = message ?: if (active) "DNS 보호 서비스가 실행 중입니다." else "보호 시작을 눌러 VPN 권한을 승인하세요."
        }
        if (currentTab == "protection") {
            protectionStatusText?.text = when {
                active -> "보호가 실행 중입니다"
                compatibilityPaused -> "호환 앱 실행 중 · 보호 일시 중지"
                recoveryPending -> "보호 자동 복구 중"
                staleSession -> "보호 복구 대기 중"
                else -> "보호가 꺼져 있습니다"
            }
            protectionStatusDetail?.text = ProtectionDiagnostics.statusDetail(
                active,
                prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0L),
                prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L),
                prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L),
                prefs.getLong(GuardianVpnService.KEY_DNS_FAILED, 0L),
                privateDnsMode(),
                message
            )
        }
        statsText?.text = "질의 ${prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0)} · 차단 ${prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0)} · 전달 ${prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0)} · 실패 ${prefs.getLong(GuardianVpnService.KEY_DNS_FAILED, 0)} · 무시 ${prefs.getLong(GuardianVpnService.KEY_DNS_IGNORED, 0)}"
        val totalQueries = prefs.getLong(GuardianVpnService.KEY_DNS_QUERIES, 0L)
        val blockedQueries = prefs.getLong(GuardianVpnService.KEY_DNS_BLOCKED, 0L)
        val blockRate = if (totalQueries > 0L) blockedQueries * 100.0 / totalQueries else 0.0
        blockRateValue?.text = String.format(Locale.getDefault(), "%.1f%%", blockRate)
        blockedCountValue?.text = String.format(Locale.getDefault(), "%,d", blockedQueries)
        queryCountValue?.text = String.format(Locale.getDefault(), "%,d", totalQueries)
        val adCount = prefs.getLong(GuardianVpnService.KEY_DNS_AD_BLOCKED, 0L)
        val trackerCount = prefs.getLong(GuardianVpnService.KEY_DNS_TRACKER_BLOCKED, 0L)
        val otherCount = prefs.getLong(GuardianVpnService.KEY_DNS_OTHER_BLOCKED, 0L)
        adBlockedValue?.text = String.format(Locale.getDefault(), "%,d", adCount)
        trackerBlockedValue?.text = String.format(Locale.getDefault(), "%,d", trackerCount)
        savedDataValue?.text = estimatedSavedDataLabel(blockedQueries)
        activityChart?.update(adCount, trackerCount, otherCount, prefs.getLong(GuardianVpnService.KEY_DNS_FORWARDED, 0L))
        recentText?.text = prefs.getString(GuardianVpnService.KEY_DNS_RECENT, null) ?: "아직 기록된 DNS 활동이 없습니다."
        diagnosticText?.text = "마지막 패킷 진단: ${prefs.getString(GuardianVpnService.KEY_DNS_DIAGNOSTIC, "아직 기록 없음")}"
        updateCompatibilityPauseStatus()
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

    private fun recordSettingsUpdateCheck(channel: String) {
        getSharedPreferences(UPDATE_CHECK_THROTTLE_PREFS, MODE_PRIVATE)
            .edit()
            .putLong("last_settings_check_$channel", System.currentTimeMillis())
            .apply()
    }

    private fun shouldRunAutomaticSettingsUpdateCheck(channel: String, intervalMillis: Long): Boolean {
        val lastCheck = getSharedPreferences(UPDATE_CHECK_THROTTLE_PREFS, MODE_PRIVATE)
            .getLong("last_settings_check_$channel", 0L)
        return GuardianAutoUpdateCheckPolicy.shouldCheck(
            lastCheckAtMillis = lastCheck,
            nowMillis = System.currentTimeMillis(),
            intervalMillis = intervalMillis
        )
    }

    private fun postSettingsUpdateCheck(
        channel: String,
        intervalMillis: Long,
        checkButton: Button,
        updateStatus: TextView
    ) {
        checkButton.post {
            if (!checkButton.isEnabled) return@post
            if (shouldRunAutomaticSettingsUpdateCheck(channel, intervalMillis)) {
                checkButton.performClick()
            } else {
                updateStatus.text = "최근 업데이트를 확인했습니다. 아래 버튼으로 지금 다시 확인할 수 있습니다."
            }
        }
    }

    private fun setUpdateActionButtons(
        checkButton: Button?,
        installButton: Button,
        checking: Boolean,
        hasVerifiedApk: Boolean
    ) {
        val state = GuardianUpdateActionPolicy.resolve(checking, hasVerifiedApk)
        checkButton?.isEnabled = state.checkEnabled
        checkButton?.alpha = if (state.checkEnabled) 1f else 0.58f
        setUpdateInstallButtonReady(installButton, state.installEnabled)
    }

    private fun setUpdateInstallButtonReady(button: Button, ready: Boolean) {
        button.isEnabled = ready
        button.setTextColor(if (ready) bg else muted)
        button.background = shape(if (ready) accent else panel2, 12).apply {
            setStroke(dp(1), if (ready) accent else muted)
        }
        button.alpha = if (ready) 1f else 0.78f
    }

    private fun applyToggleTint(toggle: CompoundButton) {
        toggle.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
    }

    private fun applyAppSelectionTint(toggle: CompoundButton) {
        // Unchecked selection boxes must remain clearly visible on dark cards.
        toggle.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, Color.WHITE)
        )
    }

    private fun darkSpinnerAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    textSize = 13f
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = shape(panel2, 10)
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    textSize = 13f
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = shape(panel, 0)
                }
            }
        }

    private fun estimatedSavedDataLabel(blockedQueries: Long): String {
        val bytes = blockedQueries.coerceAtLeast(0L).toDouble() * 24.0 * 1024.0
        return if (bytes >= 1024.0 * 1024.0) {
            String.format(Locale.getDefault(), "약 %.2f MB", bytes / (1024.0 * 1024.0))
        } else {
            String.format(Locale.getDefault(), "약 %.0f KB", bytes / 1024.0)
        }
    }

    private inner class ActivityStatsChart : View(this@MainActivity) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = dp(11).toFloat()
            typeface = Typeface.DEFAULT
        }
        private val values = LongArray(4)
        private val labels = arrayOf("광고 차단", "추적 차단", "기타 차단", "정상 전달")
        private val colors = intArrayOf(Color.rgb(255, 166, 94), Color.rgb(116, 167, 255), accent, muted)

        fun update(ads: Long, trackers: Long, other: Long, forwarded: Long) {
            values[0] = ads.coerceAtLeast(0L)
            values[1] = trackers.coerceAtLeast(0L)
            values[2] = other.coerceAtLeast(0L)
            values[3] = forwarded.coerceAtLeast(0L)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            val labelWidth = dp(76).toFloat()
            val rightPadding = dp(38).toFloat()
            val barWidth = (width - labelWidth - rightPadding).coerceAtLeast(dp(20).toFloat())
            val rowHeight = height / 4f
            for (i in values.indices) {
                val top = i * rowHeight + dp(7)
                textPaint.color = muted
                canvas.drawText(labels[i], 0f, top + dp(12), textPaint)
                val barTop = top + dp(20)
                val barHeight = dp(12).toFloat()
                barPaint.color = panel2
                canvas.drawRoundRect(labelWidth, barTop, labelWidth + barWidth, barTop + barHeight, dp(6).toFloat(), dp(6).toFloat(), barPaint)
                val fraction = values[i].toDouble() / maxValue.toDouble()
                barPaint.color = colors[i]
                canvas.drawRoundRect(labelWidth, barTop, labelWidth + (barWidth * fraction).toFloat().coerceAtLeast(if (values[i] > 0L) dp(3).toFloat() else 0f), barTop + barHeight, dp(6).toFloat(), dp(6).toFloat(), barPaint)
                textPaint.color = white
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(String.format(Locale.getDefault(), "%,d", values[i]), width.toFloat(), top + dp(12), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun metricCard(label: String, value: TextView, tint: Int) = card(panel, 15).apply {
        setPadding(dp(12), dp(12), dp(8), dp(10))
        addView(text(label, 10f, muted, false))
        value.setTextColor(tint)
        addView(value.withTop(7))
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
        private const val UPDATE_CHECK_THROTTLE_PREFS = "guardian_update_check_throttle"
        private const val REQUEST_VPN = 100
        private const val MAX_USER_RULES = 1000
        private const val KEY_APP_INVENTORY_DISCLOSURE_ACCEPTED = "app_inventory_disclosure_accepted_v1"
        private const val STATE_CURRENT_TAB = "guardian.ui.current_tab"
        private const val STATE_SETTINGS_CATEGORY = "guardian.ui.settings_category"
        private const val UI_STATE_PREFS = "guardian_ui_state"
        private const val PREF_LAST_SELECTED_TAB = "last_selected_tab"
        private const val STATE_FOCUS_LICENSE_ACTIVATION = "guardian.ui.focus_license_activation"
        private const val STATE_SCROLL_Y = "guardian.ui.scroll_y"
        private const val STATE_BLOCK_RULES_DRAFT = "guardian.ui.block_rules_draft"
        private const val STATE_ALLOW_RULES_DRAFT = "guardian.ui.allow_rules_draft"
    }
}
