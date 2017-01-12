package com.zr.zlog;

import android.text.TextUtils;
import android.util.Log;

import com.zr.zlog.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * 默认日志工具<br />
 * 1.添加日志输出选项.控制日志输出位置  <br />
 * 2.添加文件日志功能.(因进程问题.现UI与Service只能打到不同的文件中) <br />
 * 3.控制单个日志文件最大限制.由LOG_MAXSIZE常量控制,保留两个最新日志文件 <br />
 * 4.文件日志输出目标:/data/data/%packetname%/files/
 *
 * @author zr
 */
public final class ZLog {

    /** 工具类默认log tag */
    private static final String TAG = ZLog.class.getSimpleName();

    /*************************以下IConfig 配置信息*****************************/
    /** 将Log日志输出到控制台 */
    public static final int TO_CONSOLE = 0x0001;
    /** 将Log日志输出到文件 */
    public static final int TO_FILE = 0x0002;
    /** 将Log debug日志输出到文件 */
    public static final int DEBUG_TO_FILE = 0x0008;
    /** 将Log feedback日志输出到文件 */
    public static final int FEEDBACK_TO_FILE = 0x0020;

    /** 当前打日志的等级范围 */
    private static int sCURRENT_DEBUG_SCOP = TO_CONSOLE | TO_FILE | /*DEBUG_TO_FILE | */ FEEDBACK_TO_FILE;

    /*************************以上IConfig 配置信息*****************************/
    //-------------------debug日志相关---------------------------
    /** 调试日志临时文件名称 */
    private static final String DEBUG_TEMP_FILE = "DEBUG_log.temp";
    /** 调试日志最近文件名称 */
    private static final String DEBUG_LAST_FILE = "DEBUG_log_last.txt";
    /** 调试日志当前文件名称 */
    private static final String DEBUG_NOW_FILE = "DEBUG_log_now.txt";
    /** debug日志文件大小 */
    private static final int DEBUG_MAXSIZE = 1024 * 1024; // double the size

    //-------------------feedback日志上报相关---------------------------
    /** 反馈日志临时文件名称 */
    private static final String FEEDBACK_TEMP_FILE = "FEEDBACK_log.temp";
    /** 反馈日志最近文件名称 */
    private static final String FEEDBACK_LAST_FILE = "FEEDBACK_log_last.txt";
    /** 反馈日志当前文件名称 */
    private static final String FEEDBACK_NOW_FILE = "FEEDBACK_log_now.txt";
    /** feedback日志文件大小 */
    private static final int FEEDBACK_MAXSIZE = 512 * 1024; // double the size

    /**
     * Priority constant for the println method; use Log.v.
     */
    private static final int VERBOSE = 2; // Log.VERBOSE

    /**
     * Priority constant for the println method; use Log.d.
     */
    private static final int DEBUG = 3; // Log.DEBUG

    /**
     * Priority constant for the println method; use Log.i.
     */
    private static final int INFO = 4; // Log.INFO

    /**
     * Priority constant for the println method; use Log.w.
     */
    private static final int WARN = 5; // Log.WARN

    /**
     * Priority constant for the println method; use Log.e.
     */
    private static final int ERROR = 6; // Log.ERROR

    /**
     * Priority constant for the println method.
     */
    private static final int ASSERT = 7; // Log.ASSERT

    /**
     * Priority constant for the println method; use Log.f.
     */
    private static final int FEEDBACK = 8; //意见反馈时日志上报级别

    /**
     * Priority constant for the println method; use Log.r.
     */
    private static final int LOG_LEVEL = VERBOSE;

    /** log文件路径 */
    private static final String LOG_PATH = "/Android/data/%s/files/";
    /** 锁 */
    private static Object[] sLock = new Object[0];

    /** app的log文件存放路径 */
    private static String sAppPath;

    /**
     * 线程执行service
     */
    private static ExecutorService sExecutorService = Executors.newFixedThreadPool(1);

    /** 调试log类型的对象 */
    private static NTLogInstance sDebugInstance;
    /** 反馈log类型的对象 */
    private static NTLogInstance sFeedbackInstance;

    public static void init(String packageName, File file) {
        synchronized (sLock) {
            if (file != null) {
                if (!file.exists() && !file.mkdir()) {
                    sAppPath = String.format(LOG_PATH, packageName);
                    File dir = new File(sAppPath);
                    if (!dir.exists()) {
                        dir.mkdir();
                    }
                }
                else {
                    sAppPath = file.getPath() + File.separator;
                }
            }
            else if (!TextUtils.isEmpty(packageName)) {
                sAppPath = String.format(LOG_PATH, packageName);
            }

            sDebugInstance = new NTLogInstance(DEBUG_TEMP_FILE, DEBUG_LAST_FILE, DEBUG_NOW_FILE, DEBUG_MAXSIZE);
            sFeedbackInstance = new NTLogInstance(FEEDBACK_TEMP_FILE, FEEDBACK_LAST_FILE, FEEDBACK_NOW_FILE, FEEDBACK_MAXSIZE);
        }
    }

    public static void d(String tag, String msg) {
        log(tag, msg, DEBUG);
    }

    public static void v(String tag, String msg) {
        log(tag, msg, VERBOSE);
    }

    public static void e(String tag, String msg) {
        log(tag, msg, ERROR);
    }

    public static void i(String tag, String msg) {
        log(tag, msg, INFO);
    }

    public static void w(String tag, String msg) {
        log(tag, msg, WARN);
    }

    public static void f(String tag, String msg) {
        log(tag, msg, FEEDBACK);
    }

    public static void deleteFeedbackLogFile() {
        if (sFeedbackInstance == null) {
            return;
        }
        sFeedbackInstance.deleteAllFile();
    }

    public static File openAbsoluteFile(String name) {
        if (sAppPath == null || sAppPath.length() == 0) {
            return null;
        } else {
            File file = new File(sAppPath + name);
            return file;
        }
    }

    public static boolean zipFeedbackLogFile(String zipFileName) {
        if (TextUtils.isEmpty(zipFileName) || sFeedbackInstance == null) {
            return false;
        }
        return zipLogFile(zipFileName, sFeedbackInstance);
    }


    /**
     * log类型类
     */
    private static class NTLogInstance {
        /** 输出流 */
        OutputStream mOutputStream;
        /** temp文件名称 */
        String mLogTempFile;
        /** last */
        String mLogLastFile;
        /** now文件名称 */
        String mLogNowFile;
        // 文件最大容量
        int mFileMaxSize;
        // 当前文件大小
        long mFileSize;

        public NTLogInstance(String tempFileName, String lastFileName, String nowFileName, int mMaxSize) {
            if (TextUtils.isEmpty(tempFileName) || TextUtils.isEmpty(lastFileName) || TextUtils.isEmpty(nowFileName) || mMaxSize <= 0) {
                throw new ExceptionInInitializerError("NTLogInstance InitializerError");
            }
            this.mLogTempFile = tempFileName;
            this.mLogLastFile = lastFileName;
            this.mLogNowFile = nowFileName;
            this.mFileMaxSize = mMaxSize;
        }

        public boolean check() {
            if (TextUtils.isEmpty(mLogLastFile)
                    || TextUtils.isEmpty(mLogNowFile)
                    || TextUtils.isEmpty(mLogTempFile) || mFileMaxSize == 0)
                return false;
            return true;
        }

        public void deleteAllFile() {
            File temp = openAbsoluteFile(mLogTempFile);
            File now = openAbsoluteFile(mLogNowFile);
            File last = openAbsoluteFile(mLogLastFile);
            if (temp != null && temp.exists()) {
                temp.delete();
            }
            if (now != null && now.exists()) {
                now.delete();
            }
            if (last != null && last.exists()) {
                last.delete();
            }
        }

        /**
         * 打开临时文件,返回输出流
         * @return
         */
        public OutputStream openTempFileOutputStream() {
            if (mOutputStream == null) {
                try {
                    if (TextUtils.isEmpty(sAppPath) || TextUtils.isEmpty(mLogTempFile)) {
                        return null;
                    }
                    File file = openAbsoluteFile(mLogTempFile);

                    if (file == null) {
                        return null;
                    }

                    if (file.exists()) {
                        mOutputStream = new FileOutputStream(file, true);
                        mFileSize = file.length();
                    } else {
                        // file.createNewFile();
                        mOutputStream = new FileOutputStream(file);
                        mFileSize = 0;
                    }
                } catch (Exception e) {
                    ZLog.e(TAG, e.getMessage());
                }
            }

            return mOutputStream;
        }

        public void closeOutputStream() {
            try {
                if (mOutputStream != null) {
                    mOutputStream.close();
                    mOutputStream = null;
                    mFileSize = 0;
                }
            } catch (Exception e) {
                ZLog.e(TAG, e.getMessage());
            }
        }

    }


    private static void log(String tag, String msg, int level) {
        if (tag == null)
            tag = "TAG_NULL";
        if (msg == null)
            msg = "MSG_NULL";

        if (level >= LOG_LEVEL) {
            if ((sCURRENT_DEBUG_SCOP & TO_CONSOLE) != 0) {
                logToConsole(tag, msg, level);
            }

            if ((sCURRENT_DEBUG_SCOP & TO_FILE) != 0) {
                final String Tag = tag;
                final String Msg = msg;
                final int Level = level;
                sExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        if ((sCURRENT_DEBUG_SCOP & DEBUG_TO_FILE) != 0 && Level >= DEBUG && Level <= ASSERT) {
                            logDebugToFile(Tag, Msg);
                        } else if ((sCURRENT_DEBUG_SCOP & FEEDBACK_TO_FILE) != 0 && Level == FEEDBACK){
                            logFeedbackToFile(Tag, Msg);
                        }
                    }
                });
            }
        }
    }

    private static void logDebugToFile(String tag, String msg) {
        logToFile(tag, msg, sDebugInstance);
    }

    private static void logFeedbackToFile(String tag, String msg) {
        logToFile(tag, msg, sFeedbackInstance);
    }

    /**
     * 打印日志到文件
     * @param tag
     * @param msg
     * @param mInstance
     */
    private static void logToFile(String tag, String msg, NTLogInstance mInstance) {
        synchronized (sLock) {
            if (mInstance == null) return;
            // 打开相应对象的输出流
            OutputStream outStream = mInstance.openTempFileOutputStream();

            // 写入打印信息
            if (outStream != null) {
                try {
                    byte[] d = getLogStr(tag, msg).getBytes("utf-8");
                    // byte[] d = msg.getBytes("utf-8");
                    if (mInstance.mFileSize < mInstance.mFileMaxSize) {
                        outStream.write(d);
                        outStream.write("\r\n".getBytes());
                        outStream.flush();
                        mInstance.mFileSize += d.length;
                    } else {  // 当temp的log日志文件大小超过设定的大小值时, 将temp重命名为last,然后新的数据重新开始写temp
                        mInstance.closeOutputStream();
                        if (renameLogFile(mInstance)) {
                            logToFile(tag, msg, mInstance);
                        }
                    }

                } catch (Exception e) {
                    ZLog.e(TAG, e.getMessage());
                }
            }
        }
    }


    private static Calendar mDate = Calendar.getInstance();
    private static StringBuffer mBuffer = new StringBuffer();

    /**
     * 拼接Log字符串.添加时间信息.
     *
     * @param tag
     * @param msg
     * @return
     */
    private static String getLogStr(String tag, String msg) {

        mDate.setTimeInMillis(System.currentTimeMillis());

        mBuffer.setLength(0);
        mBuffer.append("[");
        mBuffer.append(tag);
        mBuffer.append(" : ");
        mBuffer.append(mDate.get(Calendar.MONTH) + 1);
        mBuffer.append("-");
        mBuffer.append(mDate.get(Calendar.DATE));
        mBuffer.append(" ");
        mBuffer.append(mDate.get(Calendar.HOUR_OF_DAY));
        mBuffer.append(":");
        mBuffer.append(mDate.get(Calendar.MINUTE));
        mBuffer.append(":");
        mBuffer.append(mDate.get(Calendar.SECOND));
        mBuffer.append(":");
        mBuffer.append(mDate.get(Calendar.MILLISECOND));
        mBuffer.append("] ");
        mBuffer.append(msg);

        return mBuffer.toString();
    }

    /**
     * 重命名临时文件为最近文件(temp -> last)
     * @param mInstance
     * @return
     */
    private static boolean renameLogFile(NTLogInstance mInstance) {
        synchronized (sLock) {
            if (mInstance == null || !mInstance.check()) return false;

            File file = openAbsoluteFile(mInstance.mLogTempFile);
            File destFile = openAbsoluteFile(mInstance.mLogLastFile);

            if (destFile == null) return false;
            if (destFile.exists()) {
                destFile.delete();
            }
            return file.renameTo(destFile);
        }
    }

    /**
     * 将log打到控制台
     *
     * @param tag
     * @param msg
     * @param level
     */
    private static void logToConsole(String tag, String msg, int level) {
        switch (level) {
            case Log.DEBUG:
                Log.d(tag, msg);
                break;
            case Log.INFO:
                Log.i(tag, msg);
                break;
            case Log.VERBOSE:
                Log.v(tag, msg);
                break;
            case Log.WARN:
                Log.w(tag, msg);
                break;
            default:
            case Log.ERROR:
                Log.e(tag, msg);
                break;
        }
    }

    /**
     * zip压缩指定instance的当前log: 先将last拷贝到now,然后压缩当前now的log
     * @param zipFileName
     * @param mInstance
     * @return
     */
    private static boolean zipLogFile(String zipFileName, NTLogInstance mInstance) {
        // backup ui log file
        if (!backLogFile(mInstance)) return false;

        File destFile = openAbsoluteFile(zipFileName);
        if (destFile.exists()) {
            destFile.delete();
        }
        try {
            destFile.createNewFile();
        } catch (IOException e1) {
            ZLog.e(TAG, e1.getMessage());
            return false;
        }
        File srcFile = openAbsoluteFile(mInstance.mLogNowFile);
        boolean ret = FileUtil.zip(srcFile, destFile);
        destFile = null;
        srcFile = null;
        return ret;
    }

    /*************************tool method*******************************/
    /**
     * 将last的log拷贝到now
     * @param mInstance
     * @return
     */
    private static boolean backLogFile(NTLogInstance mInstance) {
        synchronized (sLock) {
            if (mInstance == null || TextUtils.isEmpty(mInstance.mLogLastFile)
                    || TextUtils.isEmpty(mInstance.mLogNowFile)
                    || TextUtils.isEmpty(mInstance.mLogTempFile))
                return false;
            try {
                mInstance.closeOutputStream();

                File destFile = openAbsoluteFile(mInstance.mLogNowFile);

                if (destFile == null) return false;
                if (destFile.exists()) {
                    destFile.delete();
                }

                try {
                    destFile.createNewFile();
                } catch (IOException e1) {
                    ZLog.e(TAG, e1.getMessage());
                    return false;
                }

                File srcFile1 = openAbsoluteFile(mInstance.mLogLastFile);
                File srcFile2 = openAbsoluteFile(mInstance.mLogTempFile);

                if (srcFile1 == null || srcFile2 == null) return false;
                FileUtil.copyFile(srcFile1, srcFile2, destFile);
                mInstance.openTempFileOutputStream();

            } catch (IOException e) {
                ZLog.e(TAG, e.getMessage());
                Log.w("NTLog", "backLogFile fail:" + e.toString());
                return false;
            }
            return true;
        }
    }

}

