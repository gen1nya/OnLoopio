package io.onloopio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.widget.ListView;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.model.Song;
import io.onloopio.player.AudioCache;
import io.onloopio.player.CoverCache;
import io.onloopio.player.PlaybackService;
import io.onloopio.ui.NowPlayingView;
import io.onloopio.ui.PlaylistActivity;
import java.util.ArrayList;
import java.util.List;

public final class PlayerScreenTest extends InstrumentationTestCase {
    private PlaylistActivity home;private Context context;
    protected void setUp()throws Exception{super.setUp();context=getInstrumentation().getTargetContext();home=((OnLoopioTestRunner)getInstrumentation()).awaitHome();assertNotNull(home);}
    private void center(final int action){getInstrumentation().runOnMainSync(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();home.dispatchKeyEvent(new KeyEvent(now,now,action,KeyEvent.KEYCODE_DPAD_CENTER,0));}});}
    public void testArtworkPreviewProgressAndHiddenMenu()throws Exception{
        ServerConfig config=new ConfigStore(context).load();MetadataStore store=new MetadataStore(context);AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try{
            List<Song> tracks=new ArrayList<Song>();AudioCache cache=new AudioCache(context,config);for(Song song:store.catalogSongs())if(tracks.size()<2 && song.coverArt.length()>0 && "mp3".equals(song.suffix) && song.duration>30 && cache.contains(song))tracks.add(song);
            if(tracks.size()<2)for(Song song:store.catalogSongs())if(tracks.size()<2 && song.coverArt.length()>0 && "mp3".equals(song.suffix) && song.duration>30 && !tracks.contains(song))tracks.add(song);
            if(tracks.size()<2){store.replaceCatalog(new NavidromeClient(config).catalog());tracks.clear();for(Song song:store.catalogSongs())if(tracks.size()<2 && song.coverArt.length()>0 && "mp3".equals(song.suffix) && song.duration>30)tracks.add(song);}
            assertEquals(2,tracks.size());audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);PlaybackService.play(context,tracks,0,false);
            for(int n=0;n<200 && !PlaybackService.state.playing;n++)Thread.sleep(100);assertTrue(PlaybackService.state.message,PlaybackService.state.playing);
            context.startActivity(new Intent(context,PlaylistActivity.class).setAction("io.onloopio.OPEN_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            NowPlayingView view=awaitPlayer();assertNotNull("Player is a list",view);assertNull(LauncherTest.findList(home.getWindow().getDecorView()));
            for(int n=0;n<200 && !(view.hasCover() && view.hasNextCover());n++)Thread.sleep(100);assertTrue("Current cover missing",view.hasCover());assertTrue("Next cover missing",view.hasNextCover());assertEquals(tracks.get(1).id,PlaybackService.state.next.id);assertEquals(2,PlaybackService.state.queueSize);
            java.io.FileOutputStream controls=context.openFileOutput("control-settings.json",Context.MODE_PRIVATE);try{controls.write("{\"unlock\":true}".getBytes("UTF-8"));}finally{controls.close();}
            context.startActivity(new Intent(context,PlaylistActivity.class).setAction("io.onloopio.OPEN_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int n=0;n<100 && context.getFileStreamPath("control-settings.json").exists();n++)Thread.sleep(100);
            assertFalse("Private control settings not imported",context.getFileStreamPath("control-settings.json").exists());assertTrue("USB controls import interrupted playback",PlaybackService.state.playing);assertEquals(tracks.get(0).id,PlaybackService.state.song.id);
            getInstrumentation().waitForIdleSync();view=awaitPlayer();assertNotNull(view);
            if(!view.getContentDescription().toString().contains(" · Volume · ")){center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);Thread.sleep(650);}
            center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);Thread.sleep(650);assertTrue("Center paused music instead of switching wheel mode",PlaybackService.state.playing);assertTrue(view.getContentDescription().toString().contains(" · Seek · "));
            getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));home.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));}});Thread.sleep(650);assertFalse("Lower media key did not pause",PlaybackService.state.playing);
            final int position=PlaybackService.state.position;
            wheel(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(500);assertEquals("Accidental wheel step sought",position,PlaybackService.state.position);
            int steps=new io.onloopio.device.DeviceSettings(context).number("wheel_steps_per_turn",io.onloopio.device.WheelTurnGuard.DEFAULT_STEPS_PER_TURN);
            for(int n=1;n<steps;n++)wheel(KeyEvent.KEYCODE_DPAD_DOWN);
            Thread.sleep(500);assertEquals("Arming turn sought",position,PlaybackService.state.position);
            wheel(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(700);assertTrue("Wheel seek failed",PlaybackService.state.position>=position+4000);
            wheel(KeyEvent.KEYCODE_DPAD_UP);Thread.sleep(700);assertTrue("Armed reverse seek failed",Math.abs(PlaybackService.state.position-position)<1000);
            Thread.sleep(2200);wheel(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(500);assertTrue("Idle wheel protection did not return",Math.abs(PlaybackService.state.position-position)<1000);
            for(int n=0;n<4;n++){center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);}
            assertTrue(io.onloopio.device.ControlLock.locked(context));Thread.sleep(650);assertFalse("Lock gesture resumed music",PlaybackService.state.playing);assertTrue("Lock switched wheel mode",view.getContentDescription().toString().contains(" · Seek · "));
            wheel(KeyEvent.KEYCODE_DPAD_DOWN);getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_NEXT));}});
            io.onloopio.player.MediaButtons.handle(context,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);Thread.sleep(650);
            assertFalse("Locked media key resumed music",PlaybackService.state.playing);assertEquals("Locked Next changed track",tracks.get(0).id,PlaybackService.state.song.id);
            ScreenCapture.save(getInstrumentation(),home,"qa-controls-locked-07.png");
            for(int n=0;n<4;n++){center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);}
            assertFalse(io.onloopio.device.ControlLock.locked(context));Thread.sleep(650);assertFalse("Unlock gesture resumed music",PlaybackService.state.playing);
            ScreenCapture.save(getInstrumentation(),home,"qa-player-05.png");
            center(KeyEvent.ACTION_DOWN);Thread.sleep(750);center(KeyEvent.ACTION_UP);ListView menu=(ListView)LauncherTest.findList(home.getWindow().getDecorView());assertNotNull(menu);assertEquals("Library",menu.getAdapter().getItem(0));assertEquals("Download queue",menu.getAdapter().getItem(1));
            getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.onBackPressed();}});assertNotNull(home.getWindow().getDecorView().findViewWithTag("player"));assertFalse("Hold toggled playback",PlaybackService.state.playing);
            android.graphics.Bitmap offline=new CoverCache(context,config).load(tracks.get(0),new NavidromeClient(new ServerConfig("http://127.0.0.1:9","fixture","fixture")),false);assertNotNull("Cover did not survive offline",offline);
        }finally{context.deleteFile("control-settings.json");io.onloopio.device.ControlLock.locked(context,false);PlaybackService.action(context,PlaybackService.STOP);audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);store.close();getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});}
    }
    public void testHomeShowsPlayerAndLibraryIsBehindHold()throws Exception{
        context.startActivity(new Intent(context,PlaylistActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(400);getInstrumentation().waitForIdleSync();
        try{assertNotNull(home.getWindow().getDecorView().findViewWithTag("player"));assertNull(LauncherTest.findList(home.getWindow().getDecorView()));center(KeyEvent.ACTION_DOWN);Thread.sleep(750);center(KeyEvent.ACTION_UP);ListView menu=(ListView)LauncherTest.findList(home.getWindow().getDecorView());assertNotNull(menu);assertEquals("Library",menu.getAdapter().getItem(0));}
        finally{getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});}
    }
    public void testPowerOffInPlayerMenuRequiresExplicitConfirmation()throws Exception{
        android.app.Instrumentation.ActivityMonitor shutdown=getInstrumentation().addMonitor(new android.content.IntentFilter("android.intent.action.ACTION_REQUEST_SHUTDOWN"),new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null),true);
        try{
            context.startActivity(new Intent(context,PlaylistActivity.class).setAction("io.onloopio.OPEN_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));assertNotNull(awaitPlayer());
            center(KeyEvent.ACTION_DOWN);Thread.sleep(750);center(KeyEvent.ACTION_UP);
            getInstrumentation().waitForIdleSync();assertEquals("Power off",menuItem(3));
            select(3);click();assertEquals(2,menuCount());assertEquals("Cancel",menuItem(0));assertEquals(0,menuSelection());
            ScreenCapture.save(getInstrumentation(),home,"qa-player-power-071.png");click();assertEquals("Cancel powered off",0,shutdown.getHits());
            select(3);click();getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.onBackPressed();}});assertEquals("Back powered off",0,shutdown.getHits());assertEquals("Power off",menuItem(3));
            select(3);click();select(1);
            for(int n=0;n<4;n++){center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);}Thread.sleep(550);
            assertTrue(io.onloopio.device.ControlLock.locked(context));assertEquals("Lock sequence confirmed shutdown",0,shutdown.getHits());
            for(int n=0;n<4;n++){center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);}
            center(KeyEvent.ACTION_DOWN);Thread.sleep(750);center(KeyEvent.ACTION_UP);select(3);click();select(1);click();
            assertEquals("Explicit Power off did not request shutdown",1,shutdown.getHits());assertNotNull(home.getWindow().getDecorView().findViewWithTag("player"));
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkCallingOrSelfPermission("android.permission.SHUTDOWN"));
        }finally{getInstrumentation().removeMonitor(shutdown);io.onloopio.device.ControlLock.locked(context,false);getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});}
    }
    private void click()throws Exception{center(KeyEvent.ACTION_DOWN);center(KeyEvent.ACTION_UP);Thread.sleep(600);getInstrumentation().waitForIdleSync();}
    private int menuCount(){final int[] value={-1};getInstrumentation().runOnMainSync(new Runnable(){public void run(){ListView v=(ListView)LauncherTest.findList(home.getWindow().getDecorView());value[0]=v==null?-1:v.getAdapter().getCount();}});return value[0];}
    private String menuItem(final int index){final String[] value={null};getInstrumentation().runOnMainSync(new Runnable(){public void run(){ListView v=(ListView)LauncherTest.findList(home.getWindow().getDecorView());if(v!=null && index<v.getAdapter().getCount())value[0]=String.valueOf(v.getAdapter().getItem(index));}});return value[0];}
    private int menuSelection(){final int[] value={-1};getInstrumentation().runOnMainSync(new Runnable(){public void run(){value[0]=((ListView)LauncherTest.findList(home.getWindow().getDecorView())).getSelectedItemPosition();}});return value[0];}
    private void select(final int position)throws Exception{getInstrumentation().runOnMainSync(new Runnable(){public void run(){((ListView)LauncherTest.findList(home.getWindow().getDecorView())).setSelection(position);}});final int[] selected={-1};for(int n=0;n<50;n++){getInstrumentation().runOnMainSync(new Runnable(){public void run(){selected[0]=((ListView)LauncherTest.findList(home.getWindow().getDecorView())).getSelectedItemPosition();}});if(selected[0]==position)return;Thread.sleep(50);}assertEquals("Menu selection did not finish layout",position,selected[0]);}
    private void wheel(final int code){getInstrumentation().runOnMainSync(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0));home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0));}});}
    private NowPlayingView awaitPlayer()throws Exception{
        final NowPlayingView[] found=new NowPlayingView[1];
        for(int n=0;n<100 && found[0]==null;n++){getInstrumentation().runOnMainSync(new Runnable(){public void run(){found[0]=(NowPlayingView)home.getWindow().getDecorView().findViewWithTag("player");}});if(found[0]==null)Thread.sleep(100);}
        return found[0];
    }
}
