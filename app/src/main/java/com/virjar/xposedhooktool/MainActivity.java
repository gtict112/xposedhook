package com.virjar.xposedhooktool;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setText("插件安装完成");
        setContentView(textView);
        new PluginLoadTask().start();

        new Thread() {
            @Override
            public void run() {
                try {
                    //10秒后自杀
                    Thread.sleep(10000);
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.finish();
                            System.exit(0);
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private class PluginLoadTask extends Thread {
        PluginLoadTask() {
            super("restart-master-thread");
        }

        @Override
        public void run() {
            super.run();
            InputStream inputStream = getContextClassLoader().getResourceAsStream("assets/hotload_entry.txt");
            if (inputStream == null) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "hotload_entry 加载失败", Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            try {
                BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(inputStream));
                String taskName;
                while ((taskName = moduleClassesReader.readLine()) != null) {
                    taskName = taskName.trim();
                    //ignore for action
                    if (!taskName.startsWith("action:")) {
                        continue;
                    }
                    taskName = taskName.substring("action:".length());
                    int i = taskName.indexOf(":");
                    if (i <= 0) {
                        //illegal
                        continue;
                    }
                    String action = taskName.substring(0, i);
                    String param = taskName.substring(i + 1);
                    if (StringUtils.equalsIgnoreCase(action, "kill-package")) {
                        restartPackage(param);
                    }

                }
            } catch (IOException e) {
                Log.e("xposedhooktool", "读取 hotload_entry 失败", e);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    private void restartPackage(String packageName) {
        if (StringUtils.isBlank(packageName)) {
            return;
        }
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            Log.e("xposedhooktool", "can not get activityManager instance");
            return;
        }
        //先杀掉后台进程
        activityManager.killBackgroundProcesses(packageName);

        //再尝试杀掉前台进程
        //TODO
//        PackageManager pManager = getPackageManager();
//        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
//        for (ActivityManager.RunningAppProcessInfo runningProcess : runningAppProcesses) {
//            try {
//                if (!StringUtils.equalsIgnoreCase(runningProcess.processName, packageName)) {
//                    continue;
//                }
//                ApplicationInfo applicationInfo = pManager.getPackageInfo(packageName, 0).applicationInfo;
//                if (filterApp(applicationInfo)) {
//                    forceStopPackage(packageName, this);
//                }
//                break;
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }


    }

    /**
     * 强制停止应用程序
     *
     * @param pkgName
     */
    private void forceStopPackage(String pkgName, Context context) throws Exception {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Method method = Class.forName("android.app.ActivityManager").getMethod("forceStopPackage", String.class);
        method.invoke(am, pkgName);
    }

    /**
     * 判断某个应用程序是 不是三方的应用程序
     *
     * @param info
     * @return
     */
    public boolean filterApp(ApplicationInfo info) {
        if ((info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return true;
        } else if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return true;
        }
        return false;
    }
}
