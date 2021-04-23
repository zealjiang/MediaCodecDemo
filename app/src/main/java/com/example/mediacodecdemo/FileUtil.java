package com.example.mediacodecdemo;

import java.io.File;

public class FileUtil {

    /**
     * 创建 文件夹
     * @param dirPath 文件夹路径
     * @return 创建成功返回true,失败返回false
     */
    public static boolean createDir(String dirPath) {

        File dir = new File(dirPath);
        //文件夹是否已经存在
        if (dir.exists()) {
            return true;
        }
        //创建文件夹
        if (dir.mkdirs()) {
            return true;
        }

        return false;
    }
}
