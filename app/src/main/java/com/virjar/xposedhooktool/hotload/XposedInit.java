package com.virjar.xposedhooktool.hotload;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.common.collect.Maps;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * XposedInit
 * <br/>
 * 请注意，该类是热加载入口，不允许直接访问工程其他代码，只要访问过的类，都不能实现热加载
 *
 * @author virjar@virjar.com
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final Pattern dexPath4ClassLoaderPattern = Pattern.compile("\\[zip file \"(.+)\"");

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                hotLoadPlugin(lpparam.classLoader, (Context) param.args[0], lpparam);
            }
        });
    }

    private void hotLoadPlugin(ClassLoader ownerClassLoader, Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader classLoader = XposedInit.class.getClassLoader();
        if (!(classLoader instanceof PathClassLoader)) {
            XposedBridge.log("classloader is not PathClassLoader: " + classLoader.toString());
            return;
        }

        PathClassLoader parentClassLoader = (PathClassLoader) classLoader;
        String classloaderDescription = parentClassLoader.toString();
        Matcher matcher = dexPath4ClassLoaderPattern.matcher(classloaderDescription);
        if (!matcher.find()) {
            XposedBridge.log("can not find plugin apk file location");
            return;
        }
        //更加标准的做法，读取AndroidManifest.xml，解析PackageName，可以参考apkTool的做法，或者参考ApkInstaller的代码
        String pluginApkLocation = matcher.group(1);
        String packageName = findPackageName(pluginApkLocation);
        if (StringUtils.isBlank(packageName)) {
            XposedBridge.log("can not find mirror of apk :" + pluginApkLocation);
            return;
        }

        //find real apk location by package name
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            XposedBridge.log("can not find packageManager");
            return;
        }

        PackageInfo packageInfo = null;

        try {
            packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            //ignore
        }
        if (packageInfo == null) {
            XposedBridge.log("can not find plugin install location for plugin: " + packageName);
            return;
        }

        //hotClassLoader can load apk class && classLoader.getParent() can load xposed framework and android framework
        //使用parent是为了绕过缓存，也就是不走系统启动的时候链接的插件apk，但是xposed框架在这个classloader里面持有，所以集成
        PathClassLoader hotClassLoader = createClassLoader(classLoader.getParent(), packageInfo);
        try {
            Class<?> aClass = hotClassLoader.loadClass("com.virjar.xposedhooktool.hotload.HotLoadPackageEntry");
            aClass
                    .getMethod("entry", ClassLoader.class, ClassLoader.class, Context.class, XC_LoadPackage.LoadPackageParam.class, String.class)
                    .invoke(null, ownerClassLoader, hotClassLoader, context, lpparam, packageInfo.applicationInfo.sourceDir);
        } catch (Exception e) {
            if (e instanceof ClassNotFoundException) {
                InputStream inputStream = hotClassLoader.getResourceAsStream("assets/hotload_entry.txt");
                if (inputStream == null) {
                    XposedBridge.log("do you not disable Instant Runt for Android studio?");
                } else {
                    IOUtils.closeQuietly(inputStream);
                }
            }
            XposedBridge.log(e);
        }
    }

    private static ConcurrentMap<String, PathClassLoader> classLoaderCache = Maps.newConcurrentMap();

    /**
     * 这样做的目的是保证classloader单例，因为宿主存在多个dex的时候，或者有壳的宿主在解密代码之后，存在多次context的创建，当然xposed本身也存在多次IXposedHookLoadPackage的回调
     *
     * @param parent      父classloader
     * @param packageInfo 插件自己的包信息
     * @return 根据插件apk创建的classloader
     */
    private static PathClassLoader createClassLoader(ClassLoader parent, PackageInfo packageInfo) {
        if (classLoaderCache.containsKey(packageInfo.applicationInfo.sourceDir)) {
            return classLoaderCache.get(packageInfo.applicationInfo.sourceDir);
        }
        synchronized (XposedInit.class) {
            if (classLoaderCache.containsKey(packageInfo.applicationInfo.sourceDir)) {
                return classLoaderCache.get(packageInfo.applicationInfo.sourceDir);
            }
            PathClassLoader hotClassLoader = new PathClassLoader(packageInfo.applicationInfo.sourceDir, parent);
            classLoaderCache.putIfAbsent(packageInfo.applicationInfo.sourceDir, hotClassLoader);
            return hotClassLoader;
        }
    }

    private static final Pattern apkNamePattern = Pattern.compile("/data/app/([^/]+).*");

    private String findPackageName(String apkLocation) {
        Matcher matcher = apkNamePattern.matcher(apkLocation);
        if (!matcher.matches()) {
            return null;
        }
        String candidatePackageName = matcher.group(1);
        //com.virjar.xposedhooktool-1
        //com.virjar.xposedhooktool-2
        //com.virjar.xposedhooktool
        matcher = Pattern.compile("(.+)-\\d+").matcher(candidatePackageName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return candidatePackageName;
    }
}
