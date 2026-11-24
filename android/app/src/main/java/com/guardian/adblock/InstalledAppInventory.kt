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
        val installedQueryError: String?
    ) {
        fun statusMessage(): String {
            if (apps.isEmpty()) {
                val permission = if (queryAllPackagesGranted) "허용됨" else "확인되지 않음"
                val query = installedQueryError?.let { " · 조회 오류: $it" }
                    ?: if (installedQuerySucceeded) " · PackageManager가 앱을 반환하지 않았습니다." else " · 설치 앱 조회 실패"
                return "설치 앱 0개 · 전체 앱 조회 권한 $permission$query · Android 설정에서 Guardian을 확인한 뒤 다시 시도해 주세요."
            }
            val fallback = if (launcherFallbackCount > 0) " · 실행 앱 ${launcherFallbackCount}개 추가 확인" else ""
            return "설치 앱 ${apps.size}개 조회 완료$fallback"
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
        var installedQueryReturnedEntries = false
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
            installedQueryReturnedEntries = installed.isNotEmpty()
            installed.forEach(::addApplication)
            android.util.Log.i("GuardianApps", "getInstalledApplications returned ${installed.size} entries")
        } catch (error: Exception) {
            installedQueryError = "${error.javaClass.simpleName}: ${error.message ?: "알 수 없는 오류"}"
            android.util.Log.e("GuardianApps", "Installed-app inventory query failed", error)
        }

        // Try PackageInfo as a second PackageManager enumeration path. Some OEM
        // PackageManager implementations behave differently for these two APIs.
        if (apps.isEmpty()) {
            try {
                val installedPackages = packageManager.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
                installedPackages.forEach { addApplication(it.applicationInfo) }
                android.util.Log.i("GuardianApps", "getInstalledPackages fallback returned ${installedPackages.size} entries")
            } catch (error: Exception) {
                android.util.Log.e("GuardianApps", "Installed-package fallback query failed", error)
                if (installedQueryError == null) {
                    installedQueryError = "${error.javaClass.simpleName}: ${error.message ?: "패키지 목록 조회 실패"}"
                }
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
        val snapshot = Snapshot(sorted, allPackagesGranted, installedQuerySucceeded, launcherFallbackCount, installedQueryError)
        // Never delete app data from an empty or partial inventory. Some OEM/package
        // visibility failures return an empty list without throwing. Require the
        // primary enumeration to return entries, the full visibility grant and a
        // non-empty final inventory before treating omissions as uninstalled apps.
        if (snapshot.queryAllPackagesGranted && snapshot.installedQuerySucceeded &&
            installedQueryReturnedEntries && snapshot.apps.isNotEmpty()) {
            val installedPackageNames = snapshot.apps.mapTo(linkedSetOf()) { it.first }
            AppDnsActivityStore.pruneUninstalled(context, installedPackageNames, inventoryComplete = true)
            PerAppDnsPolicyStore.pruneUninstalled(context, installedPackageNames, inventoryComplete = true)
        }
        return snapshot
    }
}
