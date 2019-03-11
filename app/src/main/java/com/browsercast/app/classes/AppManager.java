package com.browsercast.app.classes;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.browsercast.app.R;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public final class AppManager {
    public static Activity context;
    public static Activity controlSection;
    public static Boolean isConnected = false;
    public static Socket socket;
    public static String remotePeerId;
    public static String currentTabId;
    public static JSONArray tabsList;
    public static JSONArray iframesList;
    public static GoogleSignInOptions gso;
    public static GoogleApiClient googleApiClient;
    public static FirebaseUser user;

    public static void connectSocket() {
        try {
            socket = IO.socket("https://video-pc-app.herokuapp.com");
            socket.connect();
        } catch (URISyntaxException e) {}
    }

    public static void updateTabsUI() {
        ViewGroup content = controlSection.findViewById(R.id.scrollview_content);
        TextView currentTab = controlSection.findViewById(R.id.current_tab);
        Button playButton = controlSection.findViewById(R.id.play_button);
        Button seek1Button = controlSection.findViewById(R.id.seek1_button);
        Button seek2Button = controlSection.findViewById(R.id.seek2_button);
        Button seek3Button = controlSection.findViewById(R.id.seek3_button);
        Button seek4Button = controlSection.findViewById(R.id.seek4_button);

        if (content == null) {
            new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        updateTabsUI();
                    }
                },
                1000);
            return;
        }

        // Clear previous tabs
        content.removeAllViews();

        // For each tab in list
        if (tabsList != null) {
            JSONObject activeTab = null;

            for (int i = 0; i < tabsList.length(); i++) {
                JSONObject item = null;
                try {
                    item = tabsList.getJSONObject(i);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // Update UI
                View child = controlSection.getLayoutInflater().inflate(R.layout.tab_item, null);
                TextView label = child.findViewById(R.id.tab_name);
                Button closeButton = child.findViewById(R.id.close_button);

                try {
                    label.setText(item.getString("title"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // Set close event listener
                final JSONObject finalItem = item;
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Send request to close tab
                        JSONObject payload = new JSONObject();
                        JSONObject payloadParams = new JSONObject();
                        try {
                            payloadParams.put("id", finalItem.getString("id"));
                            payload.put("cmd", "closeTab");
                            payload.put("params", payloadParams);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        // Send close tab request
                        sendMessage("send", payload);
                    }
                });

                // Set event listener for focusing tab
                child.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                    // Send request to focus tab
                    JSONObject payload = new JSONObject();
                    JSONObject payloadParams = new JSONObject();
                    try {
                        payloadParams.put("id", finalItem.getString("id"));
                        payload.put("cmd", "changeTab");
                        payload.put("params", payloadParams);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    // Send request to focus tab
                    sendMessage("send", payload);
                    }
                });

                try {
                    if (item.getBoolean("active")) {
                        activeTab = item;
                        currentTabId = item.getString("id");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                content.addView(child);
            }

            // Update UI
            try {
                if (activeTab == null && currentTab != null) {
                    currentTab.setText("You must select a video tab first");
                    playButton.setEnabled(false);
                    seek1Button.setEnabled(false);
                    seek2Button.setEnabled(false);
                    seek3Button.setEnabled(false);
                    seek4Button.setEnabled(false);
                } else if (currentTab != null) {
                    currentTab.setText(activeTab.getString("title"));
                    playButton.setEnabled(true);
                    seek1Button.setEnabled(true);
                    seek2Button.setEnabled(true);
                    seek3Button.setEnabled(true);
                    seek4Button.setEnabled(true);

                    if (activeTab.getBoolean("audible")) {
                        playButton.setText("Pause");
                    } else {
                        playButton.setText("Play");
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // For each iframe in list
        if (iframesList != null) {
            for (int i = 0; i < iframesList.length(); i++) {
                JSONObject item = null;
                try {
                    item = iframesList.getJSONObject(i);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // Update UI
                View child = controlSection.getLayoutInflater().inflate(R.layout.tab_item, null);
                TextView label = child.findViewById(R.id.tab_name);
                final Button closeButton = child.findViewById(R.id.close_button);

                try {
                    label.setText(
                            String.format(
                                    "%s x %s %s",
                                    item.getString("width"),
                                    item.getString("height"),
                                    item.getString("source"))
                    );
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                closeButton.setText("Open");

                // Set close event listener
                final JSONObject finalItem = item;
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Send request to close tab
                        JSONObject payload = new JSONObject();
                        JSONObject payloadParams = new JSONObject();
                        try {
                            payloadParams.put("url", finalItem.getString("source"));
                            payload.put("cmd", "newTab");
                            payload.put("params", payloadParams);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        // Send close tab request
                        sendMessage("send", payload);
                    }
                });

                content.addView(child);
            }
        }
    }

    public static void updateTabs(JSONObject params) {
        // Get the tab list
        JSONArray tabs = null;
        JSONArray iframes = null;
        try {
            tabs = params.getJSONArray("tabsList");
            iframes = params.getJSONArray("iframesList");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Save tab list
        tabsList = tabs;
        iframesList = iframes;
        updateTabsUI();
    }

    public static void setAudibleChanged(JSONObject params) {
        String id = null;
        try {
            id = params.getString("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Button playButton = controlSection.findViewById(R.id.play_button);
        JSONObject tab = getTabById(id);

        try {
            if (playButton != null) {
                if (tab.getBoolean("audible")) {
                    playButton.setText("Pause");
                } else {
                    playButton.setText("Play");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void setCurrentTabID(JSONObject params) {
        TextView currentTab = controlSection.findViewById(R.id.current_tab);
        Button playButton = controlSection.findViewById(R.id.play_button);
        Button seek1Button = controlSection.findViewById(R.id.seek1_button);
        Button seek2Button = controlSection.findViewById(R.id.seek2_button);
        Button seek3Button = controlSection.findViewById(R.id.seek3_button);
        Button seek4Button = controlSection.findViewById(R.id.seek4_button);

        String id = null;
        try {
            id = params.getString("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Save current tab id
        currentTabId = id;

        // Get active tab, if any
        JSONObject activeTab = getTabById(id);

        // Update UI
        try {
            if (activeTab == null && currentTab != null) {
                currentTab.setText("You must select a video tab first");
                playButton.setEnabled(false);
                seek1Button.setEnabled(false);
                seek2Button.setEnabled(false);
                seek3Button.setEnabled(false);
                seek4Button.setEnabled(false);
            } else if (currentTab != null) {
                currentTab.setText(activeTab.getString("title"));
                playButton.setEnabled(true);
                seek1Button.setEnabled(true);
                seek2Button.setEnabled(true);
                seek3Button.setEnabled(true);
                seek4Button.setEnabled(true);

                if (activeTab.getBoolean("audible")) {
                    playButton.setText("Pause");
                } else {
                    playButton.setText("Play");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void seekVideo(int seconds) {
        JSONObject payload = new JSONObject();
        JSONObject payloadParams = new JSONObject();
        try {
            payloadParams.put("seconds", seconds);
            payload.put("cmd", "seekVideo");
            payload.put("params", payloadParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendMessage("send", payload);
    }

    public static JSONObject getTabById(String id) {
        for (int i = 0; i < tabsList.length(); i++) {
            try {
                if (tabsList.getJSONObject(i).getString("id").equals(id))
                    return tabsList.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static void playButtonClick() {
        Button playButton = controlSection.findViewById(R.id.play_button);

        JSONObject payload = new JSONObject();
        JSONObject payloadParams = new JSONObject();
        try {
            payloadParams.put("id", Integer.valueOf(AppManager.currentTabId));
            payload.put("cmd", "playTab");
            payload.put("params", payloadParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Send request to play video
        sendMessage("send", payload);

        // FixMe add a property to switch the text, don't use Text property
        if (playButton.getText().equals("Play")) {
            playButton.setText("Pause");
        } else {
            playButton.setText("Play");
        }
    }

    // Send a message to the server
    public static void sendMessage(String message, JSONObject payload) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", remotePeerId);
            obj.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        socket.emit(message, obj);
    }

    // Volume seek bar
    public static SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            JSONObject payload = new JSONObject();
            JSONObject payloadParams = new JSONObject();
            try {
                payloadParams.put("volume", (double)seekBar.getProgress() / 100);
                payload.put("cmd", "changeVolume");
                payload.put("params", payloadParams);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            sendMessage("send", payload);
        }
    };

    public static void showDevices(JSONArray data) {
        ViewGroup container = context.findViewById(R.id.devices);

        for (int i = 0; i < data.length(); i++) {
            try {
                if (data.getJSONObject(i).getString("osName").length() > 2) {
                    container.removeAllViews();
                    View child = context.getLayoutInflater().inflate(R.layout.device_item, container);
                    Button button = child.findViewById(R.id.device_button);

                    final JSONObject item = data.getJSONObject(i);
                    button.setText(item.getString("osName"));

                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                String peer = item.getString("peerId");
                                joinPeer(peer);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static void joinPeer(String qrcode) {
        // Save user id
        remotePeerId = qrcode;

        // Send join request to server
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", qrcode);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendMessage("join", payload);
    }

    public static void googleSignOut() {
        if (Auth.GoogleSignInApi != null) {
            Auth.GoogleSignInApi.signOut(googleApiClient).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    FirebaseAuth.getInstance().signOut();
                }
            });
        }
    }

    public static void signOut() {
        // Get devices View
        ViewGroup devices = context.findViewById(R.id.devices);

        // Sign out of Google
        googleSignOut();

        // Null user
        AppManager.user = null;
        // Remove devices
        devices.removeAllViews();
        // Close current activity
        controlSection.finish();
    }

    public static void newTab(String url) {
        // Send request to close tab
        JSONObject payload = new JSONObject();
        JSONObject payloadParams = new JSONObject();
        try {
            payloadParams.put("url", url);
            payload.put("cmd", "newTab");
            payload.put("params", payloadParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Send close tab request
        sendMessage("send", payload);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void getTabsList() {
        // Send request to get tabs list
        JSONObject payload = new JSONObject();
        try {
            payload.put("cmd", "tabsListUpdate");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendMessage("send", payload);
    }
}



