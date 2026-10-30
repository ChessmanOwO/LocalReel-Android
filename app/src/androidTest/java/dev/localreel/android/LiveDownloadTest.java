package dev.localreel.android;

import android.content.*;
import android.os.*;
import android.webkit.WebView;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Opt-in live-network test: no fabricated video or status, failures remain failures. */
@RunWith(AndroidJUnit4.class)
public class LiveDownloadTest {
 private static WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 private String js(ActivityScenario<MainActivity> scenario,String source)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};scenario.onActivity(a->{WebView w=web(a.getWindow().getDecorView());w.evaluateJavascript(source,value->{result[0]=value;done.countDown();});});assertTrue("WebView callback",done.await(20,TimeUnit.SECONDS));return result[0];}
 @Test public void sharedLinkToRealMp4()throws Exception{
  Bundle args=InstrumentationRegistry.getArguments();String url=args.getString("url");Assume.assumeTrue("Pass -e url HTTPS_POST_URL",url!=null);
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();ReelApp app=(ReelApp)context.getApplicationContext();
  android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  android.app.Instrumentation.ActivityMonitor sourceMonitor=instrumentation.addMonitor(new IntentFilter(Intent.ACTION_VIEW),new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null),true);
  Set<String> old=new HashSet<>();JSONArray before=new JSONArray(app.jobs.snapshot());for(int i=0;i<before.length();i++)old.add(before.getJSONObject(i).getString("id"));
  boolean paste="paste".equals(args.getString("input_mode"));
  Intent share=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(!paste)share.setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Video chia sẻ: "+url);
  else {share.setAction(Intent.ACTION_MAIN);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Public test URL",url)));}
  JSONObject report=new JSONObject();report.put("source_url",url);report.put("android_runtime",true);report.put("synthetic_media",false);JSONArray states=new JSONArray();report.put("states",states);JSONObject result=null;
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(share)){
   report.put("input_mode",paste?"paste":"share_intent");
   Thread.sleep(4000);
   js(scenario,"document.querySelector('#ask').checked="+("true".equals(args.getString("ask_quality"))?"true":"false"));
   if(paste){js(scenario,"document.querySelector('[data-action=paste]').click()");report.put("paste_auto_submit",true);}
   else js(scenario,"document.querySelector('[data-action=submit]').click()");
   long end=SystemClock.elapsedRealtime()+660000;String last="";boolean chosen=false;
   while(SystemClock.elapsedRealtime()<end){JSONArray list=new JSONArray(app.jobs.snapshot());for(int i=0;i<list.length();i++)if(!old.contains(list.getJSONObject(i).getString("id"))&&UrlPolicy.identity(app.jobs.source(list.getJSONObject(i).getString("id"))).equals(UrlPolicy.identity(url))){result=list.getJSONObject(i);break;}
    if(result==null&&SystemClock.elapsedRealtime()>end-640000)fail("No job created within 20 seconds after submit");
    if(result!=null){String state=result.getString("state");if(!last.equals(state)){states.put(state);last=state;}
     if(state.equals("awaiting_selection")&&!chosen){js(scenario,"document.querySelector('[data-action=quality]').click()");js(scenario,"document.querySelector('[data-action=start]').click()");chosen=true;}
     if(JobManager.TERMINAL.contains(state))break;
    }Thread.sleep(500);
   }
   assertNotNull("Actual job created through Share Intent + UI",result);report.put("job",result);report.put("reused_existing",old.contains(result.getString("id")));boolean success=result.optString("state").equals("complete");report.put("success",success);if(success){File file=app.jobs.file(result.getString("id"));assertTrue(file.length()>0);report.put("bytes",file.length());}
   js(scenario,success?"navigate('saved')":"navigate('progress')");Thread.sleep(600);android.graphics.Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(screen!=null){try(OutputStream shot=new FileOutputStream(new File(context.getFilesDir(),"live-test-screen.png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,shot);}screen.recycle();}
  }finally{report.put("source_browser_launch_attempts",sourceMonitor.getHits());instrumentation.removeMonitor(sourceMonitor);File f=new File(context.getFilesDir(),"live-test-result.json");try(OutputStream out=new FileOutputStream(f)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
  assertEquals("Direct download must not launch the source browser",0,sourceMonitor.getHits());
  assertTrue("Source download must really complete",report.optBoolean("success"));
 }
}
