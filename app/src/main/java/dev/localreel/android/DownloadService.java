package dev.localreel.android;
import android.app.*;
import android.content.*;
import android.os.IBinder;

public final class DownloadService extends Service {
    private long generation;
    @Override public void onCreate(){
        super.onCreate();generation=((ReelApp)getApplication()).jobs.serviceCreated();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("downloads","Tiến trình tải",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(42,new Notification.Builder(this,"downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("LocalReel đang xử lý video").setContentText("Mở app để theo dõi hoặc hủy tác vụ.").setContentIntent(open).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){((ReelApp)getApplication()).jobs.ensureWorker(generation,()->stopSelfResult(startId));return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){((ReelApp)getApplication()).jobs.serviceStopped(generation,"interrupted");super.onDestroy();}
    @Override public void onTimeout(int startId,int fgsType){((ReelApp)getApplication()).jobs.serviceStopped(generation,"timeout");stopSelfResult(startId);}
}
