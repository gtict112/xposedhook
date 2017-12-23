package com.virjar.xposedhooktool.apphook;

import com.virjar.xposedhooktool.hotload.SharedObject;
import com.virjar.xposedhooktool.hotload.XposedHotLoadCallBack;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/23.<br/>
 * 这是一个demo，演示如何使用热加载机制来hook代码，需要注意的是这入口需要在 /assets/hotload_entry.txt里面配置，才能被热加载模块启用
 */

public class TestHook implements XposedHotLoadCallBack {
    private boolean hasHook = false;

    @Override
    public void onXposedHotLoad() {
        if (hasHook) {
            return;
        }
        hasHook = true;

        //这里是真正hook的代码，所有需要访问的参数，都在com.virjar.xposedhooktool.hotload.SharedObject里面
        XposedBridge.log("hook package: " + SharedObject.loadPackageParam.packageName + " and plugin location is: " + SharedObject.pluginApkLocation);
        XposedBridge.log("test");
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        //只hook易通行
        return StringUtils.equalsIgnoreCase(loadPackageParam.packageName, "enfc.metro");
    }
}
