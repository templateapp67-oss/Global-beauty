package com.nexora.companion.data

data class Session(val userId: String, val email: String, val accessToken: String, val refreshToken: String, val expiresAt: Long)
data class Profile(val name: String = "", val photoPath: String = "")
data class DocumentRecord(val path: String, val name: String)
data class Template(val id: String, val name: String, val tagline: String, val category: String, val image: String, val palette: String, val services: List<String>)
data class Consent(val analytics: Boolean = false, val crashes: Boolean = false, val notifications: Boolean = false)
object Links {
    const val HOME = "https://nexora-beauty-network.templateapp67.chatgpt.site/"
    const val BUILDER = "https://nexora-one-white-label.templateapp67.chatgpt.site/"
    val services = listOf("Discover beauty services" to "https://glow-slot-virid.vercel.app/", "Business websites" to BUILDER, "Beauty jobs" to "https://nexora-salon-job-portal.templateapp67.chatgpt.site/", "Marketplace" to HOME + "marketplace/", "Growth Partner" to HOME + "growth-partner/", "Rewards" to HOME + "rewards/")
    fun permitted(url: String): Boolean = try {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && uri.host in setOf("nexora-beauty-network.templateapp67.chatgpt.site", "nexora-one-white-label.templateapp67.chatgpt.site", "glow-slot-virid.vercel.app", "nexora-salon-job-portal.templateapp67.chatgpt.site")
    } catch (_: Exception) { false }
}
object UploadRules {
    const val MAX_BYTES = 5 * 1024 * 1024
    fun allowed(mime: String, photo: Boolean) = mime in if (photo) setOf("image/jpeg", "image/png", "image/webp") else setOf("application/pdf", "image/jpeg", "image/png")
}
