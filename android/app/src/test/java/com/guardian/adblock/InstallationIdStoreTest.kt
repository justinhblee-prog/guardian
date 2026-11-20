package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

class InstallationIdStoreTest {
    @Test
    fun concurrentFirstRunCallsShareOnePersistedInstallationId() {
        val store = InstallationIdStore()
        val persisted = AtomicReference<String?>(null)
        val writes = AtomicInteger(0)
        val ready = CountDownLatch(24)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(12)
        try {
            val tasks = (1..24).map {
                executor.submit<String> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    store.getOrCreate(
                        readExisting = { persisted.get() },
                        create = { UUID.randomUUID().toString() },
                        persist = { value ->
                            writes.incrementAndGet()
                            persisted.set(value)
                            true
                        }
                    )
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val ids = tasks.map { it.get(5, TimeUnit.SECONDS) }.toSet()
            assertEquals(1, ids.size)
            assertEquals(1, writes.get())
            assertEquals(ids.single(), persisted.get())
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun existingInstallationIdIsNeverReplaced() {
        val store = InstallationIdStore()
        val writes = AtomicInteger(0)
        val value = store.getOrCreate(
            readExisting = { "existing-install-id" },
            create = { "unexpected-new-id" },
            persist = { writes.incrementAndGet(); true }
        )
        assertEquals("existing-install-id", value)
        assertEquals(0, writes.get())
    }

    @Test
    fun failedPersistenceDoesNotReturnAnUnstoredId() {
        val store = InstallationIdStore()
        try {
            store.getOrCreate(
                readExisting = { null },
                create = { "new-install-id" },
                persist = { false }
            )
            fail("persistence failure must not return an uncommitted ID")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("저장"))
        }
    }
}
