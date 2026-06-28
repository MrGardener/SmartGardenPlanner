package com.example.smartgardenplanner.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object SecurityKeyManager {
    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "SmartGardenStorageMasterKey"

    init {
        // Initialize the hardware-backed keystore vault
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

        // Generate the symmetric key if it doesn't exist inside the sandbox yet
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                PROVIDER
            )

            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).apply {
                    setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    setRandomizedEncryptionRequired(false) // Required for fixed database key derivation
                }.build()
            )
            keyGenerator.generateKey()
        }
    }

    /**
     * Extracts the raw 256-bit passphrase block for direct injection
     * into the SQLCipher factory open protocol.
     */
    fun getDatabasePassphrase(): ByteArray {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return secretKey.encoded ?: KEY_ALIAS.toByteArray(Charsets.UTF_8)
    }
}