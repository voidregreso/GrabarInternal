package com.example.internalaudiorec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.example.internalaudiorec.service.AudioForegroundService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String TAG = MainActivity.class.getSimpleName();
    private final int REQUEST_CODE = 0x3f3f4f;
    private final int REQUEST_SCREEN_CAPTURE_CODE = 0x4f4f4f;

    private Button startBtn;
    private Button stopBtn;
    private CheckBox cbMp3;
    private Intent resultData;
    private int currentResultCode;

    private final String [] appPermissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        cbMp3 = findViewById(R.id.cbMp3);

        startBtn.setOnClickListener(this);
        stopBtn.setOnClickListener(this);

        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);

        if (currentResultCode != Activity.RESULT_OK || resultData == null) {
            MediaProjectionManager mediaProjectionManager
                    = (MediaProjectionManager) this.getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(screenCaptureIntent, REQUEST_SCREEN_CAPTURE_CODE);
        }

    }

    @Override
    public void onClick(View v) {
        if(v == startBtn){
            if(checkAndRequestPermissions()){
                startAudioRecord();
            }

        } else {
            stopAudioRecord();
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, AudioForegroundService.class);
        Log.d(TAG, "Service is going to start");
        serviceIntent.putExtra("resultData", resultData);
        serviceIntent.putExtra("resultCode", currentResultCode);
        serviceIntent.putExtra("header", cbMp3.isChecked());
        startForegroundService(serviceIntent);
    }

    private void stopService() {
        Intent serviceIntent = new Intent(this, AudioForegroundService.class);
        Log.d(TAG, "Service is going to stop");
        stopService(serviceIntent);
    }

    private void startAudioRecord(){
        startService();
        Toast.makeText(MainActivity.this, "Audio Record Started", Toast.LENGTH_SHORT).show();
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        cbMp3.setEnabled(false);
    }

    private void stopAudioRecord(){
        stopService();
        Toast.makeText(MainActivity.this, "Audio Record Stopped", Toast.LENGTH_SHORT).show();
        stopBtn.setEnabled(false);
        startBtn.setEnabled(true);
        cbMp3.setEnabled(true);
    }

    private void goToSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.parse("package:" + getPackageName());
        intent.setData(uri);
        startActivity(intent);
    }

    private void showPermissionRational(String permission){
        new AlertDialog.Builder(this).setTitle("Permission Denied").setMessage("Without "+ permission +" permission app is unable to record system sound.")
                .setCancelable(false)
                .setNegativeButton("I'M SURE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("RETRY", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        checkAndRequestPermissions();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void dialogForSettings(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setCancelable(false)
                .setNegativeButton("NOT NOW", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("SETTINGS", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        goToSettings();
                        dialog.dismiss();
                    }
                }).show();
    }

    public boolean checkAndRequestPermissions(){
        // audio
        List < String > listPermissionsNeeded = new ArrayList<>();
        for(String permission : appPermissions){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                listPermissionsNeeded.add(permission);
            }
        }

        if(!listPermissionsNeeded.isEmpty()){
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), REQUEST_CODE);
        } else return true;

        // storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return true;
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivityForResult(intent, REQUEST_CODE);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            HashMap<String, Integer> permissionResults = new HashMap<>();
            int deniedCount = 0;
            for (int permissionIndx = 0; permissionIndx < permissions.length; permissionIndx++) {
                if (grantResults[permissionIndx] != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, permissions[permissionIndx]);
                    permissionResults.put(permissions[permissionIndx], grantResults[permissionIndx]);
                    deniedCount++;
                }
            }
            if (deniedCount == 0) {
                startAudioRecord();
            } else {
                for (Map.Entry<String, Integer> entry : permissionResults.entrySet()) {
                    String permName = entry.getKey();
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permName)) {
                        showPermissionRational(permName);
                    } else {
                        dialogForSettings("Permission Denied", "Now you must allow " + permName + " permission from settings.");
                    }
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(data != null){
            Log.d(TAG, "Write External Storage accepted");
        }
        if (requestCode == REQUEST_SCREEN_CAPTURE_CODE) {
            currentResultCode = resultCode;
            resultData = data;
            if(data == null) {
                finish();
            }
        }
    }
}
