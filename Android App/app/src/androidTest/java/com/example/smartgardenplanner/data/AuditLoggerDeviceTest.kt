package com.example.smartgardenplanner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartgardenplanner.core.AuditType
import com.example.smartgardenplanner.core.AuditVerdict
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** LLR-AUDS-010/020/040 on a phone: keystore AES-GCM lines, files under files/audit, intact chain. */
@RunWith(AndroidJUnit4::class)
class AuditLoggerDeviceTest {

    @Test
    fun keystoreCipher_roundTripsAndRefusesChangedLines() {
        val cipher = KeystoreAuditCipher()
        val plain = "{\"seq\":1}".toByteArray()
        val line = cipher.seal(plain)
        assertArrayEquals(plain, cipher.open(line))
        assertTrue("a fresh IV each time", line != cipher.seal(plain))
        val bytes = android.util.Base64.decode(line, android.util.Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.open(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)))
        assertNull(cipher.open("not base64 !!"))
        assertNull(cipher.open(""))
    }

    @Test
    fun logger_writesEncryptedLinesAndReadsAnIntactChain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "audit").deleteRecursively()
        val logger = SecurityAuditLogger(context)
        logger.record(AuditType.TIER_SWITCH, mapOf("from" to "BASIC", "to" to "PRO", "kept" to 0))
        logger.record(AuditType.IMPORT_ACCEPTED, mapOf("plots" to 1))
        val r = logger.read()
        assertEquals(AuditVerdict.Intact, r.verdict)
        assertEquals(listOf(AuditType.TIER_SWITCH, AuditType.IMPORT_ACCEPTED), r.entries.map { it.type })
        val text = File(context.filesDir, "audit/audit-1.log").readText()
        assertTrue("stored encrypted", !text.contains("TIER_SWITCH"))
    }
}
