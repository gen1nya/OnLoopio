package io.onloopio;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.widget.ListView;
import io.onloopio.device.CenterGesture;
import io.onloopio.device.ControlLock;
import io.onloopio.device.DeviceSettings;
import io.onloopio.device.WheelTurnGuard;
import io.onloopio.ui.PlaylistActivity;
import io.onloopio.ui.SettingsActivity;

public final class ControlGestureTest extends InstrumentationTestCase {
    private Context context;private PlaylistActivity home;private boolean originalLock;
    protected void setUp()throws Exception{
        super.setUp();context=getInstrumentation().getTargetContext();originalLock=ControlLock.locked(context);ControlLock.locked(context,false);
        home=((OnLoopioTestRunner)getInstrumentation()).awaitHome();assertNotNull(home);
    }
    protected void tearDown()throws Exception{
        ControlLock.locked(context,originalLock);
        getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});super.tearDown();
    }
    private void key(final Activity activity,final int code,final int action,final int repeat){
        getInstrumentation().runOnMainSync(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();activity.dispatchKeyEvent(new KeyEvent(now,now,action,code,repeat));}});
    }
    private void tap(Activity activity){key(activity,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0);key(activity,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0);}
    private void four(Activity activity){for(int n=0;n<4;n++)tap(activity);}
    public void testLockBlocksMenusAndLongHoldAndUnlockRestoresThem()throws Exception{
        four(home);assertTrue("Four center taps did not lock",ControlLock.locked(context));Thread.sleep(600);
        assertNotNull(home.getWindow().getDecorView().findViewWithTag("player"));
        int[] codes={KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_MEDIA_NEXT};
        for(int code:codes){key(home,code,KeyEvent.ACTION_DOWN,0);key(home,code,KeyEvent.ACTION_UP,0);}
        tap(home);tap(home);Thread.sleep(600);assertTrue("Two taps unlocked",ControlLock.locked(context));
        key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0);Thread.sleep(750);key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0);
        assertNull("Locked hold opened a menu",LauncherTest.findList(home.getWindow().getDecorView()));
        context.startActivity(new Intent(context,PlaylistActivity.class).setAction("io.onloopio.OPEN_LIBRARY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(500);
        assertNull("HOME intent bypassed lock",LauncherTest.findList(home.getWindow().getDecorView()));
        assertTrue("Lock was not persisted",new DeviceSettings(context).flag("controls_locked",false));
        four(home);assertFalse("Four center taps did not unlock",ControlLock.locked(context));
        key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0);Thread.sleep(750);key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0);
        assertNotNull("Unlocked hold failed",LauncherTest.findList(home.getWindow().getDecorView()));
        assertFalse("Volume is blocked",ControlLock.blocks(KeyEvent.KEYCODE_VOLUME_UP));assertFalse("Power is blocked",ControlLock.blocks(KeyEvent.KEYCODE_POWER));
    }
    public void testFourTapsFromSettingsDoNotApplySelectedSetting()throws Exception{
        DeviceSettings prefs=new DeviceSettings(context);int timeout=prefs.timeout();
        ActivityMonitor monitor=getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);
        sendKeys(KeyEvent.KEYCODE_MENU);final Activity settings=monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);assertNotNull(settings);
        try{
            final ListView list=(ListView)LauncherTest.findList(settings.getWindow().getDecorView());
            getInstrumentation().runOnMainSync(new Runnable(){public void run(){list.setSelection(4);}});Thread.sleep(200);
            four(settings);Thread.sleep(700);assertTrue(ControlLock.locked(context));assertEquals("Lock gesture changed timeout",timeout,prefs.timeout());
            assertTrue("Locked settings did not return to player",settings.isFinishing());assertNotNull(home.getWindow().getDecorView().findViewWithTag("player"));
            four(home);assertFalse(ControlLock.locked(context));
        }finally{getInstrumentation().runOnMainSync(new Runnable(){public void run(){settings.finish();}});}
    }
    public void testRepeatedAndCanceledCenterEventsDoNotLock()throws Exception{
        key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0);
        for(int n=1;n<=6;n++)key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,n);
        key(home,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0);Thread.sleep(600);
        assertFalse("Key repeat counted as taps",ControlLock.locked(context));
        getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});
        tap(home);tap(home);tap(home);
        key(home,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_DOWN,0);key(home,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_UP,0);tap(home);Thread.sleep(600);
        assertFalse("Navigation did not cancel the tap sequence",ControlLock.locked(context));
    }
    public void testPartialTurnsReversalIdleAndTrackChangeCannotSeek(){
        WheelTurnGuard guard=new WheelTurnGuard(12);long now=10000;
        for(int n=0;n<11;n++)assertEquals(0,guard.turn(1,now+=50,"first"));assertFalse(guard.armed());
        assertEquals(0,guard.turn(-1,now+=50,"first"));assertFalse(guard.armed());
        for(int n=0;n<10;n++)assertEquals(0,guard.turn(-1,now+=50,"first"));assertFalse(guard.armed());
        assertEquals(0,guard.turn(-1,now+=WheelTurnGuard.IDLE_MS,"first"));assertFalse("Partial turns accumulated across idle",guard.armed());
        for(int n=1;n<12;n++)assertEquals(0,guard.turn(-1,now+=50,"first"));assertTrue(guard.armed());
        assertEquals(-1,guard.turn(-1,now+=50,"first"));assertEquals(1,guard.turn(1,now+=50,"first"));
        assertEquals(0,guard.turn(1,now+=50,"second"));assertFalse("New track inherited armed seek",guard.armed());
        guard.reset();assertEquals(0,guard.progress());assertEquals(0,guard.turn(1,now+=50,null));assertFalse(guard.armed());
    }
    public void testHoldAndLifecycleCancelDoNotExecuteBufferedClicks()throws Exception{
        final int[] actions=new int[3];final CenterGesture[] gesture=new CenterGesture[1];
        getInstrumentation().runOnMainSync(new Runnable(){public void run(){gesture[0]=new CenterGesture(context,new Handler(),new CenterGesture.Listener(){
            public void started(){}public void shortPress(int count){actions[0]+=count;}public void longPress(){actions[1]++;}public void lockChanged(boolean locked){actions[2]++;}
        });}});
        try{
            gestureKey(gesture[0],KeyEvent.ACTION_DOWN);gestureKey(gesture[0],KeyEvent.ACTION_UP);
            gestureKey(gesture[0],KeyEvent.ACTION_DOWN);Thread.sleep(750);gestureKey(gesture[0],KeyEvent.ACTION_UP);Thread.sleep(550);
            assertEquals("A buffered click escaped before hold",0,actions[0]);assertEquals(1,actions[1]);
            gestureKey(gesture[0],KeyEvent.ACTION_DOWN);gestureKey(gesture[0],KeyEvent.ACTION_UP);
            getInstrumentation().runOnMainSync(new Runnable(){public void run(){gesture[0].cancel();}});Thread.sleep(550);assertEquals(0,actions[0]);
            for(int n=0;n<4;n++){gestureKey(gesture[0],KeyEvent.ACTION_DOWN);gestureKey(gesture[0],KeyEvent.ACTION_UP);}
            Thread.sleep(550);assertEquals(0,actions[0]);assertEquals(1,actions[2]);assertTrue(ControlLock.locked(context));
        }finally{getInstrumentation().runOnMainSync(new Runnable(){public void run(){gesture[0].cancel();}});}
    }
    private void gestureKey(final CenterGesture gesture,final int action){getInstrumentation().runOnMainSync(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();gesture.key(new KeyEvent(now,now,action,KeyEvent.KEYCODE_DPAD_CENTER,0));}});}
    public void testPrivateControlSettingsApplyOnlyTheirKeys()throws Exception{
        DeviceSettings prefs=new DeviceSettings(context);int steps=prefs.number("wheel_steps_per_turn",WheelTurnGuard.DEFAULT_STEPS_PER_TURN),brightness=prefs.brightness();boolean offline=prefs.flag("force_offline",false);
        try{
            ControlLock.locked(context,true);provision("{\"wheel_steps_per_turn\":16,\"unlock\":true}");
            assertTrue(io.onloopio.device.ControlConfigStore.importPrivateFile(context));assertFalse(ControlLock.locked(context));assertEquals(16,prefs.number("wheel_steps_per_turn",0));
            assertEquals(brightness,prefs.brightness());assertEquals(offline,prefs.flag("force_offline",false));assertFalse(context.getFileStreamPath("control-settings.json").exists());
        }finally{prefs.setNumber("wheel_steps_per_turn",steps);ControlLock.locked(context,false);context.deleteFile("control-settings.json");}
    }
    public void testInvalidControlSettingsCannotPartiallyUnlock()throws Exception{
        DeviceSettings prefs=new DeviceSettings(context);int steps=prefs.number("wheel_steps_per_turn",WheelTurnGuard.DEFAULT_STEPS_PER_TURN);
        String[] invalid={"{\"wheel_steps_per_turn\":7,\"unlock\":true}","{\"wheel_steps_per_turn\":16,\"unlock\":true,\"brightness\":1}","{\"wheel_steps_per_turn\":12.5,\"unlock\":true}"};
        try{ControlLock.locked(context,true);for(String json:invalid){provision(json);assertFalse(io.onloopio.device.ControlConfigStore.importPrivateFile(context));assertTrue("Invalid file unlocked controls",ControlLock.locked(context));assertEquals("Invalid file changed calibration",steps,prefs.number("wheel_steps_per_turn",WheelTurnGuard.DEFAULT_STEPS_PER_TURN));}}
        finally{ControlLock.locked(context,false);context.deleteFile("control-settings.json");}
    }
    private void provision(String json)throws Exception{java.io.FileOutputStream file=context.openFileOutput("control-settings.json",Context.MODE_PRIVATE);try{file.write(json.getBytes("UTF-8"));}finally{file.close();}}
}
