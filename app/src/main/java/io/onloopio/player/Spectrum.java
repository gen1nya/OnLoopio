package io.onloopio.player;

import android.media.audiofx.Visualizer;
import android.util.Log;

/** Session spectrum from the platform Visualizer effect: 8-bit mono FFT, at most 20 captures per second. */
public final class Spectrum {
    private Spectrum() { }
    public static final int BANDS=32;
    private static final float[] EMPTY=new float[BANDS];
    /** Log-spaced band levels 0..1 from the lowest FFT bin to Nyquist; empty while idle. */
    public static volatile float[] bands=EMPTY;
    public static volatile int captures;public static volatile String status="";
    /** The two latest captures and their timing let the view interpolate at display rate. */
    private static volatile float[] current=EMPTY,previous=EMPTY;private static volatile long capturedAt;private static volatile int captureMs=50;
    /** A visible Now Playing screen wants captures; anything else lets the effect idle. */
    public static volatile boolean wanted=true;
    private static Visualizer visualizer;private static int session=-1,failed=-1;private static volatile boolean enabled;
    private static final float[] smoothed=new float[BANDS];private static int[] edges;
    /** 8-bit FFT magnitudes are small and device dependent, so levels are relative to a slowly decaying peak. */
    private static final float CEILING_FLOOR=6;private static float ceiling=CEILING_FLOOR;

    /** True while the effect is enabled and captures are expected. */
    public static boolean active(){return enabled;}
    public static synchronized void sync(int sessionId,boolean playing){
        if(sessionId!=session){release();session=sessionId;}
        boolean want=playing && wanted && sessionId>0;
        if(want && visualizer==null && sessionId!=failed){
            try{visualizer=new Visualizer(sessionId);visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);captureMs=Math.max(16,1000000/Math.max(1,Visualizer.getMaxCaptureRate()));
                visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                    public void onWaveFormDataCapture(Visualizer v,byte[] waveform,int rate){}
                    public void onFftDataCapture(Visualizer v,byte[] fft,int rate){fold(fft);}
                },Visualizer.getMaxCaptureRate(),false,true);status="";
            }catch(RuntimeException failure){Log.w("OnLoopio","VISUALIZER_UNAVAILABLE",failure);status="Spectrum unavailable";failed=sessionId;release();session=sessionId;return;}
        }
        if(visualizer!=null && enabled!=want){try{visualizer.setEnabled(want);enabled=want;}catch(IllegalStateException dead){release();}}
        if(!want)quiet();
    }
    public static synchronized void release(){if(visualizer!=null){try{visualizer.setEnabled(false);}catch(IllegalStateException ignored){}visualizer.release();}visualizer=null;enabled=false;session=-1;quiet();}
    private static void quiet(){bands=EMPTY;current=EMPTY;previous=EMPTY;java.util.Arrays.fill(smoothed,0);ceiling=CEILING_FLOOR;}
    /** fft: re(0), re(n/2), then re/im pairs for bins 1..n/2-1; band peaks are folded into log-spaced bands with a fast attack and slow decay. */
    static void fold(byte[] fft){
        int bins=fft.length/2;if(edges==null || edges[BANDS]!=bins)edges=edges(bins);
        float[] raw=new float[BANDS];float top=0;
        for(int b=0;b<BANDS;b++){float peak=0;for(int k=edges[b];k<edges[b+1];k++){float re=fft[2*k],im=fft[2*k+1];peak=Math.max(peak,re*re+im*im);}raw[b]=(float)Math.sqrt(peak);top=Math.max(top,raw[b]);}
        ceiling=Math.max(CEILING_FLOOR,Math.max(top,ceiling*.995f));
        double floor=Math.log(2),range=Math.log(1+ceiling)-floor;float[] next=new float[BANDS];
        for(int b=0;b<BANDS;b++){float level=(float)Math.max(0,(Math.log(1+raw[b])-floor)/range);smoothed[b]=Math.max(level,smoothed[b]*.78f);next[b]=Math.min(1,smoothed[b]);}
        previous=current;current=next;capturedAt=android.os.SystemClock.uptimeMillis();bands=next;captures++;
        if(captures%40==0)Log.d("OnLoopio","SPECTRUM captures="+captures+" ceiling="+ceiling+" top="+top+" bands="+java.util.Arrays.toString(next));
    }
    /** Band levels at display time: linear blend from the previous capture to the latest over one capture interval. */
    public static void levels(float[] out,long now){
        float[] from=previous,to=current;float t=Math.max(0,Math.min(1,(now-capturedAt)/(float)captureMs));
        for(int b=0;b<BANDS;b++)out[b]=from[b]+(to[b]-from[b])*t;
    }
    static int[] edges(int bins){int[] e=new int[BANDS+1];e[0]=1;double span=Math.log(bins-1);for(int b=1;b<=BANDS;b++)e[b]=Math.max(e[b-1]+1,(int)Math.round(Math.exp(span*b/BANDS)));e[BANDS]=bins;return e;}
}
