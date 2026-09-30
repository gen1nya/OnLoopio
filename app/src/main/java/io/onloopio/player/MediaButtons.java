package io.onloopio.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public final class MediaButtons extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        KeyEvent event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if(event==null || event.getAction()!=KeyEvent.ACTION_DOWN) return;
        if(handle(context,event.getKeyCode()) && isOrderedBroadcast())abortBroadcast();
    }
    public static boolean handle(Context context,int code) {
        if(io.onloopio.device.ControlLock.locked(context) && io.onloopio.device.ControlLock.media(code))return true;
        String action=code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ? PlaybackService.TOGGLE :
                code==KeyEvent.KEYCODE_MEDIA_PLAY ? PlaybackService.RESUME : code==KeyEvent.KEYCODE_MEDIA_PAUSE ? PlaybackService.PAUSE :
                code==KeyEvent.KEYCODE_MEDIA_NEXT ? PlaybackService.NEXT : code==KeyEvent.KEYCODE_MEDIA_PREVIOUS ? PlaybackService.PREVIOUS : null;
        if(action==null) return false; PlaybackService.action(context,action); return true;
    }
}
