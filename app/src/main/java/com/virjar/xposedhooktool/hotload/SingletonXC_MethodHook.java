package com.virjar.xposedhooktool.hotload;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Created by virjar on 2018/1/3.<br/>
 * hook单例封装,推荐业务上使用这个类，可以保证hook调用不重复
 */

public abstract class SingletonXC_MethodHook extends XC_MethodHook {
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return super.equals(null);
        }
        return getClass().equals(obj.getClass());
    }
}
