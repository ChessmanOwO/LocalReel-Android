package dev.localreel.android;
import android.app.Application;
public final class ReelApp extends Application {
    public JobManager jobs;
    @Override public void onCreate(){super.onCreate();jobs=new JobManager(this);}
}
