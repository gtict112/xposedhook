package com.virjar.xposedhooktool.droidsword;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.Sets;
import com.virjar.xposedhooktool.tool.ReflectUtil;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.<br/>
 *
 * @author virjar
 */
class ActivityHooker {
    @SuppressLint("StaticFieldLeak")
    private static TextView sTextView = null;
    private static String sActivityName = null;
    private static String sViewName = null;
    static Set<String> sFragments = Sets.newConcurrentHashSet();
    private static Set<Class> hookedClass = Sets.newConcurrentHashSet();
    private static ResumeHookCallBack resumeHookCallBack = new ResumeHookCallBack();

    private static class ResumeHookCallBack extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            Object hasCalled = param.getObjectExtra("hasCalled");
            if (hasCalled != null) {
                return;
            }
            param.setObjectExtra("hasCalled", "true");
            Activity activity = (Activity) param.thisObject;
            addTextView(activity);
        }
    }

    static void hookActivity() {
        //constructor存在继承链，这样可以hook所有实现类，避免直接hook onResume时，目标代码没有call supper，导致挂钩无效
        XposedHelpers.findAndHookConstructor(Activity.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object activity = param.thisObject;
                if (activity == null) {
                    return;
                }
                XposedBridge.log("hook class " + activity.getClass());
                Class<?> aClass = activity.getClass();
                if (hookedClass.contains(aClass)) {
                    return;
                }
                hookedClass.add(aClass);
                //避免重复hook，所以使用唯一对象，因为xposed内部是一个set来存储钩子函数，唯一对象可以消重
                //XposedHelpers.findAndHookMethod(aClass, "onResume", resumeHookCallBack);
                ReflectUtil.findAndHookMethodWithSupperClass(aClass, "onResume", resumeHookCallBack);
            }
        });

    }

    private static void addTextView(Activity activity) {
        String className = activity.getClass().getName();

        if (sTextView == null) {
            genTextView(activity);
        }
        if (sTextView.getParent() != null) {
            ViewParent parent = sTextView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(sTextView);
            }
        }
        FrameLayout frameLayout = (FrameLayout) activity.getWindow().getDecorView();
        frameLayout.addView(sTextView);
        setActionInfoToMenu(className, "");
        sTextView.bringToFront();
    }


    private static void genTextView(Activity activity) {
        sTextView = new TextView(activity);
        sTextView.setTextSize(8f);
        sTextView.setY(48 * 2f);
        sTextView.setBackgroundColor(Color.parseColor("#cc888888"));
        sTextView.setTextColor(Color.WHITE);
        sTextView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));


        sTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder fragments = new StringBuilder();
                for (String fragment : sFragments) {
                    fragments.append(fragment).append("\n");// += fragment + "\n";
                }

                if (fragments.length() > 0) {
                    Toast.makeText(sTextView.getContext(), fragments, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    static void setActionInfoToMenu(String activityName, String viewName) {
        sTextView.setText(getActionInfo(activityName, viewName));
        //sTextView.invalidate();
    }

    private static String getActionInfo(String activityName, String viewName) {
        if (activityName.length() > 0) {
            sActivityName = activityName;
        }
        if (viewName.length() > 0) {
            sViewName = viewName;
        } else {
            sViewName = "none";
        }
        int pid = android.os.Process.myPid();
        String ret = "Activity: " + sActivityName + " \nPid: " + pid + " \naction: " + sViewName;
        //XposedBridge.log(ret);
        Log.i("DroidSword", ret);
        return ret;
    }
}
