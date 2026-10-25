package org.ultron.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** No background screen harvesting; only explicit commands traverse a bounded tree. */
class UltronAccessibility : AccessibilityService() {
 companion object { @Volatile var instance: UltronAccessibility? = null; private set }
 override fun onServiceConnected() { instance=this }
 override fun onDestroy() { if(instance===this)instance=null;super.onDestroy() }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no logging or persistence */ }
 override fun onInterrupt() {}
 fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()
 private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
  val result=mutableListOf<AccessibilityNodeInfo>()
  fun visit(node: AccessibilityNodeInfo,depth: Int) {
   if(result.size>=200 || depth>=20 || node.isPassword || !node.isVisibleToUser)return
   result.add(node)
   for(i in 0 until minOf(node.childCount,50)){node.getChild(i)?.let{visit(it,depth+1)}}
  }
  visit(root,0);return result
 }
 fun perform(tool: Tool): String {
  check(ToolPolicy.valid(tool.kind,tool.arg))
  if(tool.kind in setOf("home","back","recents")) {
   val code=when(tool.kind){"home"->GLOBAL_ACTION_HOME;"back"->GLOBAL_ACTION_BACK;else->GLOBAL_ACTION_RECENTS}
   check(performGlobalAction(code)) { "Navigation request rejected" }
   return "Navigation അഭ്യർത്ഥന സ്വീകരിച്ചു; destination സ്ഥിരീകരിച്ചിട്ടില്ല."
  }
  val root=rootInActiveWindow ?: error("ഈ screen-ന്റെ ഉള്ളടക്കം ലഭ്യമല്ല.")
  val app=root.packageName?.toString() ?: ""
  check(app != packageName && app != "com.android.systemui") { "പുറത്തെ app-ൽ Hey Ultron പറഞ്ഞ് screen command നൽകുക." }
  val list=nodes(root)
  return when(tool.kind) {
   "screen" -> list.mapNotNull{it.text?.toString() ?: it.contentDescription?.toString()}
    .distinct().joinToString(". ").take(2500).ifBlank { "Accessible text ലഭ്യമല്ല." }
   "tap" -> {
    val targets=list.filter{it.isEnabled && it.isClickable && (it.text?.toString()==tool.arg || it.contentDescription?.toString()==tool.arg)}
    check(targets.size==1) { "ഒരേ label ഉള്ള കൃത്യമായ ഒരു button കണ്ടെത്തിയില്ല." }
    check(targets.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Tap rejected" }
    "Tap അഭ്യർത്ഥന സ്വീകരിച്ചു; അതിന്റെ ഫലം സ്ഥിരീകരിച്ചിട്ടില്ല."
   }
   "scroll" -> {
    val target=list.firstOrNull{it.isScrollable} ?: error("Scrollable element ഇല്ല.")
    check(target.performAction(if(tool.arg=="down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) { "Scroll rejected" }
    "Scroll അഭ്യർത്ഥന സ്വീകരിച്ചു."
   }
   "type" -> {
    val target=list.singleOrNull{it.isEditable && it.isFocused && !it.isPassword} ?: error("ഒരു non-password text field focus ചെയ്യുക.")
    check(target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,tool.arg)})) { "Text entry rejected" }
    if(target.refresh() && target.text?.toString()==tool.arg) "Text field-ൽ ചേർത്തത് സ്ഥിരീകരിച്ചു." else "Text entry അഭ്യർത്ഥന സ്വീകരിച്ചു; സ്ഥിരീകരിക്കാൻ കഴിഞ്ഞില്ല."
   }
   else -> error("Unsupported accessibility operation")
  }
 }
}
