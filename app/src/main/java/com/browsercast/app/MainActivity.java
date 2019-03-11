package com.browsercast.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.browsercast.app.classes.AppManager;
import com.github.nkzawa.emitter.Emitter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
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
    private static final int SETUP_DEVICE_ACTIVITY = 1001;
    private static int RC_SIGN_IN = 100;
    private FirebaseAuth mAuth;
    private Intent intentNewDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppManager.context = this;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Start socket.io
        AppManager.connectSocket();

        // Authentication
        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                handleSignInResult(firebaseAuth.getCurrentUser());
            }
        });

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

        } else {
            // User is not connected, show the connect section
            final RelativeLayout content = findViewById(R.id.content);
            View child = getLayoutInflater().inflate(R.layout.connect_section, content);

            final Button setupDevice = child.findViewById(R.id.setup_device);
            final Button google = child.findViewById(R.id.google_button);

            google.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    googleButtonClick();
                }
            });

            setupDevice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    intentNewDevice = new Intent(MainActivity.this, NewDeviceActivity.class);
                    MainActivity.this.startActivityForResult(intentNewDevice, SETUP_DEVICE_ACTIVITY);
                }
            });
        }
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
                    //NewDeviceActivity.instance.finish();

                    AppManager.getTabsList();

                    startActivity(new Intent(MainActivity.this, Main2Activity.class));
                }
            });
        }
    };
    // Send a message to the server
    public static void sendMessage(String message, JSONObject payload) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", AppManager.remotePeerId);
            obj.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        AppManager.socket.emit(message, obj);
    }

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
                            AppManager.updateTabs(params);
                            break;
                        case "currentTabUpdate":
                            if (AppManager.tabsList != null)
                            AppManager.setCurrentTabID(params);
                            break;
                        case "audibleUpdate":
                            AppManager.setAudibleChanged(params);
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
                JSONArray data = (JSONArray) args[0];
                AppManager.showDevices(data);
            }
        });
        }
    };

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
                    //JSONObject data = (JSONObject) args[0];
                    AppManager.signOut();
                }
            });
        }
    };

    private void handleSignInResult(FirebaseUser user) {
        if (user != null) {
            AppManager.user = user;
            AppManager.socket.emit("joined-id-social-check", user.getUid());
        } else {
            Toast.makeText(getApplicationContext(), "User is null", Toast.LENGTH_LONG);
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
