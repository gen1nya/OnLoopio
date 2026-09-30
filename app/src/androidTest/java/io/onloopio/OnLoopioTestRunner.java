package io.onloopio;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.os.PowerManager;
import android.test.InstrumentationTestRunner;
import io.onloopio.ui.PlaylistActivity;

/** The sole HOME is already started by Android; startActivitySync cannot await its onNewIntent. */
public final class OnLoopioTestRunner extends InstrumentationTestRunner {
    private volatile PlaylistActivity home;
    private PowerManager.WakeLock screen;
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity instanceof PlaylistActivity) home=(PlaylistActivity)activity;
    }
    public void callActivityOnPause(Activity activity) {
        if(activity==home) home=null;
        super.callActivityOnPause(activity);
    }
    public PlaylistActivity awaitHome() {
        // Direct key dispatch does not run ViewRoot's native wheel transition out of touch mode.
        setInTouchMode(false);
        if(screen==null) {PowerManager power=(PowerManager)getTargetContext().getSystemService(android.content.Context.POWER_SERVICE);
            screen=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"OnLoopio:test-screen");screen.acquire();}
        if(PlaylistActivity.foreground==null || PlaylistActivity.foreground.isFinishing())getTargetContext().startActivity(new Intent(getTargetContext(),PlaylistActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline=SystemClock.uptimeMillis()+15000;
        while ((PlaylistActivity.foreground == null || PlaylistActivity.foreground.isFinishing()) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        home=PlaylistActivity.foreground;
        if(home==null || home.isFinishing()) return null;
        waitForIdleSync();runOnMainSync(new Runnable(){public void run(){home.openLibrary();}}); SystemClock.sleep(300);
        return home;
    }
    public void finish(int resultCode,android.os.Bundle results) {if(screen!=null && screen.isHeld())screen.release();super.finish(resultCode,results);}
}
