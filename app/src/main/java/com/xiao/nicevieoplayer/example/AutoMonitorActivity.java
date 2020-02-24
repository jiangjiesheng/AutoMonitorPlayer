package com.xiao.nicevieoplayer.example;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.xiao.nicevideoplayer.AutoMonitorPlayer;
import com.xiao.nicevideoplayer.NiceUtil;
import com.xiao.nicevieoplayer.R;

import java.lang.ref.WeakReference;

public class AutoMonitorActivity extends AppCompatActivity {
    private AutoMonitorPlayer mPlayer;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auto_monitort);
        init();
    }

    private void init() {
        mPlayer = (AutoMonitorPlayer) findViewById(R.id.auto_video_player);
        mPlayer.setPlayerType(AutoMonitorPlayer.PLAYER_TYPE_IJK);
        //这一步可以省略，有Player中有默认的实现
        mPlayer.setDragConfig(new MonitorDragConfig(this));
        mPlayer.setUp("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4", null);
    }

    @Override
    public void onBackPressed() {
        if (mPlayer != null) {
            if (mPlayer.isFullScreen()) {
                mPlayer.exitFullScreen();
            } else if (mPlayer.isTinyWindow()) {
                mPlayer.exitTinyWindow();
            } else {
                mPlayer.release();
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onStop() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onStop();
    }

    public void enterTinyWindow(View view) {
        mPlayer.enterTinyWindow();
    }

    class MonitorDragConfig implements AutoMonitorPlayer.DragConfig{
        private WeakReference<Context> mContext;

        public MonitorDragConfig(Context context) {
            this.mContext = new WeakReference<Context>(context);;
        }

        @Override
        public void onDoubleClick() {
            //普通->小窗->全屏->普通 循环
            if (mPlayer.isNormal()) {
                mPlayer.enterTinyWindow();
            } else if (mPlayer.isTinyWindow()) {
                mPlayer.enterFullScreen();
            } else if (mPlayer.isFullScreen()) {
                mPlayer.exitFullScreen();
            }
        }

        @Override
        public FrameLayout.LayoutParams getTinyWindowLayoutParams() {
            // 小窗口的宽度为屏幕宽度的60%，长宽比默认为16:9，右边距、下边距为8dp。
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (int) (NiceUtil.getScreenWidth(mContext.get()) * 0.6f),
                    (int) (NiceUtil.getScreenWidth(mContext.get()) * 0.6f * 9f / 16f));
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.rightMargin = NiceUtil.dp2px(mContext.get(), 8f);
            params.bottomMargin = NiceUtil.dp2px(mContext.get(), 8f);
            return params;
        }
    }
}
