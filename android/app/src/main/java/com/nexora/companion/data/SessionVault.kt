package com.nexora.companion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM key never leaves Android Keystore; app backup is disabled. */
class SessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("session_vault", Context.MODE_PRIVATE)
    private val alias = "nexora_session_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): Session? = try {
        val encoded = prefs.getString("value", null)
        if (encoded == null) null else {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            val j = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            Session(j.getString("id"), j.getString("email"), j.getString("access"), j.getString("refresh"), j.getLong("expires"))
        }
    } catch (_: Exception) { clear(); null }
    fun save(s: Session) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val j = JSONObject().put("id", s.userId).put("email", s.email).put("access", s.accessToken).put("refresh", s.refreshToken).put("expires", s.expiresAt)
        val value = Base64.encodeToString(cipher.iv + cipher.doFinal(j.toString().toByteArray()), Base64.NO_WRAP)
        check(prefs.edit().putString("value", value).commit()) { "Could not securely save session" }
    }
    fun clear() { prefs.edit().clear().commit() }
}
