package com.guardian.adblock

import org.junit.Assert.*
import java.util.Locale
import org.junit.Test

class DomainFilterTest {
    @Test fun classifiesCommonAdAndTrackerDomainsForReporting() {
        assertEquals(DomainFilter.BlockCategory.AD, DomainFilter.classifyBlockedDomain("page.doubleclick.net"))
        assertEquals(DomainFilter.BlockCategory.AD, DomainFilter.classifyBlockedDomain("ads.adnxs.com"))
        assertEquals(DomainFilter.BlockCategory.TRACKER, DomainFilter.classifyBlockedDomain("www.google-analytics.com"))
        assertEquals(DomainFilter.BlockCategory.TRACKER, DomainFilter.classifyBlockedDomain("api.mixpanel.com"))
        assertEquals(DomainFilter.BlockCategory.OTHER, DomainFilter.classifyBlockedDomain("malware.example"))
    }

    @Test fun loadsSharedRuleFormatAndAllowTakesPrecedence() {
        DomainFilter.loadRules(listOf(
            "# comment",
            "block=doubleclick.net",
            "allow=trusted.doubleclick.net",
            "invalid line"
        ))
        assertTrue(DomainFilter.isBlocked("ads.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("trusted.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("x.trusted.doubleclick.net"))
    }

    @Test fun loadsParsedSourcesAndPreservesPerSourceValidation() {
        DomainFilter.loadParsedRuleSources(
            listOf(
                { FilterRuleParser.parse("block=ads.example\nallow=trusted.ads.example") },
                { FilterRuleParser.parse("block=tracker.example") }
            ),
            listOf(1, 1)
        )
        assertTrue(DomainFilter.isBlocked("cdn.ads.example"))
        assertTrue(DomainFilter.isBlocked("tracker.example"))
        assertFalse(DomainFilter.isBlocked("trusted.ads.example"))
        assertThrows(IllegalArgumentException::class.java) {
            DomainFilter.loadParsedRuleSources(
                listOf({ FilterRuleParser.parse("allow=only.example") }),
                listOf(1)
            )
        }
        assertTrue(DomainFilter.isBlocked("tracker.example"))
    }

    @Test fun rejectsSharedRulesWithoutValidBlockEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            DomainFilter.loadRules(listOf("# empty", "block=bad domain", "allow=example.com", "unknown=ads.example"))
        }
    }

    @Test fun blocksExactAndSubdomains() {
        // DomainFilter keeps process-wide rules; do not depend on another test's state.
        DomainFilter.loadRules(listOf("block=doubleclick.net"))
        assertTrue(DomainFilter.isBlocked("doubleclick.net"))
        assertTrue(DomainFilter.isBlocked("ads.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("notdoubleclick.net"))
        assertFalse(DomainFilter.isBlocked("example.com"))
    }

    @Test fun normalizesUppercaseDnsQuestionNamesWithRootLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val query = DnsProbe.buildQuery("doubleclick.net").also {
                for (i in 13 until 24) it[i] = (it[i].toInt() - 32).toByte()
                for (i in 25 until 28) it[i] = (it[i].toInt() - 32).toByte()
            }
            assertEquals("doubleclick.net", DomainFilter.readQuestionName(query))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test fun matchesLargeDownloadedRuleSetsByDomainSuffix() {
        val rules = (0 until 5000).map { "block=tracker$it.example" } + "block=ads.example"
        DomainFilter.loadRules(rules)
        assertTrue(DomainFilter.isBlocked("cdn.ads.example"))
        assertTrue(DomainFilter.isBlocked("tracker4999.example"))
        assertFalse(DomainFilter.isBlocked("notads.example"))
    }

    @Test fun extractsDnsQuestionName() {
        val q = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,
            0x0b,0x64,0x6f,0x75,0x62,0x6c,0x65,0x63,0x6c,0x69,0x63,0x6b,
            0x03,0x6e,0x65,0x74,0x00,0x00,0x01,0x00,0x01
        ).map { it.toByte() }.toByteArray()
        assertEquals("doubleclick.net", DomainFilter.readQuestionName(q))
        val nx = DomainFilter.makeNxdomain(q)!!
        assertEquals(0x81, nx[2].toInt() and 0xff)
        assertEquals(0x83, nx[3].toInt() and 0xff)
        assertEquals(0, nx[6].toInt() and 0xff)
    }

    @Test fun normalizesAndValidatesUserEnteredDomains() {
        assertEquals("ads.example.com", DomainFilter.normalizeRuleDomain("  ADS.Example.COM. "))
        assertNull(DomainFilter.normalizeRuleDomain("-ads.example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("ads-.example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("ads example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("한글.example"))
        assertNull(DomainFilter.normalizeRuleDomain(""))
    }

    @Test fun customBlockAndAllowRulesComposeWithSharedRules() {
        DomainFilter.loadRules(listOf(
            "block=doubleclick.net",
            "block=ads.customer.example",
            "allow=trusted.ads.customer.example"
        ))
        assertTrue(DomainFilter.isBlocked("banner.ads.customer.example"))
        assertFalse(DomainFilter.isBlocked("trusted.ads.customer.example"))
        assertFalse(DomainFilter.isBlocked("pixel.trusted.ads.customer.example"))
    }

    @Test fun failedRuleReplacementKeepsPreviouslyActiveRules() {
        DomainFilter.loadRules(listOf("block=stable.example"))
        assertThrows(IllegalArgumentException::class.java) {
            DomainFilter.loadRules(listOf("allow=only.example"))
        }
        assertTrue(DomainFilter.isBlocked("ads.stable.example"))
    }

    @Test fun rejectsDotsEmbeddedInsideSingleDnsWireLabel() {
        val ambiguous = intArrayOf(
            0,1,0x01,0,0,1,0,0,0,0,0,0,
            3,0x61,0x2e,0x62,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        assertNull(DomainFilter.readQuestionName(ambiguous))
    }

    @Test fun rejectsMalformedName() {
        assertNull(DomainFilter.readQuestionName(byteArrayOf(0,1,2)))
    }

    @Test fun rejectsDnsResponseAndMultipleQuestions() {
        val q = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val response = q.copyOf().also { it[2] = 0x81.toByte() }
        assertNull(DomainFilter.readQuestionName(response))
        val multi = q.copyOf().also { it[5] = 2 }
        assertNull(DomainFilter.makeNxdomain(multi))
    }

    @Test fun preservesRecursionDesiredInNxdomain() {
        val q = intArrayOf(
            0,1,0x01,0,0,1,0,0,0,0,0,0,
            1,0x61,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val nx = DomainFilter.makeNxdomain(q)!!
        assertEquals(0x81, nx[2].toInt() and 0xff)
        assertEquals(0x83, nx[3].toInt() and 0xff)
        assertEquals(1, nx[5].toInt() and 0xff)
    }
    @Test fun rejectsTruncatedAndQuestionMismatchUpstreamResponses() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val valid = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[2] = 0x83.toByte() }))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOfRange(0, valid.size - 1)))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[it.lastIndex] = 2 }))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[2] = 0x89.toByte() }))
    }

    @Test fun acceptsOnlyMatchingUpstreamDnsResponses() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val response = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
        assertTrue(DomainFilter.isMatchingResponse(query, response))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[1] = 0x35 }))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[2] = 0x01 }))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[2] = 0x83.toByte() }))
    }

    @Test fun matchesDnsQuestionNameCaseInsensitivelyButKeepsTypeAndClassExact() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val uppercaseResponse = query.copyOf().also {
            it[2] = 0x81.toByte(); it[3] = 0x80.toByte()
            for (i in 13 until 20) it[i] = (it[i].toInt() - 32).toByte()
            for (i in 21 until 24) it[i] = (it[i].toInt() - 32).toByte()
        }
        assertTrue(DomainFilter.isMatchingResponse(query, uppercaseResponse))
        val qtypeMismatch = uppercaseResponse.copyOf().also { it[it.size - 4] = 0; it[it.size - 3] = 28 }
        val qclassMismatch = uppercaseResponse.copyOf().also { it[it.lastIndex] = 2 }
        assertFalse(DomainFilter.isMatchingResponse(query, qtypeMismatch))
        assertFalse(DomainFilter.isMatchingResponse(query, qclassMismatch))
    }

    @Test fun returnsServfailWhenUpstreamCannotResolve() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x10,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val servfail = DomainFilter.makeServfail(query)!!
        assertEquals(0x81, servfail[2].toInt() and 0xff) // QR + RD
        assertEquals(0x92, servfail[3].toInt() and 0xff) // RA + CD + SERVFAIL
        assertEquals(1, servfail[5].toInt() and 0xff)
        assertEquals(0, servfail[7].toInt() and 0xff)
        assertNull(DomainFilter.makeServfail(byteArrayOf(1, 2, 3)))
    }


    @Test fun buildsTruncatedUdpReplyWithinSafePayloadLimit() {
        val query = DnsProbe.buildQuery("large-answer.example").also {
            it[2] = 0x01 // RD
            it[3] = 0x10 // CD
        }
        val truncated = DomainFilter.makeTruncatedResponse(query)!!
        val flags = ((truncated[2].toInt() and 0xff) shl 8) or (truncated[3].toInt() and 0xff)
        assertTrue((flags and 0x8000) != 0) // QR
        assertTrue((flags and 0x0200) != 0) // TC
        assertTrue((flags and 0x0080) != 0) // RA
        assertTrue((flags and 0x0100) != 0) // RD preserved
        assertTrue((flags and 0x0010) != 0) // CD preserved
        assertArrayEquals(query.copyOfRange(12, query.size), truncated.copyOfRange(12, truncated.size))
        assertEquals(1, ((truncated[4].toInt() and 0xff) shl 8) or (truncated[5].toInt() and 0xff))
        assertEquals(0, ((truncated[6].toInt() and 0xff) shl 8) or (truncated[7].toInt() and 0xff))
        assertTrue(truncated.size <= 1232)
    }

    @Test fun truncatedReplyRejectsMalformedDnsQuery() {
        assertNull(DomainFilter.makeTruncatedResponse(byteArrayOf(0, 1, 2)))
    }

    @Test fun restoresEmergencyBootstrapRulesWhenBundledAssetIsUnavailable() {
        DomainFilter.loadRules(listOf("block=temporary-test.example"))
        val count = DomainFilter.resetToBootstrapRules()

        assertTrue(count >= 1)
        assertTrue(DomainFilter.isBlocked("doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("temporary-test.example"))
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(count, 0))
    }

    @Test fun emergencyBootstrapLinesParseIntoAUsableDnsBaseline() {
        val parsed = FilterRuleParser.parse(DomainFilter.bootstrapRuleLines())
        assertTrue(parsed.blocked.isNotEmpty())
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(parsed.blocked.size, parsed.importantBlocked.size))
    }

    @Test fun appliesPersistedUserBlockAndAllowRulesAlongsideDownloadedSources() {
        DomainFilter.loadParsedRuleSources(
            sources = listOf({ FilterRuleParser.parse("block=feed-ads.example") }),
            minimumRuleCounts = listOf(1),
            userBlockDomains = listOf("CUSTOM.Block.Example."),
            userAllowDomains = listOf("trusted.feed-ads.example")
        )

        assertTrue(DomainFilter.isBlocked("cdn.custom.block.example"))
        assertTrue(DomainFilter.isBlocked("feed-ads.example"))
        assertFalse(DomainFilter.isBlocked("trusted.feed-ads.example"))
        assertFalse(DomainFilter.isBlocked("cdn.trusted.feed-ads.example"))
    }

    @Test fun importantDownloadedRulesCannotBeOverriddenByUserAllowRules() {
        DomainFilter.loadParsedRuleSources(
            sources = listOf({ FilterRuleParser.parse("block-important=critical.example") }),
            minimumRuleCounts = listOf(1),
            userAllowDomains = listOf("critical.example")
        )

        assertTrue(DomainFilter.isBlocked("critical.example"))
        assertTrue(DomainFilter.isBlocked("sub.critical.example"))
    }


    @Test fun restoresOnlyTheBuiltInEmergencyRulesWhenBundledAssetIsUnavailable() {
        DomainFilter.loadRules(listOf("block=custom-only.example"))
        val restoredCount = DomainFilter.resetToBootstrapRules()

        assertEquals(10, restoredCount)
        assertEquals(restoredCount, DomainFilter.bootstrapRuleCount())
        assertTrue(DomainFilter.isBlocked("ads.doubleclick.net"))
        assertTrue(DomainFilter.isBlocked("page.googlesyndication.com"))
        assertTrue(DomainFilter.isBlocked("tracker.scorecardresearch.com"))
        assertFalse(DomainFilter.isBlocked("custom-only.example"))
        assertFalse(DomainFilter.isBlocked("ordinary.example"))
        assertEquals(0, DomainFilter.allowedRuleCount())
    }

    @Test fun bootstrapRuleLinesAreParseableAndMatchTheRuntimeEmergencySet() {
        val parsed = FilterRuleParser.parse(DomainFilter.bootstrapRuleLines())
        assertEquals(DomainFilter.bootstrapRuleCount(), parsed.blocked.size)
        assertTrue(parsed.allowed.isEmpty())
        assertTrue(parsed.importantBlocked.isEmpty())
        DomainFilter.loadRules(DomainFilter.bootstrapRuleLines())
        assertTrue(DomainFilter.isBlocked("sub.doubleclick.net"))
        assertTrue(DomainFilter.isBlocked("sub.adform.net"))
    }

    @Test fun rejectsResponseOnlyAndReservedFlagsInClientDnsQueries() {
        val valid = DnsProbe.buildQuery("example.com")
        // AA, TC, RA and reserved Z are not valid in a standard client query.
        listOf(0x0400, 0x0200, 0x0080, 0x0040).forEach { invalidFlag ->
            val malformed = valid.copyOf().also {
                val flags = ((it[2].toInt() and 0xff) shl 8) or (it[3].toInt() and 0xff)
                val updated = flags or invalidFlag
                it[2] = (updated ushr 8).toByte()
                it[3] = updated.toByte()
            }
            assertNull("flag 0x${invalidFlag.toString(16)} must be rejected",
                DomainFilter.readQuestionName(malformed))
            assertNull(DomainFilter.makeNxdomain(malformed))
        }
    }

    @Test fun acceptsDnssecAndCheckingDisabledQueryFlags() {
        val query = DnsProbe.buildQuery("example.com").also {
            // AD and CD are legitimate query flags and must not be rejected.
            it[3] = (it[3].toInt() or 0x30).toByte()
        }
        assertEquals("example.com", DomainFilter.readQuestionName(query))
        assertNotNull(DomainFilter.makeServfail(query))
    }

    @Test fun rejectsReservedZeroDnsQuestionTypeAndClass() {
        val valid = DnsProbe.buildQuery("example.com")
        // QNAME is followed by the four-byte QTYPE/QCLASS tuple.
        val questionEnd = valid.size
        val invalidType = valid.copyOf().also {
            it[questionEnd - 4] = 0
            it[questionEnd - 3] = 0
        }
        val invalidClass = valid.copyOf().also {
            it[questionEnd - 2] = 0
            it[questionEnd - 1] = 0
        }
        assertNull(DomainFilter.readQuestionName(invalidType))
        assertNull(DomainFilter.makeNxdomain(invalidType))
        assertNull(DomainFilter.readQuestionName(invalidClass))
        assertNull(DomainFilter.makeServfail(invalidClass))
    }

    @Test fun acceptsNonZeroDnsQuestionTypesAndClasses() {
        val query = DnsProbe.buildQuery("example.com").also {
            val end = it.size
            // AAAA / IN, then an uncommon but non-reserved QTYPE/QCLASS pair.
            it[end - 4] = 0
            it[end - 3] = 28
            it[end - 2] = 0
            it[end - 1] = 1
        }
        assertEquals("example.com", DomainFilter.readQuestionName(query))
        assertNotNull(DomainFilter.makeNxdomain(query))
    }

}
