package com.zr.zlog.util;

import com.zr.zlog.log.DLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author hzzhengrui
 * @Date 17/1/12
 * @Description
 */
public class FileUtil {

    /** 日志tag */
    private static String TAG = FileUtil.class.getSimpleName();

    /**
     * 文件压缩方法
     * @param unZip
     * @param zip
     * @return
     */
    public static boolean zip(File unZip, File zip) {
        if (!unZip.exists())
            return false;
        if (!zip.getParentFile().exists())
            zip.getParentFile().mkdir();

        try {
            FileInputStream in = new FileInputStream(unZip);
            FileOutputStream out = new FileOutputStream(zip);

            ZipOutputStream zipOut = new ZipOutputStream(out);

            // for buffer
            byte[] buf = new byte[1024];

            int readCnt = 0;

            boolean isNull = true;
            zipOut.putNextEntry(new ZipEntry(unZip.getName()));
            while ((readCnt = in.read(buf)) > 0) {
                zipOut.write(buf, 0, readCnt);
                isNull = false;
            }

            zipOut.closeEntry();

            in.close();
            zipOut.close();

            if (isNull) {
                return false;
            }

        } catch (Exception e) {
            DLog.e(TAG, e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * 文件拷贝,将src1、src2拷贝到dest文件中
     * @param src1
     * @param src2
     * @param dest
     * @throws IOException
     */
    public static void copyFile(File src1, File src2, File dest) throws IOException {
        if (dest.exists()) {
            dest.delete();
        }
        long total = 0;
        long count = 0;
        FileInputStream in = null;
        FileOutputStream out = new FileOutputStream(dest);
        byte[] temp = new byte[1024 * 10];

        if (src1.exists()) {
            total = src1.length();
            in = new FileInputStream(src1);
            while (count < total) {
                int size = in.read(temp);
                out.write(temp, 0, size);
                count += size;
            }
            in.close();
        }

        if (src2.exists()) {
            count = 0;
            total = src2.length();
            in = new FileInputStream(src2);
            while (count < total) {
                int size = in.read(temp);
                out.write(temp, 0, size);
                count += size;
            }
            in.close();
        }

        in = null;
        out.close();
        out = null;

    }
}
