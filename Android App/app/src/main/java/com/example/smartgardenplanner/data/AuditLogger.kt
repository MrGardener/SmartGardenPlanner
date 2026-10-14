package com.example.smartgardenplanner.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.example.smartgardenplanner.core.AuditCipher
import com.example.smartgardenplanner.core.AuditEntry
import com.example.smartgardenplanner.core.AuditFiles
import com.example.smartgardenplanner.core.AuditLog
import com.example.smartgardenplanner.core.AuditReadout
import com.example.smartgardenplanner.core.AuditType
import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The security audit log (LLR-AUD, LLR-AUDS): encrypted, hash-chained entries in files/audit/audit-<n>.log, kept
 * outside the database so key loss and data resets can be recorded when the database can't be read (LLR-AUDS-060).
 * Recording never throws: a failure to write the log must not stop the app.
 */
class SecurityAuditLogger(context: Context) {
    private val dir = File(context.filesDir, "audit")
    private val log = AuditLog(FileAuditFiles(dir), KeystoreAuditCipher(), { System.currentTimeMillis() })

    init {
        // The plain-text log of earlier builds is kept, moved next to the new files.
        val legacy = File(context.filesDir, "sgp_security_audit.log")
        if (legacy.exists()) { dir.mkdirs(); legacy.renameTo(File(dir, "legacy-plain.log")) }
    }

    fun record(type: AuditType, details: Map<String, Any?> = emptyMap()): AuditEntry? = try {
        synchronized(LOCK) { log.append(type, details) }
    } catch (e: Exception) {
        android.util.Log.w("SgpAudit", "audit entry not written: ${e.javaClass.simpleName}")
        null
    }

    /** All entries in order and the verification result (LLR-AUDS-040), for Settings → About this planner. */
    fun read(): AuditReadout = synchronized(LOCK) { log.read() }

    companion object {
        private val LOCK = Any()
    }
}

/** files/audit/audit-<n>.log; every line is flushed to storage before append returns (LLR-AUDS-020). */
class FileAuditFiles(private val dir: File) : AuditFiles {
    private val name = Regex("^audit-(\\d+)\\.log$")

    override fun numbers(): List<Int> =
        dir.listFiles()?.mapNotNull { f -> name.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull() } ?: emptyList()

    override fun readLines(n: Int): List<String> = File(dir, AuditLog.fileName(n)).takeIf { it.exists() }?.readLines() ?: emptyList()

    override fun appendLine(n: Int, line: String) {
        dir.mkdirs()
        FileOutputStream(File(dir, AuditLog.fileName(n)), true).use { out ->
            out.write((line + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
    }

    override fun sizeBytes(n: Int): Long = File(dir, AuditLog.fileName(n)).length()

    override fun setAside(tag: String) {
        val aside = File(dir, "aside-$tag").apply { mkdirs() }
        numbers().forEach { n -> File(dir, AuditLog.fileName(n)).renameTo(File(aside, AuditLog.fileName(n))) }
    }
}

/**
 * AES-256-GCM with the keystore key `sgp_audit_v1` (non-exportable, no user authentication, StrongBox when the phone
 * has it). A line is Base64(IV[12] | ciphertext + 16-byte tag) (LLR-AUDS-010). A missing key is created again, so
 * after a key loss old lines don't open and the log starts a new chain (LLR-AUDS-050).
 */
class KeystoreAuditCipher : AuditCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setIsStrongBoxBacked(strongBox)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
        }
        return try { generate(true) } catch (e: StrongBoxUnavailableException) { generate(false) }
    }

    override fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            // The key can no longer be used: replace it (old lines then read as a key loss, LLR-AUDS-050).
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
            cipher.init(Cipher.ENCRYPT_MODE, key())
        }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    override fun open(line: String): ByteArray? = try {
        val bytes = Base64.decode(line, Base64.NO_WRAP)
        if (bytes.size < 12 + 16 + 1) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            cipher.doFinal(bytes, 12, bytes.size - 12)
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: GeneralSecurityException) {
        null
    }

    companion object {
        const val ALIAS = "sgp_audit_v1"
    }
}
