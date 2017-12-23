package com.virjar.xposedhooktool.droidsword;

import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;

import org.apache.commons.lang3.StringUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.
 * <br/>hook用户事件回掉
 *
 * @author virjar
 */
class ViewClickedHooker {
    private static class EventPrintHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            MotionEvent event = (MotionEvent) param.args[0];
            if (event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_DOWN) {
                return;
            }
            View view = (View) param.thisObject;
            String onClickListenerName;
            if (view instanceof AdapterView) {
                onClickListenerName = XposedHelpers.getObjectField(view, "mOnItemClickListener").getClass().getName();
            } else {
                Object mListenerInfo = XposedHelpers.getObjectField(view, "mListenerInfo");
                Object objectField = null;
                if (mListenerInfo != null) {
                    objectField = XposedHelpers.getObjectField(mListenerInfo, "mOnClickListener");
                }
                if (objectField == null) {
                    onClickListenerName = "none";
                } else {
                    onClickListenerName = objectField.getClass().getName();
                }
            }
            if (StringUtils.startsWith(onClickListenerName, "android.")) {
                //ignore if event is system dispatch ,such as android.widget.LinearLayout,
                //because of dispatch is not the final event handler
                return;
            }

            ActivityHooker.setActionInfoToMenu("",
                    "view:<" + view.getClass().getName() + ">  viewID:< " + view.getId() + "> eventCallBack:<" + onClickListenerName + ">");
        }
    }

    private static EventPrintHook eventPrintHook = new EventPrintHook();

    static void hookEvent() {
        XposedHelpers.findAndHookMethod(View.class, "onTouchEvent", MotionEvent.class, eventPrintHook);
        XposedHelpers.findAndHookMethod(View.class, "dispatchTouchEvent", MotionEvent.class, eventPrintHook);

//为啥这个不生效
//        XposedHelpers.findAndHookConstructor(View.class, new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                Object viewClass = param.thisObject;
//                if (viewClass == null) {
//                    return;
//                }
//                ReflectUtil.findAndHookMethodWithSupperClass(viewClass.getClass(), "onTouchEvent", MotionEvent.class, eventPrintHook);
//                ReflectUtil.findAndHookMethodWithSupperClass(viewClass.getClass(), "dispatchTouchEvent", MotionEvent.class, eventPrintHook);
//            }
//        });
    }
}
