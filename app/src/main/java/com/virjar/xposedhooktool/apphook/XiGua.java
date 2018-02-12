package com.virjar.xposedhooktool.apphook;

import com.virjar.xposedhooktool.hotload.XposedHotLoadCallBack;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/1/11.
 */

public class XiGua implements XposedHotLoadCallBack {
    @Override
    public void onXposedHotLoad() {

    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return StringUtils.equalsIgnoreCase(loadPackageParam.packageName, "com.ss.android.article.video");
    }
}
