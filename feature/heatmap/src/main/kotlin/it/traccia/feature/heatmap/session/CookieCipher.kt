package it.traccia.feature.heatmap.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Encrypts the stored session cookies (M3.2). */
interface CookieCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(encrypted: ByteArray): ByteArray
}

/**
 * AES-GCM with a non-exportable key in the Android Keystore. Output: 12-byte IV followed by the
 * ciphertext. (EncryptedSharedPreferences, named in features.md, is deprecated: this is its core.)
 */
internal class KeystoreCookieCipher
    @Inject
    constructor() : CookieCipher {
        override fun encrypt(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            return cipher.iv + cipher.doFinal(plain)
        }

        override fun decrypt(encrypted: ByteArray): ByteArray {
            val iv = encrypted.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
            return cipher.doFinal(encrypted, IV_BYTES, encrypted.size - IV_BYTES)
        }

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(KEY_BITS)
                            .build(),
                    )
                }.generateKey()
        }

        private companion object {
            const val KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "traccia_strava_cookies"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_BYTES = 12
            const val TAG_BITS = 128
            const val KEY_BITS = 256
        }
    }
