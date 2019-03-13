package com.example.mdabb.dogclassifier;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.util.Arrays;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "AndroidCameraApi";
    private TextureView textureView;
    private final Object lock = new Object();
    private boolean runClassifier = false;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    protected TextView predictions_tv;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraManager cameraManager;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageClassifier classifier;
    private boolean buttonState = false;
    long avgLat=0;
    ImageReader img_reader;
    int frame = 1;

    private ImageReader.OnImageAvailableListener processImage = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            predictions_tv.setText("im processing an image !! frame " + frame);
            frame++;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setKeepScreenOn(true);
        // Try to load model.
        String model = getIntent().getStringExtra(MainActivity.MODEL);
        try {
            classifier = new ImageClassifierFloat(this, model);

        } catch (IOException e) {
            Log.e(TAG, "Failed to load", e);
            classifier = null;
        }
        if (classifier != null) {
            classifier.setNumThreads(Runtime.getRuntime().availableProcessors());
        }
        textureView.setSurfaceTextureListener(textureListener);
        Button takePictureButton = (Button) findViewById(R.id.capture_button);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (buttonState == false) {
                    closeCamera();
                    buttonState = true;
                    ((Button) v).setText("Resume");
                } else {
                    openCamera();
                    buttonState = false;
                    ((Button) v).setText("Pause");

                }
            }
        });
        img_reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
        predictions_tv = (TextView) findViewById(R.id.predictions_textView);
        assert predictions_tv != null;

    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            openCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void startBackgroundThreads() {
        synchronized (lock) {
            runClassifier = true;
        }
        // update preview thread
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThreads() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
        }
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }
            synchronized (lock) {
                runClassifier = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            img_reader.setOnImageAvailableListener(processImage, null);
            captureRequestBuilder.addTarget(surface);
//            captureRequestBuilder.addTarget(img_reader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(CameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    long startTime = 0, endTime = 0;
                    String s = "Waiting";

                    synchronized (lock) {
                        if (runClassifier) {
                            startTime = SystemClock.uptimeMillis();
                            s = classifyFrame();
                            endTime = SystemClock.uptimeMillis();
                        }
                    }
                    if (buttonState == false) {
                        avgLat=(avgLat*(frame-1)+(endTime - startTime))/frame;
                        predictions_tv.setText("frame " + frame + " Avg. delay "
                                + Long.toString(avgLat) + " ms" + "\n" + s);
                        frame++;
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private String classifyFrame() {
        if (classifier == null || this == null || cameraDevice == null) {
            // It's important to not call showToast every frame, or else the app will starve and
            // hang. updateActiveModel() already puts a error message up with showToast.
            // showToast("Uninitialized Classifier or invalid context.");
            return null;
        }
        SpannableStringBuilder textToShow = new SpannableStringBuilder();
        Bitmap bitmap = textureView.getBitmap(classifier.getImageSizeX(), classifier.getImageSizeY());
        classifier.classifyFrame(bitmap, textToShow);
        bitmap.recycle();
        return textToShow.toString();
    }

    private void closeCamera() {
        try {
            cameraCaptureSessions.abortCaptures();
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(CameraActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThreads();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThreads();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "onBackPressed");
        synchronized (lock) {
            runClassifier = false;
        }
        super.onBackPressed();
        this.finish();
    }

    @Override
    public void onDestroy() {
        stopBackgroundThreads();
        if (classifier != null) {
            classifier.close();
        }
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        super.onDestroy();
    }

    protected ImageReader createImageReader() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        ImageReader reader = null;
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        } catch (CameraAccessException e) {
//            Log.d("couldn't create an image reader");
        }

        return reader;
    }


}
