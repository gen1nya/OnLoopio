package io.onloopio.device;

/** Y1 reports steps, not angles. A complete idle turn arms the selected wheel control. */
public final class WheelTurnGuard {
    public static final long IDLE_MS=2000;
    // This Y1 produced 8/7 clockwise steps per turn and 8 anticlockwise steps.
    public static final int DEFAULT_STEPS_PER_TURN=8;
    private final int stepsPerTurn;
    private int direction,steps;
    private long lastStep;
    private String track;
    private boolean armed;
    public WheelTurnGuard(int stepsPerTurn) { this.stepsPerTurn=Math.max(1,stepsPerTurn); }
    public int turn(int direction,long now,String context) {
        expire(now,context);
        if(context==null)return 0;
        if(!armed && this.direction!=direction){steps=0;this.direction=direction;}
        lastStep=now;this.track=context;
        if(armed)return direction;
        if(++steps>=stepsPerTurn)armed=true;
        return 0; // The completing step only arms; the next step applies a change.
    }
    public void expire(long now,String track) {
        if(track==null || !track.equals(this.track) || now-lastStep>=IDLE_MS)reset();
    }
    public void reset() { direction=0;steps=0;lastStep=0;track=null;armed=false; }
    public boolean armed() { return armed; }
    public int progress() { return Math.min(100,steps*100/stepsPerTurn); }
}
