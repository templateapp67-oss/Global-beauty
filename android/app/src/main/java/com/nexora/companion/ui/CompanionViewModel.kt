package com.nexora.companion.ui

import android.app.Application
import android.net.Uri
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.nexora.companion.NexoraApplication
import com.nexora.companion.data.*
import com.nexora.companion.push.PushSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

 data class CompanionState(val session: Session?=null, val profile: Profile=Profile(), val photoUrl: String?=null, val documents: List<DocumentRecord> = emptyList(), val busy: Boolean=false, val message: String?=null, val consent: Consent=Consent())
class CompanionViewModel(app: Application) : AndroidViewModel(app) {
    private val container=(app as NexoraApplication).container
    private val repo=container.repository
    private val mutable=MutableStateFlow(CompanionState())
    val state=mutable.asStateFlow()
    val configured=repo.configured
    val firebaseAvailable=container.telemetry.available
    val templates: List<Template> = app.assets.open("templates.json").bufferedReader().use { reader ->
        val a=JSONArray(reader.readText()); (0 until a.length()).map { i -> val j=a.getJSONObject(i); val services=j.getJSONArray("services"); Template(j.getString("id"),j.getString("name"),j.getString("tagline"),j.getString("category"),Links.HOME.trimEnd('/')+j.getString("image"),j.getString("palette"),(0 until services.length()).map { n -> services.getJSONObject(n).let { "${it.getString("name")} · ₹${it.getInt("price")} · ${it.getInt("durationMinutes")} min" } }) }
    }
    init {
        viewModelScope.launch { container.preferences.consent.collect { value -> mutable.update { it.copy(consent=value) } } }
        viewModelScope.launch { repo.session.collectLatest { session ->
            mutable.update { it.copy(session=session,profile=Profile(),photoUrl=null,documents=emptyList()) }
            if(session!=null) {
                try { refreshProfile(); if(container.preferences.consent.value.notifications) PushSyncWorker.enqueue(app) }
                catch(e: CancellationException){throw e} catch (_:Exception){ mutable.update { it.copy(message="Profile could not load. Check connection and retry.") } }
            }
        } }
    }
    private suspend fun refreshProfile() {
        val uid=repo.session.value?.userId ?: return
        val p=repo.profile(); val docs=repo.documents()
        val photo=if(p.photoPath.isNotBlank()) repo.signedUrl(p.photoPath,true) else null
        if(repo.session.value?.userId==uid) mutable.update { it.copy(profile=p,documents=docs,photoUrl=photo) }
    }
    private fun action(block:suspend ()->String?) {
        if(mutable.value.busy)return
        mutable.update { it.copy(busy=true,message=null) }
        viewModelScope.launch {
            try { val message=block(); mutable.update { it.copy(message=message) } }
            catch(e:CancellationException){throw e}
            catch(e:Exception){ mutable.update { it.copy(message=e.message?.take(240) ?: "Could not complete this action. Please retry.") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    fun dismissMessage(){mutable.update { it.copy(message=null) }}
    fun signIn(email:String,password:String)=action { repo.signIn(email,password); "Signed in." }
    fun signUp(email:String,password:String)=action { require(password.length>=8){"Use a password with at least 8 characters."}; if(repo.signUp(email,password)) "Account created." else "Check your email to confirm your account, then sign in." }
    fun recover(email:String)=action { require(email.contains('@')){"Enter your email address."}; repo.recover(email); "If the account exists, a recovery email has been sent." }
    fun reset(email:String,code:String,password:String)=action { require(password.length>=8 && code.isNotBlank()){"Enter your recovery code and a new password of at least 8 characters."}; repo.resetPassword(email,code,password); "Password updated." }
    fun reload()=action { refreshProfile(); "Profile refreshed." }
    fun save(name:String)=action { require(name.trim().length in 2..80){"Name must contain 2–80 characters."}; repo.saveProfile(name,mutable.value.profile.photoPath); refreshProfile(); "Profile saved." }
    fun upload(uri:Uri,photo:Boolean)=action {
        val (bytes,mime)=UploadReader.read(getApplication(),uri,photo)
        val path=repo.upload(bytes,mime,photo)
        if(photo) { repo.saveProfile(mutable.value.profile.name,path) }
        refreshProfile(); if(photo) "Profile photo saved." else "Private document uploaded."
    }
    fun openDocument(path:String,onUrl:(String)->Unit)=action { onUrl(repo.signedUrl(path,false)); null }
    fun deleteDocument(path:String)=action { repo.deleteDocument(path); refreshProfile(); "Document deleted." }
    fun consent(analytics:Boolean?=null,crashes:Boolean?=null,notifications:Boolean?=null)=action {
        require(firebaseAvailable){"Firebase configuration is required for this feature."}
        val old=container.preferences.consent.value
        val next=old.copy(analytics=analytics?:old.analytics,crashes=crashes?:old.crashes,notifications=notifications?:old.notifications)
        container.preferences.update(next); container.telemetry.apply(next)
        if(notifications!=null) {
            if(!notifications) getApplication<Application>().getSystemService(NotificationManager::class.java).cancelAll()
            PushSyncWorker.enqueue(getApplication())
        }
        "Privacy preferences saved."
    }
    fun signOut()=action {
        try { repo.signOut() } finally {
            if(firebaseAvailable) { FirebaseMessaging.getInstance().isAutoInitEnabled=false; try { FirebaseMessaging.getInstance().deleteToken().await() } catch(e:CancellationException){throw e} catch (_:Exception){} }
            getApplication<Application>().getSystemService(NotificationManager::class.java).cancelAll()
        }; "Signed out."
    }
    fun deleteAccount()=action { repo.deleteAccount(); getApplication<Application>().getSystemService(NotificationManager::class.java).cancelAll(); if(firebaseAvailable) { FirebaseMessaging.getInstance().isAutoInitEnabled=false; FirebaseMessaging.getInstance().deleteToken().await() }; "Account deleted." }
    fun screen(route:String){container.telemetry.screen(route)}
}
