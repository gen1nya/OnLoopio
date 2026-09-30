package io.onloopio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.widget.ListView;
import io.onloopio.db.MetadataStore;
import io.onloopio.device.DeviceSettings;
import io.onloopio.device.OnlineMode;
import io.onloopio.model.Playlist;
import io.onloopio.player.PlaybackService;
import io.onloopio.sync.PlaylistSyncService;
import io.onloopio.sync.SyncReceiver;
import io.onloopio.sync.SyncScheduler;
import io.onloopio.ui.SyncSettingsActivity;
import java.util.HashSet;
import java.util.Set;

/** Live server reads only. Event receiver dispatch, real OS alarm/Wi-Fi and pause persistence. */
public final class AutoSyncTest extends InstrumentationTestCase {
    private Context context;private MetadataStore store;private DeviceSettings prefs;private boolean auto,charge,unplug,wifiTrigger,forced,paused;private int minutes;
    protected void setUp()throws Exception{super.setUp();context=getInstrumentation().getTargetContext();store=new MetadataStore(context);prefs=new DeviceSettings(context);
        auto=prefs.flag("playlist_auto_sync",true);charge=prefs.flag("sync_on_charge",true);unplug=prefs.flag("sync_on_unplug",true);wifiTrigger=prefs.flag("sync_on_wifi",true);forced=prefs.flag("force_offline",false);paused=prefs.flag("downloads_paused",false);minutes=prefs.number("sync_interval_minutes",15);
        prefs.setFlag("playlist_auto_sync",false);Thread.sleep(1800);for(int n=0;n<500 && PlaylistSyncService.busy;n++)Thread.sleep(100);
        prefs.setFlag("force_offline",false);prefs.setFlag("playlist_auto_sync",true);prefs.setFlag("sync_on_charge",true);prefs.setFlag("sync_on_unplug",true);prefs.setFlag("sync_on_wifi",true);prefs.setNumber("sync_interval_minutes",15);SyncScheduler.ensure(context,true);
        assertTrue("Home Wi-Fi missing",new OnlineMode(context).homeWifi());
    }
    protected void tearDown()throws Exception{prefs.setFlag("playlist_auto_sync",auto);prefs.setFlag("sync_on_charge",charge);prefs.setFlag("sync_on_unplug",unplug);prefs.setFlag("sync_on_wifi",wifiTrigger);prefs.setFlag("force_offline",forced);prefs.setFlag("downloads_paused",paused);prefs.setNumber("sync_interval_minutes",minutes);SyncScheduler.ensure(context,true);store.close();super.tearDown();}
    private void checkedAfter(long before)throws Exception{for(int n=0;n<600 && (store.lastPlaylistCheck()<=before || PlaylistSyncService.busy);n++)Thread.sleep(100);assertTrue("Background check failed: "+prefs.text("sync_status",""),store.lastPlaylistCheck()>before);assertFalse(PlaylistSyncService.busy);}
    public void testChargeEventsCoalesceAndDisabledTriggersDoNotCheck()throws Exception{
        long before=store.lastPlaylistCheck();SyncReceiver receiver=new SyncReceiver();receiver.onReceive(context,new Intent(Intent.ACTION_POWER_CONNECTED));receiver.onReceive(context,new Intent(Intent.ACTION_POWER_DISCONNECTED));checkedAfter(before);
        String reason=prefs.text("sync_last_trigger","");assertTrue(reason,reason.contains("charger connected"));assertTrue(reason,reason.contains("charger disconnected"));
        before=store.lastPlaylistCheck();prefs.setFlag("sync_on_charge",false);prefs.setFlag("sync_on_unplug",false);receiver.onReceive(context,new Intent(Intent.ACTION_POWER_CONNECTED));receiver.onReceive(context,new Intent(Intent.ACTION_POWER_DISCONNECTED));Thread.sleep(2400);assertEquals(before,store.lastPlaylistCheck());
    }
    public void testOsAlarmWakesPlaylistCheck()throws Exception{
        long before=store.lastPlaylistCheck();PendingIntent alarm=PendingIntent.getBroadcast(context,71,new Intent(context,SyncReceiver.class).setAction(SyncScheduler.PERIOD),PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.ELAPSED_REALTIME_WAKEUP,android.os.SystemClock.elapsedRealtime()+8000,alarm);
        try{checkedAfter(before);assertTrue(prefs.text("sync_last_trigger",""),prefs.text("sync_last_trigger","").contains("timer"));}finally{SyncScheduler.ensure(context,true);}
    }
    public void testRealHomeWifiReconnectTriggersCheck()throws Exception{
        WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);long before=store.lastPlaylistCheck();
        try{wifi.setWifiEnabled(false);for(int n=0;n<150 && new OnlineMode(context).homeWifi();n++)Thread.sleep(100);assertFalse(new OnlineMode(context).homeWifi());Thread.sleep(1000);
            wifi.setWifiEnabled(true);for(int n=0;n<300 && !new OnlineMode(context).homeWifi();n++)Thread.sleep(100);assertTrue(new OnlineMode(context).homeWifi());checkedAfter(before);assertTrue(prefs.text("sync_last_trigger",""),prefs.text("sync_last_trigger","").contains("home Wi-Fi connected"));
        }finally{wifi.setWifiEnabled(true);for(int n=0;n<300 && !new OnlineMode(context).homeWifi();n++)Thread.sleep(100);}
    }
    public void testForcedOfflineSkipsWithoutTouchingSavedSnapshot()throws Exception{
        long before=store.lastPlaylistCheck();int count=store.playlists().size();prefs.setFlag("force_offline",true);PlaylistSyncService.request(context,"manual",false,null);Thread.sleep(2800);
        assertEquals(before,store.lastPlaylistCheck());assertEquals(count,store.playlists().size());assertEquals("Forced offline",prefs.text("sync_status",""));
    }
    private Set<String> jobs(){Set<String> result=new HashSet<String>();Cursor c=store.getReadableDatabase().rawQuery("SELECT song_id FROM download_queue",null);try{while(c.moveToNext())result.add(c.getString(0));}finally{c.close();}return result;}
    public void testFollowingQueuesMissingMembersAndPreservesDownloadPause()throws Exception{
        assertFalse(store.playlists().isEmpty());Playlist p=store.playlists().get(0);boolean followed=store.followsPlaylist(p.id);Set<String> beforeJobs=jobs();prefs.setFlag("downloads_paused",true);
        try{store.followPlaylist(p.id,true);long before=store.lastPlaylistCheck();PlaylistSyncService.request(context,"manual",false,p.id);checkedAfter(before);assertTrue("Auto sync unpaused downloads",prefs.flag("downloads_paused",false));assertTrue(store.followsPlaylist(p.id));assertTrue("Missing playlist songs not queued",store.pendingDownloads()>0);}
        finally{store.followPlaylist(p.id,followed);for(String id:jobs())if(!beforeJobs.contains(id))store.removeDownload(id);PlaybackService.action(context,PlaybackService.STOP);}
    }
    public void testWheelSyncSettingsChangeIntervalAndShowStatus()throws Exception{
        ((OnLoopioTestRunner)getInstrumentation()).awaitHome();long before=store.lastPlaylistCheck();PlaylistSyncService.request(context,"manual",false,null);checkedAfter(before);
        ActivityMonitor monitor=getInstrumentation().addMonitor(SyncSettingsActivity.class.getName(),null,false);context.startActivity(new Intent(context,SyncSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SyncSettingsActivity screen=(SyncSettingsActivity)monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);assertNotNull(screen);
        try{getInstrumentation().waitForIdleSync();Thread.sleep(400);ListView root=(ListView)LauncherTest.findList(screen.getWindow().getDecorView());assertNotNull(root);assertEquals(8,root.getAdapter().getCount());
            wheelSelect(screen,1);sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);getInstrumentation().waitForIdleSync();Thread.sleep(550);
            wheelSelect(screen,3);sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);getInstrumentation().waitForIdleSync();Thread.sleep(550);assertEquals(30,prefs.number("sync_interval_minutes",0));
            String note=lastCheck(screen.getWindow().getDecorView());assertNotNull(note);assertFalse("Legacy formatter rendered literal hours",note.contains("HH:"));assertTrue("Missing actual clock time: "+note,note.matches("(?s).*\\d{2}:\\d{2}.*"));
            ScreenCapture.save(getInstrumentation(),screen,"qa-sync-06.png");
        }finally{getInstrumentation().runOnMainSync(new Runnable(){public void run(){screen.finish();}});}
    }
    private void wheelSelect(final SyncSettingsActivity screen,int index)throws Exception{for(int n=0;n<12;n++){final int[] selected={-1};getInstrumentation().runOnMainSync(new Runnable(){public void run(){ListView list=(ListView)LauncherTest.findList(screen.getWindow().getDecorView());selected[0]=list.getSelectedItemPosition();}});if(selected[0]==index)return;sendKeys(selected[0]>index?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(100);getInstrumentation().waitForIdleSync();}fail("Wheel did not select menu row "+index);}
    private String lastCheck(android.view.View view){if(view instanceof android.widget.TextView){String text=((android.widget.TextView)view).getText().toString();if(text.startsWith("Last check:"))return text;}if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++){String text=lastCheck(group.getChildAt(n));if(text!=null)return text;}}return null;}
}
