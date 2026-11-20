package com.guardian.adblock

import java.io.IOException
import java.io.InputStream

/** Loads the mandatory APK-bundled DNS baseline with an actionable startup error. */
internal object BundledFilterAssetLoader {
    const val ASSET_NAME = "filter_rules.txt"

    fun load(openAsset: (String) -> InputStream): FilterRuleParser.Parsed {
        val stream = try {
            openAsset(ASSET_NAME)
        } catch (error: IOException) {
            throw IllegalStateException(
                "필수 기본 필터 자산 assets/$ASSET_NAME을 앱에서 읽을 수 없습니다. APK 패키징 및 설치 상태를 확인하세요.",
                error
            )
        } catch (error: SecurityException) {
            throw IllegalStateException(
                "필수 기본 필터 자산 assets/$ASSET_NAME 접근이 거부되었습니다.",
                error
            )
        }

        val parsed = try {
            stream.bufferedReader(Charsets.UTF_8).use(FilterRuleParser::parse)
        } catch (error: IOException) {
            throw IllegalStateException(
                "필수 기본 필터 자산 assets/$ASSET_NAME을 읽는 중 오류가 발생했습니다.",
                error
            )
        }
        check(parsed.blocked.isNotEmpty() || parsed.importantBlocked.isNotEmpty()) {
            "필수 기본 필터 자산 assets/$ASSET_NAME에 유효한 차단 규칙이 없습니다."
        }
        return parsed
    }
}
