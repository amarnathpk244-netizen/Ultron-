package org.ultron.assistant
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class AssistantState(
 val status: String="Idle", val detail: String="മോഡലുകൾ install ചെയ്ത ശേഷം സംസാരിക്കാം.",
 val level: Float=0f, val conversation: List<Pair<String,String>> = emptyList(),
 val pending: Plan?=null, val listening: Boolean=false
)
class UltronApp : Application() {
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 val state=MutableStateFlow(AssistantState())
 @Volatile var visible=false
 var automationEnabled=false
 var historyEnabled=false
 var keepAwake=false
 lateinit var models: ModelManager
 lateinit var voice: VoiceOutput
 lateinit var controller: Controller
 private var vaultValue: SecureStore?=null
 fun vault(): SecureStore = vaultValue ?: SecureStore(this).also{vaultValue=it}
 override fun onCreate() {
  super.onCreate()
  models=ModelManager(this);voice=VoiceOutput(this);controller=Controller(this)
 }
 fun status(status: String, detail: String=state.value.detail) { state.update{it.copy(status=status,detail=detail)} }
 fun message(role: String,text: String,save: Boolean=true) {
  state.update{it.copy(conversation=(it.conversation+(role to text)).takeLast(40),detail=text)}
  if(save && historyEnabled) runCatching { vault().append(role,text) }.onFailure{
   status("Error","Encrypted history save failed; no plaintext fallback was used.")
  }
 }
}
