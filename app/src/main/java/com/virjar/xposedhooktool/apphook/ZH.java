package com.virjar.xposedhooktool.apphook;

import android.util.Log;

import com.virjar.xposedhooktool.hotload.XposedHotLoadCallBack;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/2/26.
 */

public class ZH implements XposedHotLoadCallBack {
    @Override
    public void onXposedHotLoad() {
        Log.i("weijia", "hook 回调");
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Log.i("weijia", "hook 判定" + loadPackageParam.packageName);
        return StringUtils.equalsIgnoreCase(loadPackageParam.packageName, "com.air.sz");
    }
}
