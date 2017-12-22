package com.virjar.xposedhooktool.hotload;

import android.content.Context;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/21.
 */

public class HotLoadPackageEntry {

    public static void entry(ClassLoader masterClassLoader, ClassLoader pluginClassLoader, Context context, XC_LoadPackage.LoadPackageParam loadPackageParam, String pluginApkLocation) {
        XposedBridge.log("test");
    }
}
