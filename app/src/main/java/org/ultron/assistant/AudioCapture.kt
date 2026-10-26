package org.ultron.assistant
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import kotlin.math.sqrt

/** One owner, 16kHz mono PCM16, bounded memory. Samples never leave this process. */
class AudioCapture(private val context: Context) : AutoCloseable {
 private var recorder: AudioRecord?=null
 private var suppressor: NoiseSuppressor?=null
 @android.annotation.SuppressLint("MissingPermission")
 fun start() {
  check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "Microphone permission required" }
  val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  check(min>0) { "16 kHz microphone format unavailable" }
  val record=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
   .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(maxOf(min*2,6400)).build()
  recorder=record
  check(record.state==AudioRecord.STATE_INITIALIZED) { "Microphone could not initialize" }
  if(NoiseSuppressor.isAvailable())suppressor=NoiseSuppressor.create(record.audioSessionId)?.apply{enabled=true}
  record.startRecording()
  check(record.recordingState==AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" }
 }
 fun frame(): FloatArray {
  val record=recorder ?: error("Microphone is closed")
  val raw=ShortArray(320);var offset=0
  while(offset<raw.size){
   val n=record.read(raw,offset,raw.size-offset,AudioRecord.READ_BLOCKING)
   check(n>0) { "AudioRecord read failed: "+n };offset+=n
  }
  return FloatArray(raw.size){raw[it]/32768f}
 }
 @Synchronized override fun close() {
  val r=recorder;recorder=null
  runCatching { if(r?.recordingState==AudioRecord.RECORDSTATE_RECORDING)r.stop() }
  suppressor?.release();suppressor=null;r?.release()
 }
}
class EnergyVad {
 private var noise=0.004
 private var voiced=0
 private var quiet=0
 private var frames=0
 private var speech=false
 private val audio=ArrayList<Float>(16000*8)
 private val pre=java.util.ArrayDeque<FloatArray>()
 var rms=0f;private set
 enum class Result { WAIT, COMPLETE, TIMEOUT }
 fun feed(frame: FloatArray): Result {
  frames++
  rms=sqrt(frame.sumOf { (it*it).toDouble() }/frame.size).toFloat()
  val active=rms>maxOf(0.012,noise*3.0)
  if(!speech){
   pre.addLast(frame);while(pre.size>15)pre.removeFirst()
   if(active)voiced++ else {voiced=0;noise=noise*0.95+rms*0.05}
   if(voiced>=3){speech=true;pre.forEach{f->f.forEach{audio.add(it)}};pre.clear()}
  }else {
   frame.forEach{audio.add(it)}
   quiet=if(active)0 else quiet+1
   if((quiet>=40 && audio.size>=8000) || audio.size>=16000*15)return Result.COMPLETE
  }
  return if(frames>=400 && !speech)Result.TIMEOUT else Result.WAIT
 }
 fun samples(): FloatArray = audio.toFloatArray()
}
