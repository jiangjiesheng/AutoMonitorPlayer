package com.xiao.nicevideoplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.reflect.Method;

public class TinyDialog extends Dialog {
    //500ms内点击两次，算双击
    private static final long DOUBLE_CLICK_INTERVAL = 500;
    private static final int EXE_SINGAL_CLICK = 1;

    private String TAG = "TinyDialog";

    private Context mContent;
    private WindowManager.LayoutParams mLp;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams layoutParams;
    private float mTouchStartX;
    private float mTouchStartY;
    //用于判断双击
    private long lastClickTime = 0;
    private AutoMonitorPlayer mPlayer;

    public TinyDialog(@NonNull Context context, AutoMonitorPlayer player) {
        //无边框窗口
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mContent = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        checkFloatPermission(context);
        mPlayer = player;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        layoutParams = (WindowManager.LayoutParams) getWindow().getDecorView().getLayoutParams();
        mWindowManager.updateViewLayout(getWindow().getDecorView(), layoutParams);
    }

    public void setWindowParam(FrameLayout.LayoutParams lp) {
        mLp = getWindow().getAttributes();
        mLp.width = lp.width;
        mLp.height = lp.height;
        mLp.gravity = Gravity.TOP | Gravity.LEFT;//lp.gravity;
        getWindow().setAttributes(mLp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){//6.0
            mLp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }else {
            mLp.type =  WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        getWindow().setType(mLp.type);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        //拦截消息传递, 正常会mWindow.superDispatchTouchEvent(ev)再调自己的onTouchEvent
        return onTouchEvent(ev);
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
        float x = event.getRawX();
        float y = event.getRawY();
        LogUtil.d("dialog onTouchEvent, x=" + x + " ,y=" + y);
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchStartX = x;
                mTouchStartY = y;
                keyDown();
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                LogUtil.d("dialog onTouchEvent, lx=" + layoutParams.x + " ,ly=" + layoutParams.y);
                layoutParams.x += (int) (x - mTouchStartX);
                layoutParams.y += (int) (y - mTouchStartY);
                //改变窗口位置
                mWindowManager.updateViewLayout(getWindow().getDecorView(), layoutParams);
                //更新锚点坐标
                mTouchStartX = x;
                mTouchStartY = y;
                break;
            default:
                break;
        }

        return super.onTouchEvent(event);
    }

    private void keyDown() {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastClickTime < DOUBLE_CLICK_INTERVAL) {
            mPlayer.onDoubleClick();
            //确认是双击,取消单击响应
            handler.removeMessages(EXE_SINGAL_CLICK);
        }else {
            //未避免和双击事件冲突, 发送延时消息
            Message message = new Message();
            message.what = EXE_SINGAL_CLICK;
            handler.sendMessageDelayed(message, DOUBLE_CLICK_INTERVAL);
        }
        lastClickTime = currentTime;
    }

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case EXE_SINGAL_CLICK:
                    mPlayer.onSingalClick();
                    break;
                default:
                    break;
            }
        }
    };


    @Override
    public void onBackPressed() {
        //back键销毁窗口
        super.onBackPressed();
    }
}
