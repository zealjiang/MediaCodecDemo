package com.example.mediacodecdemo;

import java.util.Arrays;

public class DataQueue {

    //当前未处理的数据
    final int MAX_VALUE = 1024 * 600;
    int frameBufferValidLength = 0;
    byte[] frameBuffer = new byte[MAX_VALUE];
    final int MaxTakeCount = 1024 * 6;
    int takeCount;
    private boolean isPutOver;

    public synchronized void put(byte[] buf,int readLen,boolean isPutOver){
        this.isPutOver = isPutOver;
        if(buf == null || buf.length <= 0 || readLen <= 0 || readLen > buf.length){
            notifyAll();
            return;
        }
        while (frameBufferValidLength >= MAX_VALUE || frameBufferValidLength + readLen > MAX_VALUE) {
            try {
                //执行到此处时，下面的将不被执行，直到不满足循环条件
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.arraycopy(buf, 0,frameBuffer,frameBufferValidLength, readLen);
        frameBufferValidLength += readLen;
        notifyAll();
    }

    public synchronized byte[] take(){

        if(frameBufferValidLength <= 0 && isPutOver)return null;
        while (frameBufferValidLength <= 0) {
            try {
                //执行到此处时，下面的list.poll()将不被执行，直到不满足循环条
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        takeCount = frameBufferValidLength;
        if(frameBufferValidLength > MaxTakeCount){
            takeCount = MaxTakeCount;
        }

        byte[] takeData = Arrays.copyOfRange(frameBuffer,0, takeCount);
        byte[] temp = Arrays.copyOfRange(frameBuffer,takeCount, frameBufferValidLength);
        System.arraycopy(temp, 0, frameBuffer, 0, temp.length);
        //修改frameLen的值
        frameBufferValidLength = temp.length;

        notifyAll();
        return takeData;
    }

    public boolean isPutOver(){
        return isPutOver;
    }
}
