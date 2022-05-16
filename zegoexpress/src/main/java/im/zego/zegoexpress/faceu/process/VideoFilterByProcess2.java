package im.zego.zegoexpress.faceu.process;


import android.util.Log;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.faceu.faceunity.FURenderer;
/**
 * VideoFilterByProcess2
 * 通过Zego视频前处理，用户可以获取到Zego SDK采集到的摄像头数据。用户后续将数据塞给FaceUnity处理，最终将处理后的数据塞回Zego SDK进行推流。
 * 采用GL_TEXTURE_2D方式传递数据
 */
/**
 * VideoFilterByProcess2
 * Through the Zego video pre-processing, users can obtain the camera data collected by the Zego SDK. The user then stuffs the data to FaceUnity for processing, and finally stuffs the processed data back to Zego SDK for publishing stream.
 *Use GL_TEXTURE_2D to transfer data
 */
public class VideoFilterByProcess2 extends IZegoCustomVideoProcessHandler {

    // faceunity 美颜处理类
    private FURenderer mFURenderer;
    private static final String TAG = "VideoFilterByProcess2";

    public VideoFilterByProcess2(FURenderer fuRenderer){
        this.mFURenderer = fuRenderer;
        // 创建及初始化 faceunity 相应的资源
    }

    /**
     * 释放资源
     *
     */

    public void stopAndDeAllocate() {
        // 销毁 faceunity 相关的资源
        mFURenderer.onSurfaceDestroyed();
    }

    @Override
    public void onCapturedUnprocessedTextureData(int textureID, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {

        int fuTextureId = mFURenderer.onDrawFrame(textureID, width, height);
        ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(fuTextureId,width,height,referenceTimeMillisecond);
    }

    @Override
    public void onStart(ZegoPublishChannel zegoPublishChannel) {
        super.onStart(zegoPublishChannel);
        Log.d(TAG, "debug>>> onStart: ");
        mFURenderer.onSurfaceCreated();

    }

    @Override
    public void onStop(ZegoPublishChannel zegoPublishChannel) {
        super.onStop(zegoPublishChannel);
        Log.d(TAG, "debug>>> onStop: ");
    }
}
