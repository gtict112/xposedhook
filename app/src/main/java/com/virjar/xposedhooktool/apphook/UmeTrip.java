package com.virjar.xposedhooktool.apphook;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.virjar.xposedhooktool.hotload.SharedObject;
import com.virjar.xposedhooktool.hotload.XposedHotLoadCallBack;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2017/12/26.<br/>
 * 航旅纵横分析
 */

public class UmeTrip implements XposedHotLoadCallBack {
    static final String tag = "weijia";

    @Override
    public void onXposedHotLoad() {
        //这是请求前加密逻辑，其中参数1是object，为参数body。参数2位协议编号，代表请求含义。参数3位协议版本
        XposedHelpers.findAndHookMethod("com.ume.android.lib.common.network.RequestBodyBuilder", SharedObject.masterClassLoader, "builder", Object.class, String.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.i(tag, "object:" + param.args[0] + " toJson:" + JSONObject.toJSONString(param.args[0]) + "  str1:" + param.args[1] + "    str2:" + param.args[2]);
            }
        });
    }

    @Override
    public boolean needHook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        return StringUtils.equalsIgnoreCase("com.umetrip.android.msky.app", loadPackageParam.packageName);
    }
}
