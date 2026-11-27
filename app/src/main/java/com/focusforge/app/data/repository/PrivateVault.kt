package com.focusforge.app.data.repository

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional AES-256-GCM encrypted vault for Private List readable names.
 *
 * Key is stored in AndroidKeyStore with user authentication required
 * (BIOMETRIC_STRONG or DEVICE_CREDENTIAL).
 * - API 30+: uses setUserAuthenticationParameters(graceSeconds, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)
 * - API 26-29: uses setUserAuthenticationValidityDurationSeconds(graceSeconds)
 *
 * Stored in `filesDir/private_vault/vault.enc` (excluded from Android Auto-Backup & D2D transfer).
 * Never logs any domain or app name.
 */
@Singleton
class PrivateVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vaultDir = File(context.filesDir, VAULT_DIR_NAME)
    private val vaultFile = File(vaultDir, VAULT_FILE_NAME)

    sealed class VaultReadResult {
        data class Success(
            val domainNames: Map<Long, String>,
            val appNames: Map<String, String>
        ) : VaultReadResult()
        data object AuthRequired : VaultReadResult()
        data class Error(val message: String) : VaultReadResult()
    }

    fun getOrCreateKey(graceSeconds: Int = DEFAULT_GRACE_SECONDS): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        val safeGrace = graceSeconds.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                safeGrace,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(safeGrace)
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    fun readVault(graceSeconds: Int = DEFAULT_GRACE_SECONDS): VaultReadResult {
        if (!vaultFile.exists()) {
            return VaultReadResult.Success(emptyMap(), emptyMap())
        }
        return try {
            val raw = vaultFile.readBytes()
            if (raw.size < 5) return VaultReadResult.Success(emptyMap(), emptyMap())
            val ivLen = raw[0].toInt() and 0xFF
            if (ivLen <= 0 || raw.size < 1 + ivLen) {
                return VaultReadResult.Error("Corrupt vault header")
            }
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val ciphertext = raw.copyOfRange(1 + ivLen, raw.size)

            val key = getOrCreateKey(graceSeconds)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            val json = JSONObject(String(plaintext, Charsets.UTF_8))

            val domainsObj = json.optJSONObject("domains") ?: JSONObject()
            val domainMap = mutableMapOf<Long, String>()
            val dKeys = domainsObj.keys()
            while (dKeys.hasNext()) {
                val k = dKeys.next()
                val hash = k.toLongOrNull(16) ?: continue
                domainMap[hash] = domainsObj.getString(k)
            }

            val appsObj = json.optJSONObject("apps") ?: JSONObject()
            val appMap = mutableMapOf<String, String>()
            val aKeys = appsObj.keys()
            while (aKeys.hasNext()) {
                val k = aKeys.next()
                appMap[k] = appsObj.getString(k)
            }

            VaultReadResult.Success(domainMap, appMap)
        } catch (_: UserNotAuthenticatedException) {
            VaultReadResult.AuthRequired
        } catch (e: Exception) {
            // Key invalidated or device lock changed
            if (e.cause is UserNotAuthenticatedException) {
                VaultReadResult.AuthRequired
            } else {
                VaultReadResult.Error("Vault unavailable")
            }
        }
    }

    fun writeVault(
        domainNames: Map<Long, String>,
        appNames: Map<String, String>,
        graceSeconds: Int = DEFAULT_GRACE_SECONDS
    ): Boolean {
        return try {
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val root = JSONObject()
            val domainsObj = JSONObject()
            for ((hash, name) in domainNames) {
                domainsObj.put(java.lang.Long.toHexString(hash), name)
            }
            val appsObj = JSONObject()
            for ((pkg, label) in appNames) {
                appsObj.put(pkg, label)
            }
            root.put("domains", domainsObj)
            root.put("apps", appsObj)

            val plaintext = root.toString().toByteArray(Charsets.UTF_8)
            val key = getOrCreateKey(graceSeconds)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)

            val out = ByteArrayOutputStream(1 + iv.size + ciphertext.size)
            out.write(iv.size and 0xFF)
            out.write(iv)
            out.write(ciphertext)
            vaultFile.writeBytes(out.toByteArray())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun wipeVault() {
        if (vaultFile.exists()) {
            // Overwrite with zeros before deleting
            try {
                val len = vaultFile.length().toInt().coerceAtMost(65536)
                if (len > 0) vaultFile.writeBytes(ByteArray(len))
            } catch (_: Exception) {}
            vaultFile.delete()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "focusforge_private_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VAULT_DIR_NAME = "private_vault"
        const val VAULT_FILE_NAME = "vault.enc"
        const val DEFAULT_GRACE_SECONDS = 30
    }
}
