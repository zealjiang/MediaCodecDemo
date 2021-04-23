package com.example.mediacodecdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private SurfaceView surfaceView;
    private Button tvPlay;

    private SurfaceHolder holder;
    //解码器
    private MediaCodecUtil codecUtil;
    //读取文件解码线程
    private MediaCodecThread thread;
    //文件路径
    private String path =  "/sdcard/h264/outfile.h264";
    private String pathUrl = "https://cdn.suapp.mobi/app_test/outfile.h264";
    private DataQueue dataQueue;


    @Override
    protected void onPause() {
        super.onPause();
        if(thread != null){
            thread.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exit();
    }

    private void exit(){
        OkHttpHelper.isQuit = true;
        if (codecUtil != null) {
            codecUtil.stopCodec();
            codecUtil = null;
        }
        if (thread != null && thread.isAlive()) {
            thread.stopThread();
            thread = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surV);
        tvPlay = findViewById(R.id.tv);
        verifyStoragePermissions();
        dataQueue = new DataQueue();
        initSurface();
        tvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    if(thread.isPlayOver()){
                        //OkHttpHelper.downloadFile(pathUrl, "/sdcard/h264", "outfile.h264", dataQueue);
                        thread.play();
                    }else {
                        OkHttpHelper.downloadFile(pathUrl, "/sdcard/h264", "outfile.h264", dataQueue);
                        if (thread.isPaused()) {
                            thread.play();
                            tvPlay.setEnabled(false);
                            tvPlay.setBackgroundColor(Color.parseColor("#f1f1f1"));
                        }
                    }
            }
        });
    }

    //初始化播放相关
    private void initSurface() {
        holder = surfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                //从onstop回到onResume扫执行
                OkHttpHelper.isQuit = false;
                if (codecUtil == null) {
                    codecUtil = new MediaCodecUtil(holder);
                    codecUtil.startCodec();
                }else{
                    codecUtil.start();
                }
                if (thread == null) {
                    //解码线程第一次初始化
                    thread = new MediaCodecThread(codecUtil, path,dataQueue);
                    thread.start();
                }else{
                    if(thread != null){
                        thread.play();
                    }
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                //onstop 会进入这个方法
      /*          if (codecUtil != null) {
                    codecUtil.stopCodec();
                    codecUtil = null;
                }*/

                if (codecUtil != null) {
                    codecUtil.stop();
                }
            }
        });
    }

    String[] permissions = new String[]{
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET};
    private boolean verifyStoragePermissions(){

        if (!PermissionUtil.checkPermissionGranted(this,permissions)) {
            try {
                ActivityCompat.requestPermissions(this,permissions, 111);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 111){
            if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, permissions[0]) && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                showMessageOKCancel(this,"need write permission to load video", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", MainActivity.this.getPackageName(), null);
                        intent.setData(uri);
                        MainActivity.this.startActivity(intent);
                    }
                }, (dialog, which) -> {

                });
            } else {
                if (PermissionUtil.checkPermissionGranted(MainActivity.this ,permissions)) {

                }
            }
        }
    }

    private static void showMessageOKCancel(final Activity context, String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancleListener) {
        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton("确定", okListener)//确定
                .setNegativeButton("取消", cancleListener)//取消
                .create()
                .show();
    }
}