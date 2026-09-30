package io.onloopio.device;

import android.view.KeyEvent;

/** Y1 wheel/center mapping confirmed with physical getevent and stock keylayouts. */
public final class Y1Keys {
    private Y1Keys() { }
    public static boolean previousRow(int code) { return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT; }
    public static boolean nextRow(int code) { return code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT; }
    public static boolean select(int code) { return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER; }
}
