package io.onloopio.library;

import android.os.Environment;
import io.onloopio.model.Song;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Portable FAT/Windows names, constrained to the public Music folder. */
public final class MusicPaths {
    public static File root(){return new File(Environment.getExternalStorageDirectory(),"Music");}
    public static String component(String value,String fallback,int limit){
        String name=value==null?"":value.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]"," - ").replaceAll("\\s+"," ").replaceAll("^[. ]+|[. ]+$","");
        if(name.length()==0)name=fallback;
        if(name.length()>limit){name=name.substring(0,limit);if(Character.isHighSurrogate(name.charAt(name.length()-1)))name=name.substring(0,name.length()-1);name=name.replaceAll("[. ]+$","");}
        String stem=name.split("\\.",2)[0];if(stem.toUpperCase(Locale.US).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))name="_"+name;
        return name;
    }
    public static String relative(Song song,String extension){
        String number=song.track>0?(song.disc>1?song.disc+"-":"")+String.format(Locale.US,"%02d",song.track)+" - ":"";
        return component(song.artist,"Unknown artist",64)+"/"+component(song.album,"Unknown album",64)+"/"+component(number+song.title,"Untitled",96)+"."+extension;
    }
    public static File resolve(File root,String relative)throws IOException {
        File file=new File(root,relative);String base=root.getCanonicalPath()+File.separator;
        if(!file.getCanonicalPath().startsWith(base))throw new IOException("Music path outside library");return file;
    }
    public static String extension(File file){int dot=file.getName().lastIndexOf('.');return dot<0?"":file.getName().substring(dot+1).toLowerCase(Locale.US);}
    public static boolean audio(File file){return extension(file).matches("mp3|flac|m4a|aac|ogg|oga|wav|opus|wma|ape|aif|aiff|amr");}
}
