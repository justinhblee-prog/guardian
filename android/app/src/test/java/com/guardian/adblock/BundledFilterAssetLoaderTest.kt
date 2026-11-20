package com.guardian.adblock

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledFilterAssetLoaderTest {
    @Test fun loadsValidBundledBaselineAndClosesStream() {
        var closed = false
        val stream = object : ByteArrayInputStream("||baseline.example^\n".toByteArray()) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val parsed = BundledFilterAssetLoader.load { name ->
            assertEquals("filter_rules.txt", name)
            stream
        }

        assertTrue(parsed.blocked.contains("baseline.example"))
        assertTrue(closed)
    }

    @Test fun missingBundledAssetProducesActionableDiagnostic() {
        val error = try {
            BundledFilterAssetLoader.load { throw FileNotFoundException(it) }
            throw AssertionError("Expected missing asset failure")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("assets/filter_rules.txt"))
        assertTrue(error.message.orEmpty().contains("APK"))
        assertTrue(error.cause is FileNotFoundException)
    }

    @Test fun emptyOrUnusableBundledAssetIsRejected() {
        val error = try {
            BundledFilterAssetLoader.load { ByteArrayInputStream("! no rules\n".toByteArray()) }
            throw AssertionError("Expected invalid baseline failure")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("유효한 차단 규칙"))
    }
}
