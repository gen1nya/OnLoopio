package io.onloopio;

import android.content.Context;
import android.os.Environment;
import android.app.Instrumentation;
import static junit.framework.Assert.*;
import io.onloopio.device.UsbStorage;
import io.onloopio.device.DeviceSettings;
import io.onloopio.library.MusicLibraryService;
import io.onloopio.library.MusicPaths;
import io.onloopio.db.MetadataStore;
import io.onloopio.model.Song;
import io.onloopio.player.PlaybackService;
import java.io.File;
import java.util.Collections;

/** Separate instrumentation entry point; ordinary suites cannot unmount storage. */
public final class UsbLibraryTest extends Instrumentation {
    private String operation;
    public void onCreate(android.os.Bundle args){super.onCreate(args);operation=args.getString("operation");start();}
    public void onStart(){android.os.Bundle result=new android.os.Bundle();try{if("enable".equals(operation))enableSharingForComputer();else if("disable".equals(operation))returnStorageToPlayer();else if("verify".equals(operation))verifyComputerImportAndPlayback();else if("cleanup".equals(operation))removeComputerFixture();else if("registry".equals(operation))verifyRemountedRegistry();else if("server".equals(operation))verifyServerConnection();else throw new IllegalArgumentException("Explicit USB operation required");result.putString("stream","USB library "+operation+" passed\n");finish(android.app.Activity.RESULT_OK,result);}catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(android.app.Activity.RESULT_CANCELED,result);}}
    public void verifyRemountedRegistry()throws Exception{
        Context c=getTargetContext();io.onloopio.library.AudioFileIndex index=new io.onloopio.library.AudioFileIndex(c);MetadataStore store=new MetadataStore(c);
        try{java.util.Set<String> owned=index.ownedPaths();for(io.onloopio.library.AudioFileIndex.Entry e:index.entries(null))assertTrue("Owned download lost after USB remount",e.matches(new File(e.path)));for(Song s:store.localSongs())assertFalse("Downloaded audio duplicated as local",owned.contains(s.localPath));assertEquals(9493,store.catalogCount());assertEquals(17,store.playlists().size());}finally{index.close();store.close();}
    }
    public void verifyServerConnection()throws Exception{
        Context c=getTargetContext();io.onloopio.api.ServerConfig config=new io.onloopio.config.ConfigStore(c).load();assertNotNull(config);assertTrue(new io.onloopio.device.OnlineMode(c).homeWifi());assertEquals(17,new io.onloopio.api.NavidromeClient(config).getPlaylists().size());
    }
    public void enableSharingForComputer()throws Exception{
        final Context context=getTargetContext();assertFalse(UsbStorage.enabled(context));assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkCallingOrSelfPermission("android.permission.MOUNT_UNMOUNT_FILESYSTEMS"));
        runOnMainSync(new Runnable(){public void run(){try{UsbStorage.enable(context);}catch(Exception e){throw new RuntimeException(e);}}});
        for(int n=0;n<300 && !UsbStorage.enabled(context);n++)Thread.sleep(100);assertTrue(MusicLibraryService.status,UsbStorage.enabled(context));assertNull(PlaybackService.state.song);
    }
    public void returnStorageToPlayer()throws Exception{
        Context context=getTargetContext();assertTrue(UsbStorage.enabled(context));UsbStorage.disable(context);
        for(int n=0;n<300 && !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());n++)Thread.sleep(100);assertEquals(Environment.MEDIA_MOUNTED,Environment.getExternalStorageState());
    }
    public void verifyComputerImportAndPlayback()throws Exception{
        Context context=getTargetContext();File copied=new File(MusicPaths.root(),"OnLoopio USB QA/Test Album/USB copied.wav");assertTrue("Windows copy missing",copied.isFile());MetadataStore store=new MetadataStore(context);Song imported=null;
        for(int n=0;n<150 && imported==null;n++){for(Song s:store.localSongs())if(copied.getCanonicalPath().equals(s.localPath))imported=s;if(imported==null)Thread.sleep(200);}assertNotNull("USB remount did not auto-scan imported audio",imported);assertEquals("OnLoopio USB QA",imported.artist);assertEquals("Test Album",imported.album);
        DeviceSettings prefs=new DeviceSettings(context);boolean forced=prefs.flag("force_offline",false);android.media.AudioManager audio=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int volume=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        try{prefs.setFlag("force_offline",true);audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,0,0);PlaybackService.play(context,Collections.singletonList(imported),0,false);for(int n=0;n<120 && !(PlaybackService.state.playing && PlaybackService.state.song!=null && imported.id.equals(PlaybackService.state.song.id));n++)Thread.sleep(100);assertTrue(PlaybackService.state.message,PlaybackService.state.playing);Thread.sleep(1200);assertTrue(PlaybackService.state.position>=500);}
        finally{PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(600);prefs.setFlag("force_offline",forced);audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,volume,0);store.close();}
    }
    public void removeComputerFixture()throws Exception{
        Context context=getTargetContext();File dir=new File(MusicPaths.root(),"OnLoopio USB QA/Test Album");File song=new File(dir,"USB copied.wav");assertEquals(new File(MusicPaths.root(),"OnLoopio USB QA/Test Album/USB copied.wav").getCanonicalPath(),song.getCanonicalPath());if(song.exists())assertTrue(song.delete());if(dir.exists())assertTrue(dir.delete());if(dir.getParentFile().exists())assertTrue(dir.getParentFile().delete());
        MusicLibraryService.request(context,true);MetadataStore store=new MetadataStore(context);try{for(int n=0;n<150;n++){boolean present=false;for(Song s:store.localSongs())if(song.getCanonicalPath().equals(s.localPath))present=true;if(!present)return;Thread.sleep(200);}fail("Deleted fixture remained in local index");}finally{store.close();}
    }
}
