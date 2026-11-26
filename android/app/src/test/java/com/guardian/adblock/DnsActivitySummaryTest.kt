package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsActivitySummaryTest {
    @Test fun countsOnlyBlockedDomainsInTheRecentActivityWindow() {
        val raw = listOf(
            "차단 · ads.example",
            "전달 · www.example.com",
            "차단 · tracker.example",
            "차단 · ads.example",
            "DNS 응답 실패 · api.example",
            "차단 · tracker.example",
            "차단 · ads.example"
        ).joinToString("\n")

        assertEquals(
            "ads.example · 3회\ntracker.example · 2회",
            DnsActivitySummary.recentBlockedDomains(raw)
        )
    }

    @Test fun groupsDomainNamesWithoutCaseOrTrailingDotDifferences() {
        val raw = listOf(
            "차단 · ADS.Example.",
            "차단 · ads.example",
            "차단 · Tracker.Example."
        ).joinToString("\n")

        assertEquals(
            "ads.example · 2회\ntracker.example · 1회",
            DnsActivitySummary.recentBlockedDomains(raw)
        )
    }

    @Test fun limitsResultsAndHandlesEmptyOrMalformedHistory() {
        val raw = listOf(
            "차단 · z.example",
            "차단 · b.example",
            "차단 · a.example",
            "차단 · c.example"
        ).joinToString("\n")

        assertEquals(
            "a.example · 1회\nb.example · 1회",
            DnsActivitySummary.recentBlockedDomains(raw, 2)
        )
        assertEquals(
            "최근 기록에 차단된 도메인이 없습니다.",
            DnsActivitySummary.recentBlockedDomains("전달 · example.com\n잘못된 기록")
        )
        assertEquals("", DnsActivitySummary.recentBlockedDomains(raw, 0))
    }
}
