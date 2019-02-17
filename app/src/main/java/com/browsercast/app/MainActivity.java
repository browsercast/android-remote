package com.browsercast.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.browsercast.app.classes.AppManager;
import com.github.nkzawa.emitter.Emitter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static int RC_SIGN_IN = 100;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Start socket.io
        AppManager.connectSocket();

        // Authentication
        mAuth = FirebaseAuth.getInstance();

        AppManager.gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_api_id))
                .requestEmail()
                .build();

        AppManager.googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Auth.GOOGLE_SIGN_IN_API, AppManager.gso)
                .build();

        AppManager.googleApiClient.connect();

        // Listen on events
        AppManager.socket.on("error", onError);
        AppManager.socket.on("disconnect", onDisconnect);
        AppManager.socket.on("join", onJoin);
        AppManager.socket.on("receive", onReceive);
        AppManager.socket.on("peer-id-social", onReceiveSocial);

        // Check if user is connected to server
        if (AppManager.isConnected) {
            RelativeLayout content = findViewById(R.id.content);
            View child = getLayoutInflater().inflate(R.layout.control_section, content);

            TextView currentTab = findViewById(R.id.current_tab);
            Button playButton = findViewById(R.id.play_button);

            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    playButtonClick();
                }
            });
        } else {
            // User is not connected, show the connect section
            RelativeLayout content = findViewById(R.id.content);
            View child = getLayoutInflater().inflate(R.layout.connect_section, content);

            final EditText input = child.findViewById(R.id.qrcode_input);
            final Button submit = child.findViewById(R.id.qrcode_button);
            final Button google = child.findViewById(R.id.google_button);

            google.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //googleSignOut();

                    googleButtonClick();
                }
            });

            submit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    submitButtonClick(input.getText().toString());
                }
            });
        }
    }

    private void playButtonClick() {
        Button playButton = findViewById(R.id.play_button);

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

    private void submitButtonClick(String qrcode) {
        // Save user id
        AppManager.remotePeerId = qrcode;

        // Send join request to server
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", qrcode);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendMessage("join", payload);
    }

    private void googleButtonClick() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(AppManager.googleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private Emitter.Listener onJoin = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Send request to get tabs list
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("cmd", "tabsListUpdate");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    sendMessage("send", payload);

                    // Get content view
                    RelativeLayout content = findViewById(R.id.content);
                    // Remove connect section
                    content.removeView(findViewById(R.id.connect_section));
                    // Show control section
                    View child = getLayoutInflater().inflate(R.layout.control_section, content);

                    TextView currentTab = child.findViewById(R.id.current_tab);
                    TextView playButton = child.findViewById(R.id.play_button);

                    playButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            playButtonClick();
                        }
                    });
                }
            });
        }
    };

    private Emitter.Listener onReceive = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject payload;
                    String cmd = null;
                    JSONObject params = null;

                    try {
                        payload = data.getJSONObject("payload");
                        cmd = payload.getString("cmd");
                        params = payload.getJSONObject("params");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    switch (cmd){
                        case "tabsListUpdate":
                            updateTabs(params);
                            break;
                        case "currentTabUpdate":
                            setCurrentTabID(params);
                            break;
                        case "audibleUpdate":
                            setAudibleChanged(params);
                            break;
                    }
                }
            });
        }
    };

    private Emitter.Listener onReceiveSocial = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String data = (String) args[0];

                    submitButtonClick(data);
                }
            });
        }
    };

    private void updateTabs(JSONObject params) {
        ViewGroup content = findViewById(R.id.scrollview_content);
        TextView currentTab = findViewById(R.id.current_tab);
        Button playButton = findViewById(R.id.play_button);

        // Get the tab list
        JSONArray tabsList = null;
        try {
            tabsList = params.getJSONArray("tabsList");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Clear previous tabs
        content.removeAllViews();

        // Save tab list
        AppManager.tabsList = tabsList;

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
                View child = getLayoutInflater().inflate(R.layout.tab_item, null);
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
                        AppManager.currentTabId = item.getString("id");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                content.addView(child);
            }

            // Update UI
            try {
                if (activeTab == null) {
                    currentTab.setText("You must select a video tab first");
                    playButton.setEnabled(false);
                } else {
                    currentTab.setText(activeTab.getString("title"));
                    playButton.setEnabled(true);

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
    }

    private void setCurrentTabID(JSONObject params) {
        TextView currentTab = findViewById(R.id.current_tab);
        Button playButton = findViewById(R.id.play_button);

        String id = null;
        try {
            id = params.getString("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Save current tab id
        AppManager.currentTabId = id;

        // Get active tab, if any
        JSONObject activeTab = getTabById(id);

        // Update UI
        try {
            if (activeTab == null) {
                currentTab.setText("You must select a video tab first");
                playButton.setEnabled(false);
            } else {
                currentTab.setText(activeTab.getString("title"));
                playButton.setEnabled(true);

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

    private void setAudibleChanged(JSONObject params) {
        String id = null;
        try {
            id = params.getString("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Button playButton = findViewById(R.id.play_button);
        JSONObject tab = getTabById(id);

        try {
            if (tab.getBoolean("audible")) {
                playButton.setText("Pause");
            } else {
                playButton.setText("Play");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private Emitter.Listener onError = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                }
            });
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                }
            });
        }
    };

    // Send a message to the server
    private void sendMessage(String message, JSONObject payload) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", AppManager.remotePeerId);
            obj.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        AppManager.socket.emit(message, obj);
    }

    private JSONObject getTabById(String id) {
        for (int i = 0; i < AppManager.tabsList.length(); i++) {
            try {
                if (AppManager.tabsList.getJSONObject(i).getString("id").equals(id))
                    return AppManager.tabsList.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private void handleSignInResult(FirebaseUser user) {
        if (user != null) {
            AppManager.socket.emit("joined-id-social-check", user.getUid());
        }
    }

    private void googleSignOut() {
        if (Auth.GoogleSignInApi != null) {
            Auth.GoogleSignInApi.signOut(AppManager.googleApiClient).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    FirebaseAuth.getInstance().signOut();
                    finish();
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                // Google Sign In failed, update UI appropriately
                Toast.makeText(getApplicationContext(), "Google connect failed", Toast.LENGTH_LONG);
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        FirebaseUser user = mAuth.getCurrentUser();
                        handleSignInResult(user);
                    } else {
                        // If sign in fails, display a message to the user.
                        handleSignInResult(null);
                    }
                }
            });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if ( v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent( event );
    }
}
