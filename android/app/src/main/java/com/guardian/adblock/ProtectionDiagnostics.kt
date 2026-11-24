package com.guardian.adblock

/** Builds truthful protection copy from observed tunnel traffic, not VPN state alone. */
object ProtectionDiagnostics {
    /**
     * Returns a bounded, cycle-safe exception type chain without exposing messages,
     * file paths, URLs, or other potentially sensitive exception details.
     */
    fun exceptionTypeChain(error: Throwable, maxDepth: Int = 4): String {
        val limit = maxDepth.coerceIn(1, 8)
        val seen = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>()
        )
        val names = mutableListOf<String>()
        var current: Throwable? = error
        var traversed = 0
        // Bound traversal independently of accepted class names: arbitrary Throwable
        // subclasses must not let an untrusted cause chain consume unbounded work.
        while (current != null && traversed < limit && seen.add(current)) {
            traversed += 1
            val throwable = current
            val name = throwable.javaClass.simpleName.ifBlank {
                throwable.javaClass.name.substringAfterLast('.')
            }
            if (name.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:Exception|Error)"))) {
                names.add(name)
            }
            current = throwable.cause
        }
        return names.ifEmpty { listOf("UnknownError") }.joinToString(" <- ")
    }

    /**
     * Captures bounded stack-frame identity without exception messages, paths, URLs or arguments.
     */
    fun exceptionTraceSummary(error: Throwable, maxFrames: Int = 16): String {
        val framesByCause = mutableListOf<List<String>>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        val limit = maxFrames.coerceIn(1, 16)
        // A wrapper often has a long Android framework stack of its own. If that
        // consumes the whole budget, the actual native/file-loader cause disappears.
        // Bound the cause chain and divide the frame budget across each throwable.
        while (current != null && seen.add(current) && framesByCause.size < 8) {
            val throwable = current
            val safeFrames = throwable.stackTrace.mapNotNull { frame ->
                val owner = frame.className.takeIf { it.matches(Regex("[A-Za-z0-9_$.]{1,180}")) }
                    ?: return@mapNotNull null
                val method = frame.methodName.takeIf { it.matches(Regex("[A-Za-z0-9_$<>]{1,100}")) }
                    ?: return@mapNotNull null
                "$owner.$method:${frame.lineNumber.coerceAtLeast(-1)}"
            }
            framesByCause.add(safeFrames.take(limit))
            current = throwable.cause
        }
        val frames = mutableListOf<String>()
        var remaining = limit
        framesByCause.forEachIndexed { index, causeFrames ->
            if (remaining <= 0) return@forEachIndexed
            val causesLeft = framesByCause.size - index
            val quota = (remaining + causesLeft - 1) / causesLeft
            val selected = causeFrames.take(quota)
            frames.addAll(selected)
            remaining -= selected.size
        }
        return "${exceptionTypeChain(error)} | ${frames.joinToString(" <- ").ifBlank { "stack unavailable" }}"
    }

    /**
     * Converts persisted service status into a safe, actionable startup phase.
     * Raw exception messages may contain local paths or endpoints, so only the
     * known phase and exception class are included in support reports.
     */
    fun startupFailureSummary(serviceMessage: String?): String {
        val message = serviceMessage?.trim().orEmpty()
        if (message.isEmpty()) return "최근 시작 오류 기록 없음"
        val phase = when {
            message.startsWith("포그라운드 서비스 시작 실패") -> "Android 포그라운드 서비스 시작 실패"
            message.startsWith("필터 규칙 로드 실패") -> "필터 규칙/리소스 로드 실패"
            message.startsWith("업스트림 DNS 연결을 확인하지 못해") ||
                message.startsWith("보호 시작 전 DNS 연결 확인 실패") -> "업스트림 DNS 사전 연결 확인 실패"
            message.startsWith("VPN 연결 생성 실패") -> "Android VPN 인터페이스 생성 실패"
            message.startsWith("보호 시작 중 예기치 않은 오류") -> "VPN 시작 중 런타임 예외"
            else -> return "최근 시작 오류 기록 없음"
        }
        val exceptionType = Regex("\\(([A-Za-z][A-Za-z0-9_]*(?:Exception|Error)(?: <- [A-Za-z][A-Za-z0-9_]*(?:Exception|Error))*)\\)")
            .find(message)?.groupValues?.getOrNull(1)
        return phase + (exceptionType?.let { " ($it)" } ?: "")
    }
    fun statusDetail(
        active: Boolean,
        queries: Long,
        blocked: Long,
        forwarded: Long,
        failed: Long,
        privateDnsMode: String?,
        serviceMessage: String? = null
    ): String {
        if (!active) return serviceMessage?.takeIf { it.isNotBlank() }
            ?: "보호를 시작하면 시스템 가상 DNS를 통해 필터링합니다."
        val observed = "Guardian 관측 DNS 질의 ${queries.coerceAtLeast(0)}건 · 차단 ${blocked.coerceAtLeast(0)} · 업스트림 전달 ${forwarded.coerceAtLeast(0)} · 응답 실패 ${failed.coerceAtLeast(0)}."
        // A local VPN can inspect ordinary DNS packets, but it cannot infer hostnames
        // hidden inside an app's own HTTPS/TLS-based DoH/DoT connection.
        val coverageCaveat = "앱이 자체 DoH/DoT(HTTPS/TLS 기반 DNS)를 사용하거나 앱 내부에 광고를 포함하면 DNS 필터를 우회할 수 있습니다."
        return when (privateDnsMode) {
            "hostname" -> "VPN 터널은 실행 중이지만 Android 비공개 DNS 공급자가 암호화 DNS를 Guardian 필터 밖으로 보낼 수 있습니다. $observed $coverageCaveat"
            "opportunistic" -> "VPN 터널은 실행 중입니다. Android 비공개 DNS 자동 모드가 네트워크에 따라 필터를 우회할 수 있습니다. $observed $coverageCaveat"
            else -> if (queries <= 0L) {
                "VPN 터널은 실행 중이지만 아직 DNS 질의를 관측하지 못했습니다. 앱이나 웹사이트를 열어 요청을 발생시켜 주세요. 실제 광고 제거 여부는 별도 확인이 필요합니다. $coverageCaveat"
            } else {
                "$observed $coverageCaveat Guardian이 처리한 DNS 요청 수치만으로 앱 내부 광고까지 제거되었다고 판단할 수는 없습니다."
            }
        }
    }

    /**
     * Creates a support report from non-sensitive runtime metadata only.
     * DNS history, custom rules, license credentials, URLs and public keys are intentionally excluded.
     */
    /**
     * Restricts the persisted filter readiness note to known status labels so a
     * support report never copies arbitrary server text, domains or user rules.
     */
    fun filterReadinessSummary(value: String?): String {
        if (value == "모든 선택 필터가 준비되었습니다.") return "모든 선택 필터 준비됨"
        val prefix = "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: "
        if (value?.startsWith(prefix) != true) return "확인 불가"

        val entries = value.removePrefix(prefix).split(',').map { it.trim() }
        val baselineMissing = entries.any { it.startsWith("기본 필터 라이브러리 누락 (") }
        val allowed = setOf("광고·추적", "보안", "Android 앱 광고")
        val sources = entries.filter { it in allowed }.distinct()
        val details = buildList {
            if (baselineMissing) add("내장 긴급 DNS 규칙 사용 · 기본 필터 파일 복구 필요")
            if (sources.isNotEmpty()) add("추가 필터 준비 필요: ${sources.joinToString(", ")}")
        }
        return if (details.isEmpty()) "확인 불가"
        else "기본 규칙으로 시작 · ${details.joinToString(" · ")}"
    }

    /** Builds a copyable recent-error report without exposing raw exception messages, paths, URLs, domains or user rules. */
    fun recentErrorReport(
        lastServiceStatus: String?,
        lastStartupTrace: String?,
        filterErrorCodes: List<String>,
        filterReadiness: String?
    ): String {
        val startup = startupFailureSummary(lastServiceStatus)
        val safeTrace = lastStartupTrace.orEmpty()
            .split(" | ")
            .take(2)
            .joinToString(" | ")
            .filter { it.isLetterOrDigit() || it in " ._$<>:-|" }
            .take(1800)
        val safeCodes = filterErrorCodes
            .map { it.trim().uppercase(java.util.Locale.ROOT) }
            .filter { it.matches(Regex("[A-Z0-9_]{1,64}")) }
            .distinct()
            .take(3)
        return buildString {
            appendLine("Guardian 최근 오류 정보")
            appendLine("보호 시작: $startup")
            appendLine("호출 경로: ${safeTrace.ifBlank { "기록 없음" }}")
            appendLine("필터 오류:")
            if (safeCodes.isEmpty()) appendLine("기록 없음")
            else safeCodes.forEach { code -> appendLine("- ${SafeFailureSummary.describeCode(code)} ($code)") }
            appendLine("필터 준비 상태: ${filterReadinessSummary(filterReadiness)}")
            appendLine()
            appendLine("개인정보 보호: 원본 오류 메시지, 로컬 경로, 서버 URL/IP, 방문 도메인, 사용자 규칙 및 라이선스 키·토큰은 포함하지 않았습니다.")
        }
    }

    fun supportReport(
        appVersion: String,
        androidRelease: String,
        apiLevel: Int,
        protectionActive: Boolean,
        bundledRuleAssetAvailable: Boolean,
        selectedProfile: String,
        baseCacheBytes: Long,
        securityFilterEnabled: Boolean,
        securityCacheBytes: Long,
        androidAdsFilterEnabled: Boolean,
        androidAdsCacheBytes: Long,
        autoUpdateEnabled: Boolean,
        updateIntervalHours: Long,
        hasBaseUpdateTimestamp: Boolean,
        signedFilterDeliveryConfigured: Boolean,
        licenseApiConfigured: Boolean,
        filterUpdateErrorPresent: Boolean,
        lastServiceStatus: String? = null,
        lastStartupTrace: String? = null,
        bundledRuleAssetBytes: Long = -1L,
        nativeLibraryDirectoryReadable: Boolean = false,
        nativeLibraryNames: List<String> = emptyList(),
        supportedAbis: List<String> = emptyList(),
        baseCacheReady: Boolean? = null,
        cachedProfile: String? = null,
        cachedRuleCount: Int? = null,
        securityCacheReady: Boolean? = null,
        securityRuleCount: Int? = null,
        androidAdsCacheReady: Boolean? = null,
        androidAdsRuleCount: Int? = null,
        filterReadiness: String? = null
    ): String {
        fun cacheState(enabled: Boolean, bytes: Long): String = when {
            !enabled -> "사용 안 함"
            bytes > 0L -> "있음 (${bytes} bytes)"
            else -> "없음"
        }
        return buildString {
            appendLine("Guardian AdBlock 진단 정보")
            appendLine("앱 버전: $appVersion")
            appendLine("Android: $androidRelease (API $apiLevel)")
            appendLine("VPN 보호 상태: ${if (protectionActive) "활성" else "비활성"}")
            val assetDescription = when {
                !bundledRuleAssetAvailable -> "누락 또는 읽기 실패"
                bundledRuleAssetBytes >= 0L -> "정상 (${bundledRuleAssetBytes} bytes)"
                else -> "정상"
            }
            appendLine("내장 DNS 규칙 파일: $assetDescription")
            appendLine("앱 네이티브 라이브러리 디렉터리: ${if (nativeLibraryDirectoryReadable) "읽기 가능" else "읽기 불가 또는 없음"}")
            val safeLibraryNames = nativeLibraryNames
                .filter { it.matches(Regex("lib[A-Za-z0-9_+.-]+\\.so")) }
                .distinct()
                .sorted()
                .take(32)
            appendLine("설치된 네이티브 라이브러리: ${safeLibraryNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "없음 또는 확인 불가"}")
            val safeAbis = supportedAbis
                .filter { it.matches(Regex("[A-Za-z0-9_-]{1,32}")) }
                .distinct()
                .take(8)
            appendLine("기기 ABI: ${safeAbis.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "확인 불가"}")
            fun readiness(value: Boolean?): String = when (value) {
                true -> "정상"
                false -> "실패 또는 준비되지 않음"
                null -> "확인 불가"
            }
            fun safeId(value: String?): String = value
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,40}")) }
                ?: "unknown"
            appendLine("선택 필터 프로필: ${safeId(selectedProfile)}")
            appendLine("캐시 프로필: ${safeId(cachedProfile)}")
            appendLine("기본 필터 프로필 일치: ${if (cachedProfile == null) "확인 불가" else if (cachedProfile == selectedProfile) "일치" else "불일치"}")
            appendLine("기본 필터 캐시: ${cacheState(true, baseCacheBytes)}")
            appendLine("기본 필터 검증: ${readiness(baseCacheReady)} · 유효 규칙 ${cachedRuleCount?.coerceAtLeast(0) ?: "확인 불가"}개")
            appendLine("보안 필터: ${cacheState(securityFilterEnabled, securityCacheBytes)} · 검증 ${if (securityFilterEnabled) readiness(securityCacheReady) else "사용 안 함"} · 유효 규칙 ${if (securityFilterEnabled) securityRuleCount?.coerceAtLeast(0) ?: "확인 불가" else "—"}개")
            appendLine("Android 앱 광고 필터: ${cacheState(androidAdsFilterEnabled, androidAdsCacheBytes)} · 검증 ${if (androidAdsFilterEnabled) readiness(androidAdsCacheReady) else "사용 안 함"} · 유효 규칙 ${if (androidAdsFilterEnabled) androidAdsRuleCount?.coerceAtLeast(0) ?: "확인 불가" else "—"}개")
            appendLine("자동 업데이트: ${if (autoUpdateEnabled) "켜짐" else "꺼짐"}")
            appendLine("업데이트 주기: ${updateIntervalHours.coerceAtLeast(1)}시간")
            appendLine("기본 필터 업데이트 기록: ${if (hasBaseUpdateTimestamp) "있음" else "없음"}")
            appendLine("서명 필터 배포 설정: ${if (signedFilterDeliveryConfigured) "설정됨" else "미설정"}")
            appendLine("라이선스 API 설정: ${if (licenseApiConfigured) "설정됨" else "미설정"}")
            appendLine("최근 필터 업데이트 오류 기록: ${if (filterUpdateErrorPresent) "있음" else "없음"}")
            appendLine("필터 시작 준비 상태: ${filterReadinessSummary(filterReadiness)}")
            appendLine("최근 보호 시작 오류: ${startupFailureSummary(lastServiceStatus)}")
            val safeTrace = lastStartupTrace.orEmpty().split(" | ").take(2).joinToString(" | ")
                .filter { it.isLetterOrDigit() || it in " ._$<>:-|" }.take(1800)
            appendLine("최근 오류 호출 스택(메시지/경로 제외): ${safeTrace.ifBlank { "기록 없음" }}")
            appendLine()
            appendLine("개인정보 보호: DNS 요청/방문 도메인, 사용자 지정 차단·허용 규칙, 라이선스 키·토큰, 서버 주소 및 공개키는 이 보고서에 포함하지 않았습니다.")
        }
    }
}