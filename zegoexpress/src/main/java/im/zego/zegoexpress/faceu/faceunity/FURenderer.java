package im.zego.zegoexpress.faceu.faceunity;


import static com.faceunity.wrapper.faceunity.FU_ADM_FLAG_FLIP_X;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;


import com.faceunity.wrapper.faceunity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.zego.zegoexpress.faceu.faceunity.entity.Effect;
import im.zego.zegoexpress.faceu.faceunity.entity.FaceMakeup;
import im.zego.zegoexpress.faceu.faceunity.entity.Filter;
import im.zego.zegoexpress.faceu.faceunity.entity.MakeupItem;
import im.zego.zegoexpress.faceu.faceunity.utils.Constant;


/**
 * 一个基于Faceunity Nama SDK的简单封装，方便简单集成，理论上简单需求的步骤：
 * <p>
 * 1.通过OnEffectSelectedListener在UI上进行交互
 * 2.合理调用FURenderer构造函数
 * 3.对应的时机调用onSurfaceCreated和onSurfaceDestroyed
 * 4.处理图像时调用onDrawFrame
 */
public class FURenderer implements OnFUControlListener {
    private static final String TAG = FURenderer.class.getSimpleName();

    public static final int FU_ADM_FLAG_EXTERNAL_OES_TEXTURE = faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE;

    private Context mContext;

    /**
     * 目录assets下的 *.bundle为程序的数据文件。
     * 其中 v3.bundle：人脸识别数据文件，缺少该文件会导致系统初始化失败；
     * face_beautification.bundle：美颜和美型相关的数据文件；
     * anim_model.bundle：优化表情跟踪功能所需要加载的动画数据文件；适用于使用Animoji和avatar功能的用户，如果不是，可不加载
     * ardata_ex.bundle：高精度模式的三维张量数据文件。适用于换脸功能，如果没用该功能可不加载
     * fxaa.bundle：3D绘制抗锯齿数据文件。加载后，会使得3D绘制效果更加平滑。
     * 目录effects下是我们打包签名好的道具
     */
    public static final String BUNDLE_V3 = "v3.bundle";
    public static final String BUNDLE_ANIMOJI_3D = "fxaa.bundle";
    // 美颜 bundle
    public static final String BUNDLE_FACE_BEAUTIFICATION = "face_beautification.bundle";
    // 轻美妆 bundle
    public static final String BUNDLE_LIGHT_MAKEUP = "light_makeup.bundle";


    private volatile static float mFilterLevel = 1.0f;//滤镜强度
    private volatile static float mSkinDetect = 1.0f;//肤色检测开关
    private volatile static float mHeavyBlur = 0.0f;//重度磨皮开关
    private volatile static float mBlurLevel = 0.7f;//磨皮程度
    private volatile static float mColorLevel = 0.3f;//美白
    private volatile static float mRedLevel = 0.3f;//红润
    private volatile static float mEyeBright = 0.0f;//亮眼
    private volatile static float mToothWhiten = 0.0f;//美牙
    private volatile static float mFaceShape = BeautificationParams.FACE_SHAPE_CUSTOM;//脸型
    private volatile static float mFaceShapeLevel = 1.0f;//程度
    private volatile static float mCheekThinning = 0f;//瘦脸
    private volatile static float mCheekV = 0.5f;//V脸
    private volatile static float mCheekNarrow = 0f;//窄脸
    private volatile static float mCheekSmall = 0f;//小脸
    private volatile static float mEyeEnlarging = 0.4f;//大眼
    private volatile static float mIntensityChin = 0.3f;//下巴
    private volatile static float mIntensityForehead = 0.3f;//额头
    private volatile static float mIntensityMouth = 0.4f;//嘴形
    private volatile static float mIntensityNose = 0.5f;//瘦鼻
    private volatile static float mChangeFrames = 0f;//渐变帧数
    // 默认滤镜，原图效果
    private volatile static String sFilterName = new Filter(Filter.Key.ORIGIN).filterName();

    private int mFrameId = 0;

    // 句柄索引
    private static final int ITEM_ARRAYS_FACE_BEAUTY_INDEX = 0;
    public static final int ITEM_ARRAYS_EFFECT_INDEX = 1;
    private static final int ITEM_ARRAYS_LIGHT_MAKEUP_INDEX = 2;

    // 句柄数量
    private static final int ITEM_ARRAYS_COUNT = 3;//13;

    //美颜和其他道具的handle数组
    private volatile int[] mItemsArray = new int[ITEM_ARRAYS_COUNT];
    //用于和异步加载道具的线程交互
    private HandlerThread mFuItemHandlerThread;
    private Handler mFuItemHandler;

    private boolean isNeedFaceBeauty = true;
    private volatile Effect mDefaultEffect;//默认道具（同步加载）
    private boolean mIsCreateEGLContext; //是否需要手动创建EGLContext
    private int mInputTextureType = 0; //输入的图像texture类型，Camera提供的默认为EXTERNAL OES
    private int mInputImageFormat = 0;
    //美颜和滤镜的默认参数
    private volatile boolean isNeedUpdateFaceBeauty = true;

    private volatile int mInputImageOrientation = 270;
    private volatile int mInputPropOrientation = 270;//道具方向（针对全屏道具）
    private volatile int mIsInputImage = 0;//输入的是否是图片
    private volatile int mCurrentCameraType = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private volatile int mMaxFaces = 4; //同时识别的最大人脸

    // 轻美妆妆容集合
    private Map<Integer, MakeupItem> mLightMakeupItemMap = new ConcurrentHashMap<>(64);

    private float[] landmarksData = new float[150];
    private float[] rotationData = new float[4];
    private float[] faceRectData = new float[4];
    private double[] mLipStickColor;

    private List<Runnable> mEventQueue;
    private OnBundleLoadCompleteListener mOnBundleLoadCompleteListener;
    private static boolean mIsInited;
    private volatile int mDefaultOrientation = 90;
    private volatile int mRotMode = 1;

    /**
     * 创建及初始化faceunity相应的资源
     */
    public void onSurfaceCreated() {
        Log.e(TAG, "onSurfaceCreated");
        onSurfaceDestroyed();

        mEventQueue = Collections.synchronizedList(new ArrayList<Runnable>());

        mFuItemHandlerThread = new HandlerThread("FUItemHandlerThread");
        mFuItemHandlerThread.start();
        mFuItemHandler = new FUItemHandler(mFuItemHandlerThread.getLooper());

        /**
         * fuCreateEGLContext 创建OpenGL环境
         * 适用于没OpenGL环境时调用
         * 如果调用了fuCreateEGLContext，在销毁时需要调用fuReleaseEGLContext
         */
        if (mIsCreateEGLContext)
            faceunity.fuCreateEGLContext();

        mFrameId = 0;
        /**
         *fuSetExpressionCalibration 控制表情校准功能的开关及不同模式，参数为0时关闭表情校准，2为被动校准。
         * 被动校准：该种模式下会在整个用户使用过程中逐渐进行表情校准，用户对该过程没有明显感觉。
         *
         * 优化后的SDK只支持被动校准功能，即fuSetExpressionCalibration接口只支持0（关闭）或2（被动校准）这两个数字，设置为1时将不再有效果。
         */
        faceunity.fuSetExpressionCalibration(2);
        faceunity.fuSetMaxFaces(mMaxFaces);//设置多脸，目前最多支持8人。

        if (isNeedFaceBeauty) {
            mFuItemHandler.sendEmptyMessage(ITEM_ARRAYS_FACE_BEAUTY_INDEX);
        }

        // 异步加载默认道具
        if (mDefaultEffect != null) {
            mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, ITEM_ARRAYS_EFFECT_INDEX, mDefaultEffect));
        }

        // 恢复质感美颜的参数值
        if (mLightMakeupItemMap.size() > 0) {
            Set<Map.Entry<Integer, MakeupItem>> entries = mLightMakeupItemMap.entrySet();
            for (Map.Entry<Integer, MakeupItem> entry : entries) {
                MakeupItem makeupItem = entry.getValue();
                onLightMakeupSelected(makeupItem, makeupItem.getLevel());
            }
        }

        // 设置同步
        setAsyncTrackFace(false);
    }

    /**
     * 获取faceunity sdk 版本库
     */
    public static String getVersion() {
        return faceunity.fuGetVersion();
    }

    /**
     * 获取证书相关的权限码
     */
    public static int getModuleCode(int index) {
        return faceunity.fuGetModuleCode(index);
    }

    /**
     * FURenderer构造函数
     */
    private FURenderer(Context context, boolean isCreateEGLContext) {
        this.mContext = context;
        this.mIsCreateEGLContext = isCreateEGLContext;
    }

    /**
     * 销毁faceunity相关的资源
     */
    public void onSurfaceDestroyed() {
        Log.e(TAG, "onSurfaceDestroyed");
        if (mFuItemHandlerThread != null) {
            mFuItemHandlerThread.quitSafely();
            mFuItemHandlerThread = null;
            mFuItemHandler = null;
        }
        if (mEventQueue != null) {
            mEventQueue.clear();
        }

        int lightMakeupIndex = mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX];
        if (lightMakeupIndex > 0) {
            Set<Integer> makeupTypes = mLightMakeupItemMap.keySet();
            for (Integer makeupType : makeupTypes) {
                faceunity.fuDeleteTexForItem(lightMakeupIndex, getFaceMakeupKeyByType(makeupType));
            }
        }

        mFrameId = 0;
        isNeedUpdateFaceBeauty = true;
        Arrays.fill(mItemsArray, 0);
        faceunity.fuDestroyAllItems();
        faceunity.fuOnDeviceLost();
        faceunity.fuDone();
        if (mIsCreateEGLContext)
            faceunity.fuReleaseEGLContext();
    }

    /**
     * 单输入接口(fuRenderToRgbaImage)
     *
     * @param img rgba数据
     * @param w
     * @param h
     * @return
     */
    public int onDrawRgbaFrame(byte[] img, int w, int h) {
        if (img == null || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputImageFormat;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuRenderToRgbaImage(img, w, h, mFrameId++, mItemsArray, flags);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 单输入接口(fuRenderToI420Image)
     *
     * @param img i420数据
     * @param w
     * @param h
     * @return
     */
    public int onDrawI420Frame(byte[] img, int w, int h) {
        if (img == null || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputImageFormat;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuRenderToI420Image(img, w, h, mFrameId++, mItemsArray, flags);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 单输入接口(fuRenderToNV21Image)，自定义画面数据需要回写到的byte[]
     *
     * @param img         NV21数据
     * @param w
     * @param h
     * @param readBackImg 画面数据需要回写到的byte[]
     * @param readBackW
     * @param readBackH
     * @return
     */
    public int onDrawFrame(byte[] img, int w, int h, byte[] readBackImg, int readBackW, int readBackH) {
        if (img == null || w <= 0 || h <= 0 || readBackImg == null || readBackW <= 0 || readBackH <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputImageFormat;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuRenderToNV21Image(img, w, h, mFrameId++, mItemsArray, flags,
                readBackW, readBackH, readBackImg);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 双输入接口(fuDualInputToTexture)(处理后的画面数据并不会回写到数组)，由于省去相应的数据拷贝性能相对最优，推荐使用。
     *
     * @param img RGBA32数据
     * @param tex 纹理ID
     * @param w
     * @param h
     * @return
     */
    public int onDrawFrame(byte[] img, int tex, int w, int h) {
        if (tex <= 0 || img == null || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputTextureType | faceunity.FU_ADM_FLAG_RGBA_BUFFER;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuDualInputToTexture(img, tex, flags, w, h, mFrameId++, mItemsArray);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 双输入接口(fuDualInputToTexture)，自定义画面数据需要回写到的byte[]
     *
     * @param img         NV21数据
     * @param tex         纹理ID
     * @param w
     * @param h
     * @param readBackImg 画面数据需要回写到的byte[]
     * @param readBackW
     * @param readBackH
     * @return
     */
    public int onDrawFrame(byte[] img, int tex, int w, int h, byte[] readBackImg, int readBackW, int readBackH) {
        if (tex <= 0 || img == null || w <= 0 || h <= 0 || readBackImg == null || readBackW <= 0 || readBackH <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputTextureType | mInputImageFormat;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuDualInputToTexture(img, tex, flags, w, h, mFrameId++, mItemsArray,
                readBackW, readBackH, readBackImg);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 单输入接口(fuRenderToTexture)
     *
     * @param tex 纹理ID
     * @param w
     * @param h
     * @return
     */
    public int onDrawFrame(int tex, int w, int h) {
        if (tex <= 0 || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputTextureType;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuRenderToTexture(tex, w, h, mFrameId++, mItemsArray, flags);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 单输入接口(fuRenderToTexture)
     *
     * @param tex 纹理ID
     * @param w
     * @param h
     * @return
     */
    public int onDrawOesFrame(int tex, int w, int h) {
        if (tex <= 0 || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = FURenderer.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE;
        if (mCurrentCameraType != Camera.CameraInfo.CAMERA_FACING_FRONT)
            flags |= FU_ADM_FLAG_FLIP_X;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuRenderToTexture(tex, w, h, mFrameId++, mItemsArray, flags);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    /**
     * 单美颜接口(fuBeautifyImage)，将输入的图像数据，送入SDK流水线进行全图美化，并输出处理之后的图像数据。
     * 该接口仅执行图像层面的美化处 理（包括滤镜、美肤），不执行人脸跟踪及所有人脸相关的操作（如美型）。
     * 由于功能集中，相比 fuDualInputToTexture 接口执行美颜道具，该接口所需计算更少，执行效率更高。
     *
     * @param tex 纹理ID
     * @param w
     * @param h
     * @return
     */
    public int onDrawFrameBeautify(int tex, int w, int h) {
        if (tex <= 0 || w <= 0 || h <= 0) {
            Log.e(TAG, "onDrawFrame data null");
            return 0;
        }
        prepareDrawFrame();

        int flags = mInputTextureType;

        if (mNeedBenchmark)
            mFuCallStartTime = System.nanoTime();
        int fuTex = faceunity.fuBeautifyImage(tex, flags, w, h, mFrameId++, mItemsArray);
        if (mNeedBenchmark)
            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
        return fuTex;
    }

    public float[] getRotationData() {
        Arrays.fill(rotationData, 0.0f);
        faceunity.fuGetFaceInfo(0, "rotation", rotationData);
        return rotationData;
    }

    /**
     * 全局加载相应的底层数据包，应用使用期间只需要初始化一次
     * 初始化系统环境，加载系统数据，并进行网络鉴权。必须在调用SDK其他接口前执行，否则会引发崩溃。
     */
    public static void initFURenderer(Context context) {
        if (mIsInited) {
            return;
        }
        try {
            //获取faceunity SDK版本信息
            Log.e(TAG, "fu sdk version " + faceunity.fuGetVersion());
            long startTime = System.currentTimeMillis();
            /**
             * fuSetup faceunity初始化
             * 其中 v3.bundle：人脸识别数据文件，缺少该文件会导致系统初始化失败；
             *      authpack：用于鉴权证书内存数组。
             * 首先调用完成后再调用其他FU API
             */
            InputStream v3 = context.getAssets().open(BUNDLE_V3);
            byte[] v3Data = new byte[v3.available()];
            v3.read(v3Data);
            v3.close();
            if (null == authpack.A()) {
                return;
            } else {
                faceunity.fuSetup(v3Data, authpack.A());
            }

            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, "setup fu sdk finish: " + duration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "initFURenderer error", e);
        }
        mIsInited = true;
    }

    /**
     * 获取 landmark 点位
     *
     * @param faceId
     * @return 调用时不要修改返回值，如需修改或传入 Nama 接口，请拷贝一份
     */
    public float[] getLandmarksData(int faceId) {
        int isTracking = faceunity.fuIsTracking();
        Arrays.fill(landmarksData, 0.0f);
        if (isTracking > 0) {
            faceunity.fuGetFaceInfo(faceId, "landmarks", landmarksData);
        }
        return landmarksData;
    }

    public float[] getFaceRectData(int i) {
        Arrays.fill(faceRectData, 0.0f);
        faceunity.fuGetFaceInfo(i, "face_rect", faceRectData);
        return faceRectData;
    }

    //--------------------------------------对外可使用的接口----------------------------------------

    /**
     * 类似GLSurfaceView的queueEvent机制
     */
    public void queueEvent(Runnable r) {
        if (mEventQueue == null)
            return;
        mEventQueue.add(r);
    }

    /**
     * 类似GLSurfaceView的queueEvent机制,保护在快速切换界面时进行的操作是当前界面的加载操作
     */
    private void queueEventItemHandle(Runnable r) {
        if (mFuItemHandlerThread == null || Thread.currentThread().getId() != mFuItemHandlerThread.getId())
            return;
        queueEvent(r);
    }

    /**
     * 设置人脸跟踪异步
     *
     * @param isAsync 是否异步
     */
    public void setAsyncTrackFace(final boolean isAsync) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "setAsyncTrackFace " + isAsync);
                faceunity.fuSetAsyncTrackFace(isAsync ? 1 : 0);
            }
        });
    }

    /**
     * 设置需要识别的人脸个数
     *
     * @param maxFaces
     */
    public void setMaxFaces(final int maxFaces) {
        if (mMaxFaces != maxFaces && maxFaces > 0) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mMaxFaces = maxFaces;
                    faceunity.fuSetMaxFaces(mMaxFaces);
                }
            });
        }
    }

    /**
     * 每帧处理画面时被调用
     */
    private void prepareDrawFrame() {
        Log.d(TAG, "prepareDrawFrame: ");
        //计算FPS等数据
        benchmarkFPS();

        //获取人脸是否识别，并调用回调接口
        int isTracking = faceunity.fuIsTracking();
        Log.e(TAG, "prepareDrawFrame: " + isTracking);
        if (mOnTrackingStatusChangedListener != null && mTrackingStatus != isTracking) {
            mOnTrackingStatusChangedListener.onTrackingStatusChanged(mTrackingStatus = isTracking);
        }

        //获取faceunity错误信息，并调用回调接口
        int error = faceunity.fuGetSystemError();
        Log.e(TAG, "fuGetSystemErrorString " + faceunity.fuGetSystemErrorString(error));
        if (error != 0) {
            if (mOnSystemErrorListener != null) {
                mOnSystemErrorListener.onSystemError(faceunity.fuGetSystemErrorString(error));
            }
        }

        //修改美颜参数
        if (isNeedUpdateFaceBeauty && mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX] > 0) {
            //filter_name 滤镜名称
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.FILTER_NAME, sFilterName);
            //filter_level 滤镜强度 范围0~1 SDK默认为 1
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.FILTER_LEVEL, mFilterLevel);

            //skin_detect 精准美肤（肤色检测开关） 0:关闭 1:开启 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.SKIN_DETECT, mSkinDetect);
            //heavy_blur 磨皮类型 0:清晰磨皮 1:重度磨皮 SDK默认为 1
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.HEAVY_BLUR, mHeavyBlur);
            //blur_level 磨皮 范围0~6 SDK默认为 6
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.BLUR_LEVEL, 6 * mBlurLevel);
            //nonskin_blur_scale 肤色检测之后，非肤色区域的融合程度，范围0-1，SDK默认为0.45
//            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.NONSKIN_BLUR_SCALE, 0);
            //color_level 美白 范围0~1 SDK默认为 0.2
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.COLOR_LEVEL, mColorLevel);
            //red_level 红润 范围0~1 SDK默认为 0.5
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.RED_LEVEL, mRedLevel);
            //eye_bright 亮眼 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.EYE_BRIGHT, mEyeBright);
            //tooth_whiten 美牙 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.TOOTH_WHITEN, mToothWhiten);

            //face_shape_level 美型程度 范围0~1 SDK默认为1
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.FACE_SHAPE_LEVEL, mFaceShapeLevel);
            //face_shape 脸型 0：女神 1：网红，2：自然，3：默认，4：精细变形，5 用户自定义，SDK默认为 3
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.FACE_SHAPE, mFaceShape);
            //eye_enlarging 大眼 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.EYE_ENLARGING, mEyeEnlarging);
            //cheek_thinning 瘦脸 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.CHEEK_THINNING, mCheekThinning);
            //cheek_narrow 窄脸 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.CHEEK_NARROW, mCheekNarrow);
            //cheek_small 小脸 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.CHEEK_SMALL, mCheekSmall);
            //cheek_v V脸 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.CHEEK_V, mCheekV);
            //intensity_nose 鼻子 范围0~1 SDK默认为 0
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.INTENSITY_NOSE, mIntensityNose);
            //intensity_chin 下巴 范围0~1 SDK默认为 0.5    大于0.5变大，小于0.5变小
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.INTENSITY_CHIN, mIntensityChin);
            //intensity_forehead 额头 范围0~1 SDK默认为 0.5    大于0.5变大，小于0.5变小
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.INTENSITY_FOREHEAD, mIntensityForehead);
            //intensity_mouth 嘴型 范围0~1 SDK默认为 0.5   大于0.5变大，小于0.5变小
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.INTENSITY_MOUTH, mIntensityMouth);
            //change_frame 变形渐变调整参数，0 渐变关闭，大于 0 渐变开启，值为渐变需要的帧数
            faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX], BeautificationParams.CHANGE_FRAMES, mChangeFrames);
            isNeedUpdateFaceBeauty = false;
        }

        //queueEvent的Runnable在此处被调用
        while (!mEventQueue.isEmpty()) {
            mEventQueue.remove(0).run();
        }
    }

    public void setDefaultEffect(Effect defaultEffect) {
        mDefaultEffect = defaultEffect;
    }

    //--------------------------------------美颜参数与道具回调----------------------------------------

    @Override
    public void onEffectSelected(Effect effectItemName) {
        mDefaultEffect = effectItemName;
        if (mDefaultEffect == null)
            return;
        if (mFuItemHandler == null) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mFuItemHandler.removeMessages(ITEM_ARRAYS_EFFECT_INDEX);
                    mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, ITEM_ARRAYS_EFFECT_INDEX, mDefaultEffect));
                }
            });
        } else {
            mFuItemHandler.removeMessages(ITEM_ARRAYS_EFFECT_INDEX);
            mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, ITEM_ARRAYS_EFFECT_INDEX, mDefaultEffect));
        }
    }

    @Override
    public void onFilterLevelSelected(float progress) {
        isNeedUpdateFaceBeauty = true;
        mFilterLevel = progress;
    }

    @Override
    public void onFilterNameSelected(String filterName) {
        isNeedUpdateFaceBeauty = true;
        sFilterName = filterName;
    }

    @Override
    public void onSkinDetectSelected(float isOpen) {
        isNeedUpdateFaceBeauty = true;
        mSkinDetect = isOpen;
    }

    @Override
    public void onHeavyBlurSelected(float isOpen) {
        isNeedUpdateFaceBeauty = true;
        mHeavyBlur = isOpen;
    }

    @Override
    public void onBlurLevelSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mBlurLevel = level;
    }

    @Override
    public void onColorLevelSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mColorLevel = level;
    }


    @Override
    public void onRedLevelSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mRedLevel = level;
    }

    @Override
    public void onEyeBrightSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mEyeBright = level;
    }

    @Override
    public void onToothWhitenSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mToothWhiten = level;
    }

    @Override
    public void onEyeEnlargeSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mEyeEnlarging = level;
    }

    @Override
    public void onCheekThinningSelected(float level) {
        mCheekThinning = level;
        isNeedUpdateFaceBeauty = true;
    }

    @Override
    public void onCheekNarrowSelected(float level) {
        // 窄脸参数上限为0.5
        mCheekNarrow = level / 2;
        isNeedUpdateFaceBeauty = true;
    }

    @Override
    public void onCheekSmallSelected(float level) {
        // 小脸参数上限为0.5
        mCheekSmall = level / 2;
        isNeedUpdateFaceBeauty = true;
    }

    @Override
    public void onCheekVSelected(float level) {
        mCheekV = level;
        isNeedUpdateFaceBeauty = true;
    }

    @Override
    public void onIntensityChinSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mIntensityChin = level;
    }

    @Override
    public void onIntensityForeheadSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mIntensityForehead = level;
    }

    @Override
    public void onIntensityNoseSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mIntensityNose = level;
    }

    @Override
    public void onIntensityMouthSelected(float level) {
        isNeedUpdateFaceBeauty = true;
        mIntensityMouth = level;
    }

    @Override
    public void onLightMakeupBatchSelected(List<MakeupItem> makeupItems) {
        Set<Integer> keySet = mLightMakeupItemMap.keySet();
        for (final Integer integer : keySet) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX], getMakeupIntensityKeyByType(integer), 0);
                }
            });
        }
        mLightMakeupItemMap.clear();

        if (makeupItems != null && makeupItems.size() > 0) {
            for (int i = 0, size = makeupItems.size(); i < size; i++) {
                MakeupItem makeupItem = makeupItems.get(i);
                onLightMakeupSelected(makeupItem, makeupItem.getLevel());
            }
        } /*else {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX], "is_makeup_on", 0);
                }
            });
        }*/
    }

    private void onLightMakeupSelected(final MakeupItem makeupItem, final float level) {
        int type = makeupItem.getType();
        MakeupItem mp = mLightMakeupItemMap.get(type);
        if (mp != null) {
            mp.setLevel(level);
        } /*else {
            // 复制一份
            mLightMakeupItemMap.put(type, makeupItem.cloneSelf());
        }*/
        if (mFuItemHandler == null) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, ITEM_ARRAYS_LIGHT_MAKEUP_INDEX, makeupItem));
                }
            });
        } else {
            mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, ITEM_ARRAYS_LIGHT_MAKEUP_INDEX, makeupItem));
        }
    }

    @Override
    public void onLightMakeupOverallLevelChanged(final float level) {
        Set<Map.Entry<Integer, MakeupItem>> entries = mLightMakeupItemMap.entrySet();
        for (final Map.Entry<Integer, MakeupItem> entry : entries) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX], getMakeupIntensityKeyByType(entry.getKey()), level);
                    entry.getValue().setLevel(level);
                }
            });
        }
    }


    /**
     * 从 assets 中读取颜色数据
     *
     * @param colorAssetPath
     * @return rgba 数组
     * @throws Exception
     */
    private double[] readMakeupLipColors(String colorAssetPath) throws IOException, JSONException {
        if (TextUtils.isEmpty(colorAssetPath)) {
            return null;
        }
        InputStream is = null;
        try {
            is = mContext.getAssets().open(colorAssetPath);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            String s = new String(bytes);
            JSONObject jsonObject = new JSONObject(s);
            JSONArray jsonArray = jsonObject.optJSONArray("rgba");
            double[] colors = new double[4];
            for (int i = 0, length = jsonArray.length(); i < length; i++) {
                colors[i] = jsonArray.optDouble(i);
            }
            return colors;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    //--------------------------------------IsTracking（人脸识别回调相关定义）----------------------------------------

    private int mTrackingStatus = 0;

    public interface OnTrackingStatusChangedListener {
        void onTrackingStatusChanged(int status);
    }

    private OnTrackingStatusChangedListener mOnTrackingStatusChangedListener;

    //--------------------------------------FaceUnitySystemError（faceunity错误信息回调相关定义）----------------------------------------

    public interface OnSystemErrorListener {
        void onSystemError(String error);
    }

    private OnSystemErrorListener mOnSystemErrorListener;


    //--------------------------------------OnBundleLoadCompleteListener（faceunity道具加载完成）----------------------------------------

    public void setOnBundleLoadCompleteListener(OnBundleLoadCompleteListener onBundleLoadCompleteListener) {
        mOnBundleLoadCompleteListener = onBundleLoadCompleteListener;
    }

    /**
     * fuCreateItemFromPackage 加载道具
     *
     * @param bundlePath 道具 bundle 的路径
     * @return 大于 0 时加载成功
     */
    private int loadItem(String bundlePath) {
        int item = 0;
        try {
            if (!TextUtils.isEmpty(bundlePath)) {
                InputStream is = bundlePath.startsWith(Constant.filePath) ? new FileInputStream(new File(bundlePath)) : mContext.getAssets().open(bundlePath);
                byte[] itemData = new byte[is.available()];
                int len = is.read(itemData);
                is.close();
                item = faceunity.fuCreateItemFromPackage(itemData);
                Log.e(TAG, "bundle path: " + bundlePath + ", length: " + len + "Byte, handle:" + item);
            }
        } catch (IOException e) {
            Log.e(TAG, "loadItem error ", e);
        }
        return item;
    }


    //--------------------------------------FPS（FPS相关定义）----------------------------------------

    private static final float NANO_IN_ONE_MILLI_SECOND = 1000000.0f;
    private static final float TIME = 5f;
    private int mCurrentFrameCnt = 0;
    private long mLastOneHundredFrameTimeStamp = 0;
    private long mOneHundredFrameFUTime = 0;
    private boolean mNeedBenchmark = true;
    private long mFuCallStartTime = 0;

    private OnFUDebugListener mOnFUDebugListener;

    public interface OnFUDebugListener {
        void onFpsChange(double fps, double renderTime);
    }

    private void benchmarkFPS() {
        if (!mNeedBenchmark)
            return;
        if (++mCurrentFrameCnt == TIME) {
            mCurrentFrameCnt = 0;
            long tmp = System.nanoTime();
            double fps = (1000.0f * NANO_IN_ONE_MILLI_SECOND / ((tmp - mLastOneHundredFrameTimeStamp) / TIME));
            mLastOneHundredFrameTimeStamp = tmp;
            double renderTime = mOneHundredFrameFUTime / TIME / NANO_IN_ONE_MILLI_SECOND;
            mOneHundredFrameFUTime = 0;

            if (mOnFUDebugListener != null) {
                mOnFUDebugListener.onFpsChange(fps, renderTime);
            }
        }
    }

    //--------------------------------------道具（异步加载道具）----------------------------------------

    /**
     * 加载美妆资源数据
     *
     * @param path
     * @return bytes, width and height
     * @throws IOException
     */
    private Pair<byte[], Pair<Integer, Integer>> loadMakeupResource(String path) throws IOException {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        InputStream is = null;
        try {
            is = mContext.getAssets().open(path);
            BitmapFactory.Options options = new BitmapFactory.Options();
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            int bmpByteCount = bitmap.getByteCount();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            byte[] bitmapBytes = new byte[bmpByteCount];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bitmapBytes);
            bitmap.copyPixelsToBuffer(byteBuffer);
            return Pair.create(bitmapBytes, Pair.create(width, height));
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public interface OnBundleLoadCompleteListener {
        /**
         * bundle 加载完成
         *
         * @param what
         */
        void onBundleLoadComplete(int what);
    }

    //    @IntDef(value = {HAIR_NORMAL, HAIR_GRADIENT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HairType {
    }

    /**
     * 设置对道具设置相应的参数
     *
     * @param itemHandle
     */
    private void updateEffectItemParams(Effect effect, final int itemHandle) {
        if (effect == null || itemHandle == 0)
            return;
        if (mIsInputImage == 1) {
            faceunity.fuItemSetParam(itemHandle, "isAndroid", 0.0);
        } else {
            faceunity.fuItemSetParam(itemHandle, "isAndroid", 1.0);
        }

        int effectType = effect.effectType();
        if (effectType == Effect.EFFECT_TYPE_NORMAL) {
            //rotationAngle 参数是用于旋转普通道具
            faceunity.fuItemSetParam(itemHandle, "rotationAngle", 360 - mInputPropOrientation);
        }
//        if (effectType == Effect.EFFECT_TYPE_BACKGROUND) {
//            //计算角度（全屏背景分割，第一次未识别人脸）
//            faceunity.fuSetDefaultRotationMode((360 - mInputImageOrientation) / 90);
//        }
        int back = mCurrentCameraType == Camera.CameraInfo.CAMERA_FACING_BACK ? 1 : 0;

        setMaxFaces(effect.maxFace());
    }

    private String getFaceMakeupKeyByType(int type) {
        switch (type) {
            case FaceMakeup.FACE_MAKEUP_TYPE_LIPSTICK:
                return "tex_lip";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_LINER:
                return "tex_eyeLiner";
            case FaceMakeup.FACE_MAKEUP_TYPE_BLUSHER:
                return "tex_blusher";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_PUPIL:
                return "tex_pupil";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYEBROW:
                return "tex_brow";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_SHADOW:
                return "tex_eye";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYELASH:
                return "tex_eyeLash";
            default:
                return "";
        }
    }

    private String getMakeupIntensityKeyByType(int type) {
        switch (type) {
            case FaceMakeup.FACE_MAKEUP_TYPE_LIPSTICK:
                return "makeup_intensity_lip";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_LINER:
                return "makeup_intensity_eyeLiner";
            case FaceMakeup.FACE_MAKEUP_TYPE_BLUSHER:
                return "makeup_intensity_blusher";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_PUPIL:
                return "makeup_intensity_pupil";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYEBROW:
                return "makeup_intensity_eyeBrow";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYE_SHADOW:
                return "makeup_intensity_eye";
            case FaceMakeup.FACE_MAKEUP_TYPE_EYELASH:
                return "makeup_intensity_eyelash";
            default:
                return "";
        }
    }

    // --------------------------------------------------------------------------------------------

    /**
     * 美颜道具参数，包含红润、美白、清晰磨皮、重度磨皮、滤镜、变形、亮眼、美牙功能。
     */
    static class BeautificationParams {
        // 滤镜名称，默认 origin
        public static final String FILTER_NAME = "filter_name";
        // 滤镜程度，0-1，默认 1
        public static final String FILTER_LEVEL = "filter_level";
        // 美白程度，0-1，默认 0.2
        public static final String COLOR_LEVEL = "color_level";
        // 红润程度，0-1，默认 0.5
        public static final String RED_LEVEL = "red_level";
        // 磨皮程度，0-6，默认 6
        public static final String BLUR_LEVEL = "blur_level";
        // 肤色检测开关，0 代表关，1 代表开，默认 0
        public static final String SKIN_DETECT = "skin_detect";
        // 肤色检测开启后，非肤色区域的融合程度，0-1，默认 0.45
        public static final String NONSKIN_BLUR_SCALE = "nonskin_blur_scale";
        // 磨皮类型，0 代表清晰磨皮，1 代表重度磨皮，默认 1
        public static final String HEAVY_BLUR = "heavy_blur";
        // 变形选择，0 代表女神，1 网红，2 自然，3 预设，4，精细变形，5 用户自定义，默认 3
        public static final String FACE_SHAPE = "face_shape";
        // 变形程度，0-1，默认 1
        public static final String FACE_SHAPE_LEVEL = "face_shape_level";
        // 大眼程度，0-1，默认 0.5
        public static final String EYE_ENLARGING = "eye_enlarging";
        // 瘦脸程度，0-1，默认 0
        public static final String CHEEK_THINNING = "cheek_thinning";
        // 窄脸程度，0-1，默认 0
        public static final String CHEEK_NARROW = "cheek_narrow";
        // 小脸程度，0-1，默认 0
        public static final String CHEEK_SMALL = "cheek_small";
        // V脸程度，0-1，默认 0
        public static final String CHEEK_V = "cheek_v";
        // 瘦鼻程度，0-1，默认 0
        public static final String INTENSITY_NOSE = "intensity_nose";
        // 嘴巴调整程度，0-1，默认 0.5
        public static final String INTENSITY_MOUTH = "intensity_mouth";
        // 额头调整程度，0-1，默认 0.5
        public static final String INTENSITY_FOREHEAD = "intensity_forehead";
        // 下巴调整程度，0-1，默认 0.5
        public static final String INTENSITY_CHIN = "intensity_chin";
        // 变形渐变调整参数，0 渐变关闭，大于 0 渐变开启，值为渐变需要的帧数
        public static final String CHANGE_FRAMES = "change_frames";
        // 亮眼程度，0-1，默认 1
        public static final String EYE_BRIGHT = "eye_bright";
        // 美牙程度，0-1，默认 1
        public static final String TOOTH_WHITEN = "tooth_whiten";
        // 美颜参数全局开关，0 代表关，1 代表开
        public static final String IS_BEAUTY_ON = "is_beauty_on";

        // 女神
        public static final int FACE_SHAPE_GODDESS = 0;
        // 网红
        public static final int FACE_SHAPE_NET_RED = 1;
        // 自然
        public static final int FACE_SHAPE_NATURE = 2;
        // 默认
        public static final int FACE_SHAPE_DEFAULT = 3;
        // 精细变形
        public static final int FACE_SHAPE_CUSTOM = 4;
    }

    /*----------------------------------Builder---------------------------------------*/

    /**
     * FURenderer Builder
     */
    public static class Builder {

        private boolean createEGLContext = true;
        private Effect defaultEffect;
        private int maxFaces = 1;
        private Context context;
        private int inputTextureType = 0;
        private int inputImageFormat = 0;
        private int inputImageRotation = 270;
        private int inputPropRotation = 270;
        private int isIputImage = 0;
        private boolean isNeedFaceBeauty = true;
        private int currentCameraType = Camera.CameraInfo.CAMERA_FACING_FRONT;
        private OnBundleLoadCompleteListener onBundleLoadCompleteListener;
        private OnFUDebugListener onFUDebugListener;
        private OnTrackingStatusChangedListener onTrackingStatusChangedListener;
        private OnSystemErrorListener onSystemErrorListener;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        /**
         * 是否需要自己创建EGLContext
         *
         * @param createEGLContext
         * @return
         */
        public Builder createEGLContext(boolean createEGLContext) {
            this.createEGLContext = createEGLContext;
            return this;
        }

        /**
         * 是否需要立即加载道具
         *
         * @param defaultEffect
         * @return
         */
        public Builder defaultEffect(Effect defaultEffect) {
            this.defaultEffect = defaultEffect;
            return this;
        }


        /**
         * 输入的是否是图片
         *
         * @param isIputImage
         * @return
         */
        public Builder inputIsImage(int isIputImage) {
            this.isIputImage = isIputImage;
            return this;
        }

        /**
         * 识别最大人脸数
         *
         * @param maxFaces
         * @return
         */
        public Builder maxFaces(int maxFaces) {
            this.maxFaces = maxFaces;
            return this;
        }

        /**
         * 传入纹理的类型（传入数据没有纹理则无需调用）
         * camera OES纹理：1
         * 普通2D纹理：2
         *
         * @param textureType
         * @return
         */
        public Builder inputTextureType(int textureType) {
            this.inputTextureType = textureType;
            return this;
        }

        /**
         * 输入的byte[]数据类型
         *
         * @param inputImageFormat
         * @return
         */
        public Builder inputImageFormat(int inputImageFormat) {
            this.inputImageFormat = inputImageFormat;
            return this;
        }

        /**
         * 输入的画面数据方向
         *
         * @param inputImageRotation
         * @return
         */
        public Builder inputImageOrientation(int inputImageRotation) {
            this.inputImageRotation = inputImageRotation;
            return this;
        }

        /**
         * 道具方向
         *
         * @param inputPropRotation
         * @return
         */
        public Builder inputPropOrientation(int inputPropRotation) {
            this.inputPropRotation = inputPropRotation;
            return this;
        }

        /**
         * 是否需要美颜效果
         *
         * @param needFaceBeauty
         * @return
         */
        public Builder setNeedFaceBeauty(boolean needFaceBeauty) {
            isNeedFaceBeauty = needFaceBeauty;
            return this;
        }

        /**
         * 当前的摄像头（前后置摄像头）
         *
         * @param cameraType
         * @return
         */
        public Builder setCurrentCameraType(int cameraType) {
            currentCameraType = cameraType;
            return this;
        }

        /**
         * 设置debug数据回调
         *
         * @param onFUDebugListener
         * @return
         */
        public Builder setOnFUDebugListener(OnFUDebugListener onFUDebugListener) {
            this.onFUDebugListener = onFUDebugListener;
            return this;
        }

        /**
         * 设置是否检查到人脸的回调
         *
         * @param onTrackingStatusChangedListener
         * @return
         */
        public Builder setOnTrackingStatusChangedListener(OnTrackingStatusChangedListener onTrackingStatusChangedListener) {
            this.onTrackingStatusChangedListener = onTrackingStatusChangedListener;
            return this;
        }

        /**
         * 设置bundle加载完成回调
         *
         * @param onBundleLoadCompleteListener
         * @return
         */
        public Builder setOnBundleLoadCompleteListener(OnBundleLoadCompleteListener onBundleLoadCompleteListener) {
            this.onBundleLoadCompleteListener = onBundleLoadCompleteListener;
            return this;
        }


        /**
         * 设置SDK使用错误回调
         *
         * @param onSystemErrorListener
         * @return
         */
        public Builder setOnSystemErrorListener(OnSystemErrorListener onSystemErrorListener) {
            this.onSystemErrorListener = onSystemErrorListener;
            return this;
        }

        public FURenderer build() {
            FURenderer fuRenderer = new FURenderer(context, createEGLContext);
            fuRenderer.mMaxFaces = maxFaces;
            fuRenderer.mInputTextureType = inputTextureType;
            fuRenderer.mInputImageFormat = inputImageFormat;
            fuRenderer.mInputImageOrientation = inputImageRotation;
            fuRenderer.mInputPropOrientation = inputPropRotation;
            fuRenderer.mIsInputImage = isIputImage;
            fuRenderer.mDefaultEffect = defaultEffect;
            fuRenderer.isNeedFaceBeauty = isNeedFaceBeauty;
            fuRenderer.mCurrentCameraType = currentCameraType;

            fuRenderer.mOnFUDebugListener = onFUDebugListener;
            fuRenderer.mOnTrackingStatusChangedListener = onTrackingStatusChangedListener;
            fuRenderer.mOnSystemErrorListener = onSystemErrorListener;
            fuRenderer.mOnBundleLoadCompleteListener = onBundleLoadCompleteListener;
            return fuRenderer;
        }
    }

//--------------------------------------Builder----------------------------------------

    class FUItemHandler extends Handler {

        FUItemHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                //加载普通道具
                case ITEM_ARRAYS_EFFECT_INDEX: {
                    final Effect effect = (Effect) msg.obj;
                    if (effect == null) {
                        return;
                    }
                    boolean isNone = effect.effectType() == Effect.EFFECT_TYPE_NONE;
                    final int itemEffect = isNone ? 0 : loadItem(effect.path());
                    if (!isNone && itemEffect <= 0) {
                        Log.w(TAG, "create effect item failed: " + itemEffect);
                        return;
                    }
                    queueEventItemHandle(new Runnable() {
                        @Override
                        public void run() {
                            if (mItemsArray[ITEM_ARRAYS_EFFECT_INDEX] > 0) {
                                faceunity.fuDestroyItem(mItemsArray[ITEM_ARRAYS_EFFECT_INDEX]);
                                mItemsArray[ITEM_ARRAYS_EFFECT_INDEX] = 0;
                            }

                            if (itemEffect > 0) {
                                updateEffectItemParams(effect, itemEffect);
                            }
                            mItemsArray[ITEM_ARRAYS_EFFECT_INDEX] = itemEffect;
                        }
                    });
                }
                break;
                //加载美颜bundle
                case ITEM_ARRAYS_FACE_BEAUTY_INDEX: {
                    final int itemBeauty = loadItem(BUNDLE_FACE_BEAUTIFICATION);
                    if (itemBeauty <= 0) {
                        Log.w(TAG, "load face beauty item failed: " + itemBeauty);
                        return;
                    }
                    queueEventItemHandle(new Runnable() {
                        @Override
                        public void run() {
                            mItemsArray[ITEM_ARRAYS_FACE_BEAUTY_INDEX] = itemBeauty;
                            isNeedUpdateFaceBeauty = true;
                        }
                    });
                }
                break;
                // 加载轻美妆bundle
                case ITEM_ARRAYS_LIGHT_MAKEUP_INDEX: {
                    final MakeupItem makeupItem = (MakeupItem) msg.obj;
                    if (makeupItem == null) {
                        return;
                    }
                    String path = makeupItem.getPath();
                    if (!TextUtils.isEmpty(path)) {
                        if (mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX] <= 0) {
                            int itemLightMakeup = loadItem(BUNDLE_LIGHT_MAKEUP);
                            if (itemLightMakeup <= 0) {
                                Log.w(TAG, "create light makeup item failed: " + itemLightMakeup);
                                return;
                            }
                            mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX] = itemLightMakeup;
                        }
                        final int itemHandle = mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX];
                        try {
                            byte[] itemBytes = null;
                            int width = 0;
                            int height = 0;
                            if (makeupItem.getType() == FaceMakeup.FACE_MAKEUP_TYPE_LIPSTICK) {
                                mLipStickColor = readMakeupLipColors(path);
                            } else {
                                Pair<byte[], Pair<Integer, Integer>> pair = loadMakeupResource(path);
                                if (pair == null) {
                                    return;
                                }
                                itemBytes = pair.first;
                                width = pair.second.first;
                                height = pair.second.second;
                            }
                            final byte[] makeupItemBytes = itemBytes;
                            final int finalHeight = height;
                            final int finalWidth = width;
                            queueEventItemHandle(new Runnable() {
                                @Override
                                public void run() {
                                    String key = getFaceMakeupKeyByType(makeupItem.getType());
                                    faceunity.fuItemSetParam(itemHandle, "is_makeup_on", 1);
                                    faceunity.fuItemSetParam(itemHandle, "makeup_intensity", 1);
                                    faceunity.fuItemSetParam(itemHandle, "reverse_alpha", 1);
                                    if (mLipStickColor != null) {
                                        if (makeupItem.getType() == FaceMakeup.FACE_MAKEUP_TYPE_LIPSTICK) {
                                            faceunity.fuItemSetParam(itemHandle, "makeup_lip_color", mLipStickColor);
                                            faceunity.fuItemSetParam(itemHandle, "makeup_lip_mask", 1);
                                        }
                                    } else {
                                        faceunity.fuItemSetParam(itemHandle, "makeup_intensity_lip", 0);
                                    }
                                    if (makeupItemBytes != null) {
                                        faceunity.fuCreateTexForItem(itemHandle, key, makeupItemBytes, finalWidth, finalHeight);
                                    }
                                    faceunity.fuItemSetParam(itemHandle, getMakeupIntensityKeyByType(
                                            makeupItem.getType()), makeupItem.getLevel());
                                }
                            });
                        } catch (IOException | JSONException e) {
                            Log.e(TAG, "load makeup resource error", e);
                        }
                    } else {
                        // 卸某个妆
                        if (mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX] > 0) {
                            queueEventItemHandle(new Runnable() {
                                @Override
                                public void run() {
                                    faceunity.fuItemSetParam(mItemsArray[ITEM_ARRAYS_LIGHT_MAKEUP_INDEX],
                                            getMakeupIntensityKeyByType(makeupItem.getType()), 0);
                                }
                            });
                        }
                    }
                }
                break;
                default:
            }
            if (mOnBundleLoadCompleteListener != null) {
                mOnBundleLoadCompleteListener.onBundleLoadComplete(msg.what);
            }
        }
    }
}
