package io.onloopio.device;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONObject;

/** Bounded, private USB provision file; configuration never needs exported setting extras. */
public final class ControlConfigStore {
    private ControlConfigStore() {}
    public static boolean importPrivateFile(Context context) {
        File file=new File(context.getFilesDir(),"control-settings.json");if(!file.isFile())return false;
        try {
            if(file.length()<2 || file.length()>1024)throw new IllegalArgumentException();
            byte[] bytes=new byte[(int)file.length()];FileInputStream input=new FileInputStream(file);
            try{int offset=0;while(offset<bytes.length){int read=input.read(bytes,offset,bytes.length-offset);if(read<0)throw new IllegalArgumentException();offset+=read;}}finally{input.close();}
            JSONObject value=new JSONObject(new String(bytes,"UTF-8"));
            if(value.length()==0)throw new IllegalArgumentException();
            java.util.Iterator<?> keys=value.keys();while(keys.hasNext()){String key=(String)keys.next();if(!"wheel_steps_per_turn".equals(key) && !"unlock".equals(key))throw new IllegalArgumentException();}
            int steps=value.has("wheel_steps_per_turn")?value.getInt("wheel_steps_per_turn"):0;
            if(value.has("wheel_steps_per_turn") && (steps<8 || steps>128 || !String.valueOf(steps).equals(value.get("wheel_steps_per_turn").toString())))throw new IllegalArgumentException();
            boolean unlock=value.has("unlock") && Boolean.TRUE.equals(value.get("unlock"));
            if(value.has("unlock") && !unlock)throw new IllegalArgumentException();
            SharedPreferences.Editor change=context.getSharedPreferences("device",0).edit();
            if(steps!=0)change.putInt("wheel_steps_per_turn",steps);if(unlock)change.putBoolean("controls_locked",false);
            if(!change.commit())throw new IllegalStateException();
            if(!file.delete())android.util.Log.w("OnLoopio","CONTROL_CONFIG_REMOVE_FAILED");
            return true;
        }catch(Exception invalid){android.util.Log.w("OnLoopio","CONTROL_CONFIG_INVALID");return false;}
    }
}
