package com.xiao.nicevieoplayer.example;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;

import com.xiao.nicevideoplayer.AutoMonitorPlayer;
import com.xiao.nicevideoplayer.NiceUtil;
import com.xiao.nicevieoplayer.R;

/*
//用于海康播放器
import com.hikvision.recorderplayer.player.PlayerWrapper;
import android.view.SurfaceHolder;
import com.xiao.nicevideoplayer.AutoMonitorPlayer.ISurfaceViewCallBack;
import com.hikvision.utils.LogUtils;
 */

public class AutoMonitorActivity extends AppCompatActivity {
    private static final String TAG = "AutoMonitorActivity";

    private AutoMonitorPlayer mPlayer;
    /*
    //hik视频播放
    private PlayerWrapper mHikPlayer;
    private SurfaceHolder mHolder;
    private boolean mIsVisibleToUser = true;
    private boolean mSurfaceCreated = false;
    private static final String ADDRESS = "192.168.42.1";
    private int mPort;
    private static int mPorts[] = {9801, 9802, 9803, 9804, 9805};
    */

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auto_monitor);
        init();
    }

    private void init() {
        mPlayer = (AutoMonitorPlayer) findViewById(R.id.auto_video_player);

        //普通视频的设置
        mPlayer.setPlayerType(AutoMonitorPlayer.PLAYER_TYPE_IJK);
        mPlayer.setViewType(AutoMonitorPlayer.VIEW_TYPE_SURFACEVIEW);
        mPlayer.setLandscape(false);
        //这一步可以省略，AutoMonitorPlayer中有默认的实现
        mPlayer.setDragConfig(new MonitorDragConfig(this, mPlayer));
        mPlayer.setTinyWindowType(AutoMonitorPlayer.WINDOW_TYPE_DIALOG);
        //mPlayer.setTinyWindowType(AutoMonitorPlayer.WINDOW_TYPE_VIEW);
        mPlayer.setUp("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4", null);

        /*
        //hik监控视频的设置。使用外部player，必须设置回调
        mPort = mPorts[0];
        mPlayer.setPlayerType(AutoMonitorPlayer.PLAYER_TYPE_NULL);
        mPlayer.setViewType(AutoMonitorPlayer.VIEW_TYPE_SURFACEVIEW);
        mPlayer.setLandscape(false);
        mPlayer.setDragConfig(new MonitorDragConfig(this, mPlayer));
        mPlayer.setSurfaceViewCallBack(new ISurfaceViewCallBack(){

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mHolder = holder;
                mSurfaceCreated = true;
                if (mIsVisibleToUser) {
                    createPlayer();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mHolder = null;
                mSurfaceCreated = false;
                if (mIsVisibleToUser) {
                    destroyPlayer();
                }
            }
        });
        */
    }

    /*
    private void createPlayer() {
        mHikPlayer = new PlayerWrapper(getAssets());
        mHikPlayer.setErrorCallback(new PlayerWrapper.ErrorCallback() {
            @Override
            public void onError(int errCode) {
                if (mHikPlayer != null) {
                    mHikPlayer.stop();
                    mHikPlayer.play(ADDRESS, mPort, mHolder);
                    LogUtils.d(TAG, "recreate player");
                }
            }
        });
        mHikPlayer.play(ADDRESS, mPort, mHolder);
    }

    private void destroyPlayer() {
        mHikPlayer.destroy();
        mHikPlayer = null;
        LogUtils.d(TAG, "port:" + mPort + " destroyPlayer");
    }
     */

    @Override
    protected void onRestart() {
        Log.d(TAG,"onRestart");
        super.onRestart();
        mPlayer.resetSurfaceView();
    }


    @Override
    public void onBackPressed() {
        if (mPlayer != null) {
            if (mPlayer.isFullScreen()) {
                mPlayer.enterNormalScreen();
            } else if (mPlayer.isTinyWindow()) {
                mPlayer.enterNormalScreen();
            } else {
                mPlayer.release();
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onDestroy();
    }

    public void enterTinyWindow(View view) {
        mPlayer.enterTinyWindow();
    }

    class MonitorDragConfig implements AutoMonitorPlayer.IDragConfig{
        private WeakReference<Context> mContext;

        public MonitorDragConfig(Context context, AutoMonitorPlayer player) {
            this.mContext = new WeakReference<Context>(context);;
        }

        @Override
        public void onSingalClick() {
            //暂停和播放交替
            mPlayer.pauseOrStart();
        }

        @Override
        public void onDoubleClick() {
            //普通->小窗->全屏->普通 循环
            if (mPlayer.isNormal()) {
                mPlayer.enterTinyWindow();
            } else if (mPlayer.isTinyWindow()) {
                mPlayer.enterFullScreen();
            } else if (mPlayer.isFullScreen()) {
                mPlayer.enterNormalScreen();
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
