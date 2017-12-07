package com.virjar.xposedhooktool.socket;


import com.virjar.xposedhooktool.log.LogUtil;

import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class NetDataPrinter {

    public static final boolean print_track = false;

    //key input/output value socket
    static final Map<Object, Socket> map = Collections.synchronizedMap(new HashMap<Object, Socket>());
    //key socket   value  inputprinter/outputprinter
    static final Map<Socket, OutputStreamPrinter> outMap = new HashMap<Socket, OutputStreamPrinter>();
    static final Map<Socket, InputStreamPrinter> inMap = new HashMap<Socket, InputStreamPrinter>();

    //key outputStream value statck
    static final Map<Object, List<String>> callWriteStack = new HashMap<Object, List<String>>();
    //key inputStream value statck
    static final Map<Object, List<String>> callReadStack = new HashMap<Object, List<String>>();

    static final Map<Socket, OutPrinter> socketPrinterMap = new HashMap<Socket, OutPrinter>();

    static final Set<String> hookedClassMethod = new HashSet<String>();

    public static void hook(final Class<? extends OutPrinter.FormatOuter> tcpFormatOuterClass, final boolean printStackTrace) {

        XposedHelpers.findAndHookConstructor(Socket.class, new XC_MethodHook() {

            private Class<?> findHasMethodNameClass(Class<?> clz, String name) {
                while (clz != null && clz != Object.class && !Modifier.isAbstract(clz.getModifiers())) {
                    try {
                        clz.getDeclaredMethod(name);
                        return clz;
                    } catch (NoSuchMethodException e) {
                    }
                    clz = clz.getSuperclass();
                }
                return null;
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Class<?> sclz = param.thisObject.getClass();
//				String msg = "after new Socket(), this:" + sclz + "@" + System.identityHashCode(param.thisObject) ;
//				LogUtil.outLog("\n" + msg+ "\n");
//				
                Class<?> clz = findHasMethodNameClass(sclz, "getOutputStream");
                if (clz != null) {
                    String mn = (clz.getName() + ".getOutputStream()");
                    boolean hook = false;
                    if (!hookedClassMethod.contains(mn)) {
                        synchronized (clz) {
                            if (!hookedClassMethod.contains(mn)) {
                                hookedClassMethod.add(mn);
                                hook = true;
                            }
                        }
                    }
                    if (hook) {
                        hookSocketOutput(clz, tcpFormatOuterClass, printStackTrace);
                    }

                }

                clz = findHasMethodNameClass(sclz, "getInputStream");
                if (clz != null) {
                    boolean addClass = false;
                    String mn = clz.getName() + ".getInputStream()";
                    if (!hookedClassMethod.contains(mn)) {
                        synchronized (clz) {
                            if (!hookedClassMethod.contains(mn)) {
                                hookedClassMethod.add(mn);
                                addClass = true;
                            }
                        }
                    }
                    if (addClass) {
                        hookSocketInput(clz, tcpFormatOuterClass, printStackTrace);
                    }

                }


            }
        });


    }

    private static void clearMap(Class<?> clz) {
        try {
            String mn = clz.getName() + ".close()";
            if (!hookedClassMethod.contains(mn)) {
                synchronized (clz) {
                    if (!hookedClassMethod.contains(mn)) {
                        hookedClassMethod.add(mn);

                        XposedHelpers.findAndHookMethod(clz, "close", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                List<Object> removeList = new ArrayList<Object>();
                                synchronized (map) {
                                    for (Object key : map.keySet()) {
                                        Object value = map.get(key);
                                        if (param.thisObject == value) {
                                            removeList.add(key);
                                            outMap.remove(value);
                                            inMap.remove(value);
                                            OutPrinter op = socketPrinterMap.remove(value);
                                            if (op != null) {
                                                op.close();
                                            }
                                        }
                                    }

                                    for (Object key : removeList) {
                                        map.remove(key);
                                    }
                                }

                                for (Object key : removeList) {
                                    callWriteStack.remove(key);
                                    callReadStack.remove(key);
                                }
                            }
                        });

                    }
                }
            }
        } catch (Throwable e) {
        }

        try {
            String mn = clz.getName() + ".flush()";
            if (!hookedClassMethod.contains(mn)) {
                synchronized (clz) {
                    if (!hookedClassMethod.contains(mn)) {
                        hookedClassMethod.add(mn);

                        XposedHelpers.findAndHookMethod(clz, "flush", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                List<Object> removeList = new ArrayList<Object>();
                                synchronized (map) {
                                    for (Iterator<Object> iterator = map.keySet().iterator(); iterator.hasNext(); ) {
                                        Object key = iterator.next();
                                        Object value = map.get(key);
                                        if (param.thisObject == value) {
                                            removeList.add(key);
                                            outMap.remove(value);
                                            inMap.remove(value);
                                            OutPrinter op = socketPrinterMap.remove(value);
                                            if (op != null) {
                                                op.flush();
                                            }
                                        }
                                    }

                                    for (Object key : removeList) {
                                        map.remove(key);
                                    }
                                }

                                for (Object key : removeList) {
                                    callWriteStack.remove(key);
                                    callReadStack.remove(key);
                                }
                            }
                        });

                    }
                }
            }
        } catch (Throwable e) {
        }
    }

    private static void hookSocketOutput(Class<?> clz, final Class<? extends OutPrinter.FormatOuter> tcpFormatOuterClass, final boolean printStackTrack) {
        clearMap(clz);

        //LogUtil.outLog("\nhook class " + clz + ".getOutputStream\n");
        XposedHelpers.findAndHookMethod(clz, "getOutputStream", new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (printStackTrack) {
                    LogUtil.outTrack("socket getOutputStream");
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object result = param.getResult();
                Socket socket = (Socket) param.thisObject;
                synchronized (map) {
                    map.put(result, socket);
                }
                InetSocketAddress isa = (InetSocketAddress) socket.getRemoteSocketAddress();
                synchronized (socket) {
                    if (!socketPrinterMap.containsKey(socket)) {
                        //tcpFormatOuter
                        OutPrinter outPrinter = new OutPrinter(isa.getAddress().getHostAddress(), isa.getPort(), socket.getLocalPort());
                        socketPrinterMap.put(socket, outPrinter);
                        if (tcpFormatOuterClass != null) {
                            outPrinter.setFormatOuter(tcpFormatOuterClass.newInstance());
                        }
                    }

                }


                Class<?> resultClz = result.getClass();
                String mn = resultClz.getName() + ".write(int)";
                if (!hookedClassMethod.contains(mn)) {
                    synchronized (resultClz) {
                        if (!hookedClassMethod.contains(mn)) {
                            hookedClassMethod.add(mn);

                            XposedHelpers.findAndHookMethod(resultClz, "write", int.class, new XC_MethodHook() {
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {

                                        List<String> calls = callWriteStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callWriteStack.put(param.thisObject, calls);
                                        }

                                        calls.add("write(int)");
                                        if (calls.size() == 1) {

                                            OutputStreamPrinter osp;
                                            synchronized (socket) {
                                                osp = outMap.get(socket);
                                                if (osp == null) {
                                                    osp = new OutputStreamPrinter(socket, socketPrinterMap.get(socket));
                                                    outMap.put(socket, osp);
                                                }
                                            }

                                            int oneByte = (Integer) param.args[0];
                                            osp.write(oneByte);

                                        }

                                    }

                                }

                                ;

                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {

                                        List<String> calls = callWriteStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callWriteStack.put(param.thisObject, calls);
                                        }
                                        calls.remove(calls.size() - 1);

                                    }
                                }

                                ;

                            });

                            XposedHelpers.findAndHookMethod(resultClz, "write", byte[].class, int.class, int.class, new XC_MethodHook() {
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {

                                        List<String> calls = callWriteStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callWriteStack.put(param.thisObject, calls);
                                        }
                                        calls.add("write(byte[],int,int)");
                                        if (calls.size() == 1) {
                                            OutputStreamPrinter osp;
                                            synchronized (socket) {
                                                osp = outMap.get(socket);
                                                if (osp == null) {
                                                    osp = new OutputStreamPrinter(socket, socketPrinterMap.get(socket));
                                                    outMap.put(socket, osp);
                                                }
                                            }
                                            byte[] buffer = (byte[]) param.args[0];
                                            int offset = (Integer) param.args[1];
                                            int count = (Integer) param.args[2];
                                            osp.write(buffer, offset, count);
                                        }

                                    }

                                }

                                ;

                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {
                                        List<String> calls = callWriteStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callWriteStack.put(param.thisObject, calls);
                                        }
                                        calls.remove(calls.size() - 1);
                                    }
                                }

                                ;
                            });

                        }
                    }
                }


            }
        });
    }

    private static void hookSocketInput(Class<?> clz, final Class<? extends OutPrinter.FormatOuter> tcpFormatOuterClass, final boolean printStackTrace) {
        //LogUtil.outLog("\nhook class " + clz + ".getInputStream\n");
        clearMap(clz);

        XposedHelpers.findAndHookMethod(clz, "getInputStream", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (printStackTrace) {
                    LogUtil.outTrack("socket getInputStream");
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object result = param.getResult();
                Socket socket = (Socket) param.thisObject;
                synchronized (map) {
                    map.put(result, socket);
                }

                InetSocketAddress isa = (InetSocketAddress) socket.getRemoteSocketAddress();
                synchronized (socket) {
                    if (!socketPrinterMap.containsKey(socket)) {
                        //tcpFormatOuter
                        if (isa != null) {//TODO 这里可能是null，why
                            OutPrinter outPrinter = new OutPrinter(isa.getAddress().getHostAddress(), isa.getPort(), socket.getLocalPort());
                            socketPrinterMap.put(socket, outPrinter);
                            if (tcpFormatOuterClass != null) {
                                outPrinter.setFormatOuter(tcpFormatOuterClass.newInstance());
                            }
                        }
                    }
                }

                Class<?> resultClz = result.getClass();
                String mn = resultClz.getName() + ".read()";
                if (!hookedClassMethod.contains(mn)) {
                    synchronized (resultClz) {
                        if (!hookedClassMethod.contains(mn)) {
                            hookedClassMethod.add(mn);

                            XposedHelpers.findAndHookMethod(resultClz, "read", new XC_MethodHook() {
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {
                                        List<String> calls = callReadStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callReadStack.put(param.thisObject, calls);
                                        }
                                        calls.add("read()");
                                    }
                                }

                                ;

                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {
                                        List<String> calls = callReadStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callReadStack.put(param.thisObject, calls);
                                        }
                                        if (calls.size() == 1) {
                                            InputStreamPrinter isp;
                                            synchronized (socket) {
                                                isp = inMap.get(socket);
                                                if (isp == null) {
                                                    isp = new InputStreamPrinter(socket, socketPrinterMap.get(socket));
                                                    inMap.put(socket, isp);
                                                }
                                            }
                                            Integer oneByte = (Integer) param.getResult();
                                            if (oneByte != null) {
                                                isp.read(oneByte);
                                            }
                                        }

                                        calls.remove(calls.size() - 1);
                                    }

                                }

                                ;
                            });

                            XposedHelpers.findAndHookMethod(resultClz, "read", byte[].class, int.class, int.class, new XC_MethodHook() {
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {
                                        List<String> calls = callReadStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callReadStack.put(param.thisObject, calls);
                                        }
                                        calls.add("read(byte[],int,int)");
                                    }
                                }

                                ;

                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object output = param.thisObject;
                                    Socket socket = map.get(output);
                                    if (socket != null) {
                                        List<String> calls = callReadStack.get(param.thisObject);
                                        if (calls == null) {
                                            calls = new ArrayList<String>(2);
                                            callReadStack.put(param.thisObject, calls);
                                        }
                                        if (calls.size() == 1) {
                                            InputStreamPrinter isp;
                                            synchronized (socket) {
                                                isp = inMap.get(socket);
                                                if (isp == null) {
                                                    isp = new InputStreamPrinter(socket, socketPrinterMap.get(socket));
                                                    inMap.put(socket, isp);
                                                }
                                            }
                                            byte[] buffer = (byte[]) param.args[0];
                                            int offset = (Integer) param.args[1];
                                            //int count = (Integer) param.args[2];

                                            Integer readLen = (Integer) param.getResult();
                                            if (readLen != null) {
                                                isp.read(buffer, offset, readLen);
                                            }
                                        }

                                        calls.remove(calls.size() - 1);
                                    }

                                }

                                ;
                            });

                        }
                    }
                }

            }
        });
    }

}
