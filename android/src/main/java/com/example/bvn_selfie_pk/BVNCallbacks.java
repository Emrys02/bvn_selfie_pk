package com.example.bvn_selfie_pk;

public interface BVNCallbacks {
    void onTextTureCreated(long textureId);
    void gestureCallBack(String methodName,int id);
    void actionCallBack(int action);
    void onProgressChanged(int count);
    void onImageCapture(String imagePath);
    void onError(String error);

}
