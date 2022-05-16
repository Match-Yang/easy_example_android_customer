package im.zego.expresssample.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSON;

import org.json.JSONObject;

import im.zego.expresssample.databinding.ActivityMainBinding;
import im.zego.zegoexpress.ExpressManager;
import im.zego.zegoexpress.ExpressManager.ExpressManagerHandler;
import im.zego.zegoexpress.ZegoDeviceUpdateType;
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoRoomExtraInfo;
import im.zego.zegoexpress.entity.ZegoUser;
import im.zego.zegoexpress.faceu.faceunity.view.BeautyControlView;

import java.util.ArrayList;
import java.util.Set;

public class CallActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "CallActivity";
    private BeautyControlView beautyControlView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        beautyControlView = binding.fuBeautyControl;
        ExpressManager.getInstance().setBeautyControlView(beautyControlView);

        binding.logoutRoom.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ExpressManager.getInstance().leaveRoom();
                finish();
            }
        });

        binding.switchBtn.setSelected(true);
        binding.switchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean selected = v.isSelected();
                v.setSelected(!selected);
                ExpressManager.getInstance().switchFrontCamera(!selected);
            }
        });

        binding.cameraBtn.setSelected(true);
        binding.cameraBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean selected = v.isSelected();
                v.setSelected(!selected);
                ExpressManager.getInstance().enableCamera(!selected);
            }
        });
        binding.micBtn.setSelected(true);
        binding.micBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean selected = v.isSelected();
                v.setSelected(!selected);
                ExpressManager.getInstance().enableMic(!selected);
            }
        });

        ExpressManager.getInstance().setLocalVideoView(binding.smallViewTexture);
        String name = ExpressManager.getInstance().getLocalParticipant().name;
        binding.smallViewName.setText(name);
        ExpressManager.getInstance().setExpressHandler(new ExpressManagerHandler() {
            @Override
            public void onRoomUserUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoUser> userList) {
                if (updateType == ZegoUpdateType.ADD) {
                    for (int i = 0; i < userList.size(); i++) {
                        ZegoUser user = userList.get(i);
                        TextureView remoteTexture = binding.fullViewTexture;
                        binding.fullViewName.setText(user.userName);
                        setRemoteViewVisible(true);
                        ExpressManager.getInstance().setRemoteVideoView(user.userID, remoteTexture);
                    }
                } else {
//                    setRemoteViewVisible(false);
                }
            }

            @Override
            public void onRoomUserDeviceUpdate(ZegoDeviceUpdateType updateType, String userID, String roomID) {
                Log.d(TAG,
                        "onRoomUserDeviceUpdate() called with: updateType = [" + updateType + "], userID = [" + userID
                                + "], roomID = [" + roomID + "]");
                if (updateType == ZegoDeviceUpdateType.cameraOpen) {
                    setRemoteViewVisible(true);
                } else if (updateType == ZegoDeviceUpdateType.cameraClose) {
//                    setRemoteViewVisible(false);
                }
            }

            @Override
            public void onRoomTokenWillExpire(String roomID, int remainTimeInSecond) {

            }
            @Override
            public void onRoomExtraInfoUpdate(String roomID, ArrayList<ZegoRoomExtraInfo> roomExtraInfoList) {
            }

            @Override
            public void onRoomStateChanged(String roomID, ZegoRoomStateChangedReason reason, int errorCode,
                                           JSONObject extendedData) {
            }

            @Override
            public void onFaceDetected(Boolean hasFace) {
                String tip = "";
                if (hasFace){
                    tip = "Detect face!";
                }else{
                    tip = "Don't detect face!";
                }
                Toast.makeText(CallActivity.this,tip,Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void setRemoteViewVisible(boolean visible) {
        binding.fullViewTexture.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        ExpressManager.getInstance().leaveRoom();
        finish();
    }
}