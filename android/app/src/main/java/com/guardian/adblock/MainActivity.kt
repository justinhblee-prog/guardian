            !usageAccess -> "사용 정보 접근 권한을 허용해야 선택 앱의 실행 상태를 감지할 수 있습니다."
            selected.isEmpty() -> "일시중지할 앱을 선택하면 Wallet 등 호환성 문제가 있는 앱에서 자동 전환할 수 있습니다."
            enabled -> "자동 일시중지 활성화 · 선택 앱 종료 후 VPN 자동 재연결"
            else -> "선택 앱 ${selected.size}개 저장됨 · 자동 일시중지 꺼짐"
        }
        compatibilityAppsButton?.text = "일시중지 앱 선택 (${selected.size}개)"
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
                        showTab("settings", focusAppExclusions = true)
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
        // PackageManager calls and label loading can cross Binder for hundreds of
        // packages. Keep them off the UI thread so consent does not appear frozen.
        var apps: List<Pair<String, String>> = emptyList()
        var inventoryReady = false
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
            setOnFocusChangeListener { view, focused ->
                view.background = shape(panel2, 12).apply {
                    setStroke(dp(2), if (focused) accent else muted)
                }
            }
        }
        appExclusionsSearchInput = search
        val inventorySummary = text("설치 앱 목록을 불러오는 중…", 11f, muted, false) as TextView
        val card = card(panel, 16).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(text("설치된 앱 검색", 13f, white, true))
            addView(inventorySummary.apply {
                contentDescription = "앱별 보호 제외 앱 목록 불러오는 중"
            }.withTop(4))
            addView(text(
                "제외한 앱의 네트워크는 Android 시스템으로 우회됩니다. Guardian VPN 연결 자체는 계속 유지되므로, 기기 전체의 VPN 상태를 확인하는 앱은 경고를 표시할 수 있습니다.",
                10f, muted, false
            ).withTop(5))
            addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(9) })
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
            "설치 앱 목록을 불러오는 중…",
            11f, muted, false
        ).withTop(8) as TextView
        body.addView(appFilterFeedback!!)
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
                        appFilterFeedback?.text = "설정은 저장됐지만 VPN 재적용 요청에 실패했습니다: ${error.message ?: error.javaClass.simpleName}"
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
            val query = rawQuery.trim().lowercase(Locale.ROOT)
            if (!inventoryReady) {
                appList.addView(text("설치 앱 목록을 불러오는 중…", 12f, muted, false).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return
            }
            val visible = apps.filter { (pkg, label) ->
                query.isEmpty() || label.lowercase(Locale.ROOT).contains(query) || pkg.lowercase(Locale.ROOT).contains(query)
            }
            val generation = ++renderGeneration
            var nextIndex = 0
            fun appendBatch() {
                if (generation != renderGeneration || appExclusionsSearchInput !== search) return
                val batchEnd = minOf(nextIndex + 40, visible.size)
                for (index in nextIndex until batchEnd) {
                    val (pkg, label) = visible[index]
                    appList.addView(CheckBox(this).apply {
                        text = "$label\n$pkg"
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
        renderApps("")
        thread(name = "guardian-app-inventory") {
            val result = runCatching {
                packageManager.getInstalledApplications(
                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
                )
                    .asSequence()
                    .filter { it.packageName != packageName }
                    .map { info ->
                        info.packageName to try {
                            info.loadLabel(packageManager).toString()
                        } catch (error: Exception) {
                            android.util.Log.w("GuardianApps", "Could not load app label for ${info.packageName}", error)
                            info.packageName
                        }
                    }
                    .distinctBy { it.first }
                    .sortedWith(compareBy<Pair<String, String>>(
                        { it.second.lowercase(Locale.ROOT) },
                        { it.first.lowercase(Locale.ROOT) }
                    ))
                    .toList()
            }
            runOnUiThread {
                if (appExclusionsSearchInput !== search) return@runOnUiThread
                result.onSuccess { loaded ->