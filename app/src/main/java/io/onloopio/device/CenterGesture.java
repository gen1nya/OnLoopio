package io.onloopio.device;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Resolve a center sequence before applying actions, so four taps never pause or open rows. */
public final class CenterGesture {
    public static final long TAP_GAP_MS=420,HOLD_MS=650;
    public interface Listener {
        void started();
        void shortPress(int count);
        void longPress();
        void lockChanged(boolean locked);
    }
    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private int taps;
    private long lastRelease;
    private boolean down,held;
    public CenterGesture(Context context,Handler handler,Listener listener) {
        this.context=context;this.handler=handler;this.listener=listener;
    }
    public void key(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            if(event.getRepeatCount()!=0 || down)return;
            if(taps>0 && SystemClock.uptimeMillis()-lastRelease>TAP_GAP_MS)finish.run();
            handler.removeCallbacks(finish);
            if(taps==0)listener.started();
            down=true;held=false;handler.postDelayed(hold,HOLD_MS);
        } else if(event.getAction()==KeyEvent.ACTION_UP && down) {
            down=false;handler.removeCallbacks(hold);
            if(event.isCanceled()){cancel();return;}
            if(held){held=false;return;}
            lastRelease=SystemClock.uptimeMillis();
            if(++taps==4) {
                cancel();boolean locked=!ControlLock.locked(context);ControlLock.locked(context,locked);
                listener.lockChanged(locked);
            } else handler.postDelayed(finish,TAP_GAP_MS);
        }
    }
    private final Runnable finish=new Runnable(){public void run(){
        int count=taps;taps=0;handler.removeCallbacks(finish);
        if(!ControlLock.locked(context) && (count==1 || count==2))listener.shortPress(count);
    }};
    private final Runnable hold=new Runnable(){public void run(){
        if(!down)return;handler.removeCallbacks(finish);taps=0;held=true;
        if(!ControlLock.locked(context))listener.longPress();
    }};
    public void cancel() { handler.removeCallbacks(finish);handler.removeCallbacks(hold);taps=0;down=false;held=false; }
    /** Wheel movement ends a released tap sequence; resolve its action before changing modes. */
    public void finishBeforeWheel(){if(!down && taps>0)finish.run();cancel();}
}
