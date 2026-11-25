package com.guardian.adblock

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/** Downloads and validates DNS-compatible community filters into app-private storage. */
object FilterListManager {
    const val FILTER_URL = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt"
    const val KEY_FILTER_PROFILE = "filter_profile"
    const val KEY_PENDING_PROFILE_ROLLBACK = "filter_profile_pending_rollback"
    const val KEY_CACHED_PROFILE = "filter_cached_profile"
    const val SECURITY_FILTER_URL = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.medium.txt"
    const val ANDROID_ADS_URL = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_53.txt"
    private const val CACHE_FILE = "adguard_dns_filter.txt"
    private const val SECURITY_CACHE_FILE = "hagezi_threat_intelligence.txt"
    private const val ANDROID_ADS_CACHE_FILE = "awavenue_android_ads.txt"
    private const val MAX_BYTES = MAX_FILTER_DOWNLOAD_BYTES
    const val MIN_SECURITY_BLOCKS = 100
    const val MIN_ANDROID_ADS_BLOCKS = 100
    private const val PREFS = "guardian_settings"
    const val KEY_UPDATED_AT = "filter_list_updated_at"
    private const val KEY_BLOCK_COUNT = "filter_list_block_count"
    private const val KEY_IGNORED_COUNT = "filter_list_ignored_count"
    private const val KEY_SECURITY_UPDATED_AT = "security_filter_updated_at"
    private const val KEY_SECURITY_BLOCK_COUNT = "security_filter_block_count"
    private const val KEY_SECURITY_IGNORED_COUNT = "security_filter_ignored_count"
    const val KEY_AUTO_UPDATE = "filter_auto_update"
    const val KEY_UPDATE_INTERVAL_MS = "filter_update_interval_ms"
    const val KEY_LAST_ERROR = "filter_list_last_error"
    const val KEY_SECURITY_LAST_ERROR = "security_filter_last_error"
    const val KEY_SECURITY_ENABLED = "security_filter_enabled"
    const val KEY_ANDROID_ADS_ENABLED = "android_ads_filter_enabled"
    const val KEY_ANDROID_ADS_UPDATED_AT = "android_ads_filter_updated_at"
    const val KEY_ANDROID_ADS_BLOCK_COUNT = "android_ads_filter_block_count"
    const val KEY_ANDROID_ADS_IGNORED_COUNT = "android_ads_filter_ignored_count"
    const val KEY_ANDROID_ADS_LAST_ERROR = "android_ads_filter_last_error"

    data class UpdateResult(val blocked: Int, val allowed: Int, val ignored: Int, val bytes: Int, val updatedAt: Long)
    data class FilterProfile(val key: String, val label: String, val url: String, val minimumRules: Int)

    private fun parsedBlockCount(parsed: FilterRuleParser.Parsed): Int =
        (parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private val profiles = listOf(
        FilterProfile("adguard", "AdGuard DNS Filter · 권장", FILTER_URL, 1000),
        FilterProfile("1hosts_lite", "1Hosts Lite · 균형", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_24.txt", 100),
        FilterProfile("oisd_big", "OISD Big · 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_27.txt", 100),
        FilterProfile("hagezi_normal", "HaGeZi Normal · 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_34.txt", 100),
        FilterProfile("hagezi_pro", "HaGeZi Pro · 매우 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_48.txt", 100)
    )

    fun availableProfiles(): List<FilterProfile> = profiles

    data class ManifestInfo(val sourceVersion: String, val sha256: String, val generatedAt: Long)

    fun manifestInfo(context: Context, cacheName: String): ManifestInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sourceId = if (cacheName == CACHE_FILE) prefs.getString(KEY_CACHED_PROFILE, "adguard") ?: "adguard" else null
        val keys = manifestPreferenceKeys(cacheName, sourceId)
        val version = prefs.getString(keys.first, null) ?: return null
        val hash = prefs.getString(keys.second, null) ?: return null
        val generatedAt = prefs.getLong(keys.third, 0L)
        if (version.isBlank() || !hash.matches(Regex("[a-fA-F0-9]{64}")) || generatedAt <= 0L) return null
        return ManifestInfo(version, hash, generatedAt)
    }

    fun signedManifestStatus(context: Context): String? {
        val entries = listOf(
            "광고·추적" to CACHE_FILE,
            "보안" to SECURITY_CACHE_FILE,
            "Android 앱 광고" to ANDROID_ADS_CACHE_FILE
        ).mapNotNull { (label, cacheName) ->
            val info = manifestInfo(context, cacheName) ?: return@mapNotNull null
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(info.generatedAt * 1000L))
            "$label 서명 버전 ${info.sourceVersion} · ${info.sha256.take(12)}… · $date"
        }
        return entries.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }

    internal fun manifestPreferenceKeys(cacheName: String, sourceId: String? = null): Triple<String, String, String> {
        val prefix = when (cacheName) {
            SECURITY_CACHE_FILE -> "security"
            ANDROID_ADS_CACHE_FILE -> "android_ads"
            else -> if (sourceId == null || sourceId == "adguard") "main" else "main_$sourceId"
        }
        return Triple(
            "manifest_${prefix}_source_version",
            "manifest_${prefix}_sha256",
            "manifest_${prefix}_generated_at"
        )
    }


    /** A cached list is usable for protection only when it matches the selected profile. */
    internal fun isProfileCacheCurrent(
        hasCache: Boolean,
        selectedKey: String,
        cachedKey: String,
        securityEnabled: Boolean = false,
        hasSecurityCache: Boolean = true,
        androidAdsEnabled: Boolean = false,
        hasAndroidAdsCache: Boolean = true
    ): Boolean = hasCache && selectedKey == cachedKey &&
        (!securityEnabled || hasSecurityCache) && (!androidAdsEnabled || hasAndroidAdsCache)
    fun cachedProfile(context: Context): FilterProfile {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CACHED_PROFILE, null)
        return profiles.firstOrNull { it.key == key } ?: profiles.first()
    }
    fun selectedProfile(context: Context): FilterProfile {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FILTER_PROFILE, "adguard")
        return profiles.firstOrNull { it.key == key } ?: profiles.first()
    }

    /** Delete invalid cache generations or fail closed if storage refuses cleanup. */
    private fun discardCacheFiles(target: File) {
        listOf(
            target,
            File(target.parentFile, "${target.name}.bak"),
            File(target.parentFile, "${target.name}.tmp")
        ).forEach { candidate ->
            try {
                Files.deleteIfExists(candidate.toPath())
            } catch (error: Exception) {
                throw IllegalStateException("손상된 필터 캐시 파일을 안전하게 제거하지 못했습니다.", error)
            }
        }
    }

    private fun recoverCache(context: Context, cacheName: String): File {
        val target = File(context.filesDir, cacheName)
        val signedConfigured = FilterDistributionSettings.requiresSignedDelivery(
            FilterDistributionSettings.configuredBaseUrl(context),
            BuildConfig.FILTER_MANIFEST_PUBLIC_KEY
        )
        if (!signedConfigured) {
            // Unsigned legacy caches still need a hard local size boundary. A
            // corrupted/oversized cache must never be loaded into memory without
            // the signed-hash path's MAX_BYTES check.
            val isBoundedCache: (File) -> Boolean = { candidate ->
                candidate.isFile && candidate.length() in 1..MAX_BYTES.toLong()
            }
            try {
                FilterCacheStore.recover(target, isBoundedCache)
                if (target.exists() && !isBoundedCache(target)) {
                    throw IllegalStateException("필터 캐시 크기가 허용 범위를 벗어났습니다.")
                }
            } catch (_: IllegalStateException) {
                discardCacheFiles(target)
                val countKeys = when (cacheName) {
                    SECURITY_CACHE_FILE -> KEY_SECURITY_BLOCK_COUNT to KEY_SECURITY_IGNORED_COUNT
                    ANDROID_ADS_CACHE_FILE -> KEY_ANDROID_ADS_BLOCK_COUNT to KEY_ANDROID_ADS_IGNORED_COUNT
                    else -> KEY_BLOCK_COUNT to KEY_IGNORED_COUNT
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(countKeys.first, 0)
                    .putInt(countKeys.second, 0)
                    .apply()
            }
            return target
        }
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sourceId = if (cacheName == CACHE_FILE) preferences.getString(KEY_CACHED_PROFILE, "adguard") ?: "adguard" else when (cacheName) {
            SECURITY_CACHE_FILE -> "security"
            ANDROID_ADS_CACHE_FILE -> "android_ads"
            else -> "adguard"
        }
        val keys = manifestPreferenceKeys(cacheName, sourceId)
        val expectedHash = preferences.getString(keys.second, null)
        if (expectedHash == null || !expectedHash.matches(Regex("[a-fA-F0-9]{64}"))) {
            // A cache without a committed signed hash may be a partially replaced first install
            // or an old unsigned cache. Neither is trusted once signed delivery is configured.
            discardCacheFiles(target)
            return target
        }
        fun matchesCommittedHash(candidate: File): Boolean {
            val bytes = readBoundedFile(candidate, MAX_BYTES) ?: return false
            return FilterManifestVerifier.matchesSha256(bytes, expectedHash)
        }
        try {
            FilterCacheStore.recover(target, ::matchesCommittedHash)
            if (target.exists() && !matchesCommittedHash(target)) {
                throw IllegalStateException("저장된 서명 해시와 필터 캐시가 일치하지 않습니다.")
            }
            // SharedPreferences may have committed immediately before process death while
            // the separate anti-rollback timestamp file had not yet been updated.
            val committedGeneratedAt = preferences.getLong(keys.third, 0L)
            if (committedGeneratedAt > 0L) {
                val timestampStore = ManifestTimestampStore(
                    File(context.filesDir, "manifest_timestamps.properties")
                )
                if (timestampStore.lastAccepted(sourceId) < committedGeneratedAt) {
                    timestampStore.restore(sourceId, committedGeneratedAt)
                }
            }
        } catch (_: IllegalStateException) {
            discardCacheFiles(target)
            preferences.edit()
                .remove(keys.first).remove(keys.second).remove(keys.third)
                .putInt(when (cacheName) {
                    SECURITY_CACHE_FILE -> KEY_SECURITY_BLOCK_COUNT
                    ANDROID_ADS_CACHE_FILE -> KEY_ANDROID_ADS_BLOCK_COUNT
                    else -> KEY_BLOCK_COUNT
                }, 0)
                .apply()
        }
        return target
    }

    fun hasCachedFile(context: Context): Boolean =
        recoverCache(context, CACHE_FILE).let { it.isFile && it.length() > 0L }

    internal fun isCacheValid(fileExists: Boolean, blockCount: Int, minimumRules: Int): Boolean =
        fileExists && minimumRules > 0 && blockCount >= minimumRules

    fun hasCachedList(context: Context): Boolean {
        val selected = selectedProfile(context)
        val cached = cachedProfile(context)
        val validForSelectedProfile = isCacheValid(
            hasCachedFile(context),
            cachedBlockCount(context),
            selected.minimumRules
        )
        // A cache downloaded for a different profile is never a substitute,
        // even when its rule count happens to exceed the newly selected profile's
        // threshold. This also makes first-run/profile-change recovery mandatory
        // when recurring updates are disabled.
        return isProfileCacheCurrent(
            hasCache = validForSelectedProfile,
            selectedKey = selected.key,
            cachedKey = cached.key
        )
    }

    fun hasCachedSecurityList(context: Context): Boolean {
        val fileExists = recoverCache(context, SECURITY_CACHE_FILE).let { it.isFile && it.length() > 0L }
        return isCacheValid(fileExists, cachedSecurityBlockCount(context), MIN_SECURITY_BLOCKS)
    }

    fun hasCachedAndroidAdsList(context: Context): Boolean {
        val fileExists = recoverCache(context, ANDROID_ADS_CACHE_FILE).let { it.isFile && it.length() > 0L }
        return isCacheValid(fileExists, cachedAndroidAdsBlockCount(context), MIN_ANDROID_ADS_BLOCKS)
    }
    fun cachedLines(context: Context): List<String> = readCache(context, CACHE_FILE)
    fun cachedSecurityLines(context: Context): List<String> = readCache(context, SECURITY_CACHE_FILE)
    fun cachedAndroidAdsLines(context: Context): List<String> = readCache(context, ANDROID_ADS_CACHE_FILE)

    /**
     * Loads only validated, enabled cache files for the optional Guardian WebView.
     * Parsing is streaming so large DNS lists are not duplicated as line lists.
     */
    fun cachedCosmeticRules(context: Context): CosmeticFilterEngine.RuleSet {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheNames = buildList {
            val baseProfileMatches = selectedProfile(context).key == cachedProfile(context).key
            if (baseProfileMatches && FilterStartupGate.isOptionalCacheReady { hasCachedList(context) }) {
                add(CACHE_FILE)
            }
            if (prefs.getBoolean(KEY_SECURITY_ENABLED, false) &&
                FilterStartupGate.isOptionalCacheReady { hasCachedSecurityList(context) }) {
                add(SECURITY_CACHE_FILE)
            }
            if (prefs.getBoolean(KEY_ANDROID_ADS_ENABLED, true) &&
                FilterStartupGate.isOptionalCacheReady { hasCachedAndroidAdsList(context) }) {
                add(ANDROID_ADS_CACHE_FILE)
            }
        }
        val sources = mutableListOf(CosmeticFilterEngine.parse(CosmeticFilterEngine.DEFAULT_RULES))
        for (cacheName in cacheNames) {
            val parsed = FilterStartupGate.optionalCacheResult {
                val file = recoverCache(context, cacheName)
                if (!file.isFile || file.length() <= 0L) null
                else file.bufferedReader(Charsets.UTF_8).use { CosmeticFilterEngine.parse(it) }
            } ?: continue
            if (parsed != null) sources.add(parsed)
        }
        return CosmeticFilterEngine.merge(sources)
    }

    /** Parse an app-private cache incrementally to avoid retaining millions of line objects. */
    internal fun parseCachedList(context: Context, cacheName: String): FilterRuleParser.Parsed {
        require(cacheName in setOf(CACHE_FILE, SECURITY_CACHE_FILE, ANDROID_ADS_CACHE_FILE)) {
            "지원하지 않는 필터 캐시입니다."
        }
        val file = recoverCache(context, cacheName)
        check(file.isFile && file.length() > 0L) { "검증된 필터 캐시 파일을 찾을 수 없습니다: $cacheName" }
        return file.bufferedReader(Charsets.UTF_8).use(FilterRuleParser::parse)
    }

    fun update(context: Context): UpdateResult {
        val profile = selectedProfile(context)
        return download(context, profile.url, CACHE_FILE, profile.minimumRules, KEY_UPDATED_AT, KEY_BLOCK_COUNT, KEY_IGNORED_COUNT, profile.key)
    }

    fun updateSecurity(context: Context): UpdateResult =
        download(context, SECURITY_FILTER_URL, SECURITY_CACHE_FILE, MIN_SECURITY_BLOCKS, KEY_SECURITY_UPDATED_AT, KEY_SECURITY_BLOCK_COUNT, KEY_SECURITY_IGNORED_COUNT)

    fun updateAndroidAds(context: Context): UpdateResult =
        download(context, ANDROID_ADS_URL, ANDROID_ADS_CACHE_FILE, MIN_ANDROID_ADS_BLOCKS,
            KEY_ANDROID_ADS_UPDATED_AT, KEY_ANDROID_ADS_BLOCK_COUNT, KEY_ANDROID_ADS_IGNORED_COUNT)

    private fun readCache(context: Context, name: String): List<String> {
        val file = recoverCache(context, name)
        return if (file.isFile && file.length() > 0L) file.readLines(Charsets.UTF_8) else emptyList()
    }

    @Synchronized
    private fun download(
        context: Context,
        url: String,
        cacheName: String,
        minimumRules: Int,
        updatedKey: String,
        countKey: String,
        ignoredKey: String,
        cachedProfileKey: String? = null
    ): UpdateResult {
        val startedAt = System.currentTimeMillis()
        android.util.Log.i("Guardian", "Filter download started: cache=$cacheName url=$url")
        val manifestBaseUrl = FilterDistributionSettings.configuredBaseUrl(context).trimEnd('/')
        val manifestPublicKey = BuildConfig.FILTER_MANIFEST_PUBLIC_KEY
        if (FilterDistributionSettings.requiresSignedDelivery(manifestBaseUrl, manifestPublicKey)) {
            require(manifestBaseUrl.isNotBlank() && manifestPublicKey.isNotBlank()) {
                "서명 manifest 서버 주소와 공개키를 함께 설정해야 합니다."
            }
            val sourceId = cachedProfileKey ?: when (cacheName) {
                SECURITY_CACHE_FILE -> "security"
                ANDROID_ADS_CACHE_FILE -> "android_ads"
                else -> "adguard"
            }
            return downloadSignedManifest(
                context, manifestBaseUrl, manifestPublicKey, sourceId, cacheName,
                minimumRules, updatedKey, countKey, ignoredKey, cachedProfileKey
            )
        }
        var connection: HttpsURLConnection? = null
        try {
            var currentUrl = URL(url)
            require(currentUrl.protocol.equals("https", ignoreCase = true) && currentUrl.userInfo == null) {
                "필터 다운로드 URL은 사용자 정보가 없는 HTTPS 주소여야 합니다."
            }
            var redirectsFollowed = 0
            while (connection == null) {
                val candidate = currentUrl.openConnection() as? HttpsURLConnection
                    ?: throw IllegalStateException("HTTPS 필터 연결을 만들 수 없습니다.")
                try {
                    candidate.requestMethod = "GET"
                    candidate.connectTimeout = 12000
                    candidate.readTimeout = 20000
                    candidate.instanceFollowRedirects = false
                    candidate.setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
                    candidate.setRequestProperty("Accept", "text/plain")
                    val status = candidate.responseCode
                    if (status in 300..399) {
                        val target = FilterRedirectPolicy.resolve(
                            currentUrl.toExternalForm(), status,
                            candidate.getHeaderField("Location"), redirectsFollowed
                        )
                        candidate.disconnect()
                        require(target != null) {
                            "필터 서버가 안전하지 않거나 허용 횟수를 초과한 리디렉션을 반환했습니다."
                        }
                        currentUrl = URL(target)
                        redirectsFollowed++
                    } else {
                        require(status in 200..299) { "필터 서버 HTTP $status" }
                        android.util.Log.i("Guardian", "Filter HTTP response: cache=$cacheName status=$status elapsedMs=${System.currentTimeMillis() - startedAt}")
                        connection = candidate
                    }
                } catch (e: Exception) {
                    if (connection !== candidate) candidate.disconnect()
                    throw e
                }
            }
            val bytes = connection!!.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "필터 파일이 허용 크기(32MB)를 초과했습니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            android.util.Log.i("Guardian", "Filter bytes downloaded: cache=$cacheName bytes=${bytes.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            android.util.Log.i("Guardian", "Filter parsing started: cache=$cacheName")
            // Public community feeds may contain isolated legacy-encoding bytes in comments.
            // Replace malformed sequences; invalid rule tokens are then rejected by the parser.
            val parsed = FilterRuleParser.parse(FilterManifestVerifier.decodeUtf8Lenient(bytes))
            android.util.Log.i("Guardian", "Filter parsing completed: cache=$cacheName blocked=${parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong()} elapsedMs=${System.currentTimeMillis() - startedAt}")
            require((parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong()) >= minimumRules) {
                "필터 검증 실패: 유효한 차단 도메인이 ${parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong()}개뿐입니다."
            }
            val target = File(context.filesDir, cacheName)
            val now = System.currentTimeMillis()
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousUpdatedAt = preferences.getLong(updatedKey, 0L)
            val previousBlockCount = preferences.getInt(countKey, 0)
            val previousIgnoredCount = preferences.getInt(ignoredKey, 0)
            val previousCachedProfile = preferences.getString(KEY_CACHED_PROFILE, null)
            val metadata = preferences.edit()
                .putLong(updatedKey, now)
                .putInt(countKey, parsedBlockCount(parsed))
                .putInt(ignoredKey, parsed.ignored)
            if (cachedProfileKey != null) metadata.putString(KEY_CACHED_PROFILE, cachedProfileKey)
            android.util.Log.i("Guardian", "Filter cache commit started: cache=$cacheName")
            FilterCacheStore.replace(
                target,
                bytes,
                commitMetadata = {
                    check(metadata.commit()) { "필터 캐시 메타데이터를 저장하지 못했습니다." }
                },
                rollbackMetadata = {
                    val rollback = preferences.edit()
                        .putLong(updatedKey, previousUpdatedAt)
                        .putInt(countKey, previousBlockCount)
                        .putInt(ignoredKey, previousIgnoredCount)
                    if (cachedProfileKey != null) {
                        if (previousCachedProfile == null) rollback.remove(KEY_CACHED_PROFILE)
                        else rollback.putString(KEY_CACHED_PROFILE, previousCachedProfile)
                    }
                    check(rollback.commit()) { "이전 필터 메타데이터를 복구하지 못했습니다." }
                }
            )
            android.util.Log.i("Guardian", "Filter cache committed: cache=$cacheName bytes=${bytes.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            return UpdateResult(parsedBlockCount(parsed), parsed.allowed.size, parsed.ignored, bytes.size, now)
        } finally {
            connection?.disconnect()
        }
    }


    private fun downloadSignedManifest(
        context: Context,
        manifestBaseUrl: String,
        publicKey: String,
        sourceId: String,
        cacheName: String,
        minimumRules: Int,
        updatedKey: String,
        countKey: String,
        ignoredKey: String,
        cachedProfileKey: String?
    ): UpdateResult {
        recoverCache(context, cacheName)
        val stateStore = ManifestTimestampStore(File(context.filesDir, "manifest_timestamps.properties"))
        val previousGeneratedAt = stateStore.lastAccepted(sourceId)
        val manifestUrl = "$manifestBaseUrl/$sourceId.manifest"
        val download = SignedManifestHttpClient().download(
            manifestUrl, sourceId, publicKey, previousGeneratedAt
        )
        require(download.manifest.minimumRules >= minimumRules) {
            "서명 manifest의 최소 규칙 수가 앱 정책보다 낮습니다."
        }
        val bytes = download.content
        // Signed payloads are hash-verified, but the filter syntax may still contain harmless
        // legacy-encoded comments. Decode with replacement; malformed rule tokens fail validation.
        val parsed = FilterRuleParser.parse(FilterManifestVerifier.decodeUtf8Lenient(bytes))
        val effectiveMinimum = maxOf(minimumRules, download.manifest.minimumRules)
        require(parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong() >= effectiveMinimum) {
            "서명 필터의 유효한 차단 도메인이 최소 규칙 수보다 적습니다."
        }

        val target = File(context.filesDir, cacheName)
        val now = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousUpdatedAt = preferences.getLong(updatedKey, 0L)
        val previousBlockCount = preferences.getInt(countKey, 0)
        val previousIgnoredCount = preferences.getInt(ignoredKey, 0)
        val previousCachedProfile = preferences.getString(KEY_CACHED_PROFILE, null)
        val infoKeys = manifestPreferenceKeys(cacheName, sourceId)
        val previousManifestVersion = preferences.getString(infoKeys.first, null)
        val previousManifestHash = preferences.getString(infoKeys.second, null)
        val previousManifestGeneratedAt = preferences.getLong(infoKeys.third, 0L)
        FilterManifestVerifier.requireImmutableSourceVersion(
            previousManifestVersion,
            previousManifestHash,
            download.manifest.sourceVersion,
            download.manifest.sha256
        )
        val metadata = preferences.edit()
            .putLong(updatedKey, now)
            .putInt(countKey, parsedBlockCount(parsed))
            .putInt(ignoredKey, parsed.ignored)
            .putString(infoKeys.first, download.manifest.sourceVersion)
            .putString(infoKeys.second, download.manifest.sha256)
            .putLong(infoKeys.third, download.manifest.generatedAt)
        if (cachedProfileKey != null) metadata.putString(KEY_CACHED_PROFILE, cachedProfileKey)
        FilterCacheStore.replace(
            target,
            bytes,
            commitMetadata = {
                check(metadata.commit()) { "필터 캐시 메타데이터를 저장하지 못했습니다." }
                stateStore.recordAccepted(sourceId, download.manifest.generatedAt)
            },
            rollbackMetadata = {
                val rollback = preferences.edit()
                    .putLong(updatedKey, previousUpdatedAt)
                    .putInt(countKey, previousBlockCount)
                    .putInt(ignoredKey, previousIgnoredCount)
                    .putLong(infoKeys.third, previousManifestGeneratedAt)
                if (previousManifestVersion == null) rollback.remove(infoKeys.first)
                else rollback.putString(infoKeys.first, previousManifestVersion)
                if (previousManifestHash == null) rollback.remove(infoKeys.second)
                else rollback.putString(infoKeys.second, previousManifestHash)
                if (cachedProfileKey != null) {
                    if (previousCachedProfile == null) rollback.remove(KEY_CACHED_PROFILE)
                    else rollback.putString(KEY_CACHED_PROFILE, previousCachedProfile)
                }
                try {
                    check(rollback.commit()) { "이전 필터 메타데이터를 복구하지 못했습니다." }
                } finally {
                    // Restore the anti-rollback watermark even if preference rollback
                    // itself fails; otherwise it could remain ahead of the restored cache.
                    stateStore.restore(sourceId, previousGeneratedAt)
                }
            }
        )
        return UpdateResult(
            parsedBlockCount(parsed), parsed.allowed.size,
            parsed.ignored, bytes.size, now
        )
    }

    fun lastUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED_AT, 0L)

    fun cachedBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BLOCK_COUNT, 0)

    fun cachedIgnoredCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_IGNORED_COUNT, 0)

    fun lastSecurityUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SECURITY_UPDATED_AT, 0L)

    fun cachedSecurityBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SECURITY_BLOCK_COUNT, 0)

    fun cachedSecurityIgnoredCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SECURITY_IGNORED_COUNT, 0)

    fun lastAndroidAdsUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ANDROID_ADS_UPDATED_AT, 0L)

    fun cachedAndroidAdsBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ANDROID_ADS_BLOCK_COUNT, 0)

    fun cachedAndroidAdsIgnoredCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ANDROID_ADS_IGNORED_COUNT, 0)
}
