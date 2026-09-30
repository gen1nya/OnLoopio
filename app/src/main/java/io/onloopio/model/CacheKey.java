package io.onloopio.model;
import java.security.MessageDigest;
/** Stable audio identity shared by SQLite and the hashed SD namespace. */
public final class CacheKey {
    private static final ThreadLocal<MessageDigest> HASH=new ThreadLocal<MessageDigest>(){protected MessageDigest initialValue(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception unavailable){return null;}}};
    private CacheKey(){}
    public static String hash(String value) {
        try {MessageDigest algorithm=HASH.get();if(algorithm==null)throw new IllegalStateException("SHA-256 unavailable");byte[] digest=algorithm.digest(value.getBytes("UTF-8"));StringBuilder text=new StringBuilder(64);for(byte b:digest){int n=b&255;text.append("0123456789abcdef".charAt(n>>4));text.append("0123456789abcdef".charAt(n&15));}return text.toString();}
        catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
    }
    public static String audioName(String id){return hash(id)+".audio";}
}
