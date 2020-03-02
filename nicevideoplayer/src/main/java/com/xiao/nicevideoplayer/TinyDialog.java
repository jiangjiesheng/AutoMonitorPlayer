package com.xiao.nicevideoplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.reflect.Method;

public class TinyDialog extends Dialog {
    private String TAG = "TinyDialog";

    private Context mContent;

    public TinyDialog(@NonNull Context context) {
        //无边框窗口
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mContent = context;
        checkFloatPermission(context);
    }

    public void setWindowParam(FrameLayout.LayoutParams lp) {
        WindowManager.LayoutParams selfLp = getWindow().getAttributes();
        selfLp.width = lp.width;
        selfLp.height = lp.height;
        selfLp.gravity = lp.gravity;
        getWindow().setAttributes(selfLp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){//6.0
            selfLp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }else {
            selfLp.type =  WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        getWindow().setType(selfLp.type);
    }


    private boolean checkFloatPermission(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {//6.0以上
            boolean result = false;
            try {
                Class clazz = Settings.class;
                Method canDrawOverlays = clazz.getDeclaredMethod("canDrawOverlays", Context.class);
                result = (boolean) canDrawOverlays.invoke(null, context);
                Log.e(TAG, "checkFloatPermission:-->" + result);
                if(!result) {
                    ((Activity)context).startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.getPackageName())), 0);

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {//4.4-5.1
            //4.4-5.1版本的权限判断是通过反射来获取的
            return false;
        } else {//4.4以下
            return true;
        }
    }

    @Override
    public void show() {
        if(checkFloatPermission(mContent)) {
            super.show();
        }else {
            Toast.makeText(mContent, "请给悬浮窗权限", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
    }
}
