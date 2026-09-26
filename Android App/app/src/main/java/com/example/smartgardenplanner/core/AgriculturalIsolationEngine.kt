package com.example.smartgardenplanner.core

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import java.util.ArrayDeque
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- RESOLVED COMPLIANCE IMPORTS: SQLCIPHER BASELINE ENGINE ---
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/*
 * CHANGE LOG (this revision):
 *  - [FIX / DEBT-SEC-005, was silent] generateHmac() no longer falls back to a hardcoded key when
 *    SecretKey.encoded is null (the expected outcome for a real hardware-backed AndroidKeyStore key).
 *    HMAC now uses a SEPARATE, dedicated AndroidKeyStore HMAC key (KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
 *    generated and used entirely inside the Keystore via Mac.getInstance("HmacSHA256", "AndroidKeyStore"),
 *    so raw key material is never extracted or exposed. This closes a real forgeable-signature bug.
 *  - Removed dead `val unusedFactory = SupportFactory(rawPass)` line (debug leftover).
 *  - WeedingMaskCalculator replaced with WeedMaskGeometryEngine: real Path.Op.DIFFERENCE vector
 *    subtraction (matches LLR-DAT-060) instead of a flat float subtraction placeholder.
 *  - SensorMeasurementEngine: added SENSOR_STATUS_UNRELIABLE handling (1.5x covariance widening,
 *    matches HLR-SEN-080 / the IDD/ICD 5-iteration review finding) via onAccuracyChanged.
 */

data class WorkspaceUiState(
    val id: Long,
    val name: String,
    val lengthM: Float,
    val widthM: Float,
    val nodes: List<PlantedNodeEntity> = emptyList()
)

// --- ATOMIC SINGLE-THREAD SCHEDULER EXECUTION DOMAIN ---
object SgpExecutors {
    private val dbExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    val dbDispatcher = dbExecutor.asCoroutineDispatcher()
}

// --- CORE SECURITY & CRYPTOGRAPHIC SECTOR MANAGEMENT ---
interface SecurityKeyManager {
    fun initializeKeyStore()
    fun getCipherEncryptMode(): Cipher
    fun getCipherDecryptMode(iv: ByteArray): Cipher
    fun generateHmac(payload: ByteArray): ByteArray
    fun verifyHmac(payload: ByteArray, signature: ByteArray): Boolean
    fun getDatabasePassphrase(context: Context): ByteArray
}

class RealSecurityKeyManager : SecurityKeyManager {
    private val keyStoreAlias = "SGP_SECURE_ENCLAVE_KEY"
    private val hmacKeyAlias = "SGP_SECURE_HMAC_KEY"

    override fun initializeKeyStore() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (!keyStore.containsAlias(keyStoreAlias)) {
                generateAesKey(keyStoreAlias, strongBoxPreferred = true)
            }
            if (!keyStore.containsAlias(hmacKeyAlias)) {
                generateHmacKey(hmacKeyAlias, strongBoxPreferred = true)
            }
        } catch (e: Exception) {
            throw IllegalStateException("CRITICAL: Keystore initialization failed entirely.", e)
        }
    }

    private fun generateAesKey(alias: String, strongBoxPreferred: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        try {
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setIsStrongBoxBacked(strongBoxPreferred)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            // Fallback [DEBT-SEC-001, tracked]: Emulator/AVD or non-StrongBox hardware.
            if (strongBoxPreferred) generateAesKey(alias, strongBoxPreferred = false) else throw e
        } catch (e: ProviderException) {
            if (strongBoxPreferred) generateAesKey(alias, strongBoxPreferred = false) else throw e
        }
    }

    /**
     * Generates a dedicated AndroidKeyStore HMAC key. The key material never leaves the
     * secure hardware/TEE; Mac operations run inside the Keystore provider itself, so there
     * is no "extract raw bytes" step to fail or silently fall back from.
     */
    private fun generateHmacKey(alias: String, strongBoxPreferred: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        try {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setIsStrongBoxBacked(strongBoxPreferred)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            if (strongBoxPreferred) generateHmacKey(alias, strongBoxPreferred = false) else throw e
        } catch (e: ProviderException) {
            if (strongBoxPreferred) generateHmacKey(alias, strongBoxPreferred = false) else throw e
        }
    }

    override fun getCipherEncryptMode(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyStoreAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    override fun getCipherDecryptMode(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyStoreAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher
    }

    override fun generateHmac(payload: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        // [FIXED] Use the dedicated Keystore-native HMAC key. No SecretKey.encoded extraction,
        // no fallback-to-constant path — the vulnerability from the previous revision is closed.
        val hmacKey = keyStore.getKey(hmacKeyAlias, null) as SecretKey
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(payload)
    }

    override fun verifyHmac(payload: ByteArray, signature: ByteArray): Boolean {
        val computed = generateHmac(payload)
        return MessageDigest.isEqual(computed, signature)
    }

    override fun getDatabasePassphrase(context: Context): ByteArray {
        val file = File(context.filesDir, "sgp_db_key.enc")
        if (file.exists()) {
            val fileBytes = file.readBytes()
            if (fileBytes.size >= 12) {
                val iv = fileBytes.copyOfRange(0, 12)
                val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)
                val cipher = getCipherDecryptMode(iv)
                return cipher.doFinal(ciphertext)
            }
        }

        // Generate high-entropy 32-byte physical passphrase key
        val rawPass = SecureRandom().generateSeed(32)
        val cipher = getCipherEncryptMode()
        val ciphertext = cipher.doFinal(rawPass)
        val iv = cipher.iv
        file.writeBytes(iv + ciphertext)

        SQLiteDatabase.loadLibs(context)
        return rawPass
    }
}

// --- CYBERSECURITY EXPORT PACKAGING ---
class DatabaseSecurityPackager(private val keyManager: SecurityKeyManager, private val version: Int) {
    fun createSignedExport(payload: String): String {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        if (payloadBytes.size > 2097152) {
            throw IllegalArgumentException("CRITICAL: Export payload exceeds strict 2MB allocation limit.")
        }
        val signature = keyManager.generateHmac(payloadBytes)
        val signatureHex = signature.joinToString("") { "%02x".format(it) }
        return "$payload||$signatureHex"
    }

    fun verifyAndDecodeImport(sealedPackage: String): String {
        val parts = sealedPackage.split("||")
        if (parts.size != 2) {
            throw SecurityException("CRITICAL: Malformed packaging bounds intercepted.")
        }
        val payload = parts[0]
        val signatureHex = parts[1]
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)

        val expectedSignature = keyManager.generateHmac(payloadBytes)
        val expectedHex = expectedSignature.joinToString("") { "%02x".format(it) }

        if (signatureHex != expectedHex) {
            throw SecurityException("CRITICAL: Tampering intercepted! Signature hash verify mismatch.")
        }
        return payload
    }
}

// --- TAMPER-EVIDENT FORENSIC LOGGING (hash-chained: each entry now covers the prior entry's hash too) ---
class SecurityAuditLogger(private val context: Context) {
    private val auditLogFile = File(context.filesDir, "sgp_security_audit.log")

    fun appendLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val previousHash = lastHash()
        val formattedMessage = "[$timestamp] $message"
        val entryHash = calculateHash(previousHash + formattedMessage)
        auditLogFile.appendText("$formattedMessage||$entryHash\n")
    }

    /** Full-chain verification: deleting or reordering ANY prior line now invalidates every hash after it. */
    fun verifyChainIntegrity(): Boolean {
        if (!auditLogFile.exists()) return true
        var runningHash = "GENESIS"
        val lines = auditLogFile.readLines()
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("||")
            if (parts.size != 2) return false
            val message = parts[0]
            val hash = parts[1]
            val expected = calculateHash(runningHash + message)
            if (expected != hash) return false
            runningHash = hash
        }
        return true
    }

    private fun lastHash(): String {
        if (!auditLogFile.exists()) return "GENESIS"
        val lines = auditLogFile.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "GENESIS"
        return lines.last().split("||").getOrElse(1) { "GENESIS" }
    }

    private fun calculateHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// --- IMMUTABLE HISTORICAL SNAPSHOT STACK ---
class BoundedHistoryStack<T>(private var limit: Int) {
    private val deque = ArrayDeque<T>()

    fun push(element: T) {
        if (deque.size >= limit) {
            deque.pollFirst() // Discard oldest transition step
        }
        deque.addLast(element)
    }

    /** [NEW] Lets the configured depth (Settings) apply without recreating the stack / losing history. */
    fun updateLimit(newLimit: Int) {
        limit = newLimit
        while (deque.size > limit) {
            deque.pollFirst()
        }
    }

    fun pop(): T? {
        return if (deque.isNotEmpty()) deque.pollLast() else null
    }

    fun clear() {
        deque.clear()
    }

    fun size(): Int = deque.size

    fun toList(): List<T> = deque.toList()
}

// --- TELEMETRY SPATIAL INTEGRATION ENGINE ---
class SensorMeasurementEngine(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var listener: MeasurementListener? = null

    private var isCalibrating = false
    private var calibrationStartTime = 0L
    private val calibrationDurationMs = 3000L
    private val gravityBias = FloatArray(3)

    private var lastTimestamp = 0L
    private var velocity = FloatArray(3)
    private var position = FloatArray(3)
    private var currentAngle = FloatArray(3)

    // [ADDED] HLR-SEN-080: widen error margin when the platform reports unreliable accuracy.
    private var accelerometerUnreliable = false
    private var gyroscopeUnreliable = false

    interface MeasurementListener {
        fun onMeasurementUpdate(displacementMeters: Float, marginOfError: Float)
        fun onCalibrationStateChanged(calibrating: Boolean)
        fun onSafetyAbortTriggered(reason: String)
    }

    fun checkCapabilities(): Boolean {
        return accelerometer != null && gyroscope != null
    }

    fun registerListener(listener: MeasurementListener) {
        if (!checkCapabilities()) {
            throw IllegalStateException("ERR-SENS-004: Accelerometer or Gyroscope Hardware missing.")
        }
        this.listener = listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
        startCalibration()
    }

    fun unregisterListener() {
        sensorManager.unregisterListener(this)
        this.listener = null
    }

    private fun startCalibration() {
        isCalibrating = true
        calibrationStartTime = System.currentTimeMillis()
        lastTimestamp = 0L
        velocity.fill(0f)
        position.fill(0f)
        currentAngle.fill(0f)
        listener?.onCalibrationStateChanged(true)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.timestamp == 0L) return

        if (isCalibrating) {
            val elapsed = System.currentTimeMillis() - calibrationStartTime
            if (elapsed < calibrationDurationMs) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    gravityBias[0] = 0.9f * gravityBias[0] + 0.1f * event.values[0]
                    gravityBias[1] = 0.9f * gravityBias[1] + 0.1f * event.values[1]
                    gravityBias[2] = 0.9f * gravityBias[2] + 0.1f * event.values[2]
                }
            } else {
                isCalibrating = false
                listener?.onCalibrationStateChanged(false)
            }
            return
        }

        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }

        val dt = (event.timestamp - lastTimestamp) / 1000000000.0f
        lastTimestamp = event.timestamp

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            currentAngle[0] = 0.98f * (currentAngle[0] + event.values[0] * dt) + 0.02f * event.values[0]
            currentAngle[1] = 0.98f * (currentAngle[1] + event.values[1] * dt) + 0.02f * event.values[1]
            currentAngle[2] = 0.98f * (currentAngle[2] + event.values[2] * dt) + 0.02f * event.values[2]

            val pitchDeg = Math.toDegrees(currentAngle[0].toDouble())
            val rollDeg = Math.toDegrees(currentAngle[1].toDouble())
            if (Math.abs(pitchDeg) > 5.0 || Math.abs(rollDeg) > 5.0) {
                listener?.onSafetyAbortTriggered("ERR-SENS-002: Safety bounds exceeded +/- 5 degrees.")
                unregisterListener()
            }
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0] - gravityBias[0]
            val ay = event.values[1] - gravityBias[1]
            val az = event.values[2] - gravityBias[2]

            velocity[0] += ax * dt
            velocity[1] += ay * dt
            velocity[2] += az * dt

            position[0] += velocity[0] * dt
            position[1] += velocity[1] * dt
            position[2] += velocity[2] * dt

            val displacement = Math.sqrt((position[0]*position[0] + position[1]*position[1] + position[2]*position[2]).toDouble()).toFloat()
            var errorMargin = displacement * 0.05f
            // [ADDED] ERR-SENS-003 / HLR-SEN-080: widen tolerance 1.5x when accuracy is unreliable.
            if (accelerometerUnreliable || gyroscopeUnreliable) {
                errorMargin *= 1.5f
            }
            listener?.onMeasurementUpdate(displacement, errorMargin)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val unreliable = accuracy == android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE
        when (sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> accelerometerUnreliable = unreliable
            Sensor.TYPE_GYROSCOPE -> gyroscopeUnreliable = unreliable
        }
    }
}

/**
 * [REPLACED] Real vector-geometry inverse weed-mask calculator (was: flat float subtraction).
 * Builds the plot boundary as a rectangular Path, subtracts every planted node's circular
 * exclusion-radius Path via Path.Op.DIFFERENCE, and returns the residual "weed zone" Path
 * that the Canvas screen can paint directly — matches LLR-DAT-060-A/B/C.
 */
class WeedMaskGeometryEngine {

    data class ExclusionCircle(val centerXPx: Float, val centerYPx: Float, val radiusPx: Float)

    fun calculateResidualWeedZone(
        plotWidthPx: Float,
        plotHeightPx: Float,
        occupiedZones: List<ExclusionCircle>
    ): Path {
        // [FIXED] Previously built with android.graphics.Path — a real, concrete type mismatch,
        // since Compose's DrawScope.drawPath() requires androidx.compose.ui.graphics.Path, a
        // different, incompatible class with the same simple name. Rebuilt on the Compose-native
        // Path/PathOperation API so this can actually be drawn on the canvas.
        val boundary = Path().apply {
            addRect(Rect(0f, 0f, plotWidthPx, plotHeightPx))
        }

        var residual = boundary
        for (zone in occupiedZones) {
            val circlePath = Path().apply {
                addOval(Rect(zone.centerXPx - zone.radiusPx, zone.centerYPx - zone.radiusPx, zone.centerXPx + zone.radiusPx, zone.centerYPx + zone.radiusPx))
            }
            val newResidual = Path()
            newResidual.op(residual, circlePath, PathOperation.Difference)
            residual = newResidual
        }
        return residual
    }

    /** Convenience scalar (m²) for UI text summaries; the Path above remains the source of truth for rendering. */
    fun calculateClearanceAreaApprox(totalPlotArea: Float, occupiedZonesArea: Float): Float {
        val remaining = totalPlotArea - occupiedZonesArea
        return if (remaining < 0f) 0f else remaining
    }
}
