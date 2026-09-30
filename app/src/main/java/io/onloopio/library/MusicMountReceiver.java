package io.onloopio.library;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class MusicMountReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())){if(android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState())){io.onloopio.device.UsbStorage.mounted(context);if(io.onloopio.config.UsbSetup.importFromUsb(context)){Intent reload=new Intent(context,io.onloopio.ui.PlaylistActivity.class);reload.setAction("io.onloopio.action.RELOAD");reload.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);context.startActivity(reload);}}MusicLibraryService.request(context,true);}else MusicLibraryService.status="Storage unavailable · reconnect USB in player mode";}
}
