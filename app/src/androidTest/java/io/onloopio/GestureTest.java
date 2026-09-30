package io.onloopio;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.widget.ListView;
import io.onloopio.player.PlaybackService;
import io.onloopio.ui.PlaylistActivity;

/** Hardware center gestures must not require touch input. */
public final class GestureTest extends InstrumentationTestCase {
    private PlaylistActivity home;
    protected void setUp() throws Exception {super.setUp();home=((OnLoopioTestRunner)getInstrumentation()).awaitHome();assertNotNull(home);getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.openLibrary();}});}
    private ListView list() {return (ListView)LauncherTest.findList(home.getWindow().getDecorView());}
    private void press(final int action) {getInstrumentation().runOnMainSync(new Runnable(){public void run(){long t=SystemClock.uptimeMillis();home.dispatchKeyEvent(new KeyEvent(t,t,action,KeyEvent.KEYCODE_DPAD_CENTER,0));}});}
    public void testLongPressAndDoubleClickPlaylist() throws Exception {
        try {
            press(KeyEvent.ACTION_DOWN);press(KeyEvent.ACTION_UP);Thread.sleep(600);for(int n=0;n<100 && list().getAdapter().getCount()<=2;n++)Thread.sleep(100);assertTrue("Online playlists unavailable",list().getAdapter().getCount()>2);
            press(KeyEvent.ACTION_DOWN);Thread.sleep(750);press(KeyEvent.ACTION_UP);
            assertEquals("Play now",list().getAdapter().getItem(0));
            getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.onBackPressed();}});
            press(KeyEvent.ACTION_DOWN);press(KeyEvent.ACTION_UP);press(KeyEvent.ACTION_DOWN);press(KeyEvent.ACTION_UP);
            Thread.sleep(600);
            assertNotNull("Player is still a list",home.getWindow().getDecorView().findViewWithTag("player"));
        } finally {PlaybackService.action(getInstrumentation().getTargetContext(),PlaybackService.STOP);getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.onBackPressed();}});}
    }
}
