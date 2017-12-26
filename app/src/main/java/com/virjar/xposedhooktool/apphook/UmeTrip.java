package com.virjar.xposedhooktool.apphook;

import com.virjar.xposedhooktool.hotload.XposedHotLoadCallBack;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/26.<br/>
 * 航旅纵横分析
 */

public class UmeTrip implements XposedHotLoadCallBack {
    @Override
    public void onXposedHotLoad() {

    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return StringUtils.equalsIgnoreCase("com.umetrip.android.msky.app", loadPackageParam.packageName);
    }
}
