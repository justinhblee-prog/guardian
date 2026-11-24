package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class AppPackageIdentityPolicyTest {
    @Test fun attributesOnlyAUniqueValidPackageForAUid() {
        assertEquals("com.example.mail", AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.mail")))
        assertNull(AppPackageIdentityPolicy.singleOwnerPackage(emptyArray()))
        assertNull(AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.one", "com.example.two")))
    }

    @Test fun deduplicatesIdenticalPackageEntriesButRejectsAmbiguousOwners() {
        assertEquals(
            "com.example.mail",
            AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.mail", "com.example.mail"))
        )
        assertNull(
            AppPackageIdentityPolicy.singleOwnerPackage(
                arrayOf("com.example.mail", "com.example.calendar")
            )
        )
    }

    @Test fun rejectsMalformedPackageNames() {
        listOf(
            arrayOf("not a package"),
            arrayOf("com..example"),
            arrayOf("../private"),
            arrayOf("com.example;injected")
        ).forEach { assertNull(AppPackageIdentityPolicy.singleOwnerPackage(it)) }
    }
}
