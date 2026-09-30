package io.onloopio.ui;

import android.os.Bundle;
import android.os.Handler;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.player.AudioCache;
import io.onloopio.player.CachePolicy;
import io.onloopio.player.PlaybackService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CacheSettingsActivity extends WheelActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler();private MetadataStore store;private boolean gone;
    private String summary="Calculating cache…";private CachePolicy.Result preview;
    private static final int[] LIMITS={0,512,1024,2048,4096,8192,16384,32768,65536,98304},DAYS={0,30,60,90,180,365},RESERVES={128,256,512,1024,2048,4096};
    public void onCreate(Bundle state){store=new MetadataStore(this);super.onCreate(state);}
    protected void onResume(){super.onResume();refresh();main.postDelayed(update,1000);}
    protected void onPause(){main.removeCallbacks(update);super.onPause();}
    protected void onDestroy(){gone=true;worker.shutdownNow();store.close();super.onDestroy();}
    private final Runnable update=new Runnable(){String previous="";public void run(){if(!previous.equals(PlaybackService.cleanupMessage)){previous=PlaybackService.cleanupMessage;refresh();}main.postDelayed(this,1000);}};
    private void refresh(){worker.submit(new Runnable(){public void run(){String info;CachePolicy.Result result=null;try{ServerConfig config=new ConfigStore(CacheSettingsActivity.this).load();if(config==null)throw new IllegalStateException();AudioCache cache=new AudioCache(CacheSettingsActivity.this,config);result=new CachePolicy(CacheSettingsActivity.this,store,cache).preview(PlaybackService.protectedTracks,System.currentTimeMillis());info=DownloadsActivity.size(cache.completedBytes())+" saved · "+DownloadsActivity.size(cache.freeBytes())+" free\n"+store.pinnedCount()+" protected · "+PlaybackService.cleanupMessage;}catch(Exception unavailable){info="Cache unavailable";}final String text=info;final CachePolicy.Result plan=result;main.post(new Runnable(){public void run(){if(gone)return;summary=text;preview=plan;if(stack.size()==1){remember();render();}}});}});}
    private void changed(){PlaybackService.action(this,PlaybackService.SETTINGS);refresh();}
    private Item choice(final String label,final String key,final int[] values,final int current,final boolean days){
        String value=current==0?days?"Never":"Unlimited":days?current+" days":current<1024?current+" MiB":current/1024+" GiB";
        return new Item(label+": "+value,new Runnable(){public void run(){String[] labels=new String[values.length];int selected=0;for(int n=0;n<values.length;n++){int v=values[n];labels[n]=v==0?days?"Never":"Unlimited":days?v+" days":v<1024?v+" MiB":v/1024+" GiB";if(v==current)selected=n;}choose(label,labels,selected,new Choice(){public void apply(int n){prefs.setNumber(key,values[n]);changed();}});}});
    }
    Menu rootMenu(){return new Menu("Cache policy"){List<Item> items(){note=summary;List<Item> rows=new ArrayList<Item>();
        rows.add(new Item("Rotation profiles",new Runnable(){public void run(){profiles();}}));
        rows.add(new Item("Automatic cleanup: "+(prefs.flag("cache_auto_clean",false)?"On":"Off"),new Runnable(){public void run(){choose("Automatic cleanup",new String[]{"Off","On"},prefs.flag("cache_auto_clean",false)?1:0,new Choice(){public void apply(int n){prefs.setFlag("cache_auto_clean",n==1);changed();}});}}));
        rows.add(choice("Audio cache limit","cache_limit_mb",LIMITS,prefs.number("cache_limit_mb",CachePolicy.DEFAULT_LIMIT_MB),false));
        rows.add(choice("Not listened for","cache_max_idle_days",DAYS,prefs.number("cache_max_idle_days",90),true));
        rows.add(choice("Keep SD space free","cache_reserve_mb",RESERVES,prefs.number("cache_reserve_mb",CachePolicy.DEFAULT_RESERVE_MB),false));
        rows.add(new Item("Clean now",new Runnable(){public void run(){if(preview==null){refresh();return;}confirm("Remove "+preview.removed+" tracks / "+DownloadsActivity.size(preview.freed)+"? Protected music stays.",new Runnable(){public void run(){PlaybackService.cleanupMessage="Cleaning…";PlaybackService.action(CacheSettingsActivity.this,PlaybackService.CLEAN);}});}}));
        rows.add(new Item("Back",new Runnable(){public void run(){finish();}}));return rows;
    }};}
    private void profiles(){show(new Menu("Rotation profiles"){List<Item> items(){List<Item> rows=new ArrayList<Item>();String[] names={"Keep offline collection","Daily rotation: 96 GiB / 90 days","Small card: 2 GiB / 30 days"};for(int n=0;n<names.length;n++){final int profile=n;rows.add(new Item(names[n],new Runnable(){public void run(){confirm(profile==0?"Keep saved music; stop at SD reserve?":"Enable automatic removal of old, unprotected music?",new Runnable(){public void run(){prefs.setNumber("cache_limit_mb",profile==0?0:profile==1?CachePolicy.DEFAULT_LIMIT_MB:2048);prefs.setNumber("cache_max_idle_days",profile==0?0:profile==1?90:30);prefs.setNumber("cache_reserve_mb",profile==2?256:CachePolicy.DEFAULT_RESERVE_MB);prefs.setFlag("cache_auto_clean",profile!=0);changed();}});}}));}rows.add(new Item("Back",new Runnable(){public void run(){onBackPressed();}}));return rows;}});}
}
