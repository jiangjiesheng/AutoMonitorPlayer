package com.xiao.nicevideoplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.View;

/**
 * 实现可拖拽、缩放的FrameLayout，从而使其中的视频播放View跟着拖拽、缩放
 */
public class DragFrameLayout extends FrameLayout implements View.OnTouchListener, View.OnClickListener {
    //500ms内点击两次，算双击
    private  static final long DOUBLE_CLICK_INTERVAL = 500;
    //屏幕信息
    protected int screenWidth;
    protected int screenHeight;
    //down时的触屏坐标
    protected int lastX;
    protected int lastY;
    //记录自身四角的坐标
    private int oriLeft;
    private int oriRight;
    private int oriTop;
    private int oriBottom;
    //用于判断双击
    private long lastClickTime = 0;

    private AutoMonitorPlayer mPlayer;

/*
    private static final int TOP = 0x15;
    private static final int LEFT = 0x16;
    private static final int BOTTOM = 0x17;
    private static final int RIGHT = 0x18;
    private static final int LEFT_TOP = 0x11;
    private static final int RIGHT_TOP = 0x12;
    private static final int LEFT_BOTTOM = 0x13;
    private static final int RIGHT_BOTTOM = 0x14;
    private static final int CENTER = 0x19;
*/
    public DragFrameLayout(@NonNull Context context) {
        super(context);
        setClickable(true);
        setOnTouchListener(this);
        setOnClickListener(this);
        initScreenWH();
    }

    public DragFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
        initScreenWH();
    }

    public DragFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnTouchListener(this);
        initScreenWH();
    }

    private void initScreenWH() {
        //要注意减去systemUI？
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        Log.i("DragFrameLayout","screenHeight:"+screenHeight+" ,screenWidth:"+screenWidth);
    }

    public void setPlayer(AutoMonitorPlayer player) {
        mPlayer = player;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //如果点击位置不在本控件内，不会调到这里
        //仅限于tinyWindow状态才能拖动
        if(!mPlayer.isTinyWindow()) {
            return false;
        }

        //获取坐标，如果是第一次down，需要记录下来作为移动的中心点
        int action = event.getAction();
        if(action==MotionEvent.ACTION_DOWN) {
            //当前窗口大小
            oriBottom = v.getBottom();
            oriLeft = v.getLeft();
            oriRight = v.getRight();
            oriTop = v.getTop();
            //getX是相对于自身左上角的坐标，getRawX是相对于整个屏幕左上角的坐标
            lastX = (int)event.getRawX();
            lastY = (int)event.getRawY();
        }
        //处理拖动事件
        handlerDrag(v, event, action);
        //重绘
        invalidate();
        //返回false，才能响应onClick事件
        return false;
    }

    /**
     * 处理拖动
     * @param v 自身视图
     * @param event 触摸事件
     * @param action 触摸类型
     */
    private void handlerDrag(View v, MotionEvent event, int action) {
        //move消息是临时移动，up消息时要把移动后的坐标写入窗口参数
        //不考虑边缘越界被遮挡的处理
        switch(action) {
            case MotionEvent.ACTION_MOVE:
                int curX = (int)event.getRawX();
                int curY = (int)event.getRawY();
                int offsetX = curX - lastX;
                int offsetY = curY - lastY;
                oriTop += offsetY;
                oriBottom += offsetY;
                oriRight += offsetX;
                oriLeft += offsetX;
                //临时修改视图显示
                layout(oriLeft,oriTop,oriRight,oriBottom);
                //更新最后的点击坐标
                lastY = curY;
                lastX = curX;
                break;
            case MotionEvent.ACTION_UP:
                //获取旧的窗口参数
                ViewGroup.LayoutParams oldParams = getLayoutParams();
                if(oldParams==null) {
                    //如果没有父组件就是null
                    Log.e("DragFrameLayout","getLayoutParams is null");
                    break;
                }
                //更新窗口参数
                if(oldParams instanceof FrameLayout.LayoutParams) {
                    //Log.v("DragFrameLayout","oriRight:" + oriRight + " ,oriBottom:"+ oriBottom);
                    //这里修改gravity，使用left和top为Margins的定位参数，只需要给这两个值就可以了
                    ((LayoutParams)oldParams).gravity = Gravity.LEFT | Gravity.TOP;
                    ((LayoutParams)oldParams).setMargins(oriLeft,oriTop,0,0);
                    setLayoutParams(oldParams);
                }else {
                    Log.e("DragFrameLayout","LayoutParams type is wrong");
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        //只有onTouch返回false，onClick才能消费点击事件
        Log.i("DragFrameLayout","onClick");
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastClickTime < DOUBLE_CLICK_INTERVAL) {
            mPlayer.onDoubleClick();
        }
        lastClickTime = currentTime;
    }
}
