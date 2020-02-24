package com.xiao.nicevideoplayer;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.IOException;
import java.util.Map;

import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 实时监控播放器，相比NiceVideoPlayer，减少播放控制功能，但可拖动
 * 也支持录像文件播放，只有简单的单击界面实现播放/停止的功能
 * 可选使用TextureView或SurfaceView来播放，hik播放库需要使用SurfaceView
 */
public class AutoMonitorPlayer extends FrameLayout implements TextureView.SurfaceTextureListener {
    public static final int STATE_ERROR = -1;          // 播放错误
    public static final int STATE_IDLE = 0;            // 播放未开始
    public static final int STATE_PREPARING = 1;       // 播放准备中
    public static final int STATE_PREPARED = 2;        // 播放准备就绪
    public static final int STATE_PLAYING = 3;         // 正在播放
    public static final int STATE_PAUSED = 4;          // 暂停播放
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
     **/
    public static final int STATE_BUFFERING_PLAYING = 5;
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，此时暂停播放器，继续缓冲，缓冲区数据足够后恢复暂停)
     **/
    public static final int STATE_BUFFERING_PAUSED = 6;
    public static final int STATE_COMPLETED = 7;       // 播放完成

    public static final int PLAYER_NORMAL = 10;        // 普通播放器
    public static final int PLAYER_FULL_SCREEN = 11;   // 全屏播放器
    public static final int PLAYER_TINY_WINDOW = 12;   // 小窗口播放器

    public static final int PLAYER_TYPE_IJK = 111;      // IjkPlayer
    public static final int PLAYER_TYPE_NATIVE = 222;   // Android原生MediaPlayer

    public static final int DATA_SOURCE_TYPE_URL = 1111;
    public static final int DATA_SOURCE_TYPE_STREAM = 2222;

    private Context mContext;
    private FrameLayout mContainer;
    //播放源类型设置。要注意MediaPlayer并不支持通过流播放
    private int mDataSourceType = DATA_SOURCE_TYPE_URL;
    private String mAddress;
    private int mPort;
    private SurfaceHolder mHolder;
    private int mPlayerType = PLAYER_TYPE_IJK;
    private int mCurrentState = STATE_IDLE;
    private int mPlayerState = PLAYER_NORMAL;

    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private String mUrl;
    private Map<String, String> mHeaders;
    private IMediaPlayer mMediaPlayer;
    private int mBufferPercentage;
    private DragConfig mConfig = new DefaultDragConfig();

    public AutoMonitorPlayer(@NonNull Context context) {
        this(context,null);
    }

    public AutoMonitorPlayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    /**
     * 初始化播放容器，用于存放TexureView。其实直接扩展TextureView取代Container也可以
     */
    private void init() {
        //使用可拖动FrameLayout
        mContainer = new DragFrameLayout(mContext);
        ((DragFrameLayout)mContainer).setPlayer(this);
        mContainer.setBackgroundColor(Color.BLACK);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mContainer, params);
    }

    public void setUp(String url, Map<String, String> headers) {
        mUrl = url;
        mHeaders = headers;
        //设置源后自动开始
        start();
    }

    /**
     * 用于适配海康播放库
     * @param address socket地址
     * @param port 端口号
     * @param holder 容器
     */
    public void setUp(String address, int port, SurfaceHolder holder) {
        mAddress = address;
        mPort = port;
        mHolder = holder;
        //设置源后自动开始
        //start();
    }

    /**
     * 播放开始
     */
    private void start() {
        //首先释放当前的播放器
        release();
        if (mCurrentState == STATE_IDLE
                || mCurrentState == STATE_ERROR
                || mCurrentState == STATE_COMPLETED) {
            initMediaPlayer();
            initTextureView();
            addTextureView();
        }
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            switch (mPlayerType) {
                case PLAYER_TYPE_NATIVE:
                    mMediaPlayer = new AndroidMediaPlayer();
                    break;
                case PLAYER_TYPE_IJK:
                default:
                    mMediaPlayer = new IjkMediaPlayer();
                    break;
            }
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);

            mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
            mMediaPlayer.setOnErrorListener(mOnErrorListener);
            mMediaPlayer.setOnInfoListener(mOnInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        }
    }

    private void initTextureView() {
        if (mTextureView == null) {
            mTextureView = new TextureView(mContext);
            mTextureView.setSurfaceTextureListener(this);
        }
    }

    private void addTextureView() {
        mContainer.removeView(mTextureView);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.addView(mTextureView, 0, params);
    }

    /**
     * 释放资源，重置状态
     */
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mContainer.removeView(mTextureView);
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        mCurrentState = STATE_IDLE;
        mPlayerState = PLAYER_NORMAL;
    }

    /**
     * 设置播放器类型,目前有原生MediaPlay和ijk两种
     *
     * @param playerType IjkPlayer or MediaPlayer.
     */
    public void setPlayerType(int playerType) {
        mPlayerType = playerType;
    }

    private IMediaPlayer.OnPreparedListener mOnPreparedListener
            = new IMediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(IMediaPlayer mp) {
            mp.start();
            mCurrentState = STATE_PREPARED;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("onPrepared ——> STATE_PREPARED");
        }
    };

    private IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener
            = new IMediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
            LogUtil.d("onVideoSizeChanged ——> width：" + width + "，height：" + height);
        }
    };

    private IMediaPlayer.OnCompletionListener mOnCompletionListener
            = new IMediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(IMediaPlayer mp) {
            mCurrentState = STATE_COMPLETED;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("onCompletion ——> STATE_COMPLETED");
            NiceVideoPlayerManager.instance().setCurrentNiceVideoPlayer(null);
        }
    };

    private IMediaPlayer.OnErrorListener mOnErrorListener
            = new IMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(IMediaPlayer mp, int what, int extra) {
            mCurrentState = STATE_ERROR;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("onError ——> STATE_ERROR ———— what：" + what);
            return false;
        }
    };

    private IMediaPlayer.OnInfoListener mOnInfoListener
            = new IMediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                // 播放器开始渲染
                mCurrentState = STATE_PLAYING;
                //mController.setControllerState(mPlayerState, mCurrentState);
                LogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_RENDERING_START：STATE_PLAYING");
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                // MediaPlayer暂时不播放，以缓冲更多的数据
                if (mCurrentState == STATE_PAUSED || mCurrentState == STATE_BUFFERING_PAUSED) {
                    mCurrentState = STATE_BUFFERING_PAUSED;
                    LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED");
                } else {
                    mCurrentState = STATE_BUFFERING_PLAYING;
                    LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING");
                }
                //.setControllerState(mPlayerState, mCurrentState);
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                // 填充缓冲区后，MediaPlayer恢复播放/暂停
                if (mCurrentState == STATE_BUFFERING_PLAYING) {
                    mCurrentState = STATE_PLAYING;
                    //mController.setControllerState(mPlayerState, mCurrentState);
                    LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING");
                }
                if (mCurrentState == STATE_BUFFERING_PAUSED) {
                    mCurrentState = STATE_PAUSED;
                    //mController.setControllerState(mPlayerState, mCurrentState);
                    LogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED");
                }
            } else {
                LogUtil.d("onInfo ——> what：" + what);
            }
            return true;
        }
    };

    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener
            = new IMediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            mBufferPercentage = percent;
        }
    };

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        //播放器关联surface
        if (mSurfaceTexture == null) {
            mSurfaceTexture = surfaceTexture;
            openMediaPlayer();
        } else {
            mTextureView.setSurfaceTexture(mSurfaceTexture);
        }
    }

    private void openMediaPlayer() {
        try {
            mMediaPlayer.setDataSource(mContext.getApplicationContext(), Uri.parse(mUrl), mHeaders);
            mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
            mMediaPlayer.prepareAsync();
            mCurrentState = STATE_PREPARING;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("STATE_PREPARING");
        } catch (IOException e) {
            e.printStackTrace();
            LogUtil.e("打开播放器发生错误", e);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return mSurfaceTexture == null;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void enterFullScreen() {
        if (mPlayerState == PLAYER_FULL_SCREEN) {
            return;
        }else if(mPlayerState == PLAYER_TINY_WINDOW) {
            //需要从老的parent节点把mContainer移除
            //如果是normal，因为全屏时parent并没有改变，所以不需要这一步
            ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);
        }
        // 隐藏ActionBar、状态栏，并横屏
        NiceUtil.hideActionBar(mContext);
        NiceUtil.scanForActivity(mContext)
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        this.removeView(mContainer);
        ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                .findViewById(android.R.id.content);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        contentView.addView(mContainer, params);

        mPlayerState = PLAYER_FULL_SCREEN;
        //mController.setControllerState(mPlayerState, mCurrentState);
        LogUtil.d("PLAYER_FULL_SCREEN");
    }

    /**
     * 退出全屏，移除mTextureView和mController，并添加到非全屏的容器中，即normal状态。
     * 切换竖屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期.
     *
     * @return true退出全屏.
     */
    public boolean exitFullScreen() {
        if (mPlayerState == PLAYER_FULL_SCREEN) {
            NiceUtil.showActionBar(mContext);
            NiceUtil.scanForActivity(mContext)
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);

            mPlayerState = PLAYER_NORMAL;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("PLAYER_NORMAL");
            return true;
        }
        return false;
    }

    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    public void enterTinyWindow() {
        if (mPlayerState == PLAYER_TINY_WINDOW) {
            return;
        }
        //父节点变了，需要从老的父节点处删掉容器
        this.removeView(mContainer);

        //android.R.id.content是MainActivity布局最外面的一层FrameLayout
        ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                .findViewById(android.R.id.content);

        FrameLayout.LayoutParams params = mConfig.getTinyWindowLayoutParams();
        contentView.addView(mContainer, params);

        mPlayerState = PLAYER_TINY_WINDOW;
        //mController.setControllerState(mPlayerState, mCurrentState);
        LogUtil.d("PLAYER_TINY_WINDOW");
    }

    /**
     * 退出小窗口播放
     */
    public boolean exitTinyWindow() {
        if (mPlayerState == PLAYER_TINY_WINDOW) {
            ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);

            mPlayerState = PLAYER_NORMAL;
            //mController.setControllerState(mPlayerState, mCurrentState);
            LogUtil.d("PLAYER_NORMAL");
            return true;
        }
        return false;
    }

    public boolean isFullScreen() {
        return mPlayerState == PLAYER_FULL_SCREEN;
    }

    public boolean isTinyWindow() {
        return mPlayerState == PLAYER_TINY_WINDOW;
    }

    public boolean isNormal() {
        return mPlayerState == PLAYER_NORMAL;
    }

    public void onDoubleClick() {
        mConfig.onDoubleClick();
    }

    /**
     * 自定义和可拖动/全屏的窗口相关的配置
     */
    public interface DragConfig {
        void onDoubleClick();
        FrameLayout.LayoutParams getTinyWindowLayoutParams();
    }

    public void setDragConfig(DragConfig config) {
        mConfig = config;
    }

    /**
     * 默认实现类
     */
    class DefaultDragConfig implements AutoMonitorPlayer.DragConfig{

        @Override
        public void onDoubleClick() {
            //普通->小窗->全屏->普通 循环
            if (isNormal()) {
                enterTinyWindow();
            } else if (isTinyWindow()) {
                enterFullScreen();
            } else if (isFullScreen()) {
                exitFullScreen();
            }
        }

        @Override
        public LayoutParams getTinyWindowLayoutParams() {
            // 小窗口的宽度为屏幕宽度的60%，长宽比默认为16:9，右边距、下边距为8dp。
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (int) (NiceUtil.getScreenWidth(mContext) * 0.6f),
                    (int) (NiceUtil.getScreenWidth(mContext) * 0.6f * 9f / 16f));
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.rightMargin = NiceUtil.dp2px(mContext, 8f);
            params.bottomMargin = NiceUtil.dp2px(mContext, 8f);
            return params;
        }
    }
}
