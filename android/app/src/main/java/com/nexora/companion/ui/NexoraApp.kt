package com.nexora.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.nexora.companion.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoraApp(vm:CompanionViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav=rememberNavController(); val back by nav.currentBackStackEntryAsState(); val route=back?.destination?.route ?: "home"
    val context=LocalContext.current; val snackbar=remember { SnackbarHostState() }
    val open:(String)->Unit={ url -> if(Links.permitted(url)) { try { CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url)) } catch (_:Exception) { android.widget.Toast.makeText(context,"No browser is available.",android.widget.Toast.LENGTH_SHORT).show() } } }
    LaunchedEffect(route){vm.screen(route)}
    LaunchedEffect(state.message){state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }}
    Scaffold(topBar={TopAppBar(title={Column { Text("NEXORA",fontWeight=FontWeight.Bold); Text("The Beauty Growth Network",style=MaterialTheme.typography.labelSmall) }},navigationIcon={if(route.startsWith("template/")) IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},actions={IconButton(onClick={open(Links.HOME)}){Icon(Icons.AutoMirrored.Filled.OpenInNew,"Open website")}})},snackbarHost={SnackbarHost(snackbar)},bottomBar={NavigationBar {
        listOf(Triple("home","Home",Icons.Default.Home),Triple("templates","Templates",Icons.Default.Dashboard),Triple("profile","Profile",Icons.Default.Person),Triple("settings","Settings",Icons.Default.Settings)).forEach { (destination,label,icon) -> NavigationBarItem(selected=route==destination,onClick={nav.navigate(destination){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(icon,null)},label={Text(label)}) }
    }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(nav,startDestination="home",modifier=Modifier.weight(1f)) {
                composable("home"){HomeScreen(open,{nav.navigate("templates")})}
                composable("templates"){TemplatesScreen(vm.templates){nav.navigate("template/$it")}}
                composable("template/{id}"){entry -> vm.templates.find { it.id==entry.arguments?.getString("id") }?.let { TemplateDetail(it,open) }}
                composable("profile"){if(state.session==null) AuthScreen(vm,state) else ProfileScreen(vm,state)}
                composable("settings"){SettingsScreen(vm,state,open)}
            }
        }
    }
}
@Composable
private fun HomeScreen(open:(String)->Unit,templates:()->Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { Text("YOUR NEXT CHAPTER",style=MaterialTheme.typography.labelMedium); Text("Discover. Book.\nBuild. Grow.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text("Your place in the beauty economy starts here."); Button(onClick=templates){Text("Explore 14 Studioflow designs")} } } }
        item { Text("Explore Nexora",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text("Services open in a secure browser tab. Website sessions stay separate from this app.",style=MaterialTheme.typography.bodySmall) }
        items(Links.services){(label,url)-> ElevatedCard(onClick={open(url)},modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Icon(Icons.AutoMirrored.Filled.OpenInNew,"Open $label")}}}
        item { TextButton(onClick={open(Links.HOME+"about/")}){Text("About Nexora")};Text("Native Android companion · Beauty, wellness and business",style=MaterialTheme.typography.labelSmall) }
    }
}
@Composable
private fun TemplatesScreen(templates:List<Template>,details:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("all")}
    val shown=templates.filter{(category=="all"||it.category==category)&&(it.name+it.tagline).contains(query,true)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("A design for every kind of beauty.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold); Text("All 14 templates from Studioflow.") }
        item { OutlinedTextField(query,{query=it},label={Text("Search templates")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { Column { listOf("all","hair","barber","beauty","nails","spa","ayurvedic","skin","tattoo").chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c.replaceFirstChar{it.uppercase()})}) }} };Text("${shown.size} templates",style=MaterialTheme.typography.labelMedium) }
        items(shown,key={it.id}) { t -> ElevatedCard(onClick={details(t.id)}) { AsyncImage(t.image,"${t.name} cover",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Crop);Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(t.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(t.tagline);Text(t.palette,style=MaterialTheme.typography.labelMedium);Text("View design details →",color=MaterialTheme.colorScheme.primary)} } }
        if(shown.isEmpty())item { Text("No matches. Try another name or category.") }
    }
}
@Composable
private fun TemplateDetail(t:Template,open:(String)->Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { AsyncImage(t.image,t.name,Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Fit) }
        item { Text(t.name,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(t.tagline);Text(t.palette,style=MaterialTheme.typography.labelLarge) }
        item { Text("Sample service menu",style=MaterialTheme.typography.titleLarge) }
        items(t.services){Text(it);HorizontalDivider(Modifier.padding(top=10.dp))}
        item { Button(onClick={open(Links.BUILDER)},modifier=Modifier.fillMaxWidth()){Text("Use in Studioflow")};Text("Sign in to Studioflow and choose ${t.name}. These are sample services and prices. The app does not submit demo bookings.",style=MaterialTheme.typography.bodySmall) }
    }
}
@Composable
private fun AuthScreen(vm:CompanionViewModel,state:CompanionState) {
    var email by rememberSaveable{mutableStateOf("")};var password by remember{mutableStateOf("")};var signup by rememberSaveable{mutableStateOf(false)};var recovery by rememberSaveable{mutableStateOf(false)};var code by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(if(recovery)"Recover your account" else if(signup)"Join Nexora" else "Welcome back",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("Your profile and permitted files, in one private place.") }
        if(!vm.configured)item { Text("Account features need the Supabase project configuration. You can browse Home and Templates now.",color=MaterialTheme.colorScheme.error) }
        item { OutlinedTextField(email,{email=it},label={Text("Email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,modifier=Modifier.fillMaxWidth()) }
        if(recovery)item { OutlinedTextField(code,{code=it},label={Text("Recovery code from email")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(password,{password=it},label={Text(if(recovery)"New password" else "Password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { Button(enabled=vm.configured&&!state.busy&&email.contains('@')&&password.length>=8,onClick={if(recovery)vm.reset(email,code,password) else if(signup)vm.signUp(email,password) else vm.signIn(email,password)},modifier=Modifier.fillMaxWidth()){Text(if(recovery)"Set new password" else if(signup)"Create account" else "Sign in")} }
        item { TextButton(onClick={signup=!signup;recovery=false}){Text(if(signup)"Already registered? Sign in" else "Create an account")};TextButton(enabled=vm.configured&&!state.busy,onClick={recovery=true;vm.recover(email)}){Text(if(recovery)"Resend recovery email" else "Forgot password?")};if(recovery)TextButton(onClick={recovery=false}){Text("Back to sign in")} }
    }
}
@Composable
private fun ProfileScreen(vm:CompanionViewModel,state:CompanionState) {
    val context=LocalContext.current
    var name by rememberSaveable(state.session?.userId){mutableStateOf(state.profile.name)}
    LaunchedEffect(state.profile.name){name=state.profile.name}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let {vm.upload(it,true)}}
    val document=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let {vm.upload(it,false)}}
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){if(state.photoUrl!=null)AsyncImage(state.photoUrl,"Profile photograph",Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),contentScale=ContentScale.Fit) else Icon(Icons.Default.Person,"No profile photograph",Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp));Column { Text("Your profile",style=MaterialTheme.typography.titleLarge);Text(state.session?.email.orEmpty(),style=MaterialTheme.typography.bodySmall) } } }
        item { OutlinedButton(enabled=!state.busy,onClick={photo.launch("image/*")}){Text("Upload profile photograph")};Text("JPEG, PNG or WebP · 5 MB maximum. Photos are resized and stripped of metadata.",style=MaterialTheme.typography.labelSmall) }
        item { OutlinedTextField(name,{name=it.take(80)},label={Text("Display name")},modifier=Modifier.fillMaxWidth());Button(enabled=!state.busy,onClick={vm.save(name)}){Text("Save profile")} }
        item { HorizontalDivider();Text("Private documents",style=MaterialTheme.typography.titleLarge);Text("Upload only documents you own or have permission to share. Files stay private to your account.",style=MaterialTheme.typography.bodySmall);OutlinedButton(enabled=!state.busy,onClick={document.launch(arrayOf("application/pdf","image/jpeg","image/png"))}){Text("Upload permitted document")} }
        items(state.documents,key={it.path}) { doc -> Card { Column(Modifier.padding(12.dp)) { Text(doc.name,maxLines=2);Row { TextButton(enabled=!state.busy,onClick={vm.openDocument(doc.path){url->try {CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url))}catch(_:Exception){}}}){Text("Open")};TextButton(enabled=!state.busy,onClick={vm.deleteDocument(doc.path)}){Text("Delete")} } } } }
        item { TextButton(enabled=!state.busy,onClick={vm.reload()}){Text("Refresh profile and files")};OutlinedButton(enabled=!state.busy,onClick={vm.signOut()}){Text("Sign out")} }
    }
}
@Composable
private fun SettingsScreen(vm:CompanionViewModel,state:CompanionState,open:(String)->Unit) {
    var delete by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted -> if(granted)vm.consent(notifications=true)}
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Text("Privacy & preferences",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("All optional collection is off by default. You can change your choices anytime.") }
        if(!vm.firebaseAvailable)item { Text("Push notifications, analytics and crash reporting require Firebase setup. No telemetry is collected in this build.",style=MaterialTheme.typography.bodySmall) }
        item { ConsentRow("Usage analytics","Share screen usage without account email or uploaded files.",state.consent.analytics,vm.firebaseAvailable&&!state.busy){vm.consent(analytics=it)} }
        item { ConsentRow("Crash reporting","Send crash diagnostics to help fix app failures.",state.consent.crashes,vm.firebaseAvailable&&!state.busy){vm.consent(crashes=it)} }
        item { ConsentRow("Push notifications","Receive account updates on this device. Requires sign-in and Android permission.",state.consent.notifications,vm.firebaseAvailable&&state.session!=null&&!state.busy){enabled -> if(enabled&&Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.consent(notifications=enabled)} }
        item { TextButton(onClick={open(Links.HOME+"policies/?section=privacy-policy")}){Text("Privacy policy")};TextButton(onClick={open(Links.HOME+"contact/")}){Text("Support")};Text("Nexora Android 1.0.0 · Native companion",style=MaterialTheme.typography.labelSmall) }
        if(state.session!=null)item { HorizontalDivider();TextButton(enabled=!state.busy,onClick={delete=true}){Text("Delete my account",color=MaterialTheme.colorScheme.error)} }
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("Delete account permanently?")},text={Text("Your Nexora companion account, profile photos and private documents will be permanently deleted. This does not delete accounts on separate partner websites.")},confirmButton={TextButton(onClick={delete=false;vm.deleteAccount()}){Text("Delete permanently")}},dismissButton={TextButton(onClick={delete=false}){Text("Cancel")}})
}
@Composable
private fun ConsentRow(title:String,description:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall)};Switch(checked,onCheckedChange=onChange,enabled=enabled)}
}
