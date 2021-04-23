package com.example.mediacodecdemo;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Description OkHttp封装
 * Author sunhuaxiao
 * Date 2017/11/1
 */

public class OkHttpHelper {

    private static volatile OkHttpClient mClient = null;
    public static OkHttpClient getInstance() {
        if (mClient == null) {
            synchronized (OkHttpHelper.class) {
                if (mClient == null) {
                    mClient = new OkHttpClient.Builder()
                            .hostnameVerifier(new HostnameVerifier() {
                                @Override
                                public boolean verify(String hostname, SSLSession session) {
                                    return true;
                                }
                            })
                            .build();
                }
            }
        }
        return mClient;
    }

    public static boolean isQuit = false;
    public static void downloadFile(String url,
                                    final String fileDir,
                                    final String fileName,DataQueue dataQueue) {
        if (TextUtils.isEmpty(url)) {
            return;
        }

        FileUtil.createDir(fileDir);
        if(new File(fileDir,fileName).exists()){
            //new File(fileDir,fileName).delete();
        }
        Request request = new Request.Builder()
                .url(url)
                .build();
        Call call = getInstance().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("mtest",e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                InputStream is = null;
                byte[] buf = new byte[1024*4];
                int len;
                FileOutputStream fos = null;
                File file;
                try {
                    is = response.body().byteStream();
                    file = new File(fileDir, fileName);
                    fos = new FileOutputStream(file);
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        dataQueue.put(buf,len,false);
                        if(isQuit){
                            break;
                        }
                    }
                    fos.flush();
                    dataQueue.put(null,0,true);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("mtest","download error "+e.getMessage());
                } finally {
                    if (is != null) is.close();
                    if (fos != null) fos.close();
                    Log.d("mtest","download over");
                }
            }
        });
    }
}
