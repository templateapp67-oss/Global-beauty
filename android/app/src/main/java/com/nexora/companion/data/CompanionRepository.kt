package com.nexora.companion.data

import kotlinx.coroutines.flow.StateFlow

interface CompanionRepository {
    val session: StateFlow<Session?>
    val configured: Boolean
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String): Boolean
    suspend fun recover(email: String)
    suspend fun resetPassword(email: String, code: String, password: String)
    suspend fun signOut()
    suspend fun profile(): Profile
    suspend fun saveProfile(name: String, photoPath: String)
    suspend fun upload(bytes: ByteArray, mime: String, photo: Boolean): String
    suspend fun signedUrl(path: String, photo: Boolean): String
    suspend fun documents(): List<DocumentRecord>
    suspend fun deleteDocument(path: String)
    suspend fun registerDevice(token: String?)
    suspend fun deleteAccount()
}
