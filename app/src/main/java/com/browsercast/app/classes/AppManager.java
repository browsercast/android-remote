package com.browsercast.app.classes;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.GoogleApiClient;

import org.json.JSONArray;

import java.net.URISyntaxException;

public final class AppManager {
    public static Boolean isConnected = false;
    public static Socket socket;
    public static String remotePeerId;
    public static String currentTabId;
    public static JSONArray tabsList;
    public static GoogleSignInOptions gso;
    public static GoogleApiClient googleApiClient;

    public static void connectSocket() {
        try {
            socket = IO.socket("https://video-pc-app.herokuapp.com");
            socket.connect();
        } catch (URISyntaxException e) {}
    }
}



