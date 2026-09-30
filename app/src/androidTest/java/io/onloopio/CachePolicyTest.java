package io.onloopio;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.RenamingDelegatingContext;
import io.onloopio.api.ServerConfig;
import io.onloopio.db.MetadataStore;
import io.onloopio.device.DeviceSettings;
import io.onloopio.model.Library;
import io.onloopio.model.Song;
import io.onloopio.player.AudioCache;
import io.onloopio.player.CachePolicy;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** Destructive policy checks use only a unique fixture account on SD and a separate database. */
public final class CachePolicyTest extends InstrumentationTestCase {
    private Context context;private MetadataStore store;private AudioCache cache;private DeviceSettings prefs;private int limit,days,reserve;private boolean auto;
    protected void setUp()throws Exception{super.setUp();final Context actual=getInstrumentation().getTargetContext();context=new android.content.ContextWrapper(actual){public Context getApplicationContext(){return this;}public android.content.SharedPreferences getSharedPreferences(String name,int mode){return actual.getSharedPreferences("policy_test_"+name,mode);}};prefs=new DeviceSettings(context);limit=prefs.number("cache_limit_mb",CachePolicy.DEFAULT_LIMIT_MB);days=prefs.number("cache_max_idle_days",90);reserve=prefs.number("cache_reserve_mb",CachePolicy.DEFAULT_RESERVE_MB);auto=prefs.flag("cache_auto_clean",false);
        store=new MetadataStore(new RenamingDelegatingContext(context,"policy_test_"));String key="policy-"+System.nanoTime();store.selectAccount(key);cache=new AudioCache(context,new ServerConfig("https://fixture.invalid",key,"fixture"));prefs.setFlag("cache_auto_clean",true);prefs.setNumber("cache_limit_mb",0);prefs.setNumber("cache_max_idle_days",90);prefs.setNumber("cache_reserve_mb",16);
    }
    protected void tearDown()throws Exception{try{cache.clear();store.close();}finally{prefs.setNumber("cache_limit_mb",limit);prefs.setNumber("cache_max_idle_days",days);prefs.setNumber("cache_reserve_mb",reserve);prefs.setFlag("cache_auto_clean",auto);super.tearDown();}}
    private Song saved(String id,long age,long bytes)throws Exception{Song song=new Song(id,id,"Fixture","Fixture","mp3",60);Library lib=new Library();lib.songs.add(song);store.saveDetail(new io.onloopio.model.PlaylistDetail(new io.onloopio.model.Playlist(id,id,"",1,60),lib.songs));RandomAccessFile file=new RandomAccessFile(cache.file(id),"rw");try{file.setLength(bytes);}finally{file.close();}store.downloaded(id,System.currentTimeMillis()-age*86400000L);cache.refresh();return song;}
    public void testAgeCleanupProtectsPinsPlaybackDownloadsAndRecentlyListened()throws Exception{
        saved("old",180,4096);saved("pinned",180,4096);saved("playing",180,4096);saved("queued",180,4096);saved("recent-listen",180,4096);
        store.pin(Arrays.asList("pinned"),true);store.enqueueDownloads(Arrays.asList("queued"));store.listened("recent-listen",System.currentTimeMillis()-10*86400000L);
        CachePolicy policy=new CachePolicy(context,store,cache);java.util.Set<String> playing=new HashSet<String>(Arrays.asList("playing"));CachePolicy.Result preview=policy.preview(playing,System.currentTimeMillis());assertEquals(1,preview.removed);assertTrue("Preview deleted audio",cache.contains("old"));
        CachePolicy.Result result=policy.clean(playing,0,System.currentTimeMillis(),true);assertEquals(1,result.removed);assertFalse(cache.contains("old"));for(String id:Arrays.asList("pinned","playing","queued","recent-listen"))assertTrue(id,cache.contains(id));
        Library refresh=new Library();refresh.songs.add(new Song("pinned","Updated","Fixture","Fixture","mp3",60));store.replaceCatalog(refresh);assertTrue("Catalog refresh lost pin",store.audioState("pinned").pinned);assertEquals(1,store.audioState("recent-listen").plays);
    }
    public void testSpaceLimitUsesLeastRecentlyPlayedAndStopsForProtectedFiles()throws Exception{
        saved("old",20,600*1024);saved("middle",10,600*1024);saved("new",1,600*1024);prefs.setNumber("cache_limit_mb",1);prefs.setNumber("cache_max_idle_days",0);
        CachePolicy.Result result=new CachePolicy(context,store,cache).clean(Collections.<String>emptySet(),0,System.currentTimeMillis(),true);assertEquals(2,result.removed);assertTrue(cache.contains("new"));assertFalse(result.blocked);
        saved("protected",2,600*1024);store.pin(Arrays.asList("new","protected"),true);result=new CachePolicy(context,store,cache).clean(Collections.<String>emptySet(),0,System.currentTimeMillis(),true);assertTrue("Pinned files should stop downloads at quota",result.blocked);assertEquals(0,result.removed);
    }
    public void testDisabledAutomationDoesNotDeleteAndStillEnforcesDownloadBudget()throws Exception{saved("old",180,600*1024);prefs.setFlag("cache_auto_clean",false);prefs.setNumber("cache_limit_mb",1);CachePolicy policy=new CachePolicy(context,store,cache);try{policy.requireSpace(Collections.<String>emptySet(),600*1024,0);fail("Exceeded cache limit");}catch(java.io.IOException expected){}assertTrue(cache.contains("old"));}
    public void testLargeIncomingAudioBudgetDoesNotOverflowOrDeleteSavedMusic()throws Exception{
        saved("saved",1,1024*1024);prefs.setFlag("cache_auto_clean",false);prefs.setNumber("cache_limit_mb",96*1024);
        CachePolicy policy=new CachePolicy(context,store,cache);long below=95L*1024*CachePolicy.MIB,over=96L*1024*CachePolicy.MIB;
        // Simulate already received bytes without allocating a huge file on the physical card.
        policy.requireSpace(Collections.<String>emptySet(),below,below);
        try{policy.requireSpace(Collections.<String>emptySet(),over,over);fail("Large byte count bypassed quota");}catch(java.io.IOException expected){}
        assertTrue("Quota check deleted saved music",cache.contains("saved"));
    }
    public void testProgressFailureAndCompletedHistoryPersist()throws Exception{saved("job",1,4096);store.enqueueDownloads(Arrays.asList("job"));store.downloadProgress("job",123,1000);store.failedDownload("job","Fixture failure");assertEquals("Fixture failure",store.downloads().get(0).error);assertEquals(123L,store.downloads().get(0).received);store.retryDownload("job");assertEquals("job",store.nextDownload());store.completedDownload("job");assertEquals(0,store.pendingDownloads());assertEquals(2,store.downloads().get(0).state);store.enqueueDownloads(Arrays.asList("job"));assertEquals(1,store.pendingDownloads());}
    public void testFollowingOwnershipProtectsUntilLastPlaylistReleasesTrack()throws Exception{
        Song shared=saved("shared-followed",180,4096);Song pinned=saved("manual-pin",180,4096);store.pin(Arrays.asList(pinned.id),true);
        for(String id:Arrays.asList("one","two")){store.saveDetail(new io.onloopio.model.PlaylistDetail(new io.onloopio.model.Playlist(id,id,"r",1,60),Arrays.asList(shared)));store.followPlaylist(id,true);}
        CachePolicy policy=new CachePolicy(context,store,cache);assertEquals(0,policy.clean(Collections.<String>emptySet(),0,System.currentTimeMillis(),true).removed);
        store.followPlaylist("one",false);assertEquals(0,policy.clean(Collections.<String>emptySet(),0,System.currentTimeMillis(),true).removed);
        store.followPlaylist("two",false);assertEquals(1,policy.clean(Collections.<String>emptySet(),0,System.currentTimeMillis(),true).removed);assertTrue(cache.contains(pinned));
    }
}
