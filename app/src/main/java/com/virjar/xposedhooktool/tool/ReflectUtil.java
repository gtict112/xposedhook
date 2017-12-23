package com.virjar.xposedhooktool.tool;

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
        } while ((theClazz = clazz.getSuperclass()) != null);
        throw error;
    }
}
