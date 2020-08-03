package com.s0n1.sample;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by Edsuns@qq.com on 2020/8/2.
 */
public class MutableToast {
    private static Toast toast;


    public static void show(Context context, String text) {
        show(context, text, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, int stringResId, int duration) {
        show(context, context.getString(stringResId), duration);
    }

    public static void show(Context context, String text, int duration) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
