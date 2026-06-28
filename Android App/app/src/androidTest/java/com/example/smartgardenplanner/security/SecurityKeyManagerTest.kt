package com.example.smartgardenplanner.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityKeyManagerTest {

    @Test
    fun testKeystoreKeyGenerationAndRetrieval() {
        // 1. Trigger the initialization and extract the hardware-linked passphrase bytes
        val passphrase = SecurityKeyManager.getDatabasePassphrase()

        // 2. Verify the byte array was generated successfully
        assertNotNull("Passphrase byte array should not be null", passphrase)

        // 3. Verify that the key contains actual data bytes (256-bit keys yield 32 bytes)
        assertTrue("Passphrase should contain cryptographic data", passphrase.isNotEmpty())
    }
}