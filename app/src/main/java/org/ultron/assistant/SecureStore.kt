package org.ultron.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small bounded vault, not a database. Whole-file AES-256-GCM; no backup or plaintext fallback. */
class SecureStore(context: Context) {
 private val file = AtomicFile(File(context.filesDir, "vault.bin"))
 private val key: SecretKey
 init {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  key = (ks.getKey("ultron-vault-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(
   KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
  ).apply { init(KeyGenParameterSpec.Builder("ultron-vault-v1",
   KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setKeySize(256).build()) }.generateKey()
 }
 @Synchronized fun read(): JSONObject {
  if (!file.baseFile.exists()) return JSONObject()
  val bytes = file.openRead().use { it.readBytes() }
  require(bytes.size in 29..2_000_000) { "Invalid encrypted vault" }
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0,12)))
  return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
 }
 @Synchronized fun write(value: JSONObject) {
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE, key)
  val plain = value.toString().toByteArray(Charsets.UTF_8)
  require(plain.size < 1_000_000)
  val output = file.startWrite()
  try { output.write(cipher.iv); output.write(cipher.doFinal(plain)); file.finishWrite(output) }
  catch (e: Exception) { file.failWrite(output); throw e }
 }
 @Synchronized fun putMemory(text: String) {
  require(text.length <= 3000)
  val lower = text.lowercase()
  require(!Regex("password|api.?key|secret|token|പാസ്‌വേഡ്").containsMatchIn(lower)) { "Do not store secrets here" }
  val data=read(); data.put("memory", text); write(data)
 }
 @Synchronized fun history(): List<Pair<String,String>> {
  val a=read().optJSONArray("history") ?: return emptyList()
  return (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("role") to o.getString("text") } }
 }
 @Synchronized fun append(role: String, text: String) {
  val root=read(); val old=root.optJSONArray("history") ?: JSONArray(); val fresh=JSONArray()
  for(i in maxOf(0,old.length()-39) until old.length()) fresh.put(old.getJSONObject(i))
  fresh.put(JSONObject().put("role",role).put("text",text.take(4000)))
  root.put("history",fresh);write(root)
 }
 @Synchronized fun clearHistory() { val root=read();root.remove("history");write(root) }
 @Synchronized fun erase() { file.delete() }
}
