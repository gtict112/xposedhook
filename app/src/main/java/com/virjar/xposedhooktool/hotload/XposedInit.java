package com.virjar.xposedhooktool.hotload;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.common.collect.Maps;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipFile;

import brut.androlib.res.decoder.AXmlResourceParser;
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

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {

            //由于集成了脱壳功能，所以必须选择before了
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                hotLoadPlugin(lpparam.classLoader, (Context) param.args[0], lpparam);
            }
        });
    }

    public static String packageName(ClassLoader classLoader) {
        Object element = bindApkLocation(classLoader);
        if (element == null) {
            return null;
        }
        //原文件可能已被删除，直接打开文件无法得到句柄，所以只能去获取持有删除文件句柄对象
        ZipFile zipFile = (ZipFile) XposedHelpers.getObjectField(element, "zipFile");
        return findPackageName(zipFile);
    }

    private static ClassLoader replaceClassloader(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader classLoader = XposedInit.class.getClassLoader();
        if (!(classLoader instanceof PathClassLoader)) {
            XposedBridge.log("classloader is not PathClassLoader: " + classLoader.toString());
            return classLoader;
        }

        //find the apk location installed in android system,this file maybe a dex cache mapping(do not the real installed apk)
        Object element = bindApkLocation(classLoader);
        if (element == null) {
            return classLoader;
        }
        File apkLocation = (File) XposedHelpers.getObjectField(element, "zip");
        //原文件可能已被删除，直接打开文件无法得到句柄，所以只能去获取持有删除文件句柄对象
        ZipFile zipFile = (ZipFile) XposedHelpers.getObjectField(element, "zipFile");
        if (zipFile == null && apkLocation.exists()) {
            try {
                zipFile = new ZipFile(apkLocation);
            } catch (Exception e) {
                //ignore
            }
        }
//        if (zipFile == null) {
//            return classLoader;
//        }
        String packageName = findPackageName(zipFile);
        if (StringUtils.isBlank(packageName)) {
//            XposedBridge.log("can not find package name  for this apk ");
//            return classLoader;
            //先暂时这么写，为啥有问题后面排查
            packageName = "com.virjar.xposedhooktool";
        }

        //find real apk location by package name
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            XposedBridge.log("can not find packageManager");
            return classLoader;
        }

        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            //ignore
        }
        if (packageInfo == null) {
            XposedBridge.log("can not find plugin install location for plugin: " + packageName);
            return classLoader;
        }

        //check if apk file has relocated,apk location maybe change if xposed plugin is reinstalled(system did not reboot)
        //xposed 插件安装后不能立即生效（需要重启Android系统）的本质原因是这两个文件不equal

        //hotClassLoader can load apk class && classLoader.getParent() can load xposed framework and android framework
        //使用parent是为了绕过缓存，也就是不走系统启动的时候链接的插件apk，但是xposed框架在这个classloader里面持有，所以集成

        return createClassLoader(classLoader.getParent(), packageInfo);
    }

    @SuppressLint("PrivateApi")
    private void hotLoadPlugin(ClassLoader ownerClassLoader, Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        boolean hasInstantRun = true;
        try {
            XposedInit.class.getClassLoader().loadClass(INSTANT_RUN_CLASS);
        } catch (ClassNotFoundException e) {
            //正常情况应该报错才对
            hasInstantRun = false;
        }
        if (hasInstantRun) {
            Log.e("weijia", "  Cannot load module, please disable \"Instant Run\" in Android Studio.");
            return;
        }

        ClassLoader hotClassLoader = replaceClassloader(context, lpparam);
//        if (hotClassLoader == XposedInit.class.getClassLoader()) {
//            //这证明不需要实现代码替换，或者热加载框架作用失效
//            //XposedBridge.log("热加载未生效");
//        }
        // check  Instant Run, 热加载启动后，需要重新检查Instant Run
        hasInstantRun = true;
        try {
            hotClassLoader.loadClass(INSTANT_RUN_CLASS);
        } catch (ClassNotFoundException e) {
            //正常情况应该报错才对
            hasInstantRun = false;
        }
        if (hasInstantRun) {
            Log.e("weijia", "  Cannot load module, please disable \"Instant Run\" in Android Studio.");
            return;
        }

        try {
            Class<?> aClass = hotClassLoader.loadClass("com.virjar.xposedhooktool.hotload.HotLoadPackageEntry");
            Log.i("weijia", "invoke hot load entry");
            aClass
                    .getMethod("entry", ClassLoader.class, ClassLoader.class, Context.class, XC_LoadPackage.LoadPackageParam.class)
                    .invoke(null, ownerClassLoader, hotClassLoader, context, lpparam);
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

    private static final String INSTANT_RUN_CLASS = "com.android.tools.fd.runtime.BootstrapApplication";
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
            XposedBridge.log("create a new classloader for plugin with new apk path: " + packageInfo.applicationInfo.sourceDir);
            PathClassLoader hotClassLoader = new PathClassLoader(packageInfo.applicationInfo.sourceDir, parent);
            classLoaderCache.putIfAbsent(packageInfo.applicationInfo.sourceDir, hotClassLoader);
            return hotClassLoader;
        }
    }


    /**
     * File name in an APK for the Android manifest.
     */
    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";

    private static Object bindApkLocation(ClassLoader pathClassLoader) {
        // 不能使用getResourceAsStream，这是因为classloader双亲委派的影响
//        InputStream stream = pathClassLoader.getResourceAsStream(ANDROID_MANIFEST_FILENAME);
//        if (stream == null) {
//            XposedBridge.log("can not find AndroidManifest.xml in classloader");
//            return null;
//        }

        // we can`t call package parser in android inner api,parse logic implemented with native code
        //this object is dalvik.system.DexPathList,android inner api
        Object pathList = XposedHelpers.getObjectField(pathClassLoader, "pathList");
        if (pathList == null) {
            XposedBridge.log("can not find pathList in pathClassLoader");
            return null;
        }

        //this object is  dalvik.system.DexPathList.Element[]
        Object[] dexElements = (Object[]) XposedHelpers.getObjectField(pathList, "dexElements");
        if (dexElements == null || dexElements.length == 0) {
            XposedBridge.log("can not find dexElements in pathList");
            return null;
        }

        return dexElements[0];
        // Object dexElement = dexElements[0];

        // /data/app/com.virjar.xposedhooktool/base.apk
        // /data/app/com.virjar.xposedhooktool-1/base.apk
        // /data/app/com.virjar.xposedhooktool-2/base.apk
        // return (File) XposedHelpers.getObjectField(dexElement, "zip");
    }

    private static String findPackageName(ZipFile zipFile) {
        if (zipFile == null) {
            return null;
        }
        InputStream stream = null;
        try {
            stream = zipFile.getInputStream(zipFile.getEntry(ANDROID_MANIFEST_FILENAME));
            AXmlResourceParser xpp = new AXmlResourceParser(stream);
            int eventType;
            //migrated form ApkTool
            while ((eventType = xpp.next()) > -1) {
                if (XmlPullParser.END_DOCUMENT == eventType) {
                    return null;
                } else if (XmlPullParser.START_TAG == eventType && "manifest".equalsIgnoreCase(xpp.getName())) {
                    // read <manifest> for package:
                    for (int i = 0; i < xpp.getAttributeCount(); i++) {
                        if (StringUtils.equalsIgnoreCase(xpp.getAttributeName(i), "package")) {
                            return xpp.getAttributeValue(i);
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        } finally {
            //不能关闭zipFile
            IOUtils.closeQuietly(stream);
        }
    }
}
