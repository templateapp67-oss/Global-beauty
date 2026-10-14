package com.nexora.companion

import android.app.Application
import com.nexora.companion.data.*
import com.nexora.companion.push.Telemetry

class NexoraApplication : Application() {
    lateinit var container: AppContainer; private set
    override fun onCreate() { super.onCreate(); container=AppContainer(this) }
}
class AppContainer(app: Application) {
    val preferences=Preferences(app)
    val repository: CompanionRepository=SupabaseRepository(SessionVault(app),preferences.installationId)
    val telemetry=Telemetry(app).apply { apply(preferences.consent.value) }
}
