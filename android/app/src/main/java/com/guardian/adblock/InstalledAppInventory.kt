package com.guardian.adblock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import java.util.Locale

/** Shared inventory for every Guardian app picker. */
internal object InstalledAppInventory {
    data class Snapshot(
        val apps: List<Pair<String, String>>,
        val queryAllPackagesGranted: Boolean,
        val installedQuerySucceeded: Boolean,
        val launcherFallbackCount: Int,
        val installedQueryError: String?,
        val authoritativeInventory: Boolean
    ) {
        fun statusMessage(): String {
            if (apps.isEmpty()) {
                val permission = if (queryAllPackagesGranted) "허용됨" else "확인되지 않음"
                val query = installedQueryError?.let { " · 조회 오류: $it" }
                    ?: if (installedQuerySucceeded) " · PackageManager가 앱을 반환하지 않았습니다." else " · 설치 앱 조회 실패"
                return "설치 앱 0개 · 전체 앱 조회 권한 $permission$query · Android 설정에서 Guardian을 확인한 뒤 다시 시도해 주세요."
            }
            val fallback = if (launcherFallbackCount > 0) " · 실행 앱 ${launcherFallbackCount}개 추가 확인" else ""
            val trust = if (authoritativeInventory) {
                " · 전체 앱 목록 교차 확인 완료"
            } else {
                " · 일부 앱 누락 가능 · 기존 앱별 데이터는 보존"
            }
            return "설치 앱 ${apps.size}개 확인$fallback$trust"
        }
    }

    @Suppress("DEPRECATION")
    fun load(context: Context): Snapshot {
        val packageManager = context.packageManager
        val allPackagesGranted = if (Build.VERSION.SDK_INT >= 30) {
            context.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) == PERMISSION_GRANTED
        } else true
        val apps = linkedMapOf<String, String>()
        var installedQuerySucceeded = false
        val applicationPackages = linkedSetOf<String>()
        var installedPackagesQuerySucceeded = false
        val packageInfoPackages = linkedSetOf<String>()
        var installedQueryError: String? = null

        fun addApplication(info: ApplicationInfo?) {
            if (info == null || info.packageName == context.packageName) return
            val packageName = info.packageName
            if (apps.containsKey(packageName)) return
            val label = runCatching { info.loadLabel(packageManager).toString().trim() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
            apps[packageName] = label
        }

        try {
            val installed = packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            installedQuerySucceeded = true
            installed.forEach { info ->
                if (info.packageName != context.packageName) applicationPackages.add(info.packageName)
                addApplication(info)
            }
            android.util.Log.i("GuardianApps", "getInstalledApplications returned ${installed.size} entries")
        } catch (error: Exception) {
            installedQueryError = "${error.javaClass.simpleName}: ${error.message ?: "알 수 없는 오류"}"
            android.util.Log.e("GuardianApps", "Installed-app inventory query failed", error)
        }

        // Always run an independent PackageInfo enumeration as a cleanup guard.
        // If the two PackageManager APIs disagree, retain every stored app record:
        // OEMs can return partial inventories without throwing an exception.
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
            installedPackagesQuerySucceeded = true
            installedPackages.forEach { packageInfo ->
                if (packageInfo.packageName != context.packageName) packageInfoPackages.add(packageInfo.packageName)
                addApplication(packageInfo.applicationInfo)
            }
            android.util.Log.i("GuardianApps", "getInstalledPackages returned ${installedPackages.size} entries")
        } catch (error: Exception) {
            android.util.Log.e("GuardianApps", "Installed-package cross-check failed", error)
            if (installedQueryError == null) {
                installedQueryError = "${error.javaClass.simpleName}: ${error.message ?: "패키지 목록 교차 확인 실패"}"
            }
        }

        // Merge launcher-visible apps as an OEM/package-visibility fallback.
        var launcherFallbackCount = 0
        try {
            val launchers: List<ResolveInfo> = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            launchers.forEach { resolveInfo ->
                val info = resolveInfo.activityInfo?.applicationInfo
                if (info != null && info.packageName != context.packageName && !apps.containsKey(info.packageName)) {
                    addApplication(info)
                    launcherFallbackCount++
                }
            }
        } catch (error: Exception) {
            android.util.Log.e("GuardianApps", "Launcher inventory fallback failed", error)
            if (installedQueryError == null) {
                installedQueryError = "${error.javaClass.simpleName}: ${error.message ?: "런처 앱 조회 실패"}"
            }
        }

        val sorted = apps.entries.map { it.key to it.value }.sortedWith(
            compareBy<Pair<String, String>>(
                { it.second.lowercase(Locale.ROOT) },
                { it.first.lowercase(Locale.ROOT) }
            )
        )
        // Never delete app data from an empty or partial inventory. OEMs may return
        // incomplete non-empty lists without throwing; destructive cleanup requires
        // the full visibility grant and exact agreement from two independent APIs.
        val cleanupInventoryComplete = InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
            queryAllPackagesGranted = snapshot.queryAllPackagesGranted,
            applicationQuerySucceeded = snapshot.installedQuerySucceeded,
            packageInfoQuerySucceeded = installedPackagesQuerySucceeded,
            applicationPackages = applicationPackages,
            packageInfoPackages = packageInfoPackages
        )
        val snapshot = Snapshot(
            sorted, allPackagesGranted, installedQuerySucceeded, launcherFallbackCount,
            installedQueryError, cleanupInventoryComplete
        )
        if (cleanupInventoryComplete) {
            AppDnsActivityStore.pruneUninstalled(context, applicationPackages, inventoryComplete = true)
            PerAppDnsPolicyStore.pruneUninstalled(context, applicationPackages, inventoryComplete = true)
        }
        return snapshot
    }
}
