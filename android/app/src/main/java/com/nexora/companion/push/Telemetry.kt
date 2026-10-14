package com.nexora.companion.push

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.nexora.companion.data.Consent

class Telemetry(private val context: Context) {
    private var consent=Consent()
    val available get()=FirebaseApp.getApps(context).isNotEmpty()
    fun apply(value: Consent) {
        val previous=consent; consent=value
        if(!available)return
        FirebaseAnalytics.getInstance(context).apply {
            setConsent(mapOf(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if(value.analytics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED, FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED, FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED, FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED))
            setAnalyticsCollectionEnabled(value.analytics)
            if(previous.analytics && !value.analytics) resetAnalyticsData()
        }
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(value.crashes)
            if(!value.crashes) deleteUnsentReports()
        }
    }
    fun screen(route: String) {
        if(available && consent.analytics) FirebaseAnalytics.getInstance(context).logEvent("screen_view", Bundle().apply { putString("screen_name",route) })
    }
}
