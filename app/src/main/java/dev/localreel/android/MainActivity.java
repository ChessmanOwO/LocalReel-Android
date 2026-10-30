package dev.localreel.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.Executors;

/** Trusted local UI only; network/media extraction never runs inside WebView. */
public final class MainActivity extends Activity {
    private WebView web;
    private FrameLayout insetRoot;
    private VideoView currentPreview;
    private JobManager jobs;
    private String pendingExport;
    private boolean sharedConsumed;
    private final Runnable updateUI=this::render;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final String ASSET="file:///android_asset/index.html";
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state){
        super.onCreate(state);jobs=((ReelApp)getApplication()).jobs;
        if(state!=null){pendingExport=state.getString("export");sharedConsumed=state.getBoolean("sharedConsumed");}
        web=new WebView(this);web.setBackgroundColor(0xfffcfcf8);WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setDomStorageEnabled(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setTextZoom(Math.round(getResources().getConfiguration().fontScale*100));
        WebView.setWebContentsDebuggingEnabled(false);web.addJavascriptInterface(new Bridge(),"LocalReel");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !ASSET.equals(request.getUrl().toString());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(ASSET.equals(request.getUrl().toString()))return null;return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            @Override public void onPageFinished(WebView view,String url){if(ASSET.equals(url)){render();receive(getIntent());}}
        });
        insetRoot=new FrameLayout(this);insetRoot.addView(web,new FrameLayout.LayoutParams(-1,-1));
        insetRoot.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        insetRoot.setOnApplyWindowInsetsListener((view,insets)->{
            int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}
            view.setPadding(left,top,right,bottom);return insets.consumeSystemWindowInsets();
        });
        setContentView(insetRoot);web.loadUrl(ASSET);jobs.listener=updateUI;
    }
    private void render(){if(web!=null){applyChrome(getSharedPreferences("settings",MODE_PRIVATE).getBoolean("dark",false));web.evaluateJavascript("window.receiveTheme&&receiveTheme("+getSharedPreferences("settings",MODE_PRIVATE).getBoolean("dark",false)+")",null);web.evaluateJavascript("window.receiveLimits&&receiveLimits("+jobs.unlimited()+")",null);web.evaluateJavascript("window.receiveJobs&&receiveJobs("+jobs.snapshot()+")",null);}}
    private void applyChrome(boolean dark){int color=dark?0xff101827:0xfff8fafc;web.setBackgroundColor(color);insetRoot.setBackgroundColor(color);getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);int flags=insetRoot.getSystemUiVisibility();if(dark)flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;else flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(Build.VERSION.SDK_INT>=27){if(dark)flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;else flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;}insetRoot.setSystemUiVisibility(flags);}
    private void message(String text){if(web!=null)web.evaluateJavascript("window.notice&&notice("+JSONObject.quote(text)+")",null);}
    private void error(){message("Không thực hiện được. Kiểm tra liên kết, trạng thái tác vụ hoặc bộ nhớ rồi thử lại.");}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);sharedConsumed=false;receive(intent);}
    private void receive(Intent intent){if(!sharedConsumed&&intent!=null&&Intent.ACTION_SEND.equals(intent.getAction())){String text=intent.getStringExtra(Intent.EXTRA_TEXT);sharedConsumed=true;if(text!=null)web.evaluateJavascript("window.prefill&&prefill("+JSONObject.quote(text.length()>16384?"":text)+")",null);}}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("export",pendingExport);state.putBoolean("sharedConsumed",sharedConsumed);}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.goBack&&goBack()",value->{if("false".equals(value))finish();});else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();jobs.listener=updateUI;render();}
    @Override protected void onDestroy(){if(jobs.listener==updateUI)jobs.listener=null;if(currentPreview!=null)currentPreview.stopPlayback();if(web!=null){web.removeJavascriptInterface("LocalReel");web.destroy();web=null;}super.onDestroy();}
    private final class Bridge {
        @JavascriptInterface public void theme(boolean dark){main.post(()->{getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("dark",dark).apply();render();});}
        @JavascriptInterface public void limits(boolean unlimited){main.post(()->{jobs.configure(unlimited);render();});}
        @JavascriptInterface public void ready(){main.post(MainActivity.this::render);}
        @JavascriptInterface public void paste(){main.post(()->{ClipboardManager cm=getSystemService(ClipboardManager.class);if(cm.hasPrimaryClip()&&cm.getPrimaryClip()!=null){CharSequence text=cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);if(text!=null&&text.length()<=16384)web.evaluateJavascript("prefill("+JSONObject.quote(text.toString())+",true)",null);}else web.evaluateJavascript("window.inputError&&inputError(\"Bộ nhớ tạm chưa có liên kết.\")",null);});}
        @JavascriptInterface public void submit(String text,boolean ask){main.post(()->{try{String id=jobs.submit(text,ask);web.evaluateJavascript("submitted("+JSONObject.quote(id)+")",null);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);}catch(IllegalArgumentException e){web.evaluateJavascript("window.inputError&&inputError("+JSONObject.quote("Chưa tìm được liên kết HTTPS thuộc 6 nền tảng hỗ trợ.")+")",null);}catch(Exception e){web.evaluateJavascript("window.inputError&&inputError(\"Chưa thêm được tác vụ. Kiểm tra bộ nhớ rồi thử lại.\")",null);}});}
        @JavascriptInterface public void action(String id,String action,String quality){main.post(()->{try{jobs.action(id,action,quality);}catch(Exception e){error();}});}
        @JavascriptInterface public void open(String id){main.post(()->{try{File file=jobs.file(id);VideoView video=new VideoView(MainActivity.this);currentPreview=video;MediaController controls=new MediaController(MainActivity.this);video.setMediaController(controls);video.setVideoPath(file.getAbsolutePath());FrameLayout frame=new FrameLayout(MainActivity.this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));AlertDialog dialog=new AlertDialog.Builder(MainActivity.this).setTitle("Xem video").setView(frame).setNegativeButton("Đóng",null).create();dialog.setOnDismissListener(d->{video.stopPlayback();currentPreview=null;});video.setOnErrorListener((mp,what,extra)->{message("Không phát được trên thiết bị này. Hãy lưu file để thử trình phát khác.");return true;});video.setOnPreparedListener(mp->video.start());dialog.show();frame.getLayoutParams().height=(int)(getResources().getDisplayMetrics().heightPixels*.65);frame.requestLayout();}catch(Exception e){error();}});}
        @JavascriptInterface public void share(String id){main.post(()->{try{Uri uri=uri(id);Intent send=new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newUri(getContentResolver(),"Video",uri));startActivity(Intent.createChooser(send,"Chia sẻ video"));}catch(Exception e){error();}});}
        @JavascriptInterface public void save(String id){main.post(()->{try{jobs.file(id);pendingExport=UrlPolicy.id(id);Intent pick=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"LocalReel-"+id.substring(0,8)+".mp4");startActivityForResult(pick,7);}catch(Exception e){error();}});}
        @JavascriptInterface public void gallery(String id){main.post(()->{
            if(Build.VERSION.SDK_INT<29){save(id);return;}
            try{File file=jobs.file(id);new Thread(()->{Uri output=null;try{
                ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"LocalReel-"+id.substring(0,8)+".mp4");values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/LocalReel");values.put(MediaStore.Video.Media.IS_PENDING,1);
                output=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);if(output==null)throw new IOException();try(OutputStream out=getContentResolver().openOutputStream(output)){copy(file,out);}values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(output,values,null,null);main.post(()->message("Đã lưu vào thư viện máy: Movies/LocalReel."));
            }catch(Exception e){if(output!=null)getContentResolver().delete(output,null,null);main.post(MainActivity.this::error);}}).start();}catch(Exception e){error();}
        });}
    }
    private Uri uri(String id){return FileProvider.getUriForFile(this,getPackageName()+".files",jobs.file(id));}
    private static void copy(File file,OutputStream out)throws IOException{if(out==null)throw new IOException();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){String id=pendingExport;pendingExport=null;Uri uri=data.getData();new Thread(()->{try{File file=jobs.file(id);try(OutputStream out=getContentResolver().openOutputStream(uri)){copy(file,out);}main.post(()->message("Đã lưu file vào vị trí đã chọn."));}catch(Exception e){main.post(this::error);}}).start();}}
}
