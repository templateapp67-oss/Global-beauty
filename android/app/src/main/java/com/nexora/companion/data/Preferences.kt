package com.nexora.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    val installationId: String = prefs.getString("installation", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation",it).commit() }
    private val state = MutableStateFlow(Consent(prefs.getBoolean("analytics",false),prefs.getBoolean("crashes",false),prefs.getBoolean("notifications",false)))
    val consent = state.asStateFlow()
    fun update(c: Consent) {
        check(prefs.edit().putBoolean("analytics",c.analytics).putBoolean("crashes",c.crashes).putBoolean("notifications",c.notifications).putInt("consent_version",1).putLong("consent_updated_at",System.currentTimeMillis()).commit())
        state.value = c
    }
}
