package com.browsercast.app;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.browsercast.app.classes.AppManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Main2Activity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        AppManager.controlSection = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main2, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            AppManager.signOut();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";
        public static String currentUrl = "https://google.com";
        public static WebView webView;

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);

            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = null;

            if (getArguments().getInt(ARG_SECTION_NUMBER) == 1) {
                rootView = inflater.inflate(R.layout.control_section, container, false);
            } else if(getArguments().getInt(ARG_SECTION_NUMBER) == 2) {
                rootView = inflater.inflate(R.layout.tabs_section, container, false);
            } else if (getArguments().getInt(ARG_SECTION_NUMBER) == 3) {
                rootView = inflater.inflate(R.layout.cast_section, container, false);
            } else {
                rootView = inflater.inflate(R.layout.fragment_main2, container, false);
            }

            return rootView;
        }


        @Override
        public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (getArguments().getInt(ARG_SECTION_NUMBER) == 2) {
                AppManager.updateTabsUI();
            } else if (getArguments().getInt(ARG_SECTION_NUMBER) == 1) {
                SeekBar seekBar = AppManager.controlSection.findViewById(R.id.volume_seek);
                seekBar.setOnSeekBarChangeListener(AppManager.seekBarChangeListener);

                Button playButton = AppManager.controlSection.findViewById(R.id.play_button);
                Button seek1Button = AppManager.controlSection.findViewById(R.id.seek1_button);
                Button seek2Button = AppManager.controlSection.findViewById(R.id.seek2_button);
                Button seek3Button = AppManager.controlSection.findViewById(R.id.seek3_button);
                Button seek4Button = AppManager.controlSection.findViewById(R.id.seek4_button);

                // Play button listener
                if (!playButton.hasOnClickListeners()) {
                    playButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AppManager.playButtonClick();
                        }
                    });

                    seek1Button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AppManager.seekVideo(-180);
                        }
                    });

                    seek2Button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AppManager.seekVideo(-60);
                        }
                    });

                    seek3Button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AppManager.seekVideo(60);
                        }
                    });

                    seek4Button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            AppManager.seekVideo(180);
                        }
                    });

                    AppManager.getTabsList();
                }
            } else if (getArguments().getInt(ARG_SECTION_NUMBER) == 3) {
                // Webview section
                final EditText addressBar = AppManager.controlSection.findViewById(R.id.address_bar);
                Button cast = AppManager.controlSection.findViewById(R.id.cast_button);
                View optionsButton = AppManager.controlSection.findViewById(R.id.options_button);
                final View webviewNavigation = AppManager.controlSection.findViewById(R.id.webview_navigation);
                View backButton = AppManager.controlSection.findViewById(R.id.back_button);
                View forwardButton = AppManager.controlSection.findViewById(R.id.forward_button);
                View refreshButton = AppManager.controlSection.findViewById(R.id.refresh_button);
                webView = AppManager.controlSection.findViewById(R.id.webview);

                optionsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        webviewNavigation.setVisibility(webviewNavigation.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                });

                backButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (webView.canGoBack()) {
                            webView.goBack();
                        }
                    }
                });

                forwardButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (webView.canGoForward()) {
                            webView.goForward();
                        }
                    }
                });

                refreshButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        webView.reload();
                    }
                });

                addressBar.setImeActionLabel("Go!", KeyEvent.KEYCODE_ENTER);
                addressBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                        if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                            webView.loadUrl(addressBar.getText().toString());
                        }

                        return false;
                    }
                });

                addressBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            AppManager.hideKeyboard(v);
                        }
                    }
                });

                cast.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AppManager.newTab(addressBar.getText().toString());
                    }
                });

                WebViewClient webViewClient = new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        currentUrl = url;
                        addressBar.setText(currentUrl);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        currentUrl = url;
                        addressBar.setText(currentUrl);
                    }
                };

                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(new JSInterface(),"android");
                webView.setWebViewClient(webViewClient);

                webView.loadUrl(currentUrl);
            }
            if (webView != null )
            {
                if (getArguments().getInt(ARG_SECTION_NUMBER) != 3) {
                    webView.onPause();
                } else {
                    webView.onResume();
                }
            }
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }
    }

    static class JSInterface {
        @JavascriptInterface
        public void onUrlChange(String url) {
            PlaceholderFragment.currentUrl = url;
        }
    }
}
