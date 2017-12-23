package com.virjar.xposedhooktool.droidsword;

/**
 * Created by virjar on 2017/12/23.
 * <br/>黑科技，见github项目https://github.com/githubwing/DroidSword
 * <br/>一个很有意思的项目
 */

public class DroidSword {
    public static void startDroidSword() {
        ActivityHooker.hookActivity();
        FragmentHooker.hookFragment();
    }
}
