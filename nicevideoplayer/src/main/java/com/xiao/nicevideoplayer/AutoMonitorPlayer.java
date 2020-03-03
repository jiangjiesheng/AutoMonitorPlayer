package com.xiao.nicevideoplayer;

import android.app.Dialog;
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
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.io.IOException;
import java.util.Map;

import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * create by LangZhichao on 2020/2/26
 *
 * 相比NiceVideoPlayer，去除播放控制功能
 * 可配置播放器类型,也可以用外部播放器
 * 支持网络地址、本地文件或其他外部源用外部播放器播放
 * 小窗模式下可拖动
 * 可配置单击、双击如何响应,可用于控制播放和窗口切换
 * 可选使用TextureView或SurfaceView来播放
 */
public class AutoMonitorPlayer extends FrameLayout implements TextureView.SurfaceTextureListener,SurfaceHolder.Callback {
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
    public static final int PLAYER_TYPE_NULL = 333;   // 使用外部播放器

    public static final int VIEW_TYPE_TEXTUREVIEW = 1111;
    public static final int VIEW_TYPE_SURFACEVIEW = 2222;

    public static final int WINDOW_TYPE_VIEW = 3333;
    public static final int WINDOW_TYPE_DIALOG = 4444;

    public static final int DATA_TYPE_URL = 11111;
    public static final int DATA_TYPE_FILE = 22222;

    private Context mContext;
    private DragFrameLayout mContainer;

    private int mPlayerType = PLAYER_TYPE_IJK;
    private int mCurrentState = STATE_IDLE;
    private int mPlayerState = PLAYER_NORMAL;
    private int mViewType = VIEW_TYPE_TEXTUREVIEW;
    private int mDataType = DATA_TYPE_URL;
    private int mTinyWindowType = WINDOW_TYPE_VIEW;
    //是否允许切换横屏
    private boolean mLandscape = false;

    private String mUrl;
    private Map<String, String> mHeaders;
    private String mFilePath;
    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private SurfaceView mSurfaceView;
    private Dialog mTinyDialg;
    //这个纯粹是用来标记是否要初始化MediaPlayer的
    private SurfaceHolder mHolder;
    private IMediaPlayer mMediaPlayer;
    private int mBufferPercentage;
    private IDragConfig mConfig = new DefaultDragConfig();
    private ISurfaceViewCallBack mSurfaceViewCallback;

    public AutoMonitorPlayer(@NonNull Context context) {
        this(context,null);
    }

    public AutoMonitorPlayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public void setUp(String url, Map<String, String> headers) {
        mUrl = url;
        mHeaders = headers;
        mDataType = DATA_TYPE_URL;
        //设置源后自动开始
        start();
    }

    /**
     * 播放本地文件
     * @param filePath 视频文件完整目录,如:/sdcard/test.mp4
     */
    public void setUp(String filePath) {
        mFilePath = filePath;
        mDataType = DATA_TYPE_FILE;
        //设置源后自动开始
        start();
    }

    /**
     * 是否允许最大化时横屏,默认不允许
     * @param allow true允许,false不允许
     */
    public void setLandscape(boolean allow) {
        mLandscape = allow;
    }

    public void setViewType(int type) {
        if(type <VIEW_TYPE_TEXTUREVIEW || type > VIEW_TYPE_SURFACEVIEW) {
            LogUtil.d("view type not found：" + type);
            return;
        }
        mViewType = type;
    }

    /**
     * SurfaceView在home后切回来会黑屏,因为SurfaceView不显示时会执行surfaceDestroyed
     * 但问题是，再次切回前台，不会自动调用surfaceCreated，所以需要手动重新初始化
     */
    public void resetSurfaceView() {
        releaseSurfaceView();
        start();
    }

    /**
     * 释放资源，重置状态
     */
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if(mViewType == VIEW_TYPE_TEXTUREVIEW) {
            mContainer.removeView(mTextureView);
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
        }else if(mViewType == VIEW_TYPE_SURFACEVIEW) {
            mContainer.removeView(mSurfaceView);
            if(mSurfaceView != null && mSurfaceView.getHolder() != null) {
                mSurfaceView.getHolder().removeCallback(this);
            }
            mSurfaceView = null;
            mHolder = null;
        }

        mCurrentState = STATE_IDLE;
        mPlayerState = PLAYER_NORMAL;
    }

    private void releaseSurfaceView() {
        mContainer.removeView(mSurfaceView);
        if(mSurfaceView != null && mSurfaceView.getHolder() != null) {
            mSurfaceView.getHolder().removeCallback(this);
        }
        mSurfaceView = null;
        mHolder = null;
    }

    /**
     * 设置播放器类型,目前有原生MediaPlay和ijk两种
     *
     * @param playerType IjkPlayer or MediaPlayer.
     */
    public void setPlayerType(int playerType) {
        mPlayerType = playerType;
    }

    public void setTinyWindowType(int type) {
        mTinyWindowType = type;
    }

    public void enterFullScreen() {
        if (isFullScreen()) {
            return;
        }else if(isTinyWindow()) {
            //需要从老的parent节点把mContainer移除
            //如果是normal，因为全屏时parent并没有改变，所以不需要这一步
            exitTinyWindow();
        }else if(isNormal()) {
            exitNormalScreen();
        }
        // 隐藏ActionBar、状态栏，并横屏
        NiceUtil.hideActionBar(mContext);
        if(mLandscape) {
            NiceUtil.scanForActivity(mContext)
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

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
    private boolean exitFullScreen() {
        if (isFullScreen()) {
            NiceUtil.showActionBar(mContext);
            if(mLandscape) {
                NiceUtil.scanForActivity(mContext)
                        .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);
            contentView.removeView(mContainer);

            return true;
        }
        return false;
    }

    public boolean enterNormalScreen() {
        if(isNormal()) {
            return false;
        }else if(isFullScreen()){
            exitFullScreen();
        }else if(isTinyWindow()) {
            exitTinyWindow();
        }

        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mContainer, params);
        LogUtil.d("PLAYER_NORMAL");
        mPlayerState = PLAYER_NORMAL;
        return true;
    }

    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    public void enterTinyWindow() {
        if (isTinyWindow()) {
            return;
        }
        if(isNormal()) {
            exitNormalScreen();
        }else if(isFullScreen()){
            exitFullScreen();
        }

        if(mTinyWindowType==WINDOW_TYPE_VIEW) {
            //父节点变了，需要从老的父节点处删掉容器
            this.removeView(mContainer);

            //android.R.id.content是MainActivity布局最外面的一层FrameLayout
            ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                    .findViewById(android.R.id.content);

            FrameLayout.LayoutParams params = mConfig.getTinyWindowLayoutParams();
            contentView.addView(mContainer, params);
        }else {
            mContainer.setDragable(false);
            mTinyDialg = createTinyWindow();
            mTinyDialg.addContentView(mContainer,new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            //mTinyDialg.getWindow().getWindowManager().addView(mContainer,new FrameLayout.LayoutParams(
            //        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            mTinyDialg.show();
        }
        mPlayerState = PLAYER_TINY_WINDOW;
        LogUtil.d("PLAYER_TINY_WINDOW");
    }

    /**
     * 退出小窗口播放
     */
    private boolean exitTinyWindow() {
        if (mPlayerState == PLAYER_TINY_WINDOW) {
            if(mTinyWindowType==WINDOW_TYPE_VIEW) {
                ViewGroup contentView = (ViewGroup) NiceUtil.scanForActivity(mContext)
                        .findViewById(android.R.id.content);
                contentView.removeView(mContainer);
            }else {
                //把Dialog中的child view删掉,
                ((ViewGroup)mContainer.getParent()).removeView(mContainer);
                //这样不行
                //mTinyDialg.getWindow().getWindowManager().removeView(mContainer);
                if(mTinyDialg!=null && mTinyDialg.isShowing()) {

                    mTinyDialg.dismiss();
                }
                mTinyDialg = null;
                mContainer.setDragable(true);
            }

            return true;
        }
        return false;
    }

    /**
     * 退出普通播放
     */
    private boolean exitNormalScreen() {
        if (isNormal()) {
            this.removeView(mContainer);
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

    public void pauseOrStart() {
        if(mPlayerType == PLAYER_TYPE_IJK || mPlayerType == PLAYER_TYPE_NATIVE) {
            if(mCurrentState == STATE_PLAYING) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }else if(mCurrentState == STATE_PAUSED) {
                mMediaPlayer.start();
                mCurrentState = STATE_PLAYING;
            }
        }
    }

    /**
     * 设置SurfaceView回调,同时初始化SurfaceView。用于使用外部播放器时。
     * @param cb
     */
    public void setSurfaceViewCallBack(ISurfaceViewCallBack cb) {
        mSurfaceViewCallback = cb;
        initSurfaceView();
        addSurfaceView();
    }

    public void setDragConfig(IDragConfig config) {
        mConfig = config;
    }


    protected void onDoubleClick() {
        mConfig.onDoubleClick();
    }

    protected void onSingalClick() {
        mConfig.onSingalClick();
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

    private Dialog createTinyWindow() {
        TinyDialog dialog = new TinyDialog(mContext);
        dialog.setWindowParam(mConfig.getTinyWindowLayoutParams());

        return dialog;
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
            if(mViewType == VIEW_TYPE_TEXTUREVIEW) {
                initTextureView();
                addTextureView();
            }else if(mViewType == VIEW_TYPE_SURFACEVIEW) {
                initSurfaceView();
                addSurfaceView();
            }
        }
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            switch (mPlayerType) {
                case PLAYER_TYPE_NATIVE:
                    mMediaPlayer = new AndroidMediaPlayer();
                    break;
                case PLAYER_TYPE_IJK:
                    mMediaPlayer = new IjkMediaPlayer();
                    break;
                case PLAYER_TYPE_NULL:
                    return;
                default:
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

    private void initSurfaceView() {
        if (mHolder == null) {
            mSurfaceView = new SurfaceView(mContext);
            mSurfaceView.getHolder().addCallback(this);
        }
    }

    private void addSurfaceView() {
        mContainer.removeView(mSurfaceView);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.addView(mSurfaceView, 0, params);
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
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return mSurfaceTexture == null;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtil.d("surfaceCreated");
        //这里的holder就是mSurfaceView.getHolder()
        if(mPlayerType == PLAYER_TYPE_NULL) {
            if(checkCallBack()) {
                mSurfaceViewCallback.surfaceCreated(holder);
            }
            return;
        }

        //不同于TextureView,每次切换窗口都会destro和create一次,所以不能在这openMediaPlayer
        if(mHolder == null) {
            mHolder = holder;
            openMediaPlayer();
        }else {
            mMediaPlayer.setDisplay(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtil.d("surfaceChanged");
        if(mPlayerType == PLAYER_TYPE_NULL) {
            if(checkCallBack()) {
                mSurfaceViewCallback.surfaceChanged(holder,format,width,height);
            }
            return;
        }
        //不需要
        //mMediaPlayer.setDisplay(holder);
        //mSurfaceView.setZOrderOnTop(false);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.d("surfaceDestroyed");
        if(mPlayerType == PLAYER_TYPE_NULL) {
            if(checkCallBack()) {
                mSurfaceViewCallback.surfaceDestroyed(holder);
            }
            return;
        }
        //mMediaPlayer.pause();
        //releaseSurfaceView();
    }

    private boolean checkCallBack() {
        if(mSurfaceViewCallback==null) {
            LogUtil.d("mSurfaceViewCallback is null");
            return false;
        }
        return true;
    }

    private void openMediaPlayer() {
        try {
            if(mDataType == DATA_TYPE_URL) {
                mMediaPlayer.setDataSource(mContext.getApplicationContext(), Uri.parse(mUrl), mHeaders);
            }else if(mDataType == DATA_TYPE_FILE) {
                mMediaPlayer.setDataSource(mFilePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            LogUtil.e("打开播放器发生错误", e);
        }

        if(mViewType == VIEW_TYPE_TEXTUREVIEW) {
            mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
        }else if(mViewType == VIEW_TYPE_SURFACEVIEW) {
            mMediaPlayer.setSurface(mSurfaceView.getHolder().getSurface());
        }

        mMediaPlayer.prepareAsync();
        mCurrentState = STATE_PREPARING;
        LogUtil.d("STATE_PREPARING");
    }

    /**
     * 自定义和可拖动/全屏的窗口相关的配置
     */
    public interface IDragConfig {
        void onSingalClick();
        void onDoubleClick();
        FrameLayout.LayoutParams getTinyWindowLayoutParams();
    }

    public interface ISurfaceViewCallBack {
        void surfaceCreated(SurfaceHolder holder);
        void surfaceChanged(SurfaceHolder holder, int format, int width, int height);
        void surfaceDestroyed(SurfaceHolder holder);
    }

    /**
     * 默认实现类
     */
    class DefaultDragConfig implements AutoMonitorPlayer.IDragConfig{

        @Override
        public void onSingalClick() {
            pauseOrStart();
        }

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
