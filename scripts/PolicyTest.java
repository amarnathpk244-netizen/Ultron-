import org.ultron.assistant.ToolPolicy;
public class PolicyTest {
 static int count=0;
 static void test(boolean value,String label) {count++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args) {
  for(String action: new String[]{"battery","time","home","back","recents","screen","calendar"}) {
   test(ToolPolicy.valid(action,""),action+" empty");
   test(!ToolPolicy.valid(action,"unexpected"),action+" rejects argument");
  }
  for(String kind:new String[]{"shell","exec","send","delete","deploy","merge","root","grant_permission"})
   test(!ToolPolicy.valid(kind,"anything"),"unsupported "+kind);
  test(ToolPolicy.valid("alarm","06:00"),"valid alarm");
  for(String arg:new String[]{"25:00","12:60","6:00","06:00 tomorrow","-1:00"})
   test(!ToolPolicy.valid("alarm",arg),"reject alarm "+arg);
  test(ToolPolicy.valid("timer","60"),"timer");
  for(String arg:new String[]{"0","-1","86401","1e3","999999999999"})
   test(!ToolPolicy.valid("timer",arg),"invalid timer");
  test(ToolPolicy.valid("open_app","whatsapp"),"allowlisted app");
  test(!ToolPolicy.valid("open_app","com.untrusted.app"),"no arbitrary package");
  test(ToolPolicy.valid("browse","https://github.com/example/repository"),"https URL");
  for(String arg:new String[]{"http://example.com","javascript:alert(1)","file:///etc/passwd",
     "intent://example","https://user:password@example.com","https://example.com:8080",
     "https://example.com/\nheader"})
   test(!ToolPolicy.valid("browse",arg),"unsafe URL "+arg);
  for(String arg:new String[]{"Send","Pay now","Delete","അയക്കുക","അനുവദിക്കുക","Confirm","Merge","Buy","OTP"})
   test(!ToolPolicy.valid("tap",arg),"dangerous tap "+arg);
  test(ToolPolicy.valid("tap","Search"),"harmless exact-label tap");
  test(!ToolPolicy.valid("dial","*123#"),"reject USSD");
  test(!ToolPolicy.valid("dial","+123;456"),"reject dial control characters");
  test(ToolPolicy.valid("dial","+91 99999 12345"),"literal phone number");
  test(!ToolPolicy.valid("type","x".repeat(501)),"bounded text");
  test(ToolPolicy.readOnly("battery") && !ToolPolicy.readOnly("dial"),"read-only distinction");
  test(ToolPolicy.requiresVisibleActivity("dial"),"foreground dialer");
  test(!ToolPolicy.valid(null,"") && !ToolPolicy.valid("battery",null),"null rejected");
  System.out.println("PASS: "+count+" production ToolPolicy assertions");
 }
}
