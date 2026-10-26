package org.ultron.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ModelFile(val id: String, val path: String, val url: String, val sha256: String, val size: Long)
class ModelManager(private val context: Context) {
 val root=File(context.filesDir,"models").apply { mkdirs() }
 fun manifest(): List<ModelFile> {
  val json=JSONArray(context.assets.open("models.lock.json").bufferedReader().use { it.readText() })
  return (0 until json.length()).map { i -> json.getJSONObject(i).let {
   ModelFile(it.getString("id"),it.getString("path"),it.getString("url"),it.getString("sha256"),it.getLong("size"))
  } }
 }
 fun file(path: String): File {
  val f=File(root,path).canonicalFile
  require(f.path.startsWith(root.canonicalPath+File.separator)) { "Invalid model path" }
  return f
 }
 fun ready(id: String): Boolean {
  val entries=manifest().filter { it.id==id }
  return entries.isNotEmpty() && entries.all { file(it.path).length()==it.size && it.size>0 }
 }
 suspend fun verify(id: String): Boolean = withContext(Dispatchers.IO) {
  val entries=manifest().filter { it.id==id }
  entries.isNotEmpty() && entries.all {
   file(it.path).isFile && digest(file(it.path)) == it.sha256
  }
 }
 private suspend fun digest(f: File): String {
  val hash=MessageDigest.getInstance("SHA-256")
  f.inputStream().use { input ->
   val buffer=ByteArray(128*1024)
   while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}
  }
  return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
 }
 suspend fun download(id: String, progress: (String)->Unit) = withContext(Dispatchers.IO) {
  for(entry in manifest().filter { it.id==id }) {
   require(entry.sha256.matches(Regex("[a-f0-9]{64}")) && entry.size>0)
   val bundled=entry.url.startsWith("asset:///wake/")
   val url=if(bundled)null else URL(entry.url)
   require(bundled || (url?.protocol=="https" && url?.host in setOf("huggingface.co","github.com")))
   val dest=file(entry.path);dest.parentFile!!.mkdirs()
   if(dest.isFile && dest.length()==entry.size && digest(dest)==entry.sha256)continue
   require(root.usableSpace > entry.size+100_000_000L) { "Not enough free storage" }
   val part=File(dest.path+".part")
   try {
    val inputStream=if(bundled) context.assets.open(entry.url.removePrefix("asset:///"))
     else url!!.openConnection().apply { connectTimeout=30000;readTimeout=30000 }.getInputStream()
    inputStream.use { input ->
     part.outputStream().use { out ->
      val buffer=ByteArray(128*1024);var total=0L;var reported=-1
      while(true) {
       coroutineContext.ensureActive()
       val n=input.read(buffer);if(n<0)break
       total+=n;require(total<=entry.size) { "Model exceeded expected size" };out.write(buffer,0,n)
       val percent=(total*100/entry.size).toInt()
       if(percent!=reported){reported=percent;progress(entry.path+": "+percent+"%")}
      }
     }
    }
    require(part.length()==entry.size && digest(part)==entry.sha256) { "Model integrity check failed" }
    if(dest.exists())check(dest.delete())
    check(part.renameTo(dest)) { "Cannot install verified model" }
   } finally { part.delete() }
  }
 }
 fun delete(id: String) { manifest().filter{it.id==id}.forEach{file(it.path).delete()} }
}
