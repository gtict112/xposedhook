package com.virjar.xposedhooktool.tool.log;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class LogUtil {


    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss.SSS");
    private static final boolean deleteFile = true;

    private static List<String> buffer = new LinkedList<String>();
    private static File filename;
    public static String packageName;

    public static void start(String packageName, String processName) {
        LogUtil.packageName = packageName;
        if (filename != null) {
            return;
        }
        XposedBridge.log("file start");
        String dir = "/sdcard/log/" + packageName;
        File file = new File(dir);
        boolean create = file.mkdirs();
        XposedBridge.log("create file : " + create + ",processName=" + processName);

        processName = processName.replace(":", "_");
        if (deleteFile) {
            File[] fs = file.listFiles();
            if (fs != null && fs.length > 0) {
                for (int i = 0; i < fs.length; i++) {
                    if (fs[i].getName().startsWith(processName + "_")) {
                        fs[i].delete();
                    }
                }
            }
        }
        filename = new File(dir, processName + "_" + System.currentTimeMillis() + ".txt");

        XposedBridge.log("filename:" + filename);

        appendFile("\n<=========================================================>\n");
        XposedBridge.log("start thread");
        new Thread() {
            public void run() {
                while (true) {
                    String msg = null;
                    synchronized (buffer) {
                        if (buffer.isEmpty()) {
                            try {
                                buffer.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!buffer.isEmpty()) {
                            msg = buffer.remove(0);
                        }
                    }

                    if (msg == null) {
                        continue;
                    }
                    appendFile(msg);
                }
            }
        }.start();
    }

    public static String getLogFileName() {
        return filename.getAbsolutePath();
    }

    public static String printHexString(byte[] b) {
        if (b == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            sb.append(b2h(b[i]));
        }
        return sb.toString();
    }

    public static String b2h(byte oneByte) {
        String hex = Integer.toHexString(oneByte & 0xFF);
        if (hex.length() == 1) {
            hex = '0' + hex;
        }
        return hex.toUpperCase();
    }

    public static Application getApplicationUsingReflection() throws Exception {
        return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null,
                (Object[]) null);
    }


    public static void outLog(String msg) {
        if (msg != null) {
            //XposedBridge.log("msg:" + msg);
            synchronized (buffer) {
                String time = df.format(System.currentTimeMillis());
                msg = time + "\n" + msg + "\n";
                buffer.add(msg);
                buffer.notify();
            }
        }

    }

    public static void outSslLog(String msg) {
        if (msg != null) {
            msg = "huajiao_ssl:" + msg;
        }
        //XposedBridge.log("msg:" + msg);
        synchronized (buffer) {
            String time = df.format(System.currentTimeMillis());
            msg = time + "\n" + msg + "\n";
            buffer.add(msg);
            buffer.notify();
        }
    }

    private static void appendFile(String msg) {
        if (msg == null) {
            return;
        }


        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filename, true);
            fos.write(msg.getBytes());
            fos.flush();
        } catch (Throwable e) {
            e.printStackTrace();
            XposedBridge.log("msg:" + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static void outTrack(String append) {
        String msg = append + getTrack();
        LogUtil.outLog(msg);
    }

    public static String getTrack() {
        return getTrack(new Throwable());
    }

    public static String getTrack(Throwable e) {
        String msg = "\n=============>\n";
        if (e != null) {
            StackTraceElement[] ste = e.getStackTrace();
            for (StackTraceElement stackTraceElement : ste) {
                msg += (stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName() + ":"
                        + stackTraceElement.getLineNumber() + "\n");
            }
        }
        msg += "<================\n";
        return msg;
    }
}
