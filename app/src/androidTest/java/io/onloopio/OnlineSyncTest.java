package io.onloopio;
import android.content.Context;
import android.media.AudioManager;
import android.test.InstrumentationTestCase;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.device.DeviceSettings;
import io.onloopio.device.OnlineMode;
import io.onloopio.model.Library;
import io.onloopio.model.Song;
import io.onloopio.player.AudioCache;
import io.onloopio.player.PlaybackService;
import java.util.Collections;

/** Exercises full Navidrome catalog, home-network gating, and first-byte streaming on the actual Y1. */
public final class OnlineSyncTest extends InstrumentationTestCase {
    public void testCatalogAndProgressivePlayback() throws Exception {
        Context context=getInstrumentation().getTargetContext(); ServerConfig config=new ConfigStore(context).load(); assertNotNull(config);
        OnlineMode mode=new OnlineMode(context); assertTrue("Home Wi-Fi missing",mode.homeWifi()); assertTrue("Navidrome unreachable",mode.check(config));
        DeviceSettings settings=new DeviceSettings(context); boolean forced=settings.flag("force_offline",false);
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE); int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        MetadataStore store=new MetadataStore(context);
        try {
            settings.setFlag("force_offline",true); assertFalse("Force-offline ignored",mode.check(config)); assertFalse(mode.online());
            settings.setFlag("force_offline",false); assertTrue(mode.check(config));
            assertFalse("Unreachable server stayed online",mode.check(new ServerConfig("http://127.0.0.1:9","fixture","fixture")));assertFalse(mode.online());assertTrue(mode.check(config));
            Library catalog=new NavidromeClient(config).catalog(); assertTrue("Catalog still only playlists",catalog.songs.size()>1000);
            store.selectAccount(config.accountKey()); store.replaceCatalog(catalog);
            assertEquals("Catalog rows lost",catalog.songs.size(),store.songs().size());
            assertEquals("Artists lost",catalog.artists.size(),store.artists().size()); assertEquals("Albums lost",catalog.albums.size(),store.albums().size());
            AudioCache cache=new AudioCache(context,config); Song selected=null,opus=null;
            for(Song song:catalog.songs) {
                if("opus".equalsIgnoreCase(song.suffix) && opus==null) opus=song;
                if(selected==null && "mp3".equalsIgnoreCase(song.suffix) && song.duration>20 && song.duration<150 && !cache.contains(song)) selected=song;
            }
            assertNotNull("No uncached MP3",selected); assertNotNull("No Opus in live catalog",opus);
            assertFalse("Streaming fixture already cached",cache.contains(selected));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,1,0);
            PlaybackService.play(context,Collections.singletonList(selected),0,false);
            for(int n=0;n<200 && !(PlaybackService.state.playing && PlaybackService.state.song!=null && selected.id.equals(PlaybackService.state.song.id));n++) Thread.sleep(100);
            assertTrue("MP3 did not stream: "+PlaybackService.state.message,PlaybackService.state.playing);
            assertEquals(selected.id,PlaybackService.state.song.id);
            assertTrue("Stream unexpectedly downloaded",!cache.contains(selected));
            Thread.sleep(1300); assertTrue("Streaming position did not advance",PlaybackService.state.position>500);
            context.startService(new android.content.Intent(context,PlaybackService.class).setAction(PlaybackService.SEEK).putExtra("seconds",15));Thread.sleep(1100);
            assertTrue("Range seek failed during streaming: "+PlaybackService.state.message,PlaybackService.state.position>=14000);
            PlaybackService.action(context,PlaybackService.STOP); Thread.sleep(500);
            PlaybackService.play(context,Collections.singletonList(opus),0,false);
            for(int n=0;n<250 && !(PlaybackService.state.playing && PlaybackService.state.song!=null && opus.id.equals(PlaybackService.state.song.id));n++) Thread.sleep(100);
            assertTrue("Opus transcode did not stream: "+PlaybackService.state.message,PlaybackService.state.playing);
            assertEquals(opus.id,PlaybackService.state.song.id);
        } finally {PlaybackService.action(context,PlaybackService.STOP);audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);settings.setFlag("force_offline",forced);store.close();}
    }
    public void testOpusCanBeSavedAndPlayedOffline() throws Exception {
        Context context=getInstrumentation().getTargetContext();ServerConfig config=new ConfigStore(context).load();assertNotNull(config);
        MetadataStore store=new MetadataStore(context);Song opus=null;
        for(Song candidate:store.songs()) if("opus".equalsIgnoreCase(candidate.suffix)){opus=candidate;break;}
        if(opus==null) {Library catalog=new NavidromeClient(config).catalog();for(Song candidate:catalog.songs)if("opus".equalsIgnoreCase(candidate.suffix)){opus=candidate;break;}}
        assertNotNull(opus);
        AudioCache cache=new AudioCache(context,config);java.io.File file=cache.obtain(opus,new NavidromeClient(config),null);
        assertTrue("Transcoded audio missing",file.length()>1024);assertTrue("Opus not recognized as playable cache",cache.contains(opus));
        java.io.FileInputStream input=new java.io.FileInputStream(file);int first=input.read(),second=input.read();input.close();
        assertTrue("Offline Opus was not converted to MP3",first=='I' || (first==255 && (second&224)==224));
        android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)context.getSystemService(Context.WIFI_SERVICE); boolean enabled=wifi.isWifiEnabled();
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(500);assertTrue(wifi.setWifiEnabled(false));for(int n=0;n<100 && wifi.getWifiState()!=android.net.wifi.WifiManager.WIFI_STATE_DISABLED;n++)Thread.sleep(100);
            assertFalse("Home network gate remained online with Wi-Fi off",new OnlineMode(context).homeWifi());
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,1,0);PlaybackService.play(context,Collections.singletonList(opus),0,false);
            for(int n=0;n<120 && !PlaybackService.state.playing;n++)Thread.sleep(100);
            assertTrue("Converted Opus did not play offline: "+PlaybackService.state.message,PlaybackService.state.playing);
        } finally {PlaybackService.action(context,PlaybackService.STOP);wifi.setWifiEnabled(enabled);if(enabled){for(int n=0;n<150 && (!wifi.isWifiEnabled() || !io.onloopio.device.Connectivity.wifiConnected(context));n++)Thread.sleep(100);}audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);store.close();}
    }
    public void testM4aStreamsAndCanBeSavedOffline() throws Exception {
        Context context=getInstrumentation().getTargetContext();ServerConfig config=new ConfigStore(context).load();assertNotNull(config);MetadataStore store=new MetadataStore(context);
        Song selected=null;AudioCache cache=new AudioCache(context,config);
        for(Song candidate:store.catalogSongs())if("m4a".equalsIgnoreCase(candidate.suffix) && candidate.duration>20 && candidate.duration<180 && !cache.contains(candidate)){selected=candidate;break;}
        assertNotNull("No uncached M4A fixture",selected);DeviceSettings prefs=new DeviceSettings(context);boolean forced=prefs.flag("force_offline",false);
        try {prefs.setFlag("force_offline",false);PlaybackService.play(context,Collections.singletonList(selected),0,false);for(int n=0;n<250 && !(PlaybackService.state.playing && PlaybackService.state.song!=null && selected.id.equals(PlaybackService.state.song.id));n++)Thread.sleep(100);
            assertTrue("M4A did not stream: "+PlaybackService.state.message,PlaybackService.state.playing);assertEquals(selected.id,PlaybackService.state.song.id);assertFalse("M4A streaming wrote offline cache",cache.contains(selected));
            PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(600);cache.obtain(selected,new NavidromeClient(config),null);assertTrue(cache.contains(selected));
            prefs.setFlag("force_offline",true);PlaybackService.play(context,Collections.singletonList(selected),0,false);for(int n=0;n<150 && !PlaybackService.state.playing;n++)Thread.sleep(100);
            assertTrue("M4A conversion did not play forced offline: "+PlaybackService.state.message,PlaybackService.state.playing);
        }finally{PlaybackService.action(context,PlaybackService.STOP);prefs.setFlag("force_offline",forced);store.close();}
    }
}
