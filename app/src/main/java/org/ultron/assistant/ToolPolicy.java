package org.ultron.assistant;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/** Both handwritten and AI-generated plans cross this boundary. */
public final class ToolPolicy {
 private ToolPolicy() {}
 public static final Set<String> KINDS = Set.of(
  "battery","time","open_app","settings","alarm","timer","torch","volume",
  "home","back","recents","screen","tap","scroll","type","dial","sms","whatsapp","browse","calendar");
 private static final Pattern DANGEROUS = Pattern.compile(
  "(?iu).*(send|pay|purchase|buy|delete|remove|confirm|approve|allow|install|uninstall|transfer|submit|post|publish|merge|deploy|password|pin|otp|അയക്ക|അയയ്ക്ക|പണം|വാങ്ങ|നീക്കം|അനുവദി|സ്ഥിരീകരി).*");
 public static boolean valid(String kind, String arg) {
  if(kind==null || arg==null || !KINDS.contains(kind) || arg.length()>1000 || arg.indexOf('\0')>=0)return false;
  switch(kind) {
   case "battery":case "time":case "home":case "back":case "recents":case "screen":case "calendar":return arg.isEmpty();
   case "open_app":return Set.of("whatsapp","instagram","chrome","youtube","settings","github").contains(arg);
   case "settings":return Set.of("wifi","bluetooth","brightness","battery","tts").contains(arg);
   case "alarm":return arg.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]");
   case "timer":try {int s=Integer.parseInt(arg);return s>0 && s<=86400;}catch(NumberFormatException ex){return false;}
   case "torch":return arg.equals("on") || arg.equals("off");
   case "volume":return arg.equals("up") || arg.equals("down");
   case "dial":return arg.matches("\\+?[0-9 ()-]{3,30}");
   case "sms":case "whatsapp":return !arg.isBlank();
   case "tap":return !arg.isBlank() && arg.length()<=100 && !DANGEROUS.matcher(arg).matches();
   case "scroll":return arg.equals("up") || arg.equals("down");
   case "type":return !arg.isBlank() && arg.length()<=500;
   case "browse":try {
    URI uri=new URI(arg);
    return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null && uri.getPort()==-1;
   }catch(Exception ex){return false;}
   default:return false;
  }
 }
 public static boolean readOnly(String kind) {return kind.equals("battery") || kind.equals("time");}
 public static boolean requiresVisibleActivity(String kind) {
  return Set.of("open_app","settings","alarm","timer","dial","sms","whatsapp","browse","calendar").contains(kind);
 }
}
