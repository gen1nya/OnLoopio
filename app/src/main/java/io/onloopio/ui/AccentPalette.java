package io.onloopio.ui;

import io.onloopio.device.DeviceSettings;

/** Bright accents on dark themes, deeper shades on the light theme. */
final class AccentPalette {
    static final String[] NAMES={"Theme default","Lime","Emerald","Turquoise","Sky blue","Indigo","Violet","Rose","Coral","Orange","Amber"};
    private static final int[] DARK={0,0xffb7ef60,0xff5fe0a0,0xff62ddd6,0xff73c7ff,0xffadb4ff,0xffd0a0ff,0xffffa5cf,0xffffab9c,0xffffc078,0xffffe08a};
    private static final int[] LIGHT={0,0xff3f6514,0xff146341,0xff126762,0xff165f86,0xff404fad,0xff7444a3,0xff97356b,0xffa13f32,0xff925000,0xff765d05};
    static int selected(DeviceSettings prefs) {
        int value=prefs.number("accent_color",0);
        return value>=0 && value<NAMES.length?value:0;
    }
    static int color(int theme,int choice) {
        if(choice<=0 || choice>=NAMES.length)return theme==1?0xff245b22:theme==2?0xff73c7ff:0xffb7ef60;
        return (theme==1?LIGHT:DARK)[choice];
    }
}
