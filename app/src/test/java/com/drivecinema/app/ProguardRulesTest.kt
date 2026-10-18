package com.drivecinema.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardRulesTest {
    @Test
    fun testProguardContainsKeepRulesForMedia3AndBouncyCastle() {
        val rulesFile = File("proguard-rules.pro")
        assertTrue("proguard-rules.pro must exist in app module", rulesFile.exists())
        val rules = rulesFile.readText()
        assertTrue("Must keep Bouncy Castle SRP", rules.contains("org.bouncycastle.crypto.agreement.srp.**"))
        assertTrue("Must keep Room entities", rules.contains("androidx.room"))
    }
}
