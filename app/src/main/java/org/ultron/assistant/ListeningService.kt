package org.ultron.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update

/** Started only by a visible activity. No boot receiver, sticky restart, or hidden microphone. */
class ListeningService : Service() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var job: Job?=null
 @Volatile private var audio: AudioCapture?=null
 private var wakeLock: PowerManager.WakeLock?=null
 private val app get()=application as UltronApp
 override fun onBind(intent: Intent?): IBinder?=null
 override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  if(job?.isActive==true)return START_NOT_STICKY
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("listening","ULTRON microphone",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,2,Intent(this,ListeningService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"listening").setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("ULTRON • Microphone active").setContentText("Hey Ultron • Tap to review actions")
   .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
  try {
   startForeground(7,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
  }catch(e: Exception){app.status("Error","Microphone service denied: "+e.message);stopSelf();return START_NOT_STICKY}
  app.state.update{it.copy(listening=true)}
  val continuous=intent?.getBooleanExtra("continuous",false)==true
  if(app.keepAwake){
   wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ULTRON:Listening").apply{
    acquire(2*60*60*1000L)
   }
  }
  job=scope.launch {
   var wake=0L
   try {
    app.status("Processing","മോഡലുകൾ പരിശോധിക്കുന്നു…")
    check(app.models.verify("asr")) { "Malayalam ASR model missing or damaged. Download it in Models." }
    NativeEngine.requireAvailable()
    if(continuous){
     check(app.models.verify("wake")) { "Wake model missing or damaged. Download it in Models." }
     val keyword=java.io.File(filesDir,"keywords.txt")
     assets.open("keywords.txt").use{input->keyword.outputStream().use{input.copyTo(it)}}
     wake=NativeEngine.openWake(app.models.file("wake").path,keyword.path)
     check(wake!=0L)
    }
    app.state.update{it.copy(listening=true)}
    do {
     currentCoroutineContext().ensureActive()
     if(continuous) {
      app.status("Idle","Hey Ultron കാത്തിരിക്കുന്നു • Offline")
      AudioCapture(this@ListeningService).use { capture ->
       audio=capture;capture.start()
       var frames=0
       while(currentCoroutineContext().isActive) {
        val frame=capture.frame()
        // Typed/UI speech output must never feed wake detection.
        if(app.voice.speaking || android.os.SystemClock.elapsedRealtime()<app.voice.mutedUntil){NativeEngine.resetWake(wake);delay(25);continue}
        if(++frames%5==0)app.state.update{it.copy(level=kotlin.math.sqrt(frame.sumOf{v->(v*v).toDouble()}/frame.size).toFloat())}
        if(NativeEngine.feedWake(wake,frame))break
       }
      }
      audio=null
     }
     currentCoroutineContext().ensureActive()
     app.status("Listening","പറയൂ…")
     android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,50).let { tone ->
      try { tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,80);delay(150) } finally { tone.release() }
     }
     val vad=EnergyVad()
     var timedOut=false
     AudioCapture(this@ListeningService).use{capture->
      audio=capture;capture.start()
      while(currentCoroutineContext().isActive){
       val result=vad.feed(capture.frame())
       app.state.update{it.copy(level=vad.rms)}
       if(result==EnergyVad.Result.COMPLETE)break
       if(result==EnergyVad.Result.TIMEOUT){timedOut=true;break}
      }
     }
     audio=null
     if(timedOut){
      app.status("Error","ശബ്ദം കണ്ടെത്തിയില്ല. മൈക്രോഫോൺ പരിശോധിച്ച് വീണ്ടും പറയുക.")
     }else{
      app.status("Processing","Malayalam transcription • Offline")
      val pcm=vad.samples()
      val text=NativeEngine.transcribe(app.models.file("asr/model.onnx").path,app.models.file("asr/tokens.txt").path,pcm).trim()
      pcm.fill(0f)
      if(text.isBlank())app.status("Error","Speech വ്യക്തമല്ല. ഒന്നും പ്രവർത്തിപ്പിച്ചിട്ടില്ല.")
      else withContext(Dispatchers.Main){app.controller.submit(text)}
     }
     if(continuous){NativeEngine.resetWake(wake);delay(400)}
    }while(continuous && currentCoroutineContext().isActive)
   }catch(e: CancellationException){throw e}
   catch(e: Exception){if(currentCoroutineContext().isActive)app.status("Error",e.message ?: "Microphone failed")}
   finally {
    audio?.close();audio=null
    if(wake!=0L)NativeEngine.closeWake(wake)
    app.state.update{it.copy(listening=false,level=0f)}
    if(wakeLock?.isHeld==true)wakeLock?.release()
    stopSelf()
   }
  }
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  job?.cancel();audio?.close();audio=null
  if(wakeLock?.isHeld==true)wakeLock?.release()
  scope.cancel();super.onDestroy()
 }
}
