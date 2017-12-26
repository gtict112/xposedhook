package com.virjar.xposedhooktool.tool.okhttp;

import android.annotation.SuppressLint;

import com.google.common.collect.Maps;
import com.virjar.xposedhooktool.hotload.SharedObject;
import com.virjar.xposedhooktool.tool.ReflectUtil;
import com.virjar.xposedhooktool.tool.log.LogUtil;

import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/26.<br/>
 * 由于okhttpclient习惯使用异步，导致线程池里面拦截不到业务逻辑
 */

public class OkHttpClientHook {
    public static class NewCallPrintCallBack extends XC_MethodHook {
        private Class callBackClazz;
        private Class responseClazz;
        private Class callClazz;
        private static Map<String, NewCallPrintCallBack> instanceMap = Maps.newHashMap();

        NewCallPrintCallBack(Class callBackClazz, Class responseClazz, Class callClazz) {
            this.callBackClazz = callBackClazz;
            this.responseClazz = responseClazz;
            this.callClazz = callClazz;
        }

        private synchronized static NewCallPrintCallBack getInstance(Class callBackClazz, Class responseClazz, Class callClazz) {
            String key = callBackClazz.getName() + "__" + responseClazz.getName();
            if (instanceMap.containsKey(key)) {
                return instanceMap.get(key);
            }
            NewCallPrintCallBack newCallPrintCallBack = new NewCallPrintCallBack(callBackClazz, responseClazz, callClazz);
            instanceMap.put(key, newCallPrintCallBack);
            return newCallPrintCallBack;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            LogUtil.outTrack("start new Request: " + param.args[0]);
            //在异步回调网络响应的时候，打印回调堆栈和回调函数
            if (param.getResult() == null) {
                //ignore if new Call call failed
                return;
            }
            XposedHelpers.findAndHookMethod(param.getResult().getClass(), "enqueue", callBackClazz, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object callBack = param.args[0];
                    if (callClazz == null) {
                        ReflectUtil.findAndHookMethodWithSupperClass(callBack.getClass(), "onResponse", responseClazz, dataResponseCallPrintCallBack);
                    } else {
                        ReflectUtil.findAndHookMethodWithSupperClass(callBack.getClass(), "onResponse", callClazz, responseClazz, dataResponseCallPrintCallBack);
                    }
                }
            });
        }
    }


    private static class DataResponseCallPrintCallBack extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            LogUtil.outTrack("network response");
            //TODO mock response
        }
    }

    private static DataResponseCallPrintCallBack dataResponseCallPrintCallBack = new DataResponseCallPrintCallBack();

    @SuppressLint("PrivateApi")
    public static void hook() {
        try {
            Class callBackClazz = SharedObject.masterClassLoader.loadClass("okhttp3.Callback");
            Class okHttpClientClazz = XposedHelpers.findClass("okhttp3.OkHttpClient", SharedObject.masterClassLoader);
            Class requestClazz = XposedHelpers.findClass("okhttp3.Request", SharedObject.masterClassLoader);
            Class callClazz = XposedHelpers.findClass("okhttp3.Call", SharedObject.masterClassLoader);
            final Class responseClazz = XposedHelpers.findClass("okhttp3.Response", SharedObject.masterClassLoader);
            //在构造请求的时候，输出请求
            XposedHelpers.findAndHookMethod(okHttpClientClazz, "newCall", requestClazz, NewCallPrintCallBack.getInstance(callBackClazz, responseClazz, callClazz));


        } catch (ClassNotFoundException e) {
            //ignore
        }
        try {
            SharedObject.masterClassLoader.loadClass("com.squareup.okhttp.Callback");
            Class callBackClazz = SharedObject.masterClassLoader.loadClass("com.squareup.okhttp.Callback");
            Class okHttpClientClazz = XposedHelpers.findClass("com.squareup.okhttp.OkHttpClient", SharedObject.masterClassLoader);
            Class requestClazz = XposedHelpers.findClass("com.squareup.okhttp.Request", SharedObject.masterClassLoader);

            final Class responseClazz = XposedHelpers.findClass("com.squareup.okhttp.Response", SharedObject.masterClassLoader);
            //在构造请求的时候，输出请求
            XposedHelpers.findAndHookMethod(okHttpClientClazz, "newCall", requestClazz, NewCallPrintCallBack.getInstance(callBackClazz, responseClazz, null));

        } catch (ClassNotFoundException e) {
            //ignore
        }

        try {
            SharedObject.masterClassLoader.loadClass("com.android.okhttp.Callback");
        } catch (ClassNotFoundException e) {
            //ignore
        }
    }

}