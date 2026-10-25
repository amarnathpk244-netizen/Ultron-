package org.ultron.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

data class Tool(val kind: String, val arg: String = "")
data class Plan(val tools: List<Tool>, val created: Long = android.os.SystemClock.elapsedRealtime(),
 val id: String = java.util.UUID.randomUUID().toString()) {
 fun validate() {
  require(tools.size in 1..3) { "A plan may contain 1–3 steps" }
  require(tools.all { ToolPolicy.valid(it.kind,it.arg) }) { "Unsupported or unsafe action" }
  require(tools.size==1 || tools.none { it.kind in setOf("tap","type","scroll","screen","back","home","recents") }) {
   "Screen actions require one reviewed step at a time"
  }
 }
 fun fresh(): Boolean = android.os.SystemClock.elapsedRealtime()-created in 0..60_000
 fun preview(): String = tools.mapIndexed{i,t -> (i+1).toString()+". "+t.kind+" "+t.arg}.joinToString("\n")
}
class Tools(private val app: UltronApp) {
 private fun open(intent: Intent): String {
  check(app.visible) { "Open ULTRON from its notification to continue this action" }
  app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  return "Android-ലേക്ക് അഭ്യർത്ഥന അയച്ചു. പൂർത്തിയായോ എന്ന് ബന്ധപ്പെട്ട app-ൽ പരിശോധിക്കണം."
 }
 @android.annotation.SuppressLint("MissingPermission")
 suspend fun execute(tool: Tool): String {
  check(ToolPolicy.valid(tool.kind,tool.arg))
  val locked=app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
  check(!locked || ToolPolicy.readOnly(tool.kind)) { "ആദ്യം ഫോൺ unlock ചെയ്യണം." }
  val t=tool.arg
  return when(tool.kind) {
   "battery" -> app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let {
    if(it in 0..100) "ബാറ്ററി "+it+" ശതമാനം." else "ബാറ്ററി നില വായിക്കാൻ കഴിഞ്ഞില്ല."
   }
   "time" -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,java.util.Locale("ml","IN")).format(Date())
   "open_app" -> {
    val packages=mapOf("whatsapp" to "com.whatsapp","instagram" to "com.instagram.android","chrome" to "com.android.chrome",
     "youtube" to "com.google.android.youtube","settings" to "com.android.settings","github" to "com.github.android")
    val pkg=packages.getValue(t)
    val intent=app.packageManager.getLaunchIntentForPackage(pkg) ?: error("ഈ app install ചെയ്തതായി കണ്ടെത്തിയില്ല.")
    open(intent)
    delay(500)
    if(UltronAccessibility.instance?.foregroundPackage()==pkg) "App മുന്നിൽ തുറന്നതായി accessibility വഴി കണ്ടു."
    else "App തുറക്കാനുള്ള അഭ്യർത്ഥന അയച്ചു; തുറന്നത് സ്ഥിരീകരിക്കാൻ കഴിഞ്ഞില്ല."
   }
   "settings" -> open(Intent(when(t){
    "wifi" -> Settings.ACTION_WIFI_SETTINGS
    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
    "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
    "battery" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    else -> "com.android.settings.TTS_SETTINGS"
   }))
   "alarm" -> {
    val parts=t.split(":").map{it.toInt()}
    open(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,parts[0])
     .putExtra(AlarmClock.EXTRA_MINUTES,parts[1]).putExtra(AlarmClock.EXTRA_MESSAGE,"ULTRON")
     .putExtra(AlarmClock.EXTRA_SKIP_UI,false))
   }
   "timer" -> open(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,t.toInt())
    .putExtra(AlarmClock.EXTRA_MESSAGE,"ULTRON").putExtra(AlarmClock.EXTRA_SKIP_UI,false))
   "torch" -> {
    check(app.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) { "Flashlight-ന് Camera permission നൽകണം." }
    val cm=app.getSystemService(CameraManager::class.java)
    val camera=cm.cameraIdList.firstOrNull{cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}
     ?: error("Flashlight ലഭ്യമല്ല.")
    cm.setTorchMode(camera,t=="on")
    "Flashlight മാറ്റാനുള്ള അഭ്യർത്ഥന Android സ്വീകരിച്ചു; hardware state വേറിട്ട് സ്ഥിരീകരിച്ചിട്ടില്ല."
   }
   "volume" -> {
    val audio=app.getSystemService(AudioManager::class.java)
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,if(t=="up")AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI)
    "Media volume: "+audio.getStreamVolume(AudioManager.STREAM_MUSIC)
   }
   "dial" -> open(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",t,null)))
   "sms" -> open(Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")).putExtra("sms_body",t))
   "whatsapp" -> open(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,t).setPackage("com.whatsapp"))
   "browse" -> open(Intent(Intent.ACTION_VIEW,Uri.parse(t)))
   "calendar" -> open(Intent(Intent.ACTION_INSERT,android.provider.CalendarContract.Events.CONTENT_URI))
   "screen","tap","scroll","type","home","back","recents" -> {
    check(app.automationEnabled) { "Automation controls-ൽ screen control അനുവദിക്കണം." }
    val accessibility=UltronAccessibility.instance ?: error("Accessibility service enable ചെയ്യണം.")
    accessibility.perform(tool)
   }
   else -> error("Unsupported tool")
  }
 }
}
