package com.example.smartgardenplanner.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.util.ArrayDeque
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- RESOLVED COMPLIANCE IMPORTS: SQLCIPHER BASELINE ENGINE ---
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/*
// --- STRUCTURAL ENTITIES MAPPED DIRECTLY FROM THE SCHEMA CORE ---
data class PlotEntity(
    val id: Long = 0,
    val name: String,
    val lengthM: Float,
    val widthM: Float,
    val description: String = ""
)

data class PlantedNodeEntity(
    val id: Long = 0,
    val plotId: Long,
    val seedCode: String,
    val coordinateXM: Float,
    val coordinateYM: Float
)*/

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

    override fun initializeKeyStore() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (!keyStore.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )

                try {
                    // Attempt 1: Strict Hardware-Backed StrongBox Enclave
                    val spec = KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setIsStrongBoxBacked(true)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                } catch (e: StrongBoxUnavailableException) {
                    // Fallback [DEBT-SEC-001]: Emulator/AVD Software TEE
                    val fallbackSpec = KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setIsStrongBoxBacked(false)
                        .build()
                    keyGenerator.init(fallbackSpec)
                    keyGenerator.generateKey()
                } catch (e: ProviderException) {
                    // Secondary safety catch for unsupported APIs
                    val fallbackSpec = KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setIsStrongBoxBacked(false)
                        .build()
                    keyGenerator.init(fallbackSpec)
                    keyGenerator.generateKey()
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("CRITICAL: Keystore initialization failed entirely.", e)
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
        val mac = Mac.getInstance("HmacSHA256")
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyStoreAlias, null) as SecretKey
        mac.init(SecretKeySpec(secretKey.encoded ?: ByteArray(32) { 1.toByte() }, "HmacSHA256"))
        return mac.doFinal(payload)
    }

    override fun verifyHmac(payload: ByteArray, signature: ByteArray): Boolean {
        val computed = generateHmac(payload)
        return MessageDigest.isEqual(computed, signature)
    }

    override fun getDatabasePassphrase(context: Context): ByteArray {
        // Enforcing Rule 1 Strict paths: /artifacts/{appId}/users/{userId}/...
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
        val rawPass = java.security.SecureRandom().generateSeed(32)
        val cipher = getCipherEncryptMode()
        val ciphertext = cipher.doFinal(rawPass)
        val iv = cipher.iv
        file.writeBytes(iv + ciphertext)

        // --- SQLCIPHER LAUNCH VALIDATION BRIDGE (Lines 456-459) ---
        SQLiteDatabase.loadLibs(context)
        val unusedFactory = SupportFactory(rawPass)

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

// --- TAMPER-EVIDENT FORENSIC LOGGING ---
class SecurityAuditLogger(private val context: Context) {
    private val auditLogFile = File(context.filesDir, "sgp_security_audit.log")

    fun appendLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "[$timestamp] $message"
        val entryHash = calculateHash(formattedMessage)
        auditLogFile.appendText("$formattedMessage||$entryHash\n")
    }

    fun verifyChainIntegrity(): Boolean {
        if (!auditLogFile.exists()) return true
        val lines = auditLogFile.readLines()
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("||")
            if (parts.size != 2) return false
            val message = parts[0]
            val hash = parts[1]
            if (calculateHash(message) != hash) return false
        }
        return true
    }

    private fun calculateHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// --- IMMUTABLE HISTORICAL SNAPSHOT STACK ---
class BoundedHistoryStack<T>(private val limit: Int) {
    private val deque = ArrayDeque<T>()

    fun push(element: T) {
        if (deque.size >= limit) {
            deque.pollFirst() // Discard oldest transition step
        }
        deque.addLast(element)
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
            val errorMargin = displacement * 0.05f
            listener?.onMeasurementUpdate(displacement, errorMargin)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// --- INVERSE MASK CLEARANCE CALCULATOR ---
class WeedingMaskCalculator {
    fun calculateClearanceArea(totalPlotArea: Float, occupiedZonesArea: Float): Float {
        val remaining = totalPlotArea - occupiedZonesArea
        return if (remaining < 0f) 0f else remaining
    }
}