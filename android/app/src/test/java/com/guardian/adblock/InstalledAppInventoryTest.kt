package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppInventoryTest {
    @Test
    fun emptyInventoryExplainsQueryAndPackageVisibilityState() {
        val snapshot = InstalledAppInventory.Snapshot(
            apps = emptyList(),
            queryAllPackagesGranted = true,
            installedQuerySucceeded = true,
            launcherFallbackCount = 0,
            installedQueryError = null
        )

        val message = snapshot.statusMessage()
        assertTrue(message.contains("설치 앱 0개"))
        assertTrue(message.contains("전체 앱 조회 권한 허용됨"))
        assertTrue(message.contains("PackageManager가 앱을 반환하지 않았습니다."))
    }

    @Test
    fun failedInventoryIncludesTheUnderlyingError() {
        val snapshot = InstalledAppInventory.Snapshot(
            apps = emptyList(),
            queryAllPackagesGranted = false,
            installedQuerySucceeded = false,
            launcherFallbackCount = 0,
            installedQueryError = "SecurityException: package query denied"
        )

        val message = snapshot.statusMessage()
        assertTrue(message.contains("전체 앱 조회 권한 확인되지 않음"))
        assertTrue(message.contains("SecurityException: package query denied"))
    }

    @Test
    fun populatedInventoryReportsCountAndLauncherFallback() {
        val snapshot = InstalledAppInventory.Snapshot(
            apps = listOf(
                "com.android.settings" to "Settings",
                "com.google.android.apps.maps" to "Maps"
            ),
            queryAllPackagesGranted = true,
            installedQuerySucceeded = true,
            launcherFallbackCount = 1,
            installedQueryError = null
        )

        assertEquals("설치 앱 2개 조회 완료 · 실행 앱 1개 추가 확인", snapshot.statusMessage())
    }
}
