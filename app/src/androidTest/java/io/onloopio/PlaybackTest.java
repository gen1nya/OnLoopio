package io.onloopio;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.test.InstrumentationTestCase;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.model.Playlist;
import io.onloopio.model.PlaylistDetail;
import io.onloopio.model.Song;
import io.onloopio.player.AudioCache;
import io.onloopio.player.PlaybackService;
import java.util.Collections;

/** Real original-audio download, local playback with Wi-Fi off, pause and seek. */
public final class PlaybackTest extends InstrumentationTestCase {
    public void testSettingsCannotStopANewerPlaybackRequest() throws Exception {
        Context context=getInstrumentation().getTargetContext();ServerConfig config=new ConfigStore(context).load();assertNotNull(config);MetadataStore store=new MetadataStore(context);
        try {AudioCache cache=new AudioCache(context,config);java.util.List<Song> saved=store.offlineSongs(cache.completedNames());assertFalse("A completed offline fixture is required",saved.isEmpty());Song song=saved.get(0);
            PlaybackService.action(context,PlaybackService.STOP);Thread.sleep(700);PlaybackService.action(context,PlaybackService.SETTINGS);PlaybackService.play(context,Collections.singletonList(song),0,false);
            for(int n=0;n<150 && !(PlaybackService.state.playing && PlaybackService.state.song!=null && song.id.equals(PlaybackService.state.song.id));n++)Thread.sleep(100);
            assertTrue("Settings stopped a newer playback request: "+PlaybackService.state.message,PlaybackService.state.playing);Thread.sleep(500);assertTrue(PlaybackService.state.playing);
        }finally{PlaybackService.action(context,PlaybackService.STOP);store.close();}
    }
    public void testCompletedSdAudioPlaysWithoutNetwork() throws Exception {
        final Context context=getInstrumentation().getTargetContext();
        final ServerConfig config=new ConfigStore(context).load(); assertNotNull("Provisioned server required",config);
        final WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE); assertTrue("Wi-Fi required to prepare test",wifi.isWifiEnabled());
        MetadataStore store=new MetadataStore(context);
        int oldVolume=((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        try {
            PlaybackService.action(context,PlaybackService.STOP); Thread.sleep(500);
            NavidromeClient client=new NavidromeClient(config); java.util.List<Playlist> playlists=client.getPlaylists(); assertFalse(playlists.isEmpty());
            store.selectAccount(config.accountKey()); store.replacePlaylists(playlists);
            for(Playlist playlist:playlists) { PlaylistDetail detail=client.getPlaylist(playlist.id); store.saveDetail(detail); }
            java.util.List<Song> songs=store.songs(); assertFalse(songs.isEmpty());
            Song selected=null;for(Song candidate:songs)if(candidate.duration>30 && (selected==null || candidate.duration<selected.duration) && (candidate.suffix.equals("mp3")||candidate.suffix.equals("flac")))selected=candidate;
            assertNotNull("No suitable offline audio fixture",selected);
            AudioCache cache=new AudioCache(context,config); java.io.File saved=cache.obtain(selected,client,null); assertTrue(saved.length()>16);
            assertFalse(new java.io.File(saved.getAbsolutePath()+".part").exists());
            android.util.Log.i("OnLoopioTest","LIBRARY tracks="+songs.size()+" AUDIO bytes="+saved.length());
            assertTrue(wifi.setWifiEnabled(false)); for(int n=0;n<100 && wifi.getWifiState()!=WifiManager.WIFI_STATE_DISABLED;n++) Thread.sleep(100);
            assertEquals(WifiManager.WIFI_STATE_DISABLED,wifi.getWifiState());
            ((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(android.media.AudioManager.STREAM_MUSIC,1,0);
            PlaybackService.play(context,Collections.singletonList(selected),0,false);
            for(int n=0;n<100 && !PlaybackService.state.playing;n++) Thread.sleep(100);
            assertTrue("Cached audio did not play with Wi-Fi off: "+PlaybackService.state.message,PlaybackService.state.playing);
            for(int n=0;n<50 && PlaybackService.state.position<500;n++)Thread.sleep(100);assertTrue("Position did not advance: "+PlaybackService.state.message+" position="+PlaybackService.state.position,PlaybackService.state.position>=500);
            context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.SEEK).putExtra("seconds",15));for(int n=0;n<50 && PlaybackService.state.position<14000;n++)Thread.sleep(100);
            assertTrue("Seek did not move playback: "+PlaybackService.state.message+" position="+PlaybackService.state.position,PlaybackService.state.position>=14000);
            PlaybackService.action(context,PlaybackService.PAUSE); Thread.sleep(700); assertFalse(PlaybackService.state.playing);
        } finally {PlaybackService.action(context,PlaybackService.STOP);wifi.setWifiEnabled(true);for(int n=0;n<150 && (!wifi.isWifiEnabled() || !io.onloopio.device.Connectivity.wifiConnected(context));n++)Thread.sleep(100);((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(android.media.AudioManager.STREAM_MUSIC,oldVolume,0);store.close();}
    }
}
