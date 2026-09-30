package io.onloopio;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.media.AudioManager;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.widget.ListView;
import io.onloopio.device.DeviceSettings;
import io.onloopio.ui.SettingsActivity;
import io.onloopio.ui.BluetoothActivity;

public final class DeviceMenuTest extends InstrumentationTestCase {
    private void keys(int... codes) throws Exception { for(int code:codes){ sendKeys(code); getInstrumentation().waitForIdleSync(); Thread.sleep(code==KeyEvent.KEYCODE_DPAD_CENTER?550:120); } }
    public void testWheelChangesPlatformSettingsAndBackCancels() throws Exception {
        ((OnLoopioTestRunner)getInstrumentation()).awaitHome();
        final DeviceSettings prefs=new DeviceSettings(getInstrumentation().getTargetContext());
        final int timeout=prefs.timeout(),brightness=prefs.brightness();
        final AudioManager audio=(AudioManager)getInstrumentation().getTargetContext().getSystemService(Context.AUDIO_SERVICE);
        final int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC); Activity menu=null;
        try {
            prefs.timeout(30000); prefs.brightness(128);
            ActivityMonitor monitor=getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);
            sendKeys(KeyEvent.KEYCODE_MENU); menu=monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor); assertNotNull(menu);
            keys(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals(45000,prefs.timeout());
            keys(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_BACK); assertEquals(45000,prefs.timeout());
            keys(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(153,prefs.brightness());
            keys(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(Math.max(0,volume-1),audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            prefs.shutdownTimer(10); assertTrue(prefs.shutdownRemaining()>=9); prefs.shutdownTimer(0); assertEquals(0,prefs.shutdownRemaining());
        } finally { prefs.timeout(timeout); prefs.brightness(brightness); audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0); if(menu!=null){ final Activity opened=menu; getInstrumentation().runOnMainSync(new Runnable(){ public void run(){ opened.finish(); }}); } }
    }
    public void testBluetoothMenuReturnsToSettingsWithoutAndroidEscape() throws Exception {
        ((OnLoopioTestRunner)getInstrumentation()).awaitHome();
        ActivityMonitor settings=getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);
        sendKeys(KeyEvent.KEYCODE_MENU); final Activity menu=settings.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(settings); assertNotNull(menu);
        ActivityMonitor bluetooth=getInstrumentation().addMonitor(BluetoothActivity.class.getName(),null,false);
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER); Activity bt=bluetooth.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(bluetooth); assertNotNull(bt);
        assertTrue(LauncherTest.findList(bt.getWindow().getDecorView()) instanceof ListView);
        keys(KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_BACK); assertTrue(bt.isFinishing()); assertTrue(menu.isFinishing());
    }
    public void testNativeEqualizerAndVendorWheelLock() throws Exception {
        android.media.audiofx.Equalizer eq=new android.media.audiofx.Equalizer(0,0);
        try { assertTrue(eq.getNumberOfPresets()>0); assertTrue(eq.getNumberOfBands()>0); } finally { eq.release(); }
        DeviceSettings prefs=new DeviceSettings(getInstrumentation().getTargetContext());
        java.io.FileInputStream input=new java.io.FileInputStream("/proc/tpd_keys_enable"); int original; try { original=input.read(); } finally { input.close(); }
        try { prefs.wheelLocked(false); input=new java.io.FileInputStream("/proc/tpd_keys_enable"); try { assertEquals('1',input.read()); } finally { input.close(); }
            prefs.wheelLocked(true); input=new java.io.FileInputStream("/proc/tpd_keys_enable"); try { assertEquals('0',input.read()); } finally { input.close(); }
        } finally { prefs.wheelLocked(original=='0'); }
    }
    public void testSystemClockAndPowerPermissions() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        String[] permissions={"android.permission.SET_TIME","android.permission.SHUTDOWN","android.permission.WRITE_SECURE_SETTINGS"};
        for(String permission:permissions) assertEquals(permission,android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkCallingOrSelfPermission(permission));
        int auto=android.provider.Settings.Global.getInt(context.getContentResolver(),android.provider.Settings.Global.AUTO_TIME,1);
        try { assertTrue(android.provider.Settings.Global.putInt(context.getContentResolver(),android.provider.Settings.Global.AUTO_TIME,0));
            long wanted=System.currentTimeMillis(); ((android.app.AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).setTime(wanted);
            assertTrue("Clock service rejected system app",Math.abs(System.currentTimeMillis()-wanted)<2000);
        } finally { android.provider.Settings.Global.putInt(context.getContentResolver(),android.provider.Settings.Global.AUTO_TIME,auto); }
        assertNotNull(context.getPackageManager().resolveActivity(new android.content.Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN"),0));
    }
    public void testPowerOffConfirmationDefaultsToCancel() throws Exception {
        ((OnLoopioTestRunner)getInstrumentation()).awaitHome();
        ActivityMonitor monitor=getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);
        sendKeys(KeyEvent.KEYCODE_MENU); final Activity menu=monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor); assertNotNull(menu);
        getInstrumentation().waitForIdleSync(); Thread.sleep(150);
        final ListView list=(ListView)LauncherTest.findList(menu.getWindow().getDecorView());
        getInstrumentation().runOnMainSync(new Runnable(){ public void run(){ list.setSelection(list.getAdapter().getCount()-2); }});
        getInstrumentation().waitForIdleSync(); Thread.sleep(150); keys(KeyEvent.KEYCODE_DPAD_CENTER);
        ListView choices=(ListView)LauncherTest.findList(menu.getWindow().getDecorView()); assertEquals(2,choices.getAdapter().getCount()); assertEquals(0,choices.getSelectedItemPosition());
        keys(KeyEvent.KEYCODE_DPAD_CENTER); assertFalse(menu.isFinishing()); keys(KeyEvent.KEYCODE_BACK);
    }
    public void testForceOfflineToggleNeedsOnlyWheel() throws Exception {
        ((OnLoopioTestRunner)getInstrumentation()).awaitHome();final DeviceSettings prefs=new DeviceSettings(getInstrumentation().getTargetContext());boolean original=prefs.flag("force_offline",false);
        ActivityMonitor monitor=getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);sendKeys(KeyEvent.KEYCODE_MENU);final Activity menu=monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);assertNotNull(menu);
        try {final ListView list=(ListView)LauncherTest.findList(menu.getWindow().getDecorView());getInstrumentation().runOnMainSync(new Runnable(){public void run(){list.setSelection(2);}});Thread.sleep(150);keys(KeyEvent.KEYCODE_DPAD_CENTER);
            final ListView choices=(ListView)LauncherTest.findList(menu.getWindow().getDecorView());getInstrumentation().runOnMainSync(new Runnable(){public void run(){choices.setSelection(1);}});Thread.sleep(150);keys(KeyEvent.KEYCODE_DPAD_CENTER);assertTrue("Forced offline flag was not saved",prefs.flag("force_offline",false));
        }finally{prefs.setFlag("force_offline",original);getInstrumentation().runOnMainSync(new Runnable(){public void run(){menu.finish();}});io.onloopio.player.PlaybackService.action(getInstrumentation().getTargetContext(),io.onloopio.player.PlaybackService.SETTINGS);}
    }
}
