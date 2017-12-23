package com.virjar.xposedhooktool.hotload;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/23.<br/>
 * 热加载代码入口，为了避免和xposed本身引起用法的混淆，这里建立新的入口规范
 */

public interface XposedHotLoadCallBack {
    void onXposedHotLoad();

    boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam);
}
