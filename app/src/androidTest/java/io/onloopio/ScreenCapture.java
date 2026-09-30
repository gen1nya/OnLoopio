package io.onloopio;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.app.Instrumentation;
import java.io.File;
import java.io.FileOutputStream;
final class ScreenCapture {
    static void save(final Instrumentation test,final Activity activity,final String name){test.runOnMainSync(new Runnable(){public void run(){try{View decor=activity.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.RGB_565);decor.draw(new Canvas(bitmap));FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name));try{bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{out.close();bitmap.recycle();}}catch(Exception failure){throw new RuntimeException(failure);}}});}
}
