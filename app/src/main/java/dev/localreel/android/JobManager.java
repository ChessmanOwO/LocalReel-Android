package dev.localreel.android;

import android.content.*;
import java.lang.Process;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One private, persisted queue. URLs never reach WebView snapshots or Android logs. */
public final class JobManager {
    private final Context context;
    private final File root,engine,store;
    private final LinkedHashMap<String,JSONObject> jobs=new LinkedHashMap<>();
    private final ArrayDeque<String> pending=new ArrayDeque<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile Process process;
    private volatile NativeMediaDownloader mediaNetwork;
    private volatile String current;
    private boolean active, initialized, stopRequested=true;
    private long serviceGeneration=0,activeGeneration=0;
    private java.util.function.BooleanSupplier idleStop;
    private boolean plannedIdleStop;
    private long requestRevision;
    public volatile Runnable listener;
    private static final long MAX_BYTES=512L*1024*1024,DISK_BYTES=2L*1024*1024*1024;
    public static final Set<String> TERMINAL=new HashSet<>(Arrays.asList("complete","error","cancelled"));

    public JobManager(Context context){
        this.context=context;root=new File(context.getFilesDir(),"jobs");root.mkdirs();engine=new File(context.getFilesDir(),"engine");store=new File(context.getFilesDir(),"jobs.json");
        try{
            AtomicFile af=new AtomicFile(store);
            JSONArray saved=new JSONArray(new String(af.readFully(),StandardCharsets.UTF_8));
            for(int i=0;i<saved.length();i++){
                JSONObject j=saved.getJSONObject(i);String id=UrlPolicy.id(j.getString("id"));UrlPolicy.validate(j.getString("url"));
                if(!TERMINAL.contains(j.optString("state")) && !j.optString("state").equals("awaiting_selection")){put(j,"state","error");put(j,"error_code","interrupted");}
                jobs.put(id,j);if(!j.optString("state").equals("complete"))clean(id,false);
            }
        }catch(Exception ignored){} prune();persist();
    }
    static void put(JSONObject j,String k,Object v){try{j.put(k,v==null?JSONObject.NULL:v);}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized String submit(String text,boolean ask){
        String url=UrlPolicy.find(text);prune();
        for(JSONObject j:jobs.values())if(UrlPolicy.identity(j.optString("url")).equals(UrlPolicy.identity(url)) && !Arrays.asList("error","cancelled").contains(j.optString("state")))return j.optString("id");
        if((!unlimited()&&(jobs.size()>=100 || size(root)>=DISK_BYTES)) || root.getUsableSpace()<128L*1024*1024)throw new IllegalStateException("limit");
        String id=UUID.randomUUID().toString();JSONObject j=new JSONObject();
        put(j,"id",id);put(j,"url",url);put(j,"platform",UrlPolicy.platform(url));put(j,"title","Video");put(j,"state","queued");put(j,"quality",ask?"ask":"best");put(j,"created_at",System.currentTimeMillis());put(j,"heights",new JSONArray());put(j,"note","");put(j,"transcoded",false);
        jobs.put(id,j);pending.add(id);changed();startService();return id;
    }
    public synchronized void action(String id,String action,String quality){
        JSONObject j=jobs.get(UrlPolicy.id(id));if(j==null)throw new IllegalArgumentException();String state=j.optString("state");
        if(action.equals("start")){
            if(!state.equals("awaiting_selection"))return;
            String q=UrlPolicy.quality(quality);if(q.equals("ask"))throw new IllegalArgumentException();
            if(!q.equals("best")){boolean found=false;JSONArray heights=j.optJSONArray("heights");for(int i=0;i<heights.length();i++)if(q.equals(heights.optString(i)))found=true;if(!found)throw new IllegalArgumentException();}
            put(j,"quality",q);put(j,"state","queued");pending.add(id);changed();startService();
        } else if(action.equals("cancel")){
            if(TERMINAL.contains(state))return;pending.remove(id);put(j,"state","cancelling");changed();
            if(id.equals(current)){cancelMedia();if(process!=null)process.destroy();}
            else {clean(id,false);put(j,"state","cancelled");changed();}
        } else if(action.equals("retry")){
            if(!Arrays.asList("error","cancelled").contains(state)||id.equals(current))return;
            clean(id,false);put(j,"state","queued");put(j,"error_code",null);put(j,"progress",null);put(j,"note","");pending.remove(id);pending.add(id);changed();startService();
        } else if(action.equals("delete")){
            if(!TERMINAL.contains(state)||id.equals(current))throw new IllegalStateException("busy");
            clean(id,false);jobs.remove(id);changed();
        } else throw new IllegalArgumentException();
    }
    public boolean unlimited(){return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("unlimited",true);}
    public void configure(boolean value){context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putBoolean("unlimited",value).apply();}
    private void startService(){requestRevision++;context.startForegroundService(new Intent(context,DownloadService.class));}
    public synchronized long serviceCreated(){stopRequested=false;plannedIdleStop=false;return ++serviceGeneration;}
    public synchronized void ensureWorker(long generation,java.util.function.BooleanSupplier stop){if(generation!=serviceGeneration)return;idleStop=stop;if(active&&activeGeneration==generation)return;active=true;activeGeneration=generation;executor.execute(()->drain(generation));}
    private void drain(long generation){
        while(true){String id;JSONObject job;
            synchronized(this){if(stopRequested||generation!=serviceGeneration){if(activeGeneration==generation)active=false;return;}id=pending.poll();if(id==null){active=false;current=null;process=null;java.util.function.BooleanSupplier stop=idleStop;long revision=requestRevision;main.postDelayed(()->{synchronized(JobManager.this){if(!active&&pending.isEmpty()&&generation==serviceGeneration&&requestRevision==revision&&stop!=null)plannedIdleStop=stop.getAsBoolean();}},500);return;}job=jobs.get(id);if(job==null||!job.optString("state").equals("queued"))continue;current=id;put(job,"state","inspecting");changed();}
            boolean inspect=job.optString("quality").equals("ask");
            try{initialize();synchronized(this){if(stopRequested||generation!=serviceGeneration||job.optString("state").equals("cancelling")||job.optString("state").equals("error"))throw new InterruptedException();}run(job,inspect,generation);}
            catch(Exception e){synchronized(this){if(!job.optString("state").equals("cancelling")&&!job.optString("state").equals("error")){put(job,"state","error");put(job,"error_code",e instanceof IOException?"dependency_missing":"internal");}}}
            finally{synchronized(this){process=null;current=null;if(job.optString("state").equals("cancelling"))put(job,"state","cancelled");clean(id,job.optString("state").equals("complete"));changed();}}
        }
    }
    private void initialize() throws Exception {
        if(initialized)return;
        com.yausername.youtubedl_android.YoutubeDL.getInstance().init(context);
        com.yausername.ffmpeg.FFmpeg.getInstance().init(context);
        engine.mkdirs();for(String name:new String[]{"engine.zip","boot.py"})try(InputStream in=context.getAssets().open(name);OutputStream out=new FileOutputStream(new File(engine,name))){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        initialized=true;
    }
    private void run(JSONObject job,boolean inspect,long generation) throws Exception {
        String id=job.optString("id");File directory=dir(id);directory.mkdirs();File bin=new File(context.getApplicationInfo().nativeLibraryDir);
        File packages=new File(context.getNoBackupFilesDir(),"youtubedl-android/packages"),python=new File(packages,"python/usr");
        ProcessBuilder pb=new ProcessBuilder(new File(bin,"libpython.so").getAbsolutePath(),new File(engine,"boot.py").getAbsolutePath(),new File(engine,"engine.zip").getAbsolutePath());
        Map<String,String> env=pb.environment();env.keySet().removeIf(k->k.toLowerCase(Locale.ROOT).endsWith("_proxy"));
        env.put("PYTHONHOME",python.getAbsolutePath());env.put("HOME",python.getAbsolutePath());env.put("PYTHONUTF8","1");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("YTDLP_NO_PLUGINS","1");
        env.put("SSL_CERT_FILE",new File(python,"etc/tls/cert.pem").getAbsolutePath());env.put("TMPDIR",context.getCacheDir().getAbsolutePath());
        env.put("LD_LIBRARY_PATH",new File(python,"lib").getAbsolutePath()+":"+new File(packages,"ffmpeg/usr/lib").getAbsolutePath());
        JSONObject request=new JSONObject();put(request,"native_http",true);put(request,"url",job.optString("url"));put(request,"mode",inspect?"inspect":"download");put(request,"quality",job.optString("quality"));final boolean unbounded=unlimited();final long cap=unbounded?Long.MAX_VALUE:MAX_BYTES;put(request,"max_bytes",cap);put(request,"timeout",unbounded?0:600);put(request,"work_dir",directory.getAbsolutePath());put(request,"ffmpeg",new File(bin,"libffmpeg.so").getAbsolutePath());put(request,"ffprobe",new File(bin,"libffprobe.so").getAbsolutePath());
        final NativeMediaDownloader transport=new NativeMediaDownloader();
        final Process p;synchronized(this){if(stopRequested||generation!=serviceGeneration||job.optString("state").equals("cancelling")||job.optString("state").equals("error"))throw new InterruptedException();transport.begin();mediaNetwork=transport;p=pb.start();process=p;}
        Thread stderr=new Thread(()->{try(InputStream in=p.getErrorStream()){byte[] b=new byte[8192];while(in.read(b)!=-1){} }catch(IOException ignored){}});stderr.setDaemon(true);stderr.start();
        final OutputStream feed=p.getOutputStream();feed.write((request.toString()+"\n").getBytes(StandardCharsets.UTF_8));feed.flush();
        long start=SystemClock.elapsedRealtime();ScheduledFuture<?> watcher=timer.scheduleWithFixedDelay(()->{
            String code=null;if(!unbounded&&SystemClock.elapsedRealtime()-start>600000)code="timeout";else if((!unbounded&&(size(directory)>MAX_BYTES || size(root)>DISK_BYTES)) || root.getUsableSpace()<32L*1024*1024)code="limit";
            if(code!=null){synchronized(this){if(process!=p||generation!=serviceGeneration||TERMINAL.contains(job.optString("state")))return;if(!job.optString("state").equals("cancelling")){put(job,"state","error");put(job,"error_code",code);changed();}transport.cancel();p.destroy();}}
        },250,250,TimeUnit.MILLISECONDS);
        JSONObject completed=null;boolean metadata=false;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;
            while((line=reader.readLine())!=null){if(line.length()>65536)throw new IOException();JSONObject event;try{event=new JSONObject(line);}catch(JSONException ignored){continue;}
                String state=event.optString("state");
                if(state.equals("native_download")){
                    JSONObject answer=new JSONObject();
                    try{
                        synchronized(this){if(stopRequested||generation!=serviceGeneration||job.optString("state").equals("cancelling")||job.optString("state").equals("error"))throw new NativeMediaDownloader.Failure("interrupted");}
                        transport.download(event,directory,unbounded?cap:Math.max(0,cap-size(directory)),(received,total)->{synchronized(JobManager.this){
                            if(process==p&&generation==serviceGeneration&&!stopRequested&&!TERMINAL.contains(job.optString("state"))&&!job.optString("state").equals("cancelling")){
                                put(job,"state","downloading");put(job,"received",received);put(job,"progress",total>0?0.1+0.75*Math.min((double)received/total,1):null);changed();
                            }
                        }});put(answer,"native_success",true);
                    }catch(Exception failure){put(answer,"native_success",false);put(answer,"error_code",failure instanceof NativeMediaDownloader.Failure?((NativeMediaDownloader.Failure)failure).code:"network");}
                    if(!p.isAlive())break;feed.write((answer.toString()+"\n").getBytes(StandardCharsets.UTF_8));feed.flush();continue;
                }
                synchronized(this){
                    if(job.optString("state").equals("cancelling")||job.optString("state").equals("error"))continue;
                    if(state.equals("metadata")){metadata=true;put(job,"title",event.optString("title","Video"));put(job,"heights",event.optJSONArray("heights"));}
                    else if(state.equals("complete"))completed=event;
                    else if(state.equals("error")){put(job,"state","error");put(job,"error_code",event.optString("error_code","extraction_failed"));}
                    else if(Arrays.asList("downloading","processing").contains(state)){put(job,"state",state);put(job,"progress",event.opt("progress"));put(job,"received",event.optLong("downloaded_bytes",job.optLong("received")));if(event.has("note"))put(job,"note",event.optString("note"));}
                    changed();
                }
            }
            int code=p.waitFor();synchronized(this){
                if(job.optString("state").equals("cancelling")||job.optString("state").equals("error"))return;
                if(code==0&&inspect&&metadata){put(job,"state","awaiting_selection");}
                else if(code==0&&completed!=null){verify(new File(directory,"result.mp4"));put(job,"state","complete");put(job,"size",completed.optLong("size"));put(job,"width",completed.optInt("width"));put(job,"height",completed.optInt("height"));put(job,"fallback",completed.optBoolean("fallback"));put(job,"transcoded",completed.optBoolean("transcoded"));put(job,"note",completed.optString("note"));put(job,"progress",1);}
                else{put(job,"state","error");put(job,"error_code","extraction_failed");}
                changed();
            }
        }finally{watcher.cancel(false);transport.close();synchronized(this){if(mediaNetwork==transport)mediaNetwork=null;}try{feed.close();}catch(IOException ignored){}if(p.isAlive()){p.destroy();if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();}}
    }
    private void cancelMedia(){NativeMediaDownloader network=mediaNetwork;if(network!=null)network.cancel();}
    private void verify(File file)throws Exception{MediaExtractor ex=new MediaExtractor();try{ex.setDataSource(file.getAbsolutePath());boolean audio=false,video=false;for(int i=0;i<ex.getTrackCount();i++){String type=ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);audio|=type!=null&&type.startsWith("audio/");video|=type!=null&&type.startsWith("video/");}if(!audio||!video||file.length()==0)throw new IOException();}finally{ex.release();}}
    public synchronized File file(String id){JSONObject j=jobs.get(UrlPolicy.id(id));File f=new File(dir(id),"result.mp4");if(j==null||!j.optString("state").equals("complete")||!f.isFile())throw new IllegalArgumentException();return f;}
    public synchronized String source(String id){JSONObject j=jobs.get(UrlPolicy.id(id));if(j==null)throw new IllegalArgumentException();String url=j.optString("url");UrlPolicy.validate(url);return url;}
    public synchronized String snapshot(){JSONArray list=new JSONArray();ArrayList<JSONObject> values=new ArrayList<>(jobs.values());Collections.reverse(values);for(JSONObject j:values){JSONObject copy=new JSONObject();Iterator<String> keys=j.keys();while(keys.hasNext()){String k=keys.next();if(!k.equals("url"))put(copy,k,j.opt(k));}list.put(copy);}return list.toString();}
    private File dir(String id){return new File(root,UrlPolicy.id(id));}
    private void clean(String id,boolean preserve){File f=dir(id);File[] children=f.listFiles();if(children!=null)for(File c:children)if(!(preserve&&c.getName().equals("result.mp4")))remove(c);if(!preserve)f.delete();}
    private static void remove(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)remove(c);}f.delete();}
    private static long size(File f){if(f.isFile())return f.length();long n=0;File[] a=f.listFiles();if(a!=null)for(File c:a)n+=size(c);return n;}
    private void prune(){long cutoff=System.currentTimeMillis()-7L*24*3600*1000;Iterator<JSONObject> it=jobs.values().iterator();while(it.hasNext()){JSONObject j=it.next();if(TERMINAL.contains(j.optString("state"))&&j.optLong("created_at")<cutoff){clean(j.optString("id"),false);it.remove();}}}
    private void changed(){persist();Runnable callback=listener;if(callback!=null)main.post(callback);}
    private void persist(){JSONArray array=new JSONArray();for(JSONObject j:jobs.values())array.put(j);AtomicFile af=new AtomicFile(store);FileOutputStream stream=null;try{stream=af.startWrite();stream.write(array.toString().getBytes(StandardCharsets.UTF_8));af.finishWrite(stream);}catch(Exception e){if(stream!=null)af.failWrite(stream);}}
    public synchronized void serviceStopped(long generation,String code){
        if(generation!=serviceGeneration)return;stopRequested=true;
        if(plannedIdleStop&&code.equals("interrupted")){
            plannedIdleStop=false;JSONObject running=jobs.get(current);
            if(running!=null&&!TERMINAL.contains(running.optString("state"))&&!running.optString("state").equals("cancelling")){put(running,"state","queued");put(running,"progress",null);if(!pending.contains(current))pending.addFirst(current);}
            cancelMedia();if(process!=null)process.destroy();changed();return;
        }
        plannedIdleStop=false;
        for(String id:pending){JSONObject j=jobs.get(id);if(j!=null){put(j,"state","error");put(j,"error_code","interrupted");}}
        pending.clear();JSONObject j=jobs.get(current);
        if(j!=null&&!TERMINAL.contains(j.optString("state"))&&!j.optString("state").equals("cancelling")){put(j,"state","error");put(j,"error_code",code);}
        cancelMedia();if(process!=null)process.destroy();changed();
    }
}
