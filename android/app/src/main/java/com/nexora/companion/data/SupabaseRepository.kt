package com.nexora.companion.data

import com.nexora.companion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Supabase Auth/Storage REST transport. No admin or service-role credentials. */
class SupabaseRepository(private val vault: SessionVault, private val installationId: String) : CompanionRepository {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publicKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    override val configured = base.startsWith("https://") && publicKey.isNotBlank() && !base.contains("YOUR-PROJECT")
    private val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).followRedirects(false).build()
    private val state = MutableStateFlow(vault.read())
    override val session = state.asStateFlow()
    private val lock = Mutex()
    private val jsonType = "application/json".toMediaType()
    private suspend fun request(path: String, method: String = "GET", body: String? = null, token: String? = null, bytes: ByteArray? = null, mime: String = "application/json"): String = withContext(Dispatchers.IO) {
        check(configured) { "Sign-in requires the Supabase project setup. Browsing is available." }
        val req = Request.Builder().url(base + path).header("apikey", publicKey).apply {
            if (token != null) header("Authorization", "Bearer $token")
            header("Prefer", "return=representation,resolution=merge-duplicates")
        }.method(method, bytes?.toRequestBody(mime.toMediaType()) ?: body?.toRequestBody(jsonType)).build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) throw AuthFailure()
                throw IllegalStateException(when(response.code) { 400, 422 -> "Please check your details and try again."; 403 -> "This action is not permitted for your account."; 413 -> "The file is too large."; 429 -> "Too many attempts. Please wait and retry."; else -> "Service unavailable (${response.code}). Try again." })
            }
            text
        }
    }
    private fun store(j: JSONObject): Session {
        val u = j.getJSONObject("user")
        val s = Session(u.getString("id"), u.optString("email"), j.getString("access_token"), j.getString("refresh_token"), j.optLong("expires_at", System.currentTimeMillis()/1000+j.optLong("expires_in", 3600)))
        vault.save(s); state.value = s; return s
    }
    private suspend fun token(force: Boolean = false): String = lock.withLock {
        val s = state.value ?: error("Please sign in first.")
        if (!force && s.expiresAt > System.currentTimeMillis()/1000+90) return@withLock s.accessToken
        try { store(JSONObject(request("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", s.refreshToken).toString()))).accessToken }
        catch (e: AuthFailure) { vault.clear(); state.value = null; throw IllegalStateException("Session expired. Please sign in again.", e) }
    }
    private suspend fun authed(path: String, method: String = "GET", body: String? = null, bytes: ByteArray? = null, mime: String = "application/json"): String {
        val t = token()
        return try { request(path,method,body,t,bytes,mime) } catch (_: AuthFailure) { request(path,method,body,token(true),bytes,mime) }
    }
    override suspend fun signIn(email: String, password: String) { try { store(JSONObject(request("/auth/v1/token?grant_type=password", "POST", JSONObject().put("email",email.trim()).put("password",password).toString()))) } catch (_: AuthFailure) { error("Sign-in failed. Check your email and password.") } }
    override suspend fun signUp(email: String, password: String): Boolean {
        val j = JSONObject(request("/auth/v1/signup", "POST", JSONObject().put("email", email.trim()).put("password",password).toString()))
        val signedIn = j.has("access_token") && !j.isNull("access_token")
        if(signedIn) store(j)
        return signedIn
    }
    override suspend fun recover(email: String) { request("/auth/v1/recover", "POST", JSONObject().put("email", email.trim()).toString()) }
    override suspend fun resetPassword(email: String, code: String, password: String) {
        store(JSONObject(request("/auth/v1/verify", "POST", JSONObject().put("email",email.trim()).put("token",code.trim()).put("type","recovery").toString())))
        authed("/auth/v1/user", "PUT", JSONObject().put("password",password).toString())
    }
    override suspend fun signOut() {
        try { if (state.value != null) { registerDevice(null); authed("/auth/v1/logout?scope=local", "POST", "{}") } }
        finally { vault.clear(); state.value = null }
    }
    override suspend fun profile(): Profile {
        val id = state.value?.userId ?: error("Please sign in first.")
        val rows = JSONArray(authed("/rest/v1/nexora_mobile_profiles?user_id=eq.$id&select=display_name,photo_path"))
        return if(rows.length()==0) { saveProfile("", ""); Profile() } else rows.getJSONObject(0).let { Profile(it.optString("display_name"),it.optString("photo_path")) }
    }
    override suspend fun saveProfile(name: String, photoPath: String) {
        val id = state.value?.userId ?: error("Please sign in first.")
        authed("/rest/v1/nexora_mobile_profiles?on_conflict=user_id", "POST", JSONObject().put("user_id",id).put("display_name",name.trim()).put("photo_path",photoPath).toString())
    }
    override suspend fun upload(bytes: ByteArray, mime: String, photo: Boolean): String {
        require(bytes.isNotEmpty() && bytes.size <= UploadRules.MAX_BYTES) { "Choose a file smaller than 5 MB." }
        require(UploadRules.allowed(mime,photo)) { "Unsupported file format." }
        val uid = state.value?.userId ?: error("Please sign in first.")
        val ext = when(mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "pdf" }
        val path = "$uid/${UUID.randomUUID()}.$ext"
        authed("/storage/v1/object/${if(photo) "nexora-mobile-photos" else "nexora-mobile-documents"}/$path", "POST", bytes=bytes, mime=mime)
        return path
    }
    override suspend fun signedUrl(path: String, photo: Boolean): String {
        require(path.startsWith("${state.value?.userId}/"))
        val bucket = if(photo) "nexora-mobile-photos" else "nexora-mobile-documents"
        val j = JSONObject(authed("/storage/v1/object/sign/$bucket/$path", "POST", "{\"expiresIn\":300}"))
        return base + "/storage/v1" + j.getString("signedURL")
    }
    override suspend fun documents(): List<DocumentRecord> {
        val uid=state.value?.userId ?: error("Please sign in first.")
        val j=JSONArray(authed("/storage/v1/object/list/nexora-mobile-documents", "POST", JSONObject().put("prefix",uid).put("limit",100).put("offset",0).toString()))
        return (0 until j.length()).map { j.getJSONObject(it).getString("name") }.filter { it != ".emptyFolderPlaceholder" }.map { DocumentRecord("$uid/$it", it) }
    }
    override suspend fun deleteDocument(path: String) { require(path.startsWith("${state.value?.userId}/")); authed("/storage/v1/object/nexora-mobile-documents", "DELETE", JSONObject().put("prefixes",JSONArray().put(path)).toString()) }
    override suspend fun registerDevice(token: String?) { authed("/functions/v1/companion-account", "POST", JSONObject().put("action","device").put("installation_id",installationId).put("token",token ?: JSONObject.NULL).toString()) }
    override suspend fun deleteAccount() { authed("/functions/v1/companion-account", "POST", "{\"action\":\"delete_companion_data\"}"); signOut() }
    private class AuthFailure : Exception("Authentication required")
}
