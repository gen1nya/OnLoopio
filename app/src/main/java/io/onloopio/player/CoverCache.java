package io.onloopio.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.model.CacheKey;
import io.onloopio.model.Song;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Small account-scoped SD covers. Offline reads never open a network connection. */
public final class CoverCache {
    private final Context context;private final File root;private static final Object IO=new Object();
    public CoverCache(Context c,ServerConfig config)throws IOException {
        context=c.getApplicationContext();File external=c.getExternalFilesDir(null);if(external==null)throw new IOException("SD unavailable");
        root=new File(new File(external,"covers"),config==null?"local":CacheKey.hash(config.accountKey()));
        if(!root.isDirectory() && !root.mkdirs())throw new IOException("Cover cache unavailable");
    }
    public File file(Song song){return new File(root,CacheKey.hash(song.coverArt)+".cover");}
    public Bitmap load(Song song,NavidromeClient client,boolean network)throws IOException {synchronized(IO){return loadLocked(song,client,network);}}
    private Bitmap loadLocked(Song song,NavidromeClient client,boolean network)throws IOException {
        if(song!=null && song.local())return localCover(song);
        if(song==null || song.coverArt.length()==0)return null;
        File file=file(song);Bitmap result=decode(file);
        if(result!=null){file.setLastModified(System.currentTimeMillis());return result;}
        if(client==null || !network || new io.onloopio.device.DeviceSettings(context).flag("force_offline",false) || !new io.onloopio.device.OnlineMode(context).homeWifi())return null;
        byte[] bytes=client.cover(song.coverArt);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>8192 || bounds.outHeight>8192)throw new IOException("Invalid cover dimensions");
        File partial=new File(root,file.getName()+".part");
        try {FileOutputStream out=new FileOutputStream(partial);try{out.write(bytes);out.getFD().sync();}finally{out.close();}if(file.exists() && !file.delete())throw new IOException("Cannot replace cover");if(!partial.renameTo(file))throw new IOException("Cannot save cover");}
        finally{if(partial.exists())partial.delete();}
        trim(file);return decode(file);
    }
    private Bitmap localCover(Song song)throws IOException {
        File source=new File(song.localPath);if(!source.isFile() || !source.getCanonicalPath().startsWith(io.onloopio.library.MusicPaths.root().getCanonicalPath()+File.separator))return null;
        File saved=new File(root,CacheKey.hash(song.localPath+":"+source.length()+":"+source.lastModified())+".cover");Bitmap result=decode(saved);if(result!=null)return result;
        android.media.MediaMetadataRetriever tags=new android.media.MediaMetadataRetriever();byte[] picture=null;try{tags.setDataSource(source.getPath());picture=tags.getEmbeddedPicture();}catch(RuntimeException missing){}finally{tags.release();}
        if(picture!=null && picture.length<=2*1024*1024){FileOutputStream out=new FileOutputStream(saved);try{out.write(picture);}finally{out.close();}result=decode(saved);if(result!=null){trim(saved);return result;}saved.delete();}
        File dir=source.getParentFile();for(String name:new String[]{"cover.jpg","folder.jpg","front.jpg","cover.png","folder.png"}){result=decode(new File(dir,name));if(result!=null)return result;}return null;
    }
    private static Bitmap decode(File file){
        if(!file.isFile() || file.length()>2*1024*1024)return null;
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<=0 || o.outHeight<=0 || o.outWidth>8192 || o.outHeight>8192)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>256 || o.outHeight/o.inSampleSize>256)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(file.getPath(),o);}catch(RuntimeException ignored){return null;}catch(OutOfMemoryError ignored){return null;}
    }
    private void trim(File keep){File[] files=root.listFiles();if(files==null)return;Arrays.sort(files,new Comparator<File>(){public int compare(File a,File b){return a.lastModified()<b.lastModified()?-1:a.lastModified()==b.lastModified()?0:1;}});long bytes=0;for(File f:files)if(f.getName().endsWith(".cover"))bytes+=f.length();for(File f:files)if(bytes>32L*1024*1024 && f.getName().endsWith(".cover") && !f.equals(keep)){long size=f.length();if(f.delete())bytes-=size;}}
}
