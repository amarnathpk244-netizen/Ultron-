package org.ultron.assistant
object NativeEngine {
 private val loaded by lazy { runCatching { System.loadLibrary("ultron") }.exceptionOrNull() }
 fun requireAvailable() { loaded?.let { throw IllegalStateException("Native runtime unavailable", it) } }
 external fun transcribe(model: String, tokens: String, pcm: FloatArray): String
 external fun generate(model: String, prompt: String, maxTokens: Int): ByteArray
 external fun cancelGeneration()
 external fun openWake(directory: String, keywords: String): Long
 external fun feedWake(handle: Long, pcm: FloatArray): Boolean
 external fun resetWake(handle: Long)
 external fun closeWake(handle: Long)
}
