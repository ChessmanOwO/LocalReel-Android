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
 private void js(ActivityScenario<MainActivity> scenario,String source)throws Exception{CountDownLatch done=new CountDownLatch(1);scenario.onActivity(a->{WebView w=web(a.getWindow().getDecorView());w.evaluateJavascript(source,value->done.countDown());});assertTrue("WebView callback",done.await(20,TimeUnit.SECONDS));}
 @Test public void sharedLinkToRealMp4()throws Exception{
  Bundle args=InstrumentationRegistry.getArguments();String url=args.getString("url");Assume.assumeTrue("Pass -e url HTTPS_POST_URL",url!=null);
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();ReelApp app=(ReelApp)context.getApplicationContext();
  Set<String> old=new HashSet<>();JSONArray before=new JSONArray(app.jobs.snapshot());for(int i=0;i<before.length();i++)old.add(before.getJSONObject(i).getString("id"));
  Intent share=new Intent(context,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Video chia sẻ: "+url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  JSONObject report=new JSONObject();report.put("source_url",url);report.put("android_runtime",true);report.put("synthetic_media",false);JSONArray states=new JSONArray();report.put("states",states);JSONObject result=null;
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(share)){
   Thread.sleep(4000);js(scenario,"document.querySelector('[data-action=submit]').click()");long end=SystemClock.elapsedRealtime()+660000;String last="";boolean chosen=false;
   while(SystemClock.elapsedRealtime()<end){JSONArray list=new JSONArray(app.jobs.snapshot());for(int i=0;i<list.length();i++)if(UrlPolicy.identity(app.jobs.source(list.getJSONObject(i).getString("id"))).equals(UrlPolicy.identity(url))){result=list.getJSONObject(i);break;}
    if(result!=null){String state=result.getString("state");if(!last.equals(state)){states.put(state);last=state;}
     if(state.equals("awaiting_selection")&&!chosen){js(scenario,"document.querySelector('[data-action=quality]').click()");js(scenario,"document.querySelector('[data-action=start]').click()");chosen=true;}
     if(JobManager.TERMINAL.contains(state))break;
    }Thread.sleep(500);
   }
   assertNotNull("Actual job created through Share Intent + UI",result);report.put("job",result);report.put("reused_existing",old.contains(result.getString("id")));boolean success=result.optString("state").equals("complete");report.put("success",success);if(success){File file=app.jobs.file(result.getString("id"));assertTrue(file.length()>0);report.put("bytes",file.length());}
   js(scenario,success?"navigate('saved')":"navigate('progress')");Thread.sleep(600);android.graphics.Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(screen!=null){try(OutputStream shot=new FileOutputStream(new File(context.getFilesDir(),"live-test-screen.png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,shot);}screen.recycle();}
  }finally{File f=new File(context.getFilesDir(),"live-test-result.json");try(OutputStream out=new FileOutputStream(f)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
  assertTrue("Source download must really complete",report.optBoolean("success"));
 }
}
