package io.onloopio.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PowerReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        DeviceSettings settings=new DeviceSettings(context);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) { settings.shutdownTimer(0); settings.wheelLocked(settings.flag("key_lock",true)); return; }
        if("io.onloopio.POWER_TIMER".equals(intent.getAction())) { settings.shutdownTimer(0); powerOff(context); }
    }
    public static void powerOff(Context context) {
        // SHUTDOWN is signature|system on the verified API-17 firmware. No root daemon is used.
        context.startActivity(new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                .putExtra("android.intent.extra.KEY_CONFIRM",false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
