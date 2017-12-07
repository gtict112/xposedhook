package com.virjar.xposedhooktool;

import com.virjar.xposedhooktool.log.LogUtil;
import com.virjar.xposedhooktool.socket.NetDataPrinter;
import com.virjar.xposedhooktool.webview.WebViewDebuggerController;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * XposedInit
 *
 * @author wrbug
 * @since 2017/4/20
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static String PACKAGE_NAME = "com.infothinker.gzmetro";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals(PACKAGE_NAME)) {
            //注意有问题，多个apk的情况如何处理包名???
            LogUtil.start(lpparam.packageName, lpparam.processName);
            NetDataPrinter.hook(null, true);
            WebViewDebuggerController.enableDebug(lpparam.classLoader, lpparam.packageName);
        }

    }
}
