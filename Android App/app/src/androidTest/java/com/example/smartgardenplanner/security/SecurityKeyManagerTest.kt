package com.example.smartgardenplanner.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartgardenplanner.core.RealSecurityKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FIXED] Previously exercised the standalone, insecure `com.example.smartgardenplanner.security.
 * SecurityKeyManager` singleton (CBC mode, no-context signature, silently fell back to a hardcoded
 * passphrase). That class has been removed; this test now exercises `RealSecurityKeyManager`
 * (core package) — the implementation MainActivity actually uses — with assertions strong enough
 * to catch the hardcoded-fallback class of bug, not just "is the array non-empty".
 */
@RunWith(AndroidJUnit4::class)
class SecurityKeyManagerTest {

    @Test
    fun testKeystoreKeyGenerationAndRetrieval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyManager = RealSecurityKeyManager()
        keyManager.initializeKeyStore()

        val passphrase = keyManager.getDatabasePassphrase(context)

        assertNotNull("Passphrase byte array should not be null", passphrase)
        assertTrue("Passphrase should be 32 bytes (256-bit)", passphrase.size == 32)

        // The known insecure fallback from the old implementation — this MUST NOT be what we get.
        val hardcodedFallback = "SmartGardenStorageMasterKey".toByteArray(Charsets.UTF_8)
        assertFalse(
            "Passphrase must not equal the old hardcoded fallback string",
            passphrase.contentEquals(hardcodedFallback)
        )
    }

    @Test
    fun testPassphrasePersistsAcrossCalls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyManager = RealSecurityKeyManager()
        keyManager.initializeKeyStore()

        val first = keyManager.getDatabasePassphrase(context)
        val second = keyManager.getDatabasePassphrase(context)

        assertArrayEquals("Repeated calls must return the same persisted passphrase", first, second)
    }

    @Test
    fun testHmac_isNotConstant_andVerifiesCorrectly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyManager = RealSecurityKeyManager()
        keyManager.initializeKeyStore()

        val payloadA = "plot-export-payload-A".toByteArray(Charsets.UTF_8)
        val payloadB = "plot-export-payload-B".toByteArray(Charsets.UTF_8)

        val hmacA = keyManager.generateHmac(payloadA)
        val hmacB = keyManager.generateHmac(payloadB)

        // The old bug: a hardcoded all-0x01 32-byte key would still produce a *deterministic*
        // per-payload HMAC, so this alone can't prove the fix — but a forged signature computed
        // with the known constant key would NOT verify against a real, device-bound key. That's
        // the property this test actually exercises:
        assertTrue("HMAC must verify against its own payload", keyManager.verifyHmac(payloadA, hmacA))
        assertFalse("HMAC for payload A must not verify against payload B's signature", keyManager.verifyHmac(payloadB, hmacA))
        assertNotEquals("Different payloads must not coincidentally produce identical HMACs",
            hmacA.toList(), hmacB.toList())

        // Forged signature using the OLD known-constant fallback key must NOT verify.
        val forgedKey = ByteArray(32) { 1.toByte() }
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(forgedKey, "HmacSHA256"))
        val forgedSignature = mac.doFinal(payloadA)
        assertFalse(
            "A signature forged with the old hardcoded fallback key must not verify",
            keyManager.verifyHmac(payloadA, forgedSignature)
        )
    }
}
