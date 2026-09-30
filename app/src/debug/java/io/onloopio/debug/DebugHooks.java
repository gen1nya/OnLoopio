package io.onloopio.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import io.onloopio.db.MetadataStore;
import io.onloopio.model.Song;
import io.onloopio.player.PlaybackService;
import io.onloopio.ui.PlaylistActivity;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Debug-build adb hooks, absent from release. Examples:
 *   am broadcast -n io.onloopio/.debug.DebugHooks -a io.onloopio.debug.PLAY_LOCAL [--es query "Дыши"]   queue all local music, start at the first match
 *   am broadcast -n io.onloopio/.debug.DebugHooks -a io.onloopio.debug.SERVICE --es action io.onloopio.STOP   any PlaybackService action
 *   am broadcast -n io.onloopio/.debug.DebugHooks -a io.onloopio.debug.PLAYER | LIBRARY | SCAN
 *   am broadcast -n io.onloopio/.debug.DebugHooks -a io.onloopio.debug.FLAG --es key spectrum --ez value false   flip a device preference
 */
public final class DebugHooks extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        String action=intent.getAction();Log.i("OnLoopio","DEBUG_HOOK "+action+" "+intent.getExtras());
        if("io.onloopio.debug.PLAY_LOCAL".equals(action)){
            MetadataStore store=new MetadataStore(context);List<Song> songs;try{songs=store.localSongs();}finally{store.close();}
            if(songs.isEmpty()){Log.w("OnLoopio","DEBUG_HOOK no local music");return;}
            Collections.sort(songs,new Comparator<Song>(){public int compare(Song a,Song b){int c=a.album.compareTo(b.album);if(c==0)c=a.disc-b.disc;if(c==0)c=a.track-b.track;return c;}});
            String query=intent.getStringExtra("query");int start=0;
            if(query!=null)for(int n=0;n<songs.size();n++){Song s=songs.get(n);if((s.title+" "+s.album+" "+s.artist).toLowerCase().contains(query.toLowerCase())){start=n;break;}}
            PlaybackService.play(context,songs,start,false);open(context,"io.onloopio.OPEN_PLAYER");
        }
        else if("io.onloopio.debug.SERVICE".equals(action)){String name=intent.getStringExtra("action");if(name!=null)PlaybackService.action(context,name);}
        else if("io.onloopio.debug.PLAYER".equals(action))open(context,"io.onloopio.OPEN_PLAYER");
        else if("io.onloopio.debug.LIBRARY".equals(action))open(context,"io.onloopio.OPEN_LIBRARY");
        else if("io.onloopio.debug.FLAG".equals(action)){new io.onloopio.device.DeviceSettings(context).setFlag(intent.getStringExtra("key"),intent.getBooleanExtra("value",true));PlaybackService.action(context,PlaybackService.SETTINGS);}
        else if("io.onloopio.debug.SCAN".equals(action))io.onloopio.library.MusicLibraryService.request(context,true);
    }
    private static void open(Context context,String screen){context.startActivity(new Intent(context,PlaylistActivity.class).setAction(screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
}
