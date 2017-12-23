package com.virjar.xposedhooktool.droidsword;

import com.virjar.xposedhooktool.hotload.SharedObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.<br/>
 * 把fragment的信息打印出来
 */
class FragmentHooker {
    static void hookFragment() {
        try {
            XposedHelpers.findAndHookMethod(
                    SharedObject.masterClassLoader.loadClass("android.support.v4.app.Fragment"),
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            ActivityHooker.sFragments.add(param.thisObject.getClass().getName());
                        }
                    });
        } catch (ClassNotFoundException e) {
            XposedBridge.log("can not lod class \"android.support.v4.app.Fragment\"");
            XposedBridge.log(e);
        }
    }
}
