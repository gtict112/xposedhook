package com.virjar.xposedhooktool.droidsword;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.Sets;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.<br/>
 */

class ActivityHooker {
    @SuppressLint("StaticFieldLeak")
    private static TextView sTextView = null;
    private static String sActivityName = null;
    private static String sViewName = null;
    static Set<String> sFragments = Sets.newConcurrentHashSet();

    static void hookActivity() {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Activity activity = (Activity) param.thisObject;
                addTextView(activity);
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

    private static void setActionInfoToMenu(String activityName, String viewName) {
        sTextView.setText(getActionInfo(activityName, viewName));
    }

    private static String getActionInfo(String activityName, String viewName) {
        if (activityName.length() > 0) {
            sActivityName = activityName;
        }
        if (viewName.length() > 0) {
            sViewName = viewName;
        }
        int pid = android.os.Process.myPid();
        String ret = "Activity: " + sActivityName + " \nPid: " + pid + " \nClick: " + sViewName;
        XposedBridge.log(ret);
        return ret;
    }
}
