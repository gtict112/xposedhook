package com.virjar.xposedhooktool.jsinject;

import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

/**
 * Created by virjar on 2018/2/12.
 */

public class SocketServer {
    private static AsyncHttpServer server = new AsyncHttpServer();

    // private static List<WebSocket> _sockets = Lists.newArrayList();

    public static void start() {
        server.websocket("/jsconsole", new AsyncHttpServer.WebSocketRequestCallback() {
            @Override
            public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
                // _sockets.add(webSocket);

                //Use this to clean up any references to your websocket
                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex != null) {
                            Log.e("WebSocket", "Error", ex);
                        }
                    }
                });

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        if ("Hello Server".equals(s)) {
                            webSocket.send("Welcome Client!");
                        } else {
                            webSocket.send(s);
                        }

                    }
                });

            }
        });
        server.listen(5160);
    }
}
