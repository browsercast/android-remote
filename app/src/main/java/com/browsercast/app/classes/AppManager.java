package com.browsercast.app.classes;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;

import java.net.URISyntaxException;

public final class AppManager {
    public static Boolean isConnected = false;
    public static Socket socket;
    public static String remotePeerId;
    public static String currentTabId;
    public static JSONArray tabsList;

    public static void connectSocket() {
        try {
            socket = IO.socket("https://browsercast-messaging-broker.herokuapp.com");
            socket.connect();
        } catch (URISyntaxException e) {}
    }
}



