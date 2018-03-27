package com.virjar.xposedhooktool.tool.okhttp;

import com.virjar.xposedhooktool.hotload.SingletonXC_MethodHook;

import java.util.concurrent.ThreadPoolExecutor;

import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2018/3/27.
 */

public class ThreadPoolHook {
    private static ThreadLocal<Throwable> stackTraceThreadLocal = new ThreadLocal<>();

    public static Throwable getThreadSubmitEntry() {
        return stackTraceThreadLocal.get();
    }

    public static Throwable stackTraceChain() {
        Throwable submitEntry = getThreadSubmitEntry();
        if (submitEntry == null) {
            return new Throwable();
        }
        return new Throwable(submitEntry);
    }

    public static void monitorThreadPool() {
        XposedHelpers.findAndHookMethod(ThreadPoolExecutor.class, "execute", Runnable.class, new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                //LogUtil.outTrack("execute a asynchronized task");
                Runnable target = (Runnable) param.args[0];

                Throwable parentStackTrace = getThreadSubmitEntry();
                Throwable theStackTrace;
                if (parentStackTrace != null) {
                    theStackTrace = new Throwable("parent submit task stack entry", parentStackTrace);
                } else {
                    theStackTrace = new Throwable("parent submit task stack entry");
                }
                param.args[0] = new RunnableMonitor(target, theStackTrace);
            }
        });
    }

    private static class RunnableMonitor implements Runnable {
        private Runnable delegate;
        private Throwable parentThreadStackTrace;

        public RunnableMonitor(Runnable delegate, Throwable parentThreadStackTrace) {
            this.delegate = delegate;
            this.parentThreadStackTrace = parentThreadStackTrace;
        }

        @Override
        public void run() {
            stackTraceThreadLocal.set(parentThreadStackTrace);
            try {
                delegate.run();
            } finally {
                stackTraceThreadLocal.remove();
            }
        }
    }
}
