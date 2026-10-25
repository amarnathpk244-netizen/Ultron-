package org.ultron.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
 private val app get()=application as UltronApp
 private var continuous=false
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  if(granted)startListening(continuous) else app.status("Error","Microphone permission നൽകിയിട്ടില്ല.")
 }
 private val optional=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onStart(){super.onStart();app.visible=true}
 override fun onStop(){app.visible=false;super.onStop()}
 private fun startListening(wake: Boolean) {
  continuous=wake
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   permission.launch(Manifest.permission.RECORD_AUDIO);return
  }
  runCatching{startForegroundService(Intent(this,ListeningService::class.java).putExtra("continuous",wake))}
   .onFailure{app.status("Error",it.message?:"Microphone service failed")}
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF55C9FF),background=Color(0xFF080E18),surface=Color(0xFF111D2D))) {
    Surface(modifier=Modifier.fillMaxSize()){
     UltronUi(app,
      onListen={startListening(it)},
      onStop={app.controller.stop();stopService(Intent(this,ListeningService::class.java))},
      onPermission={optional.launch(it)},
      onSettings={action->runCatching{startActivity(Intent(action))}.onFailure{app.status("Error","ഈ settings screen ലഭ്യമല്ല.")}}
     )
    }
   }
  }
 }
}

@Composable private fun UltronUi(app: UltronApp,onListen:(Boolean)->Unit,onStop:()->Unit,
 onPermission:(String)->Unit,onSettings:(String)->Unit) {
 val state by app.state.collectAsStateWithLifecycle()
 val scope=rememberCoroutineScope()
 var page by remember{mutableStateOf("Assistant")}
 var menu by remember{mutableStateOf(false)}
 var input by remember{mutableStateOf("")}
 var memory by remember{mutableStateOf("")}
 var history by remember{mutableStateOf(emptyList<Pair<String,String>>())}
 var downloadStatus by remember{mutableStateOf("")}
 var downloadJob by remember{mutableStateOf<kotlinx.coroutines.Job?>(null)}
 var modelVersion by remember{mutableIntStateOf(0)}
 val pages=listOf("Assistant","Voice interaction","Conversation history","Memory management","Permissions",
  "AI models","Voice settings","Offline downloads","Automation controls","Privacy")
 LaunchedEffect(page) {
  if(page=="Memory management" || page=="Conversation history") {
   runCatching{
    withContext(Dispatchers.IO) {
     memory=app.vault().read().optString("memory")
     history=app.vault().history()
    }
   }.onFailure{app.status("Error","Encrypted storage unavailable. No plaintext fallback.")}
  }
 }
 Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(20.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.weight(1f)){Text("ULTRON",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
    Text("LOCAL ANDROID ASSISTANT • DEV 0.1",style=MaterialTheme.typography.labelSmall)}
   Box{
    TextButton(onClick={menu=true}){Text("Menu")}
    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
     pages.forEach{label->DropdownMenuItem(text={Text(label)},onClick={page=label;menu=false})}
    }
   }
  }
  Spacer(Modifier.height(12.dp))
  Text(page,style=MaterialTheme.typography.titleLarge)
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Spacer(Modifier.height(4.dp))
   when(page) {
    "Assistant","Voice interaction" -> {
     Core(state)
     Text(state.status,color=MaterialTheme.colorScheme.primary)
     Text(state.detail)
     Text("Audio stays on this phone. Malayalam ASR is experimental; confirm the recognized command.",
      style=MaterialTheme.typography.bodySmall)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Button(onClick={onListen(false)},enabled=!state.listening){Text("സംസാരിക്കൂ")}
      OutlinedButton(onClick={onListen(true)},enabled=!state.listening){Text("Hey Ultron")}
     }
     OutlinedButton(onClick=onStop){Text("നിർത്തൂ / Stop")}
     state.pending?.let{plan->
      Card {
       Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("ഈ action അനുവദിക്കണോ?",style=MaterialTheme.typography.titleMedium)
        Text(plan.preview())
        Text("60 seconds • No message is sent automatically.",style=MaterialTheme.typography.bodySmall)
        Row {
         Button(onClick={scope.launch{runCatching{app.controller.confirm(plan.id)}.onFailure{app.controller.cancelPlan();app.status("Error",it.message?:"Action failed")}}}){Text("Confirm")}
         TextButton(onClick={app.controller.cancelPlan()}){Text("Cancel")}
        }
       }
      }
     }
     OutlinedTextField(value=input,onValueChange={input=it},label={Text("Malayalam / Manglish / English")},modifier=Modifier.fillMaxWidth(),maxLines=4)
     Button(onClick={
      val value=input;input=""
      scope.launch{app.controller.submit(value)}
     },enabled=input.isNotBlank() && !state.listening && state.status !in setOf("Processing","Speaking","Executing an action")){Text("നൽകുക")}
     state.conversation.takeLast(8).forEach{(role,text)->Card{Column(Modifier.fillMaxWidth().padding(12.dp)){Text(role,color=MaterialTheme.colorScheme.primary);Text(text)}}}
    }
    "Permissions" -> {
     Text("Each capability is optional. Android displays the permission prompt; ULTRON cannot approve it for you.")
     PermissionRow("Microphone","Voice capture and wake word. Nothing is recorded to disk."){onPermission(Manifest.permission.RECORD_AUDIO)}
     PermissionRow("Notifications","Persistent listening notification and Stop control."){onPermission(Manifest.permission.POST_NOTIFICATIONS)}
     PermissionRow("Camera / flashlight","Only used to request torch control; no pictures are captured."){onPermission(Manifest.permission.CAMERA)}
     Text("Accessibility can read visible non-password text, navigate, scroll, tap harmless exact labels and type in focused fields. Enable only if you want this. No background screen collection.")
     OutlinedButton(onClick={onSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)}){Text("Accessibility settings")}
     OutlinedButton(onClick={onSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)}){Text("Battery settings")}
    }
    "AI models","Offline downloads" -> {
     Text("One-time internet download required. Afterwards ASR, wake word and AI run locally. No account or paid API.")
     Text("Current Malayalam model is FP32 (~493 MB). INT8 and mixed-language accuracy are not validated. Brain is Qwen 1.5B Q4_K_M.")
     val manifest=remember(modelVersion){runCatching{app.models.manifest()}.getOrDefault(emptyList())}
     if(manifest.isEmpty())Text("Build-time model lock is missing. This source archive must first pass the automated build.",color=MaterialTheme.colorScheme.error)
     listOf("asr","brain","wake").forEach{id->
      val files=manifest.filter{it.id==id}
      Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text(id.uppercase(),style=MaterialTheme.typography.titleMedium)
       Text((files.sumOf{it.size}/1_000_000).toString()+" MB • "+if(runCatching{app.models.ready(id)}.getOrDefault(false))"Installed" else "Not installed")
       files.map{it.url.substringBefore("/resolve/")}.distinct().forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
       Row {
        Button(enabled=downloadJob?.isActive!=true && files.isNotEmpty() && !state.listening,
         onClick={downloadJob=scope.launch {
          runCatching{app.models.download(id){msg->downloadStatus=msg}}
           .onSuccess{downloadStatus=id+" integrity verified";modelVersion++}
           .onFailure{downloadStatus="Download failed: "+it.message}
         }}){Text("Download")}
        TextButton(enabled=downloadJob?.isActive!=true && !state.listening,onClick={
         scope.launch{runCatching{app.models.delete(id);modelVersion++}.onFailure{downloadStatus=it.message?:"Delete failed"}}
        }){Text("Delete")}
       }
      }}
     }
     Text(downloadStatus)
     if(downloadJob?.isActive==true)OutlinedButton(onClick={downloadJob?.cancel()}){Text("Cancel download")}
     Text("Storage available: "+app.models.root.usableSpace/1_000_000+" MB")
    }
    "Memory management" -> {
     Text("Only information you explicitly save here enters long-term memory. Do not enter passwords, tokens or medical records.")
     OutlinedTextField(value=memory,onValueChange={memory=it.take(3000)},label={Text("Approved memory")},modifier=Modifier.fillMaxWidth(),minLines=5)
     Button(onClick={scope.launch{
      runCatching{withContext(Dispatchers.IO){app.vault().putMemory(memory)}}
       .onSuccess{app.status("Idle","Memory saved with Android Keystore encryption.")}
       .onFailure{app.status("Error",it.message?:"Could not save memory")}
     }}){Text("Save")}
     OutlinedButton(onClick={scope.launch{
      runCatching{withContext(Dispatchers.IO){app.vault().putMemory("")};memory=""}
       .onFailure{app.status("Error","Memory deletion failed")}
     }}){Text("Delete memory")}
     Text("Project integrations are not connected. You can save project names/URLs; ULTRON cannot inspect private GitHub, Vercel or Supabase accounts.")
    }
    "Conversation history" -> {
     Text("Persistent history is off by default. Only assistant conversational replies are eligible; command inputs, screen results and action results are not saved.")
     Toggle("Save assistant replies",app.historyEnabled){app.historyEnabled=it}
     Button(onClick={scope.launch{
      runCatching{withContext(Dispatchers.IO){app.vault().clearHistory()};history=emptyList();app.controller.clearContext()}
       .onFailure{app.status("Error","History deletion failed")}
     }}){Text("Clear history and context")}
     history.forEach{(role,text)->Text(role+": "+text)}
    }
    "Voice settings" -> {
     Text("Uses an installed offline Android TTS voice. Voice availability depends on your TTS engine. A deep male Malayalam voice is not bundled or guaranteed.")
     var pitch by remember{mutableFloatStateOf(app.voice.pitch)}
     var rate by remember{mutableFloatStateOf(app.voice.rate)}
     Text("Pitch: "+String.format("%.2f",pitch))
     Slider(value=pitch,onValueChange={pitch=it;app.voice.pitch=it},valueRange=0.6f..1.2f)
     Text("Speed: "+String.format("%.2f",rate))
     Slider(value=rate,onValueChange={rate=it;app.voice.rate=it},valueRange=0.7f..1.2f)
     OutlinedButton(onClick={onSettings("com.android.settings.TTS_SETTINGS")}){Text("Install offline voices")}
     Button(enabled=!state.listening && state.status !in setOf("Processing","Speaking","Executing an action"),onClick={scope.launch{
      app.status("Speaking")
      val error=app.voice.speak("നമസ്കാരം. ഞാൻ അൾട്രോൺ.")
      app.status(if(error==null)"Idle" else "Error",error?:"Voice test completed")
     }}){Text("Test Malayalam voice")}
     Text("Stop interrupts speech. Spoken interruption while speaking is not implemented; microphone is gated to avoid self-activation.")
    }
    "Automation controls" -> {
     Text("Screen control is off at every app restart. Accessibility permission alone does not enable commands.")
     Toggle("Allow reviewed screen actions",app.automationEnabled){app.automationEnabled=it}
     Toggle("Keep CPU awake while listening (up to 2 hours)",app.keepAwake){app.keepAwake=it}
     Text("Foreground app launches need ULTRON visible. Screen reading/navigation can be voice-confirmed from the current external app. No autonomous messages, purchases, deployments or unrestricted workflows.")
     Text("Say Hey Ultron, pause, then speak the command. For a screen command, say Hey Ultron again followed by അതെ to confirm within 60 seconds.")
     Text("After reboot or force-stop, open ULTRON and enable listening again. Funtouch OS may stop the service; unrestricted battery mode can help but is not a guarantee.")
    }
    "Privacy" -> {
     Text("No analytics, advertisements, subscriptions or cloud inference. The app requests internet only to download models after you press Download. Opening a browser or messaging composer uses that app's network and privacy settings.")
     Text("Audio exists in RAM only. Screen text is not saved. Memory and optional history use AES-256-GCM with an Android Keystore key; backups are disabled. On an unlocked or compromised device this is not protection from every attacker.")
     Text("Permissions, listening and screen automation remain under your control. Delete models separately in AI models.")
     var erase by remember{mutableStateOf(false)}
     OutlinedButton(onClick={erase=true}){Text("Delete local memory and history")}
     if(erase)AlertDialog(onDismissRequest={erase=false},title={Text("Delete saved information?")},text={Text("This cannot be undone. Models are kept.")},
      confirmButton={TextButton(onClick={scope.launch{
       runCatching{withContext(Dispatchers.IO){app.vault().erase()};app.controller.stop();app.controller.clearContext()
        app.state.value=AssistantState(detail="Saved information deleted.");erase=false
       }.onFailure{app.status("Error","Deletion failed")}
      }}){Text("Delete")}},
      dismissButton={TextButton(onClick={erase=false}){Text("Cancel")}})
    }
   }
   Spacer(Modifier.height(16.dp))
  }
 }
}
@Composable private fun PermissionRow(title:String,description:String,action:()->Unit){
 Card{Column(Modifier.fillMaxWidth().padding(16.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(description);TextButton(onClick=action){Text("Grant / inspect")}}}
}
@Composable private fun Toggle(title:String,value:Boolean,onChange:(Boolean)->Unit){
 var local by remember(value){mutableStateOf(value)}
 Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f));Switch(checked=local,onCheckedChange={local=it;onChange(it)})}
}
@Composable private fun Core(state:AssistantState){
 val target=if(state.status=="Speaking")0.55f else (state.level*8).coerceIn(0.05f,1f)
 val level by animateFloatAsState(targetValue=target,label="audio-level")
 val blue=MaterialTheme.colorScheme.primary
 Canvas(Modifier.fillMaxWidth().height(150.dp)){
  val c=Offset(size.width/2,size.height/2)
  drawCircle(blue.copy(alpha=0.18f),52.dp.toPx()+level*12.dp.toPx(),c,style=Stroke(8.dp.toPx()))
  drawCircle(blue,44.dp.toPx(),c,style=Stroke(2.dp.toPx()))
  for(i in -7..7){
   val x=c.x+i*6.dp.toPx()
   val h=(5+(7-kotlin.math.abs(i))*level*4).dp.toPx()
   drawLine(blue,Offset(x,c.y-h),Offset(x,c.y+h),strokeWidth=2.dp.toPx())
  }
 }
}
