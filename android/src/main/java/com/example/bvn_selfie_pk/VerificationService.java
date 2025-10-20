package com.example.bvn_selfie_pk;

import static androidx.core.content.ContextCompat.getMainExecutor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ExperimentalImageCaptureOutputFormat;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class VerificationService implements ImageAnalysis.Analyzer {

    private final SurfaceTexture surfaceTexture;
    private final Activity pluginActivity;
    ProcessCameraProvider processCameraProvider;
    private ListenableFuture<ProcessCameraProvider> cameraProvider;
    ImageCapture imageCapture;
    private final long textureId;
    private final BVNCallbacks callbacks;
    int counter = 0;
    private int step = 1;

    VerificationService(Activity activity, SurfaceTexture surfaceTexture, BVNCallbacks callbacks, long textureId) {
        this.surfaceTexture = surfaceTexture;
        this.callbacks = callbacks;
        this.textureId = textureId;
        this.pluginActivity = activity;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            startCamera();
        } else {
            callbacks.onError("device not supported");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(pluginActivity.getApplicationContext());

        cameraProvider.addListener(() -> {
            try {
                processCameraProvider = cameraProvider.get();
                startCamerax(processCameraProvider);
            } catch (ExecutionException | InterruptedException ex) {
                ex.printStackTrace();
            }
        }, getMainExecutor(pluginActivity));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startCamerax(ProcessCameraProvider cameraProvider) {

        int height = getDisplay().heightPixels;
        int width = getDisplay().widthPixels;

        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder().
                requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();


        var resolutionSelector =
                new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                                new AspectRatioStrategy(
                                        AspectRatio.RATIO_16_9,
                                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                                )
                        )
                        .build();

        Preview preview = new Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build();

        preview.setSurfaceProvider(request -> {
            surfaceTexture.setDefaultBufferSize(width, height);
            Surface surface = new Surface(surfaceTexture);
            request.provideSurface(surface, getMainExecutor(pluginActivity), result -> {
            });
        });


//        resolutionSelector = new ResolutionSelector.Builder()
//                .setResolutionStrategy(
//                        new ResolutionStrategy(
//                                new Size(width, height),  // Preferred resolution
//                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
//                        )
//                )
//                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(getMainExecutor(pluginActivity), this);
        //image capture used case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .build();
        cameraProvider.bindToLifecycle((LifecycleOwner) pluginActivity, cameraSelector, preview, imageCapture, imageAnalysis);
        callbacks.onTextTureCreated(textureId);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void analyze(@NonNull ImageProxy image) {
        Image mediaImage = image.getImage();
        if (mediaImage != null) {
            InputImage inputImage =
                    InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
            FaceDetectorOptions realTimeOpts =
                    new FaceDetectorOptions.Builder()
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .setMinFaceSize((float) 0.1)
                            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                            .build();
            FaceDetector detector = FaceDetection.getClient(realTimeOpts);
            detector.process(inputImage)
                    .addOnSuccessListener(
                            faces -> {
                                processFacials(faces);
                                image.close();
                            })
                    .addOnFailureListener(
                            e -> {
                                System.out.println("failed");
                                image.close();
                            });

        }

    }

    private DisplayMetrics getDisplay() {
        var wm = (WindowManager) pluginActivity.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics;
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void processFacials(List<Face> faces) {

        if (faces.isEmpty()) {

            callbacks.gestureCallBack(Helps.facialGesture, Helps.NO_FACE_DETECTED);
            return;
        }
        for (Face face : faces) {
            callbacks.gestureCallBack(Helps.facialGesture, Helps.FACE_DETECTED);

            // Step 1: Head rotation
            if (step == 1) {
                callbacks.actionCallBack(Helps.ROTATE_HEAD);
                if (rotateHead(face)) {
                    counter += 1;
                    if (counter >= 4) {
                        step = 2;
                        callbacks.actionCallBack(Helps.SMILE_AND_OPEN_ACTION);
                    }
                }
                callbacks.onProgressChanged(counter);
                return;
            }

            // Step 2: Smile detection
            if (step == 2) {
                callbacks.actionCallBack(Helps.SMILE_AND_OPEN_ACTION);

                if (checkSmileAndBlink(face)) {
                    if (step == 2) {
                        step = 3;
                        callbacks.actionCallBack(Helps.NEUTRAL_FACE_ACTION);
                    }
                }
            }


            // Step 3: Neutral face detection
            if (step == 3) {
                callbacks.actionCallBack(Helps.NEUTRAL_FACE_ACTION);
                if (checkNeutralFace(face)) {
                    step = -1;
                    takePhoto();
                }
                return;
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void takePhoto() {
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(saveFile()).build();
        imageCapture.takePicture(outputFileOptions, Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        assert outputFileResults.getSavedUri() != null;
                        callbacks.onImageCapture(outputFileResults.getSavedUri().getPath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        callbacks.onError(exception.toString());
                    }
                });
    }

    private boolean checkNeutralFace(Face face) {
        if (face.getSmilingProbability() != null) {
            float smileProb = face.getSmilingProbability();
            float rightEyeOpenProb = face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : 0;
            float leftEyeOpenProb = face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : 0;

            return smileProb <= 0.3 && leftEyeOpenProb > 0.5 && rightEyeOpenProb > 0.5;
        }
        return false;
    }

    private boolean checkSmileAndBlink(Face face) {
        var smileProb = face.getSmilingProbability();
        var rightEyeOpenProb = face.getRightEyeOpenProbability();
        var leftEyeOpenProb = face.getLeftEyeOpenProbability();

        if (leftEyeOpenProb != null && rightEyeOpenProb != null && smileProb != null) {
            return smileProb > 0.7 && leftEyeOpenProb > 0.5 && rightEyeOpenProb > 0.5;
        }
        return false;
    }

    private boolean rotateHead(Face face) {
        float degreesZ = face.getHeadEulerAngleZ();
        return degreesZ > 3;
    }

    private File saveFile() {

        File directory = pluginActivity.getCacheDir();
        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        String path = directory.getAbsolutePath() + "/" + timestamp + ".jpeg";
        return new File(path);
    }

    public void dispose() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            processCameraProvider.unbindAll();
        }
    }
}
