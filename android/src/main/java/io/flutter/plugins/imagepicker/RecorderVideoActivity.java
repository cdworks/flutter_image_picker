package io.flutter.plugins.imagepicker;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.*;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class RecorderVideoActivity extends FragmentActivity implements
        View.OnClickListener, SurfaceHolder.Callback, MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener  {
    private static final String TAG = "RecorderVideoActivity";
    private final static String CLASS_LABEL = "app:RecordActivity";
    private PowerManager.WakeLock mWakeLock;
    private ImageView btnStart;
    private ImageView btnStop;
    private MediaRecorder mediaRecorder;
//    private SurfaceView mRecorderView;// to display video
    String localPath = "";// path to save recorded video
    private Camera mCamera;
    private int previewWidth = 480;
    private int previewHeight = 480;
    private Chronometer chronometer;
    private int frontCamera = 0; // 0 is back camera，1 is front camera
    private Button btn_switch;
    private Button play_btn;
    Camera.Parameters cameraParameters = null;
    private SurfaceHolder mSurfaceHolder;
    int defaultVideoFrameRate = -1;

    private String subPath;

    private RelativeLayout recorderBottom;
    private RelativeLayout finishBottom;

    private OrientationEventListener mOrientationListener;

    private int currentOrientation = 0;
    private int preBtnOrientation = 0;

    private  ImageView focusImageView;
    private  RelativeLayout mainLayout;


    private VideoView mVideoView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if(intent.hasExtra("subPath"))
        {
            subPath = intent.getStringExtra("subPath");
        }

        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;  //手机平放时，检测不到有效的角度
                }



//只检测是否有四个角度的改变
                if (orientation > 335 || orientation < 25) { //0度
                    orientation = 0;
//                } else if (orientation > 65 && orientation < 115) { //90度
//                    orientation = 90;
//                } else if (orientation > 155 && orientation < 205) { //180度
//                    orientation = 180;
                } else if (orientation > 245 && orientation < 295) { //270度
                    orientation = 270;
                } else {
                    return;
                }

                RecorderVideoActivity.this.switchOrientation(orientation);
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            Log.v("video", "Can detect orientation");
            mOrientationListener.enable();
        } else {
            Log.v("video", "Cannot detect orientation");
            mOrientationListener.disable();
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);// no title
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// full screen
        // translucency mode，used in surface view
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.recorder_activity);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                CLASS_LABEL);
        mWakeLock.acquire();



        initViews();
    }



    public void switchOrientation(int orientation)
    {
        if(currentOrientation != orientation)
        {

//            int beginDegree = preBtnOrientation;
//            if(beginDegree == -360 || beginDegree == 360)
//            {
//                beginDegree = 0;
//            }
//            //转换逆时针为顺时针
//            int covertCurrent = currentOrientation > 0 ? 360 - currentOrientation : 0;
//            int convertOrientation = orientation > 0 ? 360 - orientation : 0;
//
//            int offset =  convertOrientation - covertCurrent;
//            int endDegree = beginDegree + offset;
//
//            if(covertCurrent == 0 && convertOrientation == 270)
//            {
//                endDegree = -90;
//            }
//
//            if(convertOrientation == 0 && covertCurrent == 270)
//            {
//                if(beginDegree == 270)
//                {
//                    endDegree = 360;
//                }
//                else if(beginDegree == -90)
//                {
//                    endDegree = 0;
//                }
//
//            }
//
//
//            preBtnOrientation = endDegree;

            if(orientation == 0)
            {
                ObjectAnimator.ofFloat(btn_switch,"rotation",(float) 90,(float) 0)
                        .setDuration(250)
                        .start();
            }
            else
            {
                ObjectAnimator.ofFloat(btn_switch,"rotation",(float) 0,(float) 90)
                        .setDuration(250)
                        .start();
            }



            currentOrientation = orientation;

        }

    }

    private void initViews() {
        btn_switch = findViewById(R.id.switch_btn);
        btn_switch.setOnClickListener(this);
        btn_switch.setVisibility(View.VISIBLE);
//        mRecorderView = findViewById(R.id.mRecorderView);
        btnStart = findViewById(R.id.recorder_start);
        btnStop = findViewById(R.id.recorder_stop);
        mainLayout = findViewById(R.id.mainView);

        mVideoView = findViewById(R.id.videoView);


        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        mSurfaceHolder = mVideoView.getHolder();
        mSurfaceHolder.addCallback(this);
        chronometer = findViewById(R.id.chronometer);
        recorderBottom = findViewById(R.id.bottom_toolbar);
        finishBottom = findViewById(R.id.option_bottom);
        recorderBottom.setVisibility(View.VISIBLE);
        finishBottom.setVisibility(View.INVISIBLE);


        focusImageView = new ImageView(this);
        focusImageView.setImageResource(R.drawable.foucus);
        focusImageView.setVisibility(View.INVISIBLE);
        focusImageView.setLayoutParams(new FrameLayout.LayoutParams(120, 120));
        ((ViewGroup)getWindow().getDecorView()).addView(focusImageView);

        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                {
                    if(mCamera != null || mediaRecorder != null)
                    {
                        float x = motionEvent.getX();
                        float y = motionEvent.getY();
                        focusImageView.setVisibility(View.VISIBLE);
                        focusImageView.setTranslationX(x - 50);
                        focusImageView.setTranslationY(y - 50);

                        final AnimatorSet animatorSet = new AnimatorSet();

                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(focusImageView, "scaleX", 2.f, 1)
                                        .setDuration(250),
                                ObjectAnimator.ofFloat(focusImageView, "scaleY", 2, 1)
                                        .setDuration(250)
                        );
                        animatorSet.start();
                        if(mCamera != null)
                        {
                            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                @Override
                                public void onAutoFocus(boolean success, Camera camera) {
                                    focusImageView.setVisibility(View.INVISIBLE);
                                }
                            });
                        }
                        return true;
                    }

                }

                return false;
            }

        });

        chronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                if(mCamera == null && mVideoView.isPlaying()) {

                    int retainMs = Math.max(0, (int)Math.ceil((mVideoView.getDuration() - mVideoView.getCurrentPosition())/1000.f));

                    int seconds = retainMs % 60;
                    int minutes = (retainMs / 60) % 60;
                    int hours = retainMs / 3600;

                    StringBuilder mFormatBuilder = new StringBuilder();
                    Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
                    mFormatBuilder.setLength(0);
                    chronometer.setText(mFormatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString());
                }
            }
        });

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                //视频加载完成,准备好播放视频的回调
                chronometer.setBase(SystemClock.elapsedRealtime());
                int totalSeconds = (int)Math.ceil(mVideoView.getDuration() / 1000.f);
                StringBuilder mFormatBuilder = new StringBuilder();
                Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
                mFormatBuilder.setLength(0);
                int seconds = totalSeconds % 60;
                int minutes = (totalSeconds / 60) % 60;
                int hours = totalSeconds / 3600;
                chronometer.start();
                chronometer.setText(mFormatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString());
                Log.d("video","onPreparedxxxxxxxx");

            }
        });
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                //视频播放完成后的回调
                Log.d("video","onCompletionxxxxxxxx:" + mVideoView.getDuration());
                chronometer.stop();
                int totalSeconds = (int)Math.ceil(mVideoView.getDuration() / 1000.f);
                StringBuilder mFormatBuilder = new StringBuilder();
                Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
                mFormatBuilder.setLength(0);
                int seconds = totalSeconds % 60;
                int minutes = (totalSeconds / 60) % 60;
                int hours = totalSeconds / 3600;
                chronometer.setText(mFormatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString());
                play_btn.setSelected(false);
                play_btn.setBackground(getResources().getDrawable(R.drawable.play_icon));

            }
        });
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                //异常回调
                Log.d("video","onErrorxxxxxxx");
                return false;//如果方法处理了错误，则为true；否则为false。返回false或根本没有OnErrorListener，将导致调用OnCompletionListener。
            }
        });



        play_btn = findViewById(R.id.play_btn);

        play_btn.setOnClickListener(this);

        Button reRecorderBtn = findViewById(R.id.re_recorder_btn);
        Button useBtn = findViewById(R.id.use_btn);




        Button cancelBtn = findViewById(R.id.cancel_btn);
        cancelBtn.setOnClickListener(this);
        reRecorderBtn.setOnClickListener(this);
        useBtn.setOnClickListener(this);

    }

    public void back(View view) {
        releaseRecorder();
        releaseCamera();
        if(localPath != null)
        {
            File recorderFile = new File(localPath);
            if(recorderFile.exists())
            {
                recorderFile.delete();
            }
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            // keep screen on
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @SuppressLint("NewApi")
    private boolean initCamera() {
        try {
            if (frontCamera == 0) {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            } else {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
            Camera.Parameters camParams = mCamera.getParameters();
            mCamera.lock();
            mSurfaceHolder = mVideoView.getHolder();
            mSurfaceHolder.addCallback(this);
            mCamera.setDisplayOrientation(90);

        } catch (RuntimeException ex) {
            Log.e("video", "init Camera fail " + ex.getMessage());
            return false;
        }
        return true;
    }


    public Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
//        final double ASPECT_TOLERANCE = 0.11;
        double targetRatio = (double) h / w;
        double miniAsp = 1;
        if (sizes == null) {
            return null;
        }
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > miniAsp || size.width * size.height > 518400)
                continue;
            miniAsp = Math.abs(ratio - targetRatio);
            optimalSize = size;
//            if (Math.abs(size.height - targetHeight) < minDiff) {
//
//                minDiff = Math.abs(size.height - targetHeight);
//            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff && size.width * size.height <= 518400) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private void handleSurfaceChanged() {
        if (mCamera == null) {
            finish();
            return;
        }
        boolean hasSupportRate = false;
        List<Integer> supportedPreviewFrameRates = mCamera.getParameters()
                .getSupportedPreviewFrameRates();
        if (supportedPreviewFrameRates != null
                && supportedPreviewFrameRates.size() > 0) {
            Collections.sort(supportedPreviewFrameRates);
            for (int i = 0; i < supportedPreviewFrameRates.size(); i++) {
                int supportRate = supportedPreviewFrameRates.get(i);

                if (supportRate == 15) {
                    hasSupportRate = true;
                }

            }
            if (hasSupportRate) {
                defaultVideoFrameRate = 15;
            } else {
                defaultVideoFrameRate = supportedPreviewFrameRates.get(0);
            }

        }

        // get all resolutions which camera provide
        List<Camera.Size> resolutionList = mCamera.getParameters().getSupportedPreviewSizes();
        if (resolutionList != null && resolutionList.size() > 0) {

            Camera.Size previewSize = null;
            boolean hasSize = false;

//            DisplayMetrics dm = getResources().getDisplayMetrics();

            Collections.sort(resolutionList, new Comparator<Camera.Size>()
            {

                @Override
                public int compare(Camera.Size lhs, Camera.Size rhs) {
                    if(lhs.height!=rhs.height)
                        return rhs.height-lhs.height;
                    else
                        return rhs.width-lhs.width;
                }
            });
            Camera.Size opSize = getOptimalPreviewSize(resolutionList,mVideoView.getWidth(),mVideoView.getHeight());
            if(opSize != null)
            {
                previewWidth = opSize.width;
                previewHeight = opSize.height;
                hasSize = true;
            }



            if(!hasSize) {
                Collections.sort(resolutionList, new Comparator<Camera.Size>()
                {

                    @Override
                    public int compare(Camera.Size lhs, Camera.Size rhs) {
                        if(lhs.height!=rhs.height)
                            return lhs.height-rhs.height;
                        else
                            return lhs.width-rhs.width;
                    }
                });
                // use 60*480 if camera support
                for (int i = 0; i < resolutionList.size(); i++) {
                    Camera.Size size = resolutionList.get(i);
                    if (size != null && size.width == 640 && size.height == 480) {
                        previewSize = size;
                        previewWidth = previewSize.width;
                        previewHeight = previewSize.height;
                        hasSize = true;
                        break;
                    }
                }
            }
            // use medium resolution if camera don't support the above resolution
            if (!hasSize) {
                int mediumResolution = resolutionList.size() / 2;
                if (mediumResolution >= resolutionList.size())
                    mediumResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(mediumResolution);
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;

            }

        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        releaseRecorder();
        releaseCamera();

        finish();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.switch_btn) {
            switchCamera();
        } else if (id == R.id.recorder_start) {// start recording
            if (!startRecording())
                return;
//            Toast.makeText(this, "录像开始", Toast.LENGTH_SHORT).show();
            btn_switch.setVisibility(View.INVISIBLE);
            btnStart.setVisibility(View.INVISIBLE);
            btnStart.setEnabled(false);
            btnStop.setVisibility(View.VISIBLE);
            chronometer.setBase(SystemClock.elapsedRealtime());
//            int hour = (int) ((SystemClock.elapsedRealtime() - chronometer.getBase()) / 1000 / 60);
//            chronometer.setFormat("0"+ hour +":%s");
            chronometer.start();
        } else if (id == R.id.recorder_stop) {
            btnStop.setEnabled(false);
            stopRecording();
            btn_switch.setVisibility(View.VISIBLE);
            chronometer.stop();
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.INVISIBLE);

            recorderBottom.setVisibility(View.INVISIBLE);
            finishBottom.setVisibility(View.VISIBLE);
            play_btn.setSelected(false);
            play_btn.setBackground(getResources().getDrawable(R.drawable.play_icon));


//            new AlertDialog.Builder(this)
//                    .setMessage("是否发送？")
//                    .setPositiveButton("确定",
//                            new DialogInterface.OnClickListener() {
//
//                                @Override
//                                public void onClick(DialogInterface dialog,
//                                                    int which) {
//                                    dialog.dismiss();
//                                    sendVideo(null);
//
//                                }
//                            })
//                    .setNegativeButton("取消",
//                            new DialogInterface.OnClickListener() {
//
//                                @Override
//                                public void onClick(DialogInterface dialog,
//                                                    int which) {
//                                    if (localPath != null) {
//                                        File file = new File(localPath);
//                                        if (file.exists())
//                                            file.delete();
//                                    }
//                                    finish();
//
//                                }
//                            }).setCancelable(false).show();
        }
        else if(id == R.id.cancel_btn)
        {
            back(null);
        }
        else if(id == R.id.use_btn)
        {
            sendVideo(null);
        }
        else if(id == R.id.re_recorder_btn)
        {
//            if(msc == null)
//                msc = new MediaScannerConnection(this,
//                        new MediaScannerConnection.MediaScannerConnectionClient() {
//
//                            @Override
//                            public void onScanCompleted(String path, Uri uri) {
//                                Log.d(TAG, "scanner completed");
//                                msc.disconnect();
//                                progressDialog.dismiss();
////                                mVideoView.setVideoURI(uri);
////                                mVideoView.start();
////                                setResult(RESULT_OK, getIntent().putExtra("uri", uri));
//                                finish();
//                            }
//
//                            @Override
//                            public void onMediaScannerConnected() {
//                                msc.scanFile(localPath, "video/*");
//                            }
//                        });
//
//
//            if(progressDialog == null){
//                progressDialog = new ProgressDialog(this);
//                progressDialog.setMessage("processing...");
//                progressDialog.setCancelable(false);
//            }
//            progressDialog.show();
//            msc.connect();


            btnStart.setEnabled(true);
            btnStop.setEnabled(true);
            recorderBottom.setVisibility(View.VISIBLE);
            finishBottom.setVisibility(View.INVISIBLE);
            if(localPath != null)
            {
                File recorderFile = new File(localPath);
                if(recorderFile.exists())
                {
                    recorderFile.delete();
                }
            }


            chronometer.setBase(SystemClock.elapsedRealtime());
            initCamera();
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                handleSurfaceChanged();
            } catch (Exception e1) {
                Log.e("video", "start preview fail " + e1.getMessage());
                showFailDialog();
            }
        }
        else if(id == R.id.play_btn)
        {
            play_btn.setSelected(!play_btn.isSelected());
            if(play_btn.isSelected())
            {
                if(mCamera != null)
                {
                    mCamera.stopPreview();

                    try {
                        mCamera.setPreviewDisplay(mSurfaceHolder);
                    }
                    catch (Exception e)
                    {
                        Log.d("video","setPreviewDisplayxxxxxxx");
                    }
                    mCamera.release();
                    mCamera = null;
                }
                if(localPath != null && new File(localPath).exists())
                {

                    play_btn.setBackground(getResources().getDrawable(R.drawable.pause_icon));
                    mVideoView.setVideoPath(localPath);
                    if(mVideoView.isPlaying())
                    {
                        mVideoView.resume();
                    }
                    else {
                        mVideoView.start();
                    }
                }
                else
                {
                    Toast.makeText(this, "录像异常", Toast.LENGTH_SHORT).show();
                }

            }
            else
            {
                play_btn.setBackground(getResources().getDrawable(R.drawable.play_icon));
                mVideoView.pause();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        mSurfaceHolder = holder;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (mCamera == null){
            if(!initCamera()){
                showFailDialog();
                return;
            }

        }
        try {
            handleSurfaceChanged();
            mCamera.setPreviewDisplay(mSurfaceHolder);
            Camera.Parameters parameters = mCamera.getParameters();
//            parameters.setPreviewFormat(ImageFormat.NV16);
            parameters.setPreviewSize(previewWidth,previewHeight);
//            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            mCamera.setParameters(parameters);

            mCamera.startPreview();


        } catch (Exception e1) {
            Log.e("video", "start preview fail " + e1.getMessage());
            showFailDialog();
        }
    }



    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        Log.v("video", "surfaceDestroyed");
    }

    public boolean startRecording(){
        if (mediaRecorder == null){
            if(!initRecorder())
                return false;
        }
        mediaRecorder.setOnInfoListener(this);
        mediaRecorder.setOnErrorListener(this);
        mediaRecorder.start();
        return true;
    }

    @SuppressLint("NewApi")
    private boolean initRecorder(){
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            showNoSDCardDialog();
            return false;
        }

        if (mCamera == null) {
            if(!initCamera()){
                showFailDialog();
                return false;
            }
        }
        mVideoView.setVisibility(View.VISIBLE);
        mCamera.stopPreview();
        mediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (frontCamera == 1) {
            mediaRecorder.setOrientationHint(currentOrientation == 0 ? 270 : 0);
        } else {
            mediaRecorder.setOrientationHint(currentOrientation == 0 ? 90 : 0);

        }

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // set resolution, should be set after the format and encoder was set
        mediaRecorder.setVideoSize(previewWidth, previewHeight);
        mediaRecorder.setVideoEncodingBitRate(495*1024);
        // set frame rate, should be set after the format and encoder was set
        if (defaultVideoFrameRate != -1) {
            mediaRecorder.setVideoFrameRate(defaultVideoFrameRate);
        }
        // set the path for video file
        localPath = FileUtils.getVideoPath(subPath) + "/"
                + System.currentTimeMillis() + ".mp4";
        mediaRecorder.setOutputFile(localPath);
        mediaRecorder.setMaxDuration(20000);

        mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setOnInfoListener(null);
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                Log.e("video", "stopRecording error:" + e.getMessage());
            }
        }
        releaseRecorder();

        if (mCamera != null) {
            mCamera.stopPreview();
            releaseCamera();
        }
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    protected void releaseCamera() {
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
        }
    }

    @SuppressLint("NewApi")
    public void switchCamera() {

        if (mCamera == null) {
            return;
        }
        if (Camera.getNumberOfCameras() >= 2) {
            btn_switch.setEnabled(false);
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            switch (frontCamera) {
                case 0:
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    frontCamera = 1;
                    break;
                case 1:
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                    frontCamera = 0;
                    break;
            }
            try {
                mCamera.lock();
                mCamera.setDisplayOrientation(90);
                mCamera.setPreviewDisplay(mVideoView.getHolder());
                Camera.Parameters parameters = mCamera.getParameters();
//            parameters.setPreviewFormat(ImageFormat.NV16);
                parameters.setPreviewSize(previewWidth,previewHeight);
//                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                mCamera.setParameters(parameters);

                mCamera.startPreview();
            } catch (IOException e) {
                mCamera.release();
                mCamera = null;
            }
            btn_switch.setEnabled(true);

        }

    }

    MediaScannerConnection msc = null;
    ProgressDialog progressDialog = null;

    public void sendVideo(View view) {
        if (TextUtils.isEmpty(localPath)) {
            Log.e("Recorder", "recorder fail please try again!");
            return;
        }
        if(msc == null)
            msc = new MediaScannerConnection(this,
                    new MediaScannerConnection.MediaScannerConnectionClient() {

                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.d(TAG, "scanner completed");
                            msc.disconnect();
                            progressDialog.dismiss();

                            setResult(RESULT_OK, getIntent().putExtra("uri", uri));

                            finish();
                        }

                        @Override
                        public void onMediaScannerConnected() {
                            msc.scanFile(localPath, "video/*");
                        }
                    });


        if(progressDialog == null){
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("processing...");
            progressDialog.setCancelable(false);
        }
        progressDialog.show();
        msc.connect();

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.v("video", "onInfo");
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Log.v("video", "max duration reached");
            stopRecording();
            if (localPath == null) {
                return;
            }

            btnStop.setEnabled(false);
            btn_switch.setVisibility(View.VISIBLE);
            chronometer.stop();
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.INVISIBLE);

            recorderBottom.setVisibility(View.INVISIBLE);
            finishBottom.setVisibility(View.VISIBLE);

            play_btn.setSelected(false);
            play_btn.setBackground(getResources().getDrawable(R.drawable.play_icon));

//            new AlertDialog.Builder(this)
//                    .setMessage("是否发送？")
//                    .setPositiveButton("确定",
//                            new DialogInterface.OnClickListener() {
//
//                                @Override
//                                public void onClick(DialogInterface arg0,
//                                                    int arg1) {
//                                    arg0.dismiss();
//                                    sendVideo(null);
//
//                                }
//                            }).setNegativeButton("取消", null)
//                    .setCancelable(false).show();
        }

    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e("video", "recording onError:");
        stopRecording();
        Toast.makeText(this,
                "Recording error has occurred. Stopping the recording",
                Toast.LENGTH_SHORT).show();

    }

    public void saveBitmapFile(Bitmap bitmap) {
        File file = new File(Environment.getExternalStorageDirectory(), "a.jpg");
        try {
            BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
        mOrientationListener.disable();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if(mVideoView != null){
            mVideoView.suspend();
        }

    }

    @Override
    public void onBackPressed() {
        back(null);
    }

    private void showFailDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("打开设备失败!")
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();

    }

    private void showNoSDCardDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("No sd card!")
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();
    }


}
