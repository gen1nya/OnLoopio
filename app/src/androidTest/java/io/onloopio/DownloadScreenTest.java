package io.onloopio;

import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.ListView;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.device.DeviceSettings;
import io.onloopio.model.Song;
import io.onloopio.player.AudioCache;
import io.onloopio.player.PlaybackService;
import io.onloopio.ui.DownloadsActivity;
import io.onloopio.ui.CacheSettingsActivity;
import java.util.Collections;

public final class DownloadScreenTest extends InstrumentationTestCase {
    public void testQuotaFailureVisibleAndRetrySavesAudio()throws Exception{
        Context context=getInstrumentation().getTargetContext();((OnLoopioTestRunner)getInstrumentation()).awaitHome();ServerConfig config=new ConfigStore(context).load();MetadataStore store=new MetadataStore(context);DeviceSettings prefs=new DeviceSettings(context);
        final int limit=prefs.number("cache_limit_mb",io.onloopio.player.CachePolicy.DEFAULT_LIMIT_MB);final boolean auto=prefs.flag("cache_auto_clean",false),paused=prefs.flag("downloads_paused",false);Song selected=null;DownloadsActivity screen=null;
        try{AudioCache cache=new AudioCache(context,config);for(Song song:store.catalogSongs())if(selected==null && "mp3".equals(song.suffix) && song.duration>30 && song.duration<130 && !cache.contains(song))selected=song;assertNotNull(selected);
            prefs.setFlag("cache_auto_clean",false);prefs.setNumber("cache_limit_mb",1);PlaybackService.entityAction(context,PlaybackService.ENQUEUE,Collections.singletonList(selected));
            for(int n=0;n<300;n++){boolean failed=false;for(MetadataStore.Download job:store.downloads())if(job.song.id.equals(selected.id) && job.state==1)failed=true;if(failed)break;Thread.sleep(100);}
            MetadataStore.Download failure=null;for(MetadataStore.Download job:store.downloads())if(job.song.id.equals(selected.id))failure=job;assertNotNull(failure);assertEquals(1,failure.state);assertTrue(failure.error.contains("Cache limit"));
            ActivityMonitor monitor=getInstrumentation().addMonitor(DownloadsActivity.class.getName(),null,false);context.startActivity(new Intent(context,DownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));screen=(DownloadsActivity)monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);assertNotNull(screen);getInstrumentation().waitForIdleSync();
            ListView list=(ListView)LauncherTest.findList(screen.getWindow().getDecorView());boolean visible=false;for(int n=0;n<list.getAdapter().getCount();n++){String label=list.getAdapter().getView(n,null,list).toString();android.widget.TextView row=(android.widget.TextView)list.getAdapter().getView(n,null,list);if(row.getText().toString().contains("Failed: Cache limit"))visible=true;}assertTrue("Per-track failure missing",visible);
            prefs.setNumber("cache_limit_mb",limit);store.retryDownload(selected.id);PlaybackService.action(context,PlaybackService.RESUME_PENDING);for(int n=0;n<400;n++){cache.refresh();boolean committed=false;for(MetadataStore.Download job:store.downloads())if(job.song.id.equals(selected.id) && job.state==2)committed=true;if(cache.contains(selected) && committed)break;Thread.sleep(100);}assertTrue("Retry did not save audio",cache.contains(selected));
            MetadataStore.Download complete=null;for(MetadataStore.Download job:store.downloads())if(job.song.id.equals(selected.id))complete=job;assertNotNull(complete);assertEquals(2,complete.state);assertTrue(complete.received>16);assertEquals(complete.received,complete.total);
            Thread.sleep(1200);ScreenCapture.save(getInstrumentation(),screen,"qa-downloads-05.png");
        }finally{if(screen!=null){final DownloadsActivity finish=screen;getInstrumentation().runOnMainSync(new Runnable(){public void run(){finish.finish();}});}if(selected!=null)store.removeDownload(selected.id);prefs.setNumber("cache_limit_mb",limit);prefs.setFlag("cache_auto_clean",auto);prefs.setFlag("downloads_paused",paused);PlaybackService.action(context,PlaybackService.STOP);store.close();}
    }
    public void testPolicyMenuHasProfilesAndLimits()throws Exception{
        Context context=getInstrumentation().getTargetContext();((OnLoopioTestRunner)getInstrumentation()).awaitHome();ActivityMonitor monitor=getInstrumentation().addMonitor(CacheSettingsActivity.class.getName(),null,false);context.startActivity(new Intent(context,CacheSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final CacheSettingsActivity screen=(CacheSettingsActivity)monitor.waitForActivityWithTimeout(5000);getInstrumentation().removeMonitor(monitor);assertNotNull(screen);
        try{getInstrumentation().waitForIdleSync();Thread.sleep(500);ListView list=(ListView)LauncherTest.findList(screen.getWindow().getDecorView());android.widget.TextView first=(android.widget.TextView)list.getAdapter().getView(0,null,list);assertEquals("Rotation profiles",first.getText().toString());assertTrue(list.getAdapter().getCount()>=7);ScreenCapture.save(getInstrumentation(),screen,"qa-cache-05.png");}
        finally{getInstrumentation().runOnMainSync(new Runnable(){public void run(){screen.finish();}});}
    }
}
