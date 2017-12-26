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
    private static class FragmentResumeHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object hasCalled = param.getObjectExtra("hasCalled");
            if (hasCalled != null) {
                return;
            }
            param.setObjectExtra("hasCalled", "true");
            ActivityHooker.sFragments.add(param.thisObject.getClass().getName());
        }
    }

    private static FragmentResumeHook fragmentResumeHook = new FragmentResumeHook();

    static void hookFragment() {
        Class<?> fragmentClass;
        try {
            fragmentClass = SharedObject.masterClassLoader.loadClass("android.support.v4.app.Fragment");
        } catch (ClassNotFoundException e) {
            XposedBridge.log("can not lod class \"android.support.v4.app.Fragment\"");
            XposedBridge.log(e);
            return;
        }
        XposedHelpers.findAndHookMethod(fragmentClass, "onResume", fragmentResumeHook);
    }
}
