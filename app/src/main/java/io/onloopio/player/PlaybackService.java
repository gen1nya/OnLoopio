package io.onloopio.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.media.audiofx.Equalizer;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import io.onloopio.R;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;
import io.onloopio.db.MetadataStore;
import io.onloopio.device.DeviceSettings;
import io.onloopio.device.OnlineMode;
import io.onloopio.model.Song;
import io.onloopio.ui.PlaylistActivity;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.MediaPlayer;

/** Plays completed offline audio or streams directly; download jobs are independent and persistent. */
public final class PlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String CLEAN="io.onloopio.CLEAN_CACHE",REMOVE_DOWNLOAD="io.onloopio.REMOVE_DOWNLOAD_JOB",RESUME_PENDING="io.onloopio.RESUME_PENDING_DOWNLOADS";
    public static final String PLAY="io.onloopio.PLAY", TOGGLE="io.onloopio.TOGGLE", NEXT="io.onloopio.NEXT",
        PREVIOUS="io.onloopio.PREVIOUS", SEEK="io.onloopio.SEEK", SYNC="io.onloopio.SYNC_AUDIO",
        STOP="io.onloopio.STOP", SETTINGS="io.onloopio.APPLY_DEVICE_SETTINGS", CANCEL="io.onloopio.CANCEL_DOWNLOAD",
        PAUSE="io.onloopio.PAUSE", RESUME="io.onloopio.RESUME", CLEAR="io.onloopio.CLEAR_AUDIO",
        ENQUEUE="io.onloopio.ENQUEUE_DOWNLOAD", REMOVE="io.onloopio.REMOVE_AUDIO",
        APPEND="io.onloopio.APPEND_QUEUE", PLAY_NEXT="io.onloopio.PLAY_NEXT", RETRY="io.onloopio.RETRY_DOWNLOAD",CLEAR_QUEUE="io.onloopio.CLEAR_DOWNLOAD_QUEUE",KICK="io.onloopio.RESUME_QUEUED_DOWNLOADS";
    public static final class State {
        public final Song song; public final String message; public final boolean playing,busy;
        public final int position,duration,queuePosition,queueSize;public final Song next;
        State(Song song,String message,boolean playing,boolean busy,int position,int duration) {
            this(song,message,playing,busy,position,duration,null,0,0);
        }
        State(Song song,String message,boolean playing,boolean busy,int position,int duration,Song next,int qpos,int qsize) {
            this.song=song; this.message=message; this.playing=playing; this.busy=busy; this.position=position; this.duration=duration;this.next=next;queuePosition=qpos;queueSize=qsize;
        }
    }
    public static volatile State state=new State(null,"Choose a track to play.",false,false,0,0);
    public static final class DownloadState {
        public final Song song;public final long received,total,speed;public final String phase;
        DownloadState(Song song,long received,long total,long speed,String phase){this.song=song;this.received=received;this.total=total;this.speed=speed;this.phase=phase;}
    }
    public static volatile DownloadState downloads=new DownloadState(null,0,-1,0,"Idle");
    public static volatile String cleanupMessage="";public static volatile Set<String> protectedTracks=Collections.emptySet();
    private volatile Set<String> protectedPlayback=Collections.emptySet();private Song nextSong;private int nextIndex=-1;private boolean listeningRecorded,cleaning;private long lastCleanup;
    private final Handler main=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),downloadWorker=Executors.newSingleThreadExecutor();
    private MediaPlayer player; private Equalizer equalizer; private RemoteControlClient remote;
    private AudioManager audio; private MetadataStore store; private DeviceSettings settings;
    private OnlineMode online; private StreamProxy proxy; private boolean prepared,busy,resumeAfterFocus,transcode,destroyed;
    private int generation;private volatile int downloadGeneration;private Song song;private String message="Choose a track to play.";
    private final List<String> queue=new ArrayList<String>(); private int index; private ComponentName buttons;
    private final BroadcastReceiver noisy=new BroadcastReceiver() { public void onReceive(Context c,Intent i) { pause(); } };
    public static void action(Context context,String action) { context.startService(new Intent(context,PlaybackService.class).setAction(action)); }
    public static void play(Context context,List<Song> tracks,int selected,boolean sync) {
        ArrayList<String> ids=new ArrayList<String>(); for(Song track:tracks) ids.add(track.id);
        context.startService(new Intent(context,PlaybackService.class).setAction(sync?ENQUEUE:PLAY).putStringArrayListExtra("queue",ids).putExtra("index",selected));
    }
    public static void entityAction(Context c,String action,List<Song> tracks) {
        ArrayList<String> ids=new ArrayList<String>(); for(Song song:tracks) ids.add(song.id);
        c.startService(new Intent(c,PlaybackService.class).setAction(action).putStringArrayListExtra("queue",ids));
    }
    public void onCreate() {
        super.onCreate(); store=new MetadataStore(this); settings=new DeviceSettings(this); online=new OnlineMode(this);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE); player=new MediaPlayer();
        player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK); player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer media) { prepared=true; applyEffects(); startLocal(); } });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer media) {
            if(settings.number("repeat",0)==1) { player.seekTo(0); startLocal(); } else advance(1,false);
        }});
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer media,int what,int extra) {
            Log.e("OnLoopio","DECODER_ERROR id="+(song==null?"":song.id)+" suffix="+(song==null?"":song.suffix)+" what="+what+" extra="+extra);
            prepared=false;
            if(!transcode && song!=null && !song.local() && online.homeWifi() && !settings.flag("force_offline",false)) {transcode=true;openStream(song.id,true);}
            else { message=song!=null && song.local()?"Cannot decode local "+song.suffix+". Convert to MP3 or FLAC.":"Cannot decode this track ("+what+"/"+extra+")."; publish(); }
            return true;
        }});
        registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        buttons=new ComponentName(this,MediaButtons.class); audio.registerMediaButtonEventReceiver(buttons);
        PendingIntent receiver=PendingIntent.getBroadcast(this,0,new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(buttons),0);
        remote=new RemoteControlClient(receiver); audio.registerRemoteControlClient(remote);
        remote.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE|RemoteControlClient.FLAG_KEY_MEDIA_NEXT|RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);
        lastCleanup=android.os.SystemClock.elapsedRealtime();main.post(tick); resumeDownloads();
        main.postDelayed(new Runnable(){public void run(){if(settings.flag("cache_auto_clean",false))cleanCache(false);}},1500);
    }
    public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) { resumeDownloads(); return START_NOT_STICKY; }
        String action=intent.getAction();
        Log.i("OnLoopio","SERVICE_ACTION "+action+" startId="+startId);
        if(STOP.equals(action)) { stopSelf(startId); return START_NOT_STICKY; }
        if(CLEAN.equals(action)){cleanCache(true);return START_NOT_STICKY;}
        if(REMOVE_DOWNLOAD.equals(action)){String id=intent.getStringExtra("id");if(id!=null){store.removeDownload(id);if(downloads.song!=null && id.equals(downloads.song.id)){downloadGeneration++;busy=false;downloads=new DownloadState(null,0,-1,0,"Cancelled");resumeDownloads();}}return START_NOT_STICKY;}
        if(CLEAR.equals(action)) { clearAudio(); return START_NOT_STICKY; }
        if(CANCEL.equals(action)) {settings.setFlag("downloads_paused",true);downloadGeneration++; busy=false;downloads=new DownloadState(null,0,-1,0,"Paused");if(song==null) message="Download paused; queued tracks remain."; publish(); return START_NOT_STICKY; }
        if(CLEAR_QUEUE.equals(action)) {downloadGeneration++;busy=false;store.clearDownloads();downloads=new DownloadState(null,0,-1,0,"Idle");if(song==null)message="Download queue cleared.";publish();return START_NOT_STICKY;}
        if(SETTINGS.equals(action)) {
            if(settings.flag("force_offline",false)) {downloadGeneration++;busy=false;downloads=new DownloadState(null,0,-1,0,"Forced offline");if(proxy!=null && !queue.isEmpty()) open(index);}
            else resumeDownloads();
            planNext();applyEffects();if(settings.flag("cache_auto_clean",false))cleanCache(false);if(song==null && !busy && !cleaning && store.pendingDownloads()==0)stopSelf(startId);else publish();return START_NOT_STICKY;
        }
        if(RETRY.equals(action)) {settings.setFlag("downloads_paused",false);store.retryDownloads();resumeDownloads();}
        if(RESUME_PENDING.equals(action)) {settings.setFlag("downloads_paused",false);resumeDownloads();}
        if(KICK.equals(action)) {if(!settings.flag("downloads_paused",false))resumeDownloads();}
        if(PLAY.equals(action) || SYNC.equals(action) || ENQUEUE.equals(action) || APPEND.equals(action) || PLAY_NEXT.equals(action) || REMOVE.equals(action)) {
            ArrayList<String> ids=intent.getStringArrayListExtra("queue");
            if(ids==null || ids.isEmpty() || ids.size()>10000) { message="No tracks selected."; publish(); return START_NOT_STICKY; }
            if(SYNC.equals(action)||ENQUEUE.equals(action)) {settings.setFlag("downloads_paused",false);store.enqueueDownloads(ids);message="Added "+ids.size()+" tracks to download queue.";resumeDownloads();}
            else if(REMOVE.equals(action)) removeAudio(ids);
            else if(APPEND.equals(action)) { queue.addAll(ids); message="Added to playback queue."; }
            else if(PLAY_NEXT.equals(action)) {nextIndex=-1; queue.addAll(Math.min(queue.size(),index+1),ids); message="Will play next."; }
            else { nextIndex=-1;queue.clear(); queue.addAll(ids); index=Math.max(0,Math.min(intent.getIntExtra("index",0),queue.size()-1)); open(index); }
        } else if(TOGGLE.equals(action)) {
            resumeAfterFocus=false; if(prepared) { if(player.isPlaying()) pause(); else startLocal(); } else if(!queue.isEmpty()) open(index);
        } else if(NEXT.equals(action)) advance(1,true);
        else if(PAUSE.equals(action)) {resumeAfterFocus=false; pause();}
        else if(RESUME.equals(action)) {resumeAfterFocus=false; startLocal();}
        else if(PREVIOUS.equals(action)) { if(prepared && player.getCurrentPosition()>3000) player.seekTo(0); else advance(-1,true); }
        else if(SEEK.equals(action) && prepared) player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+intent.getIntExtra("seconds",0)*1000)));
        planNext();publish(); return START_NOT_STICKY;
    }
    private void closeStream() { if(proxy!=null) { proxy.close(); proxy=null; } }
    private void open(int position) {
        generation++;listeningRecorded=false; index=position; song=store.song(queue.get(index)); prepared=false; transcode=false; player.reset(); closeStream();
        if(song==null) { message="Track metadata is not available."; publish(); return; }
        planNext();if(song.local()){File file=new File(song.localPath);try{String base=io.onloopio.library.MusicPaths.root().getCanonicalPath()+File.separator;if(!file.getCanonicalPath().startsWith(base) || !file.isFile())throw new IOException("Missing local file");prepare(file.getPath(),true);}catch(Exception missing){message="Local file unavailable. Scan Music after USB storage returns.";publish();}return;}
        ServerConfig config=new ConfigStore(this).load(); if(config==null) { message="Configure Navidrome from your computer."; publish(); return; }
        try {
            AudioCache cache=new AudioCache(this,config);
            if(cache.contains(song)) { prepare(cache.file(song.id).getAbsolutePath(),true); return; }
            if(settings.flag("force_offline",false) || !online.homeWifi()) { message="Track is not downloaded."; publish(); return; }
            final int request=generation; final String id=song.id; final ServerConfig server=config;
            message="Connecting to Navidrome…"; publish();
            worker.submit(new Runnable(){public void run(){ final boolean ok=online.check(server); main.post(new Runnable(){public void run(){
                if(request!=generation) return; if(ok) {transcode=!("mp3".equalsIgnoreCase(song.suffix)||"flac".equalsIgnoreCase(song.suffix));openStream(id,transcode);} else {message="Offline: Navidrome is unavailable."; publish();}
            }}); }});
        } catch(IOException failure) { message="SD card unavailable."; publish(); }
    }
    private void openStream(String id,boolean fallback) {
        ServerConfig config=new ConfigStore(this).load(); if(config==null) return;
        try {
            player.reset(); prepared=false; closeStream(); proxy=new StreamProxy(config);
            proxy.setTranscode(fallback); prepare(proxy.url(id),false);
        } catch(IOException failure) {message="Cannot start audio stream.";publish();}
    }
    private void prepare(String source,boolean local) {
        try {
            message=local?"Opening offline audio…":"Buffering stream…"; publish();
            player.setDataSource(source); player.prepareAsync();
            remote.editMetadata(true).putString(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE,song.title)
                .putString(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST,song.artist)
                .putString(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM,song.album).apply();
        } catch(Exception failure) { Log.e("OnLoopio","PREPARE_FAILED suffix="+song.suffix,failure); message="Cannot open audio."; publish(); }
    }
    private void startLocal() {
        if(!prepared) return;
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {message="Audio output is in use.";publish();return;}
        try {player.start(); message=proxy==null?"Playing offline":"Streaming from Navidrome"; remote.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING); publish();}
        catch(RuntimeException failure) {message="Audio output failed.";publish();}
    }
    private void pause() { if(prepared && player.isPlaying()) player.pause(); message="Paused"; remote.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);publish(); }
    private void advance(int direction,boolean user) {
        if(queue.isEmpty()) return; int next=index+direction;
        if(direction>0 && nextIndex>=0 && !(user && settings.number("repeat",0)==1)) next=nextIndex;
        if(next<0 || next>=queue.size()) {
            if(settings.number("repeat",0)==2 || user) next=(next+queue.size())%queue.size();
            else {message="End of queue";remote.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);audio.abandonAudioFocus(this);publish();return;}
        }
        open(next);
    }
    private void planNext(){
        if(queue.isEmpty()){nextIndex=-1;nextSong=null;protectedPlayback=Collections.emptySet();protectedTracks=protectedPlayback;return;}
        if(nextIndex<0 || nextIndex>=queue.size() || nextIndex==index || !settings.flag("shuffle",false)){
            if(settings.number("repeat",0)==1)nextIndex=index;
            else if(settings.flag("shuffle",false) && queue.size()>1)nextIndex=(index+1+new Random().nextInt(queue.size()-1))%queue.size();
            else nextIndex=index+1<queue.size()?index+1:settings.number("repeat",0)==2?0:-1;
        }
        nextSong=nextIndex>=0?store.song(queue.get(nextIndex)):null;Set<String> keep=new HashSet<String>();if(song!=null)keep.add(song.id);if(nextSong!=null)keep.add(nextSong.id);if(index+2<queue.size())keep.add(queue.get(index+2));protectedPlayback=Collections.unmodifiableSet(keep);protectedTracks=protectedPlayback;
    }
    private void cleanCache(final boolean manual){if(cleaning)return;final ServerConfig config=new ConfigStore(this).load();if(config==null)return;cleaning=true;lastCleanup=android.os.SystemClock.elapsedRealtime();
        downloadWorker.submit(new Runnable(){public void run(){try{CachePolicy.Result r=new CachePolicy(PlaybackService.this,store,new AudioCache(PlaybackService.this,config)).clean(protectedPlayback,0,System.currentTimeMillis(),manual);cleanupMessage="Removed "+r.removed+" tracks · "+r.freed/(1024*1024)+" MiB"+(r.blocked?" · protected tracks exceed limit":"");}catch(Exception failed){cleanupMessage="Cannot clean cache";}finally{main.post(new Runnable(){public void run(){cleaning=false;}});}}});
    }
    private void resumeDownloads() {
        if(!android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState()))return;
        if(busy || settings.flag("downloads_paused",false) || store.pendingDownloads()==0) return;
        final ServerConfig config=new ConfigStore(this).load();
        if(config==null || settings.flag("force_offline",false) || !online.homeWifi()) return;
        busy=true; final int request=++downloadGeneration;downloads=new DownloadState(null,0,-1,0,"Connecting"); publish();
        downloadWorker.submit(new Runnable(){public void run(){
            PowerManager.WakeLock wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OnLoopio:download"); wake.acquire(3600000);
            int completed=0,failed=0;
            try {
                if(!online.check(config)) return;
                AudioCache cache=new AudioCache(PlaybackService.this,config); NavidromeClient client=new NavidromeClient(config);
                final CachePolicy policy=new CachePolicy(PlaybackService.this,store,cache);
                String id; while(request==downloadGeneration && (id=store.nextDownload())!=null) {
                    if(!online.homeWifi() || settings.flag("force_offline",false)) break;
                    try {
                        final int active=request;final String trackId=id;final Song entry=store.song(id);if(entry==null){store.completedDownload(id);continue;}
                        downloads=new DownloadState(entry,0,-1,0,"Downloading");store.downloadProgress(id,0,-1);
                        final long started=android.os.SystemClock.elapsedRealtime();
                        File saved=cache.obtain(entry,client,new NavidromeClient.DownloadProgress(){long lastPersist,lastSpaceCheck;public void bytes(long count,long total)throws IOException{
                            if(active!=downloadGeneration || !online.homeWifi() || settings.flag("force_offline",false))throw new IOException("Cancelled");
                            if(lastSpaceCheck==0 || count-lastSpaceCheck>=1024*1024){lastSpaceCheck=count;policy.requireSpace(protectedPlayback,total>0?total:count+1024*1024,count);}
                            long now=android.os.SystemClock.elapsedRealtime();downloads=new DownloadState(entry,count,total,count*1000/Math.max(1,now-started),"Downloading");
                            if(now-lastPersist>1000){store.downloadProgress(trackId,count,total);lastPersist=now;}
                        }});
                        if(request!=downloadGeneration)break;
                        store.downloadProgress(id,saved.length(),saved.length());store.downloaded(id,System.currentTimeMillis());store.completedDownload(id);completed++;
                        downloads=new DownloadState(null,0,-1,0,"Loading cover");
                        try{android.graphics.Bitmap cover=new CoverCache(PlaybackService.this,config).load(entry,client,true);if(cover!=null)cover.recycle();}catch(Exception optionalCover){ }
                    } catch(Exception error) {
                        if(request!=downloadGeneration || !online.homeWifi() || settings.flag("force_offline",false))break;
                        Log.w("OnLoopio","DOWNLOAD_FAILED id="+id+" reason="+error.getClass().getSimpleName());
                        store.failedDownload(id,error.getMessage()!=null && error.getMessage().startsWith("Cache limit")?"Cache limit / protected tracks / SD reserve":"Network, server or audio error");failed++;
                    }
                }
            } catch(Exception error) {Log.w("OnLoopio","Download queue interrupted: "+error.getClass().getSimpleName());}
            finally {if(wake.isHeld()) wake.release(); final int done=completed,errors=failed; main.post(new Runnable(){public void run(){ if(request==downloadGeneration) {busy=false;downloads=new DownloadState(null,0,-1,0,settings.flag("force_offline",false)||!online.homeWifi()?"Waiting for home Wi-Fi":errors>0?"Some downloads failed":store.pendingDownloads()>0?"Waiting for Navidrome":"Idle");if(song==null)message="Downloads: "+done+" saved, "+errors+" failed, "+store.pendingDownloads()+" queued";publish();}}});}
        }});
    }
    private void removeAudio(final List<String> ids) {
        final String playing=song==null?null:song.id; final ServerConfig config=new ConfigStore(this).load(); if(config==null)return;
        if(playing!=null && ids.contains(playing)) {player.reset();prepared=false;closeStream();song=null;queue.clear();audio.abandonAudioFocus(this);}
        downloadGeneration++; busy=false;
        downloadWorker.submit(new Runnable(){public void run(){
            try {AudioCache cache=new AudioCache(PlaybackService.this,config); int removed=0;
                for(String id:ids) {if(id.startsWith("local:"))continue;store.removeDownload(id); if(cache.remove(id)) removed++;}
                final int count=removed; main.post(new Runnable(){public void run(){message="Removed "+count+" offline tracks.";publish();}});
            } catch(IOException error) {main.post(new Runnable(){public void run(){message="Cannot remove offline audio.";publish();}});}
        }});
    }
    private void clearAudio() {
        downloadGeneration++; busy=false; store.stopFollowingPlaylists();store.clearDownloads(); player.reset(); prepared=false; closeStream(); song=null; queue.clear();
        final ServerConfig config=new ConfigStore(this).load(); if(config==null)return;
        downloadWorker.submit(new Runnable(){public void run(){try {new AudioCache(PlaybackService.this,config).clear(); main.post(new Runnable(){public void run(){message="Downloaded audio cleared.";publish();}});}catch(Exception failure){main.post(new Runnable(){public void run(){message="Cannot clear SD audio.";publish();}});}}});
    }
    private void applyEffects() {
        if(equalizer!=null) {equalizer.release();equalizer=null;} int preset=settings.number("eq_preset",-1); if(!prepared || preset==-1) return;
        try {equalizer=new Equalizer(0,player.getAudioSessionId());
            if(preset==-2) {short[] range=equalizer.getBandLevelRange();for(short n=0;n<equalizer.getNumberOfBands();n++) equalizer.setBandLevel(n,(short)Math.max(range[0],Math.min(range[1],settings.number("eq_band_"+n,0)*100)));}
            else if(preset>=0 && preset<equalizer.getNumberOfPresets()) equalizer.usePreset((short)preset); equalizer.setEnabled(true);
        } catch(RuntimeException failure) {if(equalizer!=null)equalizer.release();equalizer=null;}
    }
    private void publish() {
        if(destroyed)return;
        boolean playing=prepared && player.isPlaying(); int position=prepared?player.getCurrentPosition():0, duration=prepared?player.getDuration():0;
        state=new State(song,message,playing,busy,position,duration,nextSong,queue.isEmpty()?0:index+1,queue.size());
        PendingIntent home=PendingIntent.getActivity(this,0,new Intent(this,PlaylistActivity.class).setAction("io.onloopio.OPEN_PLAYER"),PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this).setSmallIcon(R.drawable.icon).setContentTitle(song==null?"OnLoopio":song.title).setContentText(message).setContentIntent(home).setOngoing(playing||busy).build();
        if(song!=null || busy) startForeground(7,notification); else stopForeground(true);
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(settings.flag("cache_auto_clean",false) && android.os.SystemClock.elapsedRealtime()-lastCleanup>21600000)cleanCache(false);
        if(proxy!=null && (settings.flag("force_offline",false) || !online.homeWifi())) {player.reset();prepared=false;closeStream();message="Offline: stream stopped.";publish();}
        if(prepared){int position=player.getCurrentPosition(),duration=player.getDuration();if(player.isPlaying() && !listeningRecorded && position>=Math.min(30000,Math.max(1000,duration/2))){listeningRecorded=true;if(song!=null)store.listened(song.id,System.currentTimeMillis());}state=new State(song,message,player.isPlaying(),busy,position,duration,nextSong,queue.isEmpty()?0:index+1,queue.size());}main.postDelayed(this,500);
    }};
    public void onAudioFocusChange(int change) {
        if(change==AudioManager.AUDIOFOCUS_LOSS){resumeAfterFocus=false;pause();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeAfterFocus=prepared && player.isPlaying();pause();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(prepared)player.setVolume(.2f,.2f);}
        else if(change==AudioManager.AUDIOFOCUS_GAIN){if(prepared)player.setVolume(1,1);if(resumeAfterFocus){resumeAfterFocus=false;startLocal();}}
    }
    public void onDestroy(){destroyed=true;Log.i("OnLoopio","SERVICE_DESTROY");downloadGeneration++;generation++;main.removeCallbacksAndMessages(null);worker.shutdownNow();downloadWorker.shutdownNow();closeStream();if(equalizer!=null)equalizer.release();player.release();audio.abandonAudioFocus(this);audio.unregisterMediaButtonEventReceiver(buttons);audio.unregisterRemoteControlClient(remote);unregisterReceiver(noisy);store.close();stopForeground(true);state=new State(null,"Choose a track to play.",false,false,0,0);downloads=new DownloadState(null,0,-1,0,"Idle");protectedTracks=Collections.emptySet();super.onDestroy();}
    public IBinder onBind(Intent intent){return null;}
}
