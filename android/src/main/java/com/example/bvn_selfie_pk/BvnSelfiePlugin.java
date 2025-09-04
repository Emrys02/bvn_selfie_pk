package com.example.bvn_selfie_pk;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.TextureRegistry;


/**
 * BvnSelfiePlugin
 */
public class BvnSelfiePlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener, BVNCallbacks {
    private MethodChannel channel;
    private Activity flutterActivity;
    private SurfaceTexture surfaceTexture;
    private FlutterPluginBinding flutterBinding;
    private VerificationService verificationService;
    TextureRegistry.SurfaceTextureEntry entry;

    final String[] storge_permissions = {
            Manifest.permission.CAMERA
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    final String[] storge_permissions_33 = {
            Manifest.permission.CAMERA
    };
    public static String[] permission;


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = storge_permissions_33;
        } else {
            permission = storge_permissions;
        }
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "bvn_selfie_pk");
        flutterBinding = flutterPluginBinding;
        channel.setMethodCallHandler(this);
    }

    boolean checkPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int x = 0; x < permission.length; x++) {
                if (flutterActivity.checkSelfPermission(permission[x]) == PackageManager.PERMISSION_DENIED) {
                    flutterActivity.requestPermissions(permission, 1114);
                    return false;
                }
            }
            return true;

        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("start_camera")) {
            result.success("good");
            if (checkPermissionStatus()) {
                initializeService();
                return;
            }
            channel.invokeMethod("permission_not_accepted", new HashMap[]{});
            Toast.makeText(flutterActivity.getApplicationContext(), "Permission Not Granted... Please Accept Permission.", Toast.LENGTH_LONG).show();
            return;
        }
        if (call.method.equals(Helps.takePhoto)) {
            if (surfaceTexture != null) {
                verificationService.takePhoto();
            }
        }
        if (call.method.equals("destroyer")) {
            destroy();
            result.success("good");
            return;

        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onDetachedFromActivity() {
        destroy();
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode,
                                              @NonNull String[] permissions,
                                              @NonNull int[] grantResults) {
        if (requestCode == 1114 && grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(flutterActivity.getApplicationContext(),
                            "Permission Not Yet Granted.",
                            Toast.LENGTH_LONG).show();
                    return true;
                }
            }

            initializeService();
            Toast.makeText(flutterActivity.getApplicationContext(),
                    "Permission Granted.",
                    Toast.LENGTH_LONG).show();
            return true;
        }

        return false; // not our requestCode, let others handle it
    }

    void initializeService() {
        entry = flutterBinding.getTextureRegistry().createSurfaceTexture();
        surfaceTexture = entry.surfaceTexture();
        verificationService = new VerificationService(flutterActivity, surfaceTexture, this, entry.id());
    }

    private void destroy() {

        if (surfaceTexture != null) {
            verificationService.dispose();
            // surfaceTexture.release();
        }
    }

    @Override
    public void onTextTureCreated(String val, long textureId) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("textureId", textureId);
        channel.invokeMethod("showTextureView", hashMap);
    }

    @Override
    public void gestureCallBack(String methodName, int id) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("type", id);
        channel.invokeMethod(methodName, hashMap);
    }

    @Override
    public void actionCallBack(int action) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("action_type", action);
        channel.invokeMethod(Helps.actionGesutre, hashMap);
    }

    @Override
    public void onProgressChanged(int count) {
        flutterActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put("progress", count);
                channel.invokeMethod(Helps.onProgressChange, hashMap);
            }
        });

    }

    @Override
    public void onImageCapture(String imagePath) {
        flutterActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put("imagePath", imagePath);
                channel.invokeMethod(Helps.imageCapture, hashMap);
            }
        });

    }

    @Override
    public void onError(String error) {

        flutterActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put("error", error);
                channel.invokeMethod(Helps.onError, hashMap);
            }
        });

    }
}
