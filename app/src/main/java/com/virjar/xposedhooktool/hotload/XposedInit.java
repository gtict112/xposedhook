package com.virjar.xposedhooktool.hotload;

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
import java.io.IOException;
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

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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

        //find the apk location installed in android system,this file maybe a dex cache mapping(do not the real installed apk)
        File apkLocation = bindApkLocation(classLoader);
        if (apkLocation == null) {
            return;
        }
        String packageName = findPackageName(apkLocation);
        if (StringUtils.isBlank(packageName)) {
            XposedBridge.log("can not find package name  for this apk :");
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

        PathClassLoader hotClassLoader;
        //check if apk file has relocated,apk location maybe change if xposed plugin is reinstalled(system did not reboot)
        //xposed 插件安装后不能立即生效（需要重启Android系统）的本质原因是这两个文件不equal
        if (new File(packageInfo.applicationInfo.sourceDir).equals(apkLocation)) {
            hotClassLoader = (PathClassLoader) classLoader;
        } else {
            //hotClassLoader can load apk class && classLoader.getParent() can load xposed framework and android framework
            //使用parent是为了绕过缓存，也就是不走系统启动的时候链接的插件apk，但是xposed框架在这个classloader里面持有，所以集成
            Log.i("weijia", "create a new classloader for plugin");
            hotClassLoader = createClassLoader(classLoader.getParent(), packageInfo);
        }
        try {
            Class<?> aClass = hotClassLoader.loadClass("com.virjar.xposedhooktool.hotload.HotLoadPackageEntry");
            Log.i("weijia", "invoke hot load entry");
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


    /**
     * File name in an APK for the Android manifest.
     */
    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";

    private File bindApkLocation(ClassLoader pathClassLoader) {
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

        Object dexElement = dexElements[0];

        // /data/app/com.virjar.xposedhooktool/base.apk
        // /data/app/com.virjar.xposedhooktool-1/base.apk
        // /data/app/com.virjar.xposedhooktool-2/base.apk
        return (File) XposedHelpers.getObjectField(dexElement, "zip");
    }

    private String findPackageName(File originSourceFile) {

        ZipFile zipFile = null;
        InputStream stream = null;
        try {
            zipFile = new ZipFile(originSourceFile);
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
            try {
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (IOException e) {
                //ignore
            }
            IOUtils.closeQuietly(stream);
        }
    }
}
