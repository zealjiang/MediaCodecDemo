package com.example.mediacodecdemo;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;

public class MediaCodecThread extends Thread{

    //解码器
    private MediaCodecUtil util;
    //文件路径
    private String path;
    //文件读取完成标识
    private boolean isFinish = false;
    //这个值用于找到第一个帧头后，继续寻找第二个帧头，如果解码失败可以尝试缩小这个值
    private int FRAME_MIN_LEN = 1024;
    //一般H264帧大小不超过200k,如果解码失败可以尝试增大这个值
    private static int FRAME_MAX_LEN = 300 * 1024;
    //根据帧率获取的解码每帧需要休眠的时间,根据实际帧率进行操作
    private int PRE_FRAME_TIME = 1000 / 15;
    private DataQueue dataQueue;
    private PlayState state = PlayState.IDLE;
    private String control = "";
    private RandomAccessFile file = null;
    enum PlayState {
        IDLE,PLAY,PLAYING,PAUSE,PAUSED,OVER;
    }

    /**
     * 初始化解码器
     *
     * @param util 解码Util
     * @param path 文件路径
     */
    public MediaCodecThread(MediaCodecUtil util, String path,DataQueue dataQueue) {
        this.util = util;
        this.path = path;
        this.dataQueue = dataQueue;
    }

    /**
     * 寻找指定buffer中h264头的开始位置
     *
     * @param data   数据
     * @param offset 偏移量
     * @param max    需要检测的最大值
     * @return h264头的开始位置 ,-1表示未发现
     */
    private int findHead(byte[] data, int offset, int max) {
        int i;
        for (i = offset; i < max - 3; i++) {
            //发现帧头
            if (isHead(data, i))
                break;
        }
        //检测到最大值，未发现帧头
        if (i == max) {
            i = -1;
        }
        return i;
    }

    /**
     * 判断是否是I帧/P帧头:
     * 00 00 00 01 65    (I帧)
     * 00 00 00 01 61 / 41   (P帧)
     *
     * @param data
     * @param offset
     * @return 是否是帧头
     */
    private boolean isHead(byte[] data, int offset) {
        if(true) {
            return isHead2(data, offset);
        }


        boolean result = false;
        // 00 00 00 01 x
        if (data[offset] == 0x00 && data[offset + 1] == 0x00
                && data[offset + 2] == 0x00 && data[3] == 0x01 && isVideoFrameHeadType(data[offset + 4])) {
            result = true;
        }
        // 00 00 01 x
        if (data[offset] == 0x00 && data[offset + 1] == 0x00
                && data[offset + 2] == 0x01 && isVideoFrameHeadType(data[offset + 3])) {
            result = true;
        }
        return result;
    }


    private boolean isHead2(byte[] pData, int i) {
        boolean result = false;

        if(pData.length < 4 + i){
            return false;
        }
        if(pData[i] == 0 && pData[i+1] == 0 &&
                pData[i+2] == 0 && pData[i+3] == 1 ){
            result = true;
        }else if(pData[i] == 0 && pData[i+1] == 0 &&
                pData[i+2] == 1){
            result = true;
        }
/*        if(pData[i] == 0 && pData[i+1] == 0 &&
                pData[i+2] == 0 && pData[i+3] == 1 &&
                (pData[i+4] & 0x1F) == 5)
        {
            result = true;//cout<<"IS I_FRAME"<<endl;
        }
        else if(pData[i] == 0 && pData[i+1] == 0 &&
                pData[i+2] == 0 && pData[i+3] == 1 &&
                (pData[i+4] & 0x1F) == 1)
        {
            result = true;//cout<<"IS P_FRAME"<<endl;
        }*/
        return result;
    }

    /**
     * I帧或者P帧
     */
    private boolean isVideoFrameHeadType(byte head) {
        return head == (byte) 0x65 || head == (byte) 0x61 || head == (byte) 0x41;
    }

    public void pause(){
        state = PlayState.PAUSE;

    }

    public void play(){
        if(file == null)return;
        state = PlayState.PLAY;
        synchronized (control) {
            try{
                control.notify();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public boolean isPaused(){
        if(PlayState.PAUSE == state){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void run() {
        handleNetFileData();
        if(true)return;
        //以下是从本地读取
        File file = new File(path);
        //判断文件是否存在
        if (file.exists()) {
            handleLocalData();
        }else {
            handleNetData();
        }
    }

    /**
     * 从本地读取
     */
    private void handleLocalData(){
        //以下是从本地读取
        File file = new File(path);
        //判断文件是否存在
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                //保存完整数据帧
                byte[] frame = new byte[FRAME_MAX_LEN];
                //当前帧长度
                int frameLen = 0;
                //每次从文件读取的数据
                byte[] readData = new byte[10 * 1024];
                //开始时间
                long startTime = System.currentTimeMillis();
                //循环读取数据
                while (!isFinish) {
                    //Log.e("mtest","循环读取数据 ");
                    if (fis.available() > 0) {
                        int readLen = fis.read(readData);
                        //当前长度小于最大值
                        if (frameLen + readLen < FRAME_MAX_LEN) {
                            //将readData拷贝到frame
                            System.arraycopy(readData, 0, frame, frameLen, readLen);
                            //修改frameLen
                            frameLen += readLen;
                            //寻找第一个帧头
                            int headFirstIndex = findHead(frame, 0, frameLen);
                            //Log.e("mtest","headFirstIndex:" + headFirstIndex);
                            while (headFirstIndex >= 0 && isHead(frame, headFirstIndex)) {
                                //寻找第二个帧头
                                int headSecondIndex = findHead(frame, headFirstIndex + FRAME_MIN_LEN, frameLen);
                                //如果第二个帧头存在，则两个帧头之间的就是一帧完整的数据
                                if (headSecondIndex > 0 && isHead(frame, headSecondIndex)) {
                                    //Log.e("mtest","headSecondIndex:" + headSecondIndex);
                                    //视频解码
                                    onFrame(frame, headFirstIndex, headSecondIndex - headFirstIndex);
                                    Log.d("mtest","onFrame: frame: "+frame.length+"  headFirstIndex: "+headFirstIndex+"  headSecondIndex: "+headSecondIndex);
                                    //截取headSecondIndex之后到frame的有效数据,并放到frame最前面
                                    byte[] temp = Arrays.copyOfRange(frame, headSecondIndex, frameLen);
                                    System.arraycopy(temp, 0, frame, 0, temp.length);
                                    //修改frameLen的值
                                    frameLen = temp.length;
                                    //线程休眠
                                    sleepThread(startTime, System.currentTimeMillis());
                                    //重置开始时间
                                    startTime = System.currentTimeMillis();
                                    //继续寻找数据帧
                                    headFirstIndex = findHead(frame, 0, frameLen);
                                } else {
                                    //找不到第二个帧头
                                    headFirstIndex = -1;
                                }
                            }
                        } else {
                            //如果长度超过最大值，frameLen置0
                            frameLen = 0;
                        }
                    } else {
                        //文件读取结束
                        isFinish = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e("mtest","File not found");
        }
    }


    //保存完整数据帧
    byte[] frame = new byte[FRAME_MAX_LEN];
    //当前帧长度
    int frameLen = 0;
    //开始时间
    long startTime = System.currentTimeMillis();
    int findHeadIndex = 0;
    private void handleNetData(){
        if(dataQueue == null)return;
        Log.d("mtest","handleNetData began");
        while (true){
            byte[] readData = dataQueue.take();
            if(readData == null){
                if(dataQueue.isPutOver()){
                    return;
                }
            }
            if(PlayState.PAUSE == state){
                synchronized (control) {
                    try {
                        control.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            int readLen = readData.length;
            try {
                if (readData != null && readData.length > 0) {
                    //当前长度小于最大值
                    if (frameLen + readLen < FRAME_MAX_LEN) {
                        //将readData拷贝到frame
                        System.arraycopy(readData, 0, frame, frameLen, readLen);
                        //修改frameLen
                        frameLen += readLen;
                        //寻找第一个帧头
                        int headFirstIndex = findHead(frame, findHeadIndex, frameLen);
                        //Log.e("mtest","headFirstIndex:" + headFirstIndex);
                        while(headFirstIndex >= 0 && isHead(frame, headFirstIndex)){
                            //寻找第二个帧头
                            int headSecondIndex = findHead(frame, headFirstIndex + FRAME_MIN_LEN, frameLen);
                            //如果第二个帧头存在，则两个帧头之间的就是一帧完整的数据
                            if (headSecondIndex > 0 && isHead(frame, headSecondIndex)) {
                                //Log.e("mtest","headSecondIndex:" + headSecondIndex);
                                //视频解码
                                onFrame(frame, headFirstIndex, headSecondIndex - headFirstIndex);
                                Log.d("mtest","onFrame: frame: "+frame.length+"  headFirstIndex: "+headFirstIndex+"  headSecondIndex: "+headSecondIndex);
                                //截取headSecondIndex之后到frame的有效数据,并放到frame最前面
                                byte[] temp = Arrays.copyOfRange(frame, headSecondIndex, frameLen);
                                System.arraycopy(temp, 0, frame, 0, temp.length);
                                //修改frameLen的值
                                frameLen = temp.length;
                                //线程休眠
                                sleepThread(startTime, System.currentTimeMillis());
                                //重置开始时间
                                startTime = System.currentTimeMillis();
                                findHeadIndex = 0;
                                //继续寻找数据帧
                                headFirstIndex = findHead(frame, 0, frameLen);
                            } else {
                                //找不到第二个帧头
                                headFirstIndex = -1;
                            }

                        }

                    } else {
                        //如果长度超过最大值，frameLen置0
                        frameLen = 0;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private int lastIFrameIndex = -1;
    private boolean isPlayOver = false;
    public boolean isPlayOver(){
        return isPlayOver;
    }



    private void handleNetFileData(){
        long filePos = 0,start = 0;
        int MaxTakeCount = 1024 * 6;
        isPlayOver = false;
        Log.d("mtest","handleNetData began");
        while (true){
            byte[] readData = dataQueue.take();
            if(readData == null && dataQueue.isPutOver()){
                //网络数据缓存完毕
                if (file == null) {
                    try {
                        file = new RandomAccessFile(path, "r");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }else{
                //网络数据还未开始写入或正在写入
                if (readData != null && readData.length > 0) {
                    if (file == null) {
                        try {
                            file = new RandomAccessFile(path, "r");
                            play();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if(file == null){
                synchronized (control) {
                    try {
                        state = PlayState.PAUSED;
                        control.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }


            if(PlayState.PAUSE == state){
                synchronized (control) {
                    try {
                        state = PlayState.PAUSED;
                        control.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }


            try {
                start = filePos;
                if(file.length() - start > MaxTakeCount){
                    filePos += MaxTakeCount;
                }else{
                    filePos += file.length() - start;
                }

                byte[] readDataN = new byte[(int)(filePos - start)];
                file.seek(start);
                file.read(readDataN,0,readDataN.length);
                Log.d("mtest","file.length(): "+file.length()+"  start: "+start+"  readDataN.length: "+readDataN.length);
                int readLen = readDataN.length;
                if (readDataN != null && readDataN.length > 0) {
                    //当前长度小于最大值
                    if (frameLen + readLen < FRAME_MAX_LEN) {
                        //将readData拷贝到frame
                        System.arraycopy(readDataN, 0, frame, frameLen, readLen);
                        //修改frameLen
                        frameLen += readLen;
                        //寻找第一个帧头
                        int headFirstIndex = findHead(frame, findHeadIndex, frameLen);
                        //Log.e("mtest","headFirstIndex:" + headFirstIndex);
                        while(headFirstIndex >= 0 && isHead(frame, headFirstIndex)){
                            //寻找第二个帧头
                            int headSecondIndex = findHead(frame, headFirstIndex + FRAME_MIN_LEN, frameLen);
                            //如果第二个帧头存在，则两个帧头之间的就是一帧完整的数据
                            if (headSecondIndex > 0 && isHead(frame, headSecondIndex)) {
                                //Log.e("mtest","headSecondIndex:" + headSecondIndex);
                                //视频解码
                                onFrame(frame, headFirstIndex, headSecondIndex - headFirstIndex);
                                Log.d("mtest","onFrame: frame: "+frame.length+"  headFirstIndex: "+headFirstIndex+"  headSecondIndex: "+headSecondIndex);
                                //截取headSecondIndex之后到frame的有效数据,并放到frame最前面
                                byte[] temp = Arrays.copyOfRange(frame, headSecondIndex, frameLen);
                                System.arraycopy(temp, 0, frame, 0, temp.length);
                                //修改frameLen的值
                                frameLen = temp.length;
                                //线程休眠
                                sleepThread(startTime, System.currentTimeMillis());
                                //重置开始时间
                                startTime = System.currentTimeMillis();
                                findHeadIndex = 0;
                                //继续寻找数据帧
                                headFirstIndex = findHead(frame, 0, frameLen);
                            } else {
                                //找不到第二个帧头
                                headFirstIndex = -1;
                            }

                        }

                    } else {
                        //如果长度超过最大值，frameLen置0
                        frameLen = 0;
                    }
                }

                if(dataQueue.isPutOver() && start + readDataN.length >= file.length()){
                    isPlayOver = true;
                    filePos = 0;
                    start = 0;
                    frameLen = 0;
                    findHeadIndex = 0;
                    startTime = System.currentTimeMillis();
                    pause();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //视频解码
    private void onFrame(byte[] frame, int offset, int length) {
        if (util != null) {
            try {
                util.onFrame2(frame, offset, length);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e("mtest","mediaCodecUtil is NULL");
        }
    }

    //修眠
    private void sleepThread(long startTime, long endTime) {
        //根据读文件和解码耗时，计算需要休眠的时间
        long time = PRE_FRAME_TIME - (endTime - startTime);
/*        Log.d("mtest","sleepThread: PRE_FRAME_TIME: "+PRE_FRAME_TIME
                +"  endTime: "+endTime+"  startTime: "+startTime);*/
        if (time > 0) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //手动终止读取文件，结束线程
    public void stopThread() {
        isFinish = true;
    }
}
