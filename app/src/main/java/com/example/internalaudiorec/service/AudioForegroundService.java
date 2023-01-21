package com.example.internalaudiorec.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.example.internalaudiorec.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class AudioForegroundService extends Service {

    private int minBufferSize;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    private final String TAG = AudioForegroundService.class.getSimpleName();
    private String FILE_NAME;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onStartCommand in Audio Service");

        Notification.Builder builder;
        String CHANNEL_ID = "InnerRecordService";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel Channel = new NotificationChannel(CHANNEL_ID,
                "Recording service", NotificationManager.IMPORTANCE_HIGH);
        Channel.enableLights(true);
        Channel.setLightColor(Color.RED);
        Channel.setShowBadge(true);
        Channel.setDescription("InnerRecord Notification");
        Channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (manager != null) {
            manager.createNotificationChannel(Channel);
        }
        builder = new Notification.Builder(this, CHANNEL_ID);
        Notification notification = builder.setAutoCancel(false)
                .setContentTitle("Audio Foreground Service")
                .setContentText("The service is recording loopback audio")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .build();
        startForeground(1, notification);
    }

    private void startRecord(boolean header) {
        if (isRecording) {
            return;
        }
        isRecording = true;
        recordingThread = new Thread(() -> {
            byte[] data = new byte[minBufferSize];
            String FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + "InRec";
            if (!new File(FOLDER_PATH).exists()) new File(FOLDER_PATH).mkdir();
            FILE_NAME = FOLDER_PATH + File.separator + "recordar-" + System.currentTimeMillis() + ".pcm";
            File pcmFile = new File(FILE_NAME);
            FileOutputStream os = null;
            try {
                if (!pcmFile.exists()) pcmFile.createNewFile();
                os = new FileOutputStream(pcmFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (os != null) {
                while (isRecording) {
                    audioRecord.read(data, 0, minBufferSize);
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (header) {
                    String aacPath = Environment.getExternalStorageDirectory().toString() + File.separator +
                            "InRec" + File.separator + "recordar-" + System.currentTimeMillis() + ".aac";
                    AacEncoder aacEncoder = new AacEncoder(44100,1,64000, FILE_NAME, aacPath, new IHandlerCallback() {
                        @Override
                        public void onFail() {
                            Log.e(TAG, "Failed to record");
                        }

                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "Successfully saved recording!");
                        }
                    });
                    aacEncoder.startEncoding();
                } else Log.i(TAG, "Successfully saved recording!");
            }
        });
        audioRecord.startRecording();
        recordingThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int currentResultCode = intent.getIntExtra("resultCode", 0);
        Intent resultData = intent.getParcelableExtra("resultData");
        boolean header = intent.getBooleanExtra("header", false);
        minBufferSize = AudioRecord.getMinBufferSize(44100,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getBaseContext()
                .getSystemService(MEDIA_PROJECTION_SERVICE);
        MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(currentResultCode,
                Objects.requireNonNull(resultData));
        AudioRecord.Builder builder = new AudioRecord.Builder();
        builder.setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(minBufferSize);
        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();
        builder.setAudioPlaybackCaptureConfig(config);
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                audioRecord = builder.build();
            }
        } catch (Exception e) {
            Log.e("Recorder error", "Failed to initialize recorder");
        }
        startRecord(header);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
        }
    }

}
