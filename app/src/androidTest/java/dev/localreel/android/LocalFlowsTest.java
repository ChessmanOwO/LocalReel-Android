package dev.localreel.android;
import android.content.*;
import android.database.Cursor;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.WebView;
import android.widget.VideoView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Device integration tests use a real already-downloaded MP4 and private queue state. */
@RunWith(AndroidJUnit4.class)
public class LocalFlowsTest {
 Context context;ReelApp app;ActivityScenario<MainActivity> scenario;
 @Before public void setup()throws Exception{context=InstrumentationRegistry.getInstrumentation().getTargetContext();app=(ReelApp)context.getApplicationContext();scenario=ActivityScenario.launch(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1500);}
 @After public void close(){scenario.close();}
 private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 private String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={null};scenario.onActivity(a->web(a.getWindow().getDecorView()).evaluateJavascript(code,s->{value[0]=s;done.countDown();}));assertTrue(done.await(15,TimeUnit.SECONDS));return value[0];}
 private String complete()throws Exception{JSONArray list=new JSONArray(app.jobs.snapshot());for(int i=0;i<list.length();i++)if(list.getJSONObject(i).optString("state").equals("complete"))return list.getJSONObject(i).getString("id");throw new AssertionError("Run successful live source test first");}
 private JSONObject job(String id)throws Exception{JSONArray list=new JSONArray(app.jobs.snapshot());for(int i=0;i<list.length();i++)if(list.getJSONObject(i).getString("id").equals(id))return list.getJSONObject(i);return null;}
 @Test public void clipboardAndMalformedInputStaySafe()throws Exception{
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("test","https://youtu.be/jNQXAC9IVRw")));
  js("document.querySelector('[data-action=paste]').click()");Thread.sleep(250);assertEquals("\"https://youtu.be/jNQXAC9IVRw\"",js("document.querySelector('textarea').value"));
  int count=new JSONArray(app.jobs.snapshot()).length();js("prefill('https://127.0.0.1/private');document.querySelector('[data-action=submit]').click()");Thread.sleep(250);assertEquals(count,new JSONArray(app.jobs.snapshot()).length());assertEquals("true",js("document.querySelector('#input-error').textContent.includes('HTTPS')"));
 }
 @Test public void duplicateAndSnapshotPrivacy()throws Exception{
  String id=complete(),source=app.jobs.source(id);int count=new JSONArray(app.jobs.snapshot()).length();String[] duplicate={null};
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->duplicate[0]=app.jobs.submit(source,false));assertEquals(id,duplicate[0]);assertEquals(count,new JSONArray(app.jobs.snapshot()).length());js("prefill("+JSONObject.quote(source)+");document.querySelector('[data-action=submit]').click()");Thread.sleep(500);assertEquals("\"saved\"",js("view"));JSONArray list=new JSONArray(app.jobs.snapshot());for(int i=0;i<list.length();i++)assertFalse(list.getJSONObject(i).has("url"));
 }
 @Test public void settingsCopyDarkThemeAndInputRetention()throws Exception{
  js("prefill('https://youtu.be/jNQXAC9IVRw');document.querySelector('[data-action=settings]').click();const c=document.querySelector('#unlimited');c.checked=false;c.dispatchEvent(new Event('change',{bubbles:true}));");Thread.sleep(300);assertFalse(app.jobs.unlimited());assertEquals("true",js("document.querySelector('#input-help').textContent.includes('512')"));assertEquals("\"https://youtu.be/jNQXAC9IVRw\"",js("document.querySelector('textarea').value"));
  js("document.querySelector('[data-action=theme]').click()");Thread.sleep(300);assertEquals("true",js("document.body.classList.contains('dark')"));js("LocalReel.theme(false);LocalReel.limits(true)");Thread.sleep(250);assertTrue(app.jobs.unlimited());
 }
 @Test public void galleryExportActuallyWritesVideo()throws Exception{
  Assume.assumeTrue(Build.VERSION.SDK_INT>=29);String id=complete(),name="LocalReel-"+id.substring(0,8)+".mp4";
  js("LocalReel.gallery("+JSONObject.quote(id)+")");long size=0;for(int n=0;n<40;n++){try(Cursor c=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Video.Media.SIZE,MediaStore.Video.Media.IS_PENDING},MediaStore.Video.Media.DISPLAY_NAME+"=?",new String[]{name},null)){if(c!=null&&c.moveToLast()&&c.getInt(1)==0)size=c.getLong(0);}if(size>0)break;Thread.sleep(250);}assertEquals(app.jobs.file(id).length(),size);
 }
 @Test public void nativeVideoPreviewPlays()throws Exception{
  String id=complete();js("LocalReel.open("+JSONObject.quote(id)+")");boolean[] playing={false};int[] position={0};
  for(int n=0;n<40;n++){scenario.onActivity(a->{try{Field f=MainActivity.class.getDeclaredField("currentPreview");f.setAccessible(true);VideoView v=(VideoView)f.get(a);if(v!=null){playing[0]=v.isPlaying();position[0]=v.getCurrentPosition();}}catch(Exception e){throw new AssertionError(e);}});if(playing[0]&&position[0]>300)break;Thread.sleep(250);}assertTrue(playing[0]);assertTrue(position[0]>300);
  InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK),true);InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),true);
 }
 @Test public void cancelRetryDeleteQueueWithoutRacingCleanup()throws Exception{
  String url="https://www.instagram.com/tv/BkfuX9UB-eK/";String[] ident={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ident[0]=app.jobs.submit(url,false));String id=ident[0];
  for(int n=0;n<30;n++){JSONObject j=job(id);if(!j.optString("state").equals("queued"))break;Thread.sleep(100);}
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.jobs.action(id,"cancel","best"));
  for(int n=0;n<50;n++){if(JobManager.TERMINAL.contains(job(id).optString("state")))break;Thread.sleep(100);}assertEquals("cancelled",job(id).optString("state"));
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.jobs.action(id,"retry","best"));InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.jobs.action(id,"cancel","best"));
  for(int n=0;n<60;n++){if(job(id).optString("state").equals("cancelled"))break;Thread.sleep(100);}assertEquals("cancelled",job(id).optString("state"));
  Thread.sleep(200);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.jobs.action(id,"delete","best"));assertNull(job(id));
 }
 @Test public void serviceStopInterruptsCurrentAndPending()throws Exception{
  String[] ids={null,null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ids[0]=app.jobs.submit("https://www.instagram.com/reel/Chunk8-jurw/",false);ids[1]=app.jobs.submit("https://www.douyin.com/video/6982497745948921092",false);});
  for(int n=0;n<30;n++){if(job(ids[0]).optString("state").equals("inspecting"))break;Thread.sleep(100);}
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->context.stopService(new Intent(context,DownloadService.class)));
  for(int n=0;n<60;n++){if(JobManager.TERMINAL.contains(job(ids[0]).optString("state"))&&JobManager.TERMINAL.contains(job(ids[1]).optString("state")))break;Thread.sleep(100);}assertEquals("error",job(ids[0]).optString("state"));assertEquals("error",job(ids[1]).optString("state"));assertEquals("interrupted",job(ids[1]).optString("error_code"));
 }
}
