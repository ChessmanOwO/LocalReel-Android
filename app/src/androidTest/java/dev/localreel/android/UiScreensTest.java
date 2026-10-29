package dev.localreel.android;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiScreensTest {
 private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 private String js(ActivityScenario<MainActivity> scene,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={null};scene.onActivity(a->web(a.getWindow().getDecorView()).evaluateJavascript(code,s->{value[0]=s;done.countDown();}));assertTrue(done.await(15,TimeUnit.SECONDS));return value[0];}
 private void screenshot(String name)throws Exception{Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 @Test public void lightDarkLargeAndLandscape()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))){
   Thread.sleep(1500);scene.onActivity(a->{WebView w=web(a.getWindow().getDecorView());assertEquals(0,w.getPaddingTop());assertEquals(0,w.getPaddingBottom());});js(scene,"LocalReel.theme(false);navigate('home')");Thread.sleep(400);assertEquals("false",js(scene,"document.documentElement.scrollWidth>innerWidth"));assertEquals("true",js(scene,"[...document.querySelectorAll('button')].filter(b=>b.offsetWidth).every(b=>b.getBoundingClientRect().height>=48)"));screenshot("ui-home-light.png");
   js(scene,"LocalReel.theme(true)");Thread.sleep(500);assertEquals("true",js(scene,"document.body.classList.contains('dark')"));screenshot("ui-home-dark.png");
   js(scene,"LocalReel.theme(false);document.body.classList.add('large-type');navigate('saved')");Thread.sleep(350);assertEquals("false",js(scene,"document.documentElement.scrollWidth>innerWidth"));screenshot("ui-library-large.png");
   scene.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1500);assertEquals("true",js(scene,"innerWidth>innerHeight"));assertEquals("false",js(scene,"document.documentElement.scrollWidth>innerWidth"));screenshot("ui-landscape.png");scene.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
  }
 }
}
