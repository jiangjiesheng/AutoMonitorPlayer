package com.xiao.nicevieoplayer.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.xiao.nicevideoplayer.AutoMonitorPlayer;
import com.xiao.nicevideoplayer.NiceUtil;
import com.xiao.nicevieoplayer.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public class MultiMonitorActivity extends AppCompatActivity {
    private static final String TAG = "MultiMonitorActivity";

    private List<AutoMonitorPlayer> mPlayers = new ArrayList<>();
    private AutoMonitorPlayer mPlayer1;
    private AutoMonitorPlayer mPlayer2;
    private AutoMonitorPlayer mPlayer3;
    private AutoMonitorPlayer mPlayer4;
    private AutoMonitorPlayer mPlayer5;
    private AutoMonitorPlayer mPlayer6;
    private AutoMonitorPlayer mPlayer7;
    private AutoMonitorPlayer mPlayer8;
    private AutoMonitorPlayer mPlayer9;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_monitor);
        init();
    }

    private void init() {
        mPlayer1 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player1);
        mPlayer2 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player2);
        mPlayer3 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player3);
        mPlayer4 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player4);
        mPlayer5 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player5);
        mPlayer6 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player6);
        mPlayer7 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player7);
        mPlayer8 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player8);
        mPlayer9 = (AutoMonitorPlayer) findViewById(R.id.auto_video_player9);
        mPlayers.add(mPlayer1);mPlayers.add(mPlayer2);mPlayers.add(mPlayer3);
        mPlayers.add(mPlayer4);mPlayers.add(mPlayer5);mPlayers.add(mPlayer6);
        mPlayers.add(mPlayer7);mPlayers.add(mPlayer8);mPlayers.add(mPlayer9);

        //普通视频的设置
        mPlayer1.setViewType(AutoMonitorPlayer.VIEW_TYPE_SURFACEVIEW);
        mPlayer1.setLandscape(false);
        mPlayer1.setUp("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4", null);
        mPlayer5.setViewType(AutoMonitorPlayer.VIEW_TYPE_TEXTUREVIEW);
        mPlayer5.setLandscape(true);
        mPlayer5.setUp("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4", null);
        mPlayer9.setViewType(AutoMonitorPlayer.VIEW_TYPE_TEXTUREVIEW);
        mPlayer9.setUp("http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4", null);
        mPlayer1.setDragConfig(new MonitorDragConfig(this, mPlayer1));
        mPlayer5.setDragConfig(new MonitorDragConfig(this, mPlayer5));
        mPlayer9.setDragConfig(new MonitorDragConfig(this, mPlayer9));
    }

    @Override
    protected void onRestart() {
        Log.d(TAG,"onRestart");
        super.onRestart();
        //使用surfaceView时需要这样处理
        mPlayer1.resetSurfaceView();
        //for(AutoMonitorPlayer player:mPlayers) {
        //    player.resetSurfaceView();
        //}
    }

    @Override
    public void onBackPressed() {
        boolean allPlayerReadyExit = true;
        for(AutoMonitorPlayer player:mPlayers) {
            if (player != null) {
                if (player.isFullScreen()) {
                    player.enterNormalScreen();
                    allPlayerReadyExit = false;
                } else if (player.isTinyWindow()) {
                    player.enterNormalScreen();
                    allPlayerReadyExit = false;
                }
            }
        }
        if(allPlayerReadyExit) {
            for(AutoMonitorPlayer player:mPlayers) {
                player.release();
            }
            super.onBackPressed();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public void goToNicePlayer(View view) {
        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
    }

    class MonitorDragConfig implements AutoMonitorPlayer.IDragConfig{
        private WeakReference<Context> mContext;
        private AutoMonitorPlayer mPlayer;

        public MonitorDragConfig(Context context, AutoMonitorPlayer player) {
            this.mContext = new WeakReference<Context>(context);
            mPlayer = player;
        }

        @Override
        public void onSingalClick() {
            //暂停和播放交替
            mPlayer.pauseOrStart();
        }

        @Override
        public void onDoubleClick() {
            //普通 -> 全屏 ->普通 循环
            if (mPlayer.isNormal()) {
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
