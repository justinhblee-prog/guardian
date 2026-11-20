package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DnsFailureContextTest {
    @Test fun concurrentRequestsKeepFailureReasonsIsolated() {
        val context = DnsFailureContext()
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val results = arrayOfNulls<String>(2)

        val first = Thread {
            context.set("UDP resolver timed out")
            ready.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            results[0] = context.get()
        }
        val second = Thread {
            context.set("DoH certificate validation failed")
            ready.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            results[1] = context.get()
            context.clear()
            assertNull(context.get())
        }

        first.start()
        second.start()
        assertEquals(true, ready.await(5, TimeUnit.SECONDS))
        release.countDown()
        first.join(5_000)
        second.join(5_000)

        assertEquals("UDP resolver timed out", results[0])
        assertEquals("DoH certificate validation failed", results[1])
        assertEquals(null, context.get())
    }
}
