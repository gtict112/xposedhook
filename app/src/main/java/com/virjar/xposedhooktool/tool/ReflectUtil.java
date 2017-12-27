package com.virjar.xposedhooktool.tool;

import android.util.Log;

import com.virjar.xposedhooktool.hotload.SharedObject;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.<br/>处理XposedHelpers一些不能处理的问题
 */

public class ReflectUtil {
    //能够子子类向父类寻找moethod
    public static void findAndHookMethodWithSupperClass(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        Class theClazz = clazz;
        NoSuchMethodError error = null;
        do {
            try {
                XposedHelpers.findAndHookMethod(theClazz, methodName, parameterTypesAndCallback);
                return;
            } catch (NoSuchMethodError e) {
                if (error == null) {
                    error = e;
                }
            }
        } while ((theClazz = theClazz.getSuperclass()) != null);
        throw error;
    }

    //很多时候只有一个名字，各种寻找参数类型太麻烦了
    public static void findAndHookMethodOnlyByMethodName(Class<?> clazz, String methodName, XC_MethodHook xcMethodHook) {
        Class theClazz = clazz;

        do {
            Method[] declaredMethods = theClazz.getDeclaredMethods();
            for (Method method : declaredMethods) {
                if (method.getName().equals(methodName)) {
                    XposedBridge.log("find method:" + methodName + " for class :" + clazz.getName());
                    Log.i("weijia", method.toString());
                    XposedBridge.hookMethod(method, xcMethodHook);
                    return;
                }
            }
        } while ((theClazz = theClazz.getSuperclass()) != null);
        throw new NoSuchMethodError("no method " + methodName + " for class:" + clazz.getName());
    }

    public static void findAndHookMethodOnlyByMethodName(String className, String methodName, XC_MethodHook xcMethodHook) {
        Class<?> aClass = XposedHelpers.findClass(className, SharedObject.masterClassLoader);
        findAndHookMethodOnlyByMethodName(aClass, methodName, xcMethodHook);
    }
}
