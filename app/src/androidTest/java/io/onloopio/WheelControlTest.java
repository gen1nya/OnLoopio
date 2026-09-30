package io.onloopio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import io.onloopio.device.ControlLock;
import io.onloopio.device.DeviceSettings;
import io.onloopio.device.WheelTurnGuard;
import io.onloopio.db.MetadataStore;
import io.onloopio.library.MusicLibraryService;
import io.onloopio.library.MusicPaths;
import io.onloopio.model.Song;
import io.onloopio.player.PlaybackService;
import io.onloopio.ui.NowPlayingView;
import io.onloopio.ui.PlaylistActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/** Real stream volume, paused-media seeking and transport separation on a silent local fixture. */
public final class WheelControlTest extends InstrumentationTestCase {
    private Context context;private PlaylistActivity home;private AudioManager audio;private int originalVolume,steps;private boolean originalLock,heldScan;private File folder,file,scanHold;private String fixtureId;private MetadataStore store;private List<MetadataStore.LocalEntry> previous;
    protected void setUp()throws Exception{
        super.setUp();context=getInstrumentation().getTargetContext();originalLock=ControlLock.locked(context);ControlLock.locked(context,false);
        audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);originalVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
        try{PlaybackService.action(context,PlaybackService.STOP);home=((OnLoopioTestRunner)getInstrumentation()).awaitHome();assertNotNull(home);
        scanHold=new File(context.getFilesDir(),"music-library.hold");heldScan=scanHold.createNewFile();assertTrue("External scan hold exists",heldScan);for(int n=0;n<100 && MusicLibraryService.running;n++)Thread.sleep(100);assertFalse(MusicLibraryService.running);
        store=new MetadataStore(context);previous=store.localEntries();removeFailedFixtures();folder=new File(MusicPaths.root(),".OnLoopio-wheel-QA-"+System.nanoTime());assertTrue(folder.mkdir());file=new File(folder,"silence.wav");
        InputStream in=getInstrumentation().getContext().getAssets().open("music/fixture.wav");FileOutputStream out=new FileOutputStream(file);try{byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}finally{in.close();out.close();}
        fixtureId="local:wheel-qa-"+System.nanoTime();Song silent=new Song(fixtureId,"Wheel controls QA","QA","QA","wav",12,"",0,"","",0,"",file.getCanonicalPath());List<MetadataStore.LocalEntry> fixture=new ArrayList<MetadataStore.LocalEntry>(previous);fixture.add(new MetadataStore.LocalEntry(silent,file.length(),file.lastModified()));store.replaceLocalSongs(fixture);PlaybackService.play(context,Collections.singletonList(silent),0,false);
        awaitPlaying(true);context.startActivity(new Intent(context,PlaylistActivity.class).setAction("io.onloopio.OPEN_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(500);getInstrumentation().waitForIdleSync();
        if(description().contains(" · Seek · "))tap();assertTrue(description(),description().contains(" · Volume · "));
        media();awaitPlaying(false);steps=new DeviceSettings(context).number("wheel_steps_per_turn",WheelTurnGuard.DEFAULT_STEPS_PER_TURN);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2),0);
        }catch(Throwable failure){cleanupFixture();if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;}
    }
    protected void tearDown()throws Exception{
        try{cleanupFixture();
            if(description().contains(" · Seek · "))tap();getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});
        }finally{super.tearDown();}
    }
    private void cleanupFixture()throws Exception{
        PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(500);
        try{if(store!=null && previous!=null){store.replaceLocalSongs(previous);if(fixtureId!=null)store.getWritableDatabase().delete("audio_state","song_id=?",new String[]{fixtureId});}if(file!=null && file.exists()){assertEquals(folder.getCanonicalPath(),file.getParentFile().getCanonicalPath());assertTrue(file.delete());}if(folder!=null && folder.exists())assertTrue(folder.delete());}
        finally{if(store!=null){store.close();store=null;}if(heldScan){heldScan=false;assertTrue(scanHold.delete());}audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0);ControlLock.locked(context,originalLock);}
    }
    private byte[] digest(InputStream in)throws Exception{java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try{byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)hash.update(bytes,0,n);return hash.digest();}finally{in.close();}}
    private void removeFailedFixtures()throws Exception{
        File root=MusicPaths.root();File[] dirs=root.listFiles();assertNotNull(dirs);byte[] expected=digest(getInstrumentation().getContext().getAssets().open("music/fixture.wav"));
        for(File dir:dirs)if(dir.getName().matches("\\.OnLoopio-wheel-QA-[0-9]+")){assertEquals(root.getCanonicalPath(),dir.getParentFile().getCanonicalPath());assertTrue(dir.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator));File[] files=dir.listFiles();assertNotNull(files);assertEquals(1,files.length);assertEquals("silence.wav",files[0].getName());assertTrue("Unexpected QA audio",java.util.Arrays.equals(expected,digest(new java.io.FileInputStream(files[0]))));assertTrue(files[0].delete());assertTrue(dir.delete());}
    }
    public void testFirstVolumeTurnIdleRepeatAndLimits()throws Exception{
        int initial=volume(),position=PlaybackService.state.position;wheel(1,steps);assertEquals("Arming changed volume",initial,volume());wheel(1,1);assertEquals(initial+1,volume());wheel(-1,1);assertEquals(initial,volume());assertEquals("Volume sought media",position,PlaybackService.state.position);
        getInstrumentation().runOnMainSync(new Runnable(){public void run(){long t=SystemClock.uptimeMillis();home.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,1));}});assertEquals("Held/repeated wheel changed volume",initial,volume());
        Thread.sleep(WheelTurnGuard.IDLE_MS+100);wheel(1,steps-1);assertEquals(initial,volume());wheel(-1,steps);assertEquals("Partial opposite turns armed volume",initial,volume());wheel(-1,1);assertEquals(initial-1,volume());
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);wheel(-1,3);assertEquals(0,volume());int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,max,0);wheel(1,3);assertEquals(max,volume());assertFalse(PlaybackService.state.playing);
    }
    public void testModeSwitchRearmsWithoutChangingPlayback()throws Exception{
        int initial=volume();wheel(1,steps);int position=PlaybackService.state.position;key(KeyEvent.KEYCODE_DPAD_CENTER,1);wheel(1,steps);assertTrue(description(),description().contains(" · Seek · "));assertFalse(PlaybackService.state.playing);
        Thread.sleep(250);assertEquals("Switch inherited armed volume session",position,PlaybackService.state.position);wheel(1,1);for(int n=0;n<30 && PlaybackService.state.position<position+4000;n++)Thread.sleep(100);assertTrue("Selected seek did not work",PlaybackService.state.position>=position+4000);assertEquals(initial,volume());
        key(KeyEvent.KEYCODE_DPAD_CENTER,1);wheel(1,steps);assertTrue(description(),description().contains(" · Volume · "));assertEquals("Switch inherited armed seek session",initial,volume());wheel(1,1);assertEquals(initial+1,volume());assertFalse("Mode switch resumed playback",PlaybackService.state.playing);
        PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(500);key(KeyEvent.KEYCODE_BACK,1);assertNull(PlaybackService.state.song);wheel(-1,steps);int before=volume();wheel(-1,1);assertEquals("Volume requires a selected song",before-1,volume());
    }
    public void testLowerButtonAndLockDoNotSwitchWheelMode()throws Exception{
        media();awaitPlaying(true);assertTrue(description().contains(" · Volume · "));tap();assertTrue("Center paused music",PlaybackService.state.playing);assertTrue(description().contains(" · Seek · "));media();awaitPlaying(false);assertTrue(description().contains(" · Seek · "));
        four();assertTrue(ControlLock.locked(context));assertTrue("Lock changed mode",description().contains(" · Seek · "));int level=volume(),position=PlaybackService.state.position;wheel(1,steps+2);media();Thread.sleep(500);assertFalse(PlaybackService.state.playing);assertEquals(level,volume());assertEquals(position,PlaybackService.state.position);
        four();assertFalse(ControlLock.locked(context));assertTrue("Unlock changed mode",description().contains(" · Seek · "));assertFalse(PlaybackService.state.playing);
    }
    private int volume(){return audio.getStreamVolume(AudioManager.STREAM_MUSIC);}
    private String description(){final String[] result={null};getInstrumentation().runOnMainSync(new Runnable(){public void run(){NowPlayingView v=(NowPlayingView)home.getWindow().getDecorView().findViewWithTag("player");assertNotNull(v);result[0]=v.getContentDescription().toString();}});return result[0];}
    private void key(final int code,final int count){getInstrumentation().runOnMainSync(new Runnable(){public void run(){for(int n=0;n<count;n++){long t=SystemClock.uptimeMillis();home.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));home.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0));}}});}
    private void wheel(int direction,int count){key(direction>0?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP,count);}
    private void tap()throws Exception{key(KeyEvent.KEYCODE_DPAD_CENTER,1);Thread.sleep(550);getInstrumentation().waitForIdleSync();}
    private void four()throws Exception{key(KeyEvent.KEYCODE_DPAD_CENTER,4);Thread.sleep(550);}
    private void media(){getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);getInstrumentation().waitForIdleSync();}
    private void awaitPlaying(boolean expected)throws Exception{for(int n=0;n<100 && PlaybackService.state.playing!=expected;n++)Thread.sleep(100);assertEquals(PlaybackService.state.message,expected,PlaybackService.state.playing);}
}
