package za.co.bettercare.app;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {
    private WebView menuWebView;
    private WebView contentWebView;
    private Boolean isOnHome = true;
    private long activeTime = 0L;
    private long screenActiveTime = 0L;
    ProgressBar contentProgressBar;
    Timer myTimer;
    Timer screenTimer;
    private String currentContentURL;
    private String currentContentHash;
    private View screenSaverView;
    private AlertDialog activeAlertDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        contentProgressBar = (ProgressBar) findViewById(R.id.contentProgressIndicator);
        setupMenuWebView();
        setupContentWebView();

        menuWebView.setOnTouchListener(TouchwebViewListener);
        contentWebView.setOnTouchListener(TouchwebViewListener);

        ScreenActiveTask screenTask = new ScreenActiveTask();
        screenTimer = new Timer();
        screenTimer.schedule(screenTask, 0, 1000);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mainView);
        LayoutTransition layoutTransition = layout.getLayoutTransition();
        layoutTransition.addTransitionListener(new LayoutTransition.TransitionListener(){
            @Override
            public void endTransition(LayoutTransition arg0, ViewGroup arg1,
                                      View arg2, int arg3) {
                //only load the contentview after the views have animated in
                if(menuWebView.getVisibility()==View.VISIBLE && menuWebView.equals(arg2)) {
                    contentWebView.loadUrl(currentContentURL + "chapter1.html");
                }
            }
            @Override
            public void startTransition(LayoutTransition transition,
                                        ViewGroup container, View view, int transitionType) {
            }});

    }
    //reset timers on touch
    private View.OnTouchListener TouchwebViewListener
            = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            activeTime = 0;
            screenActiveTime = 0;
            return false;
        }
    };

    public void setupMenuWebView()
    {
        screenSaverView = (View) findViewById(R.id.screenSaver);
        menuWebView = (WebView) findViewById(R.id.menuWebview);
        WebSettings webSettings = menuWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        menuWebView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                //need to redraw the webview, there is a bug in android that sometimes displays a blank white page
                forceMenuWebViewRedraw();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(myTimer!=null)
                {
                    myTimer.cancel();
                    myTimer = null;
                }
                clearSession();
                if(url.contains("MainActivity.html")) {
                    //going back to main view
                    contentWebView.loadUrl(url);
                    showMainView();
                    isOnHome = true;
                    return true;
                }
                else
                {
                    int sharpPos = url.indexOf('#');
                    if (sharpPos >= 0) {
                        currentContentHash = url.substring(sharpPos+1);
                        url = url.substring(0,sharpPos);
                    }
                    contentWebView.loadUrl(url);
                    return true;
                }
            }
        });
    }


    public void setupContentWebView()
    {
        contentWebView = (WebView) findViewById(R.id.contentWebview);
        contentWebView.setWebChromeClient(new WebChromeClient() {
        });
        WebSettings webSettings = contentWebView.getSettings();
        //set useragent string to a desktop browser - this is so that qurio test site loads correctly
        webSettings.setUserAgentString("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0");
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDefaultTextEncodingName("utf-8");

        contentWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                contentProgressBar.setVisibility(View.VISIBLE);
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if(currentContentHash!=null && currentContentHash.length() >0) {
                    view.loadUrl("javascript:scrollAnchor('" + currentContentHash + "');");
                    currentContentHash="";
                }
                if(url.contains("qurio.co")) {
                    //start our timer for qurio
                    if (myTimer == null) {
                        activeTime = 0;
                        MyTimerTask myTask = new MyTimerTask();
                        myTimer = new Timer();
                        myTimer.schedule(myTask, 0, 1000);
                    }
                    contentProgressBar.setVisibility(View.GONE);
                }
                else if(!url.equals("about:blank")) {
                    forceContentWebViewRedraw();
                    contentProgressBar.setVisibility(View.GONE);
                }

            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(isOnHome)
                {
                    currentContentURL = url;
                    showBookContent();
                    menuWebView.loadUrl(url+"Menu/menu.html");
                    isOnHome = false;
                    return true;
                }
                else
                {
                    return false;
                }

            }
        });

        contentWebView.loadUrl("file:///android_asset/MainMenu/MainActivity.html");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showConnectError(String message) {
        if(screenSaverView.getVisibility() ==View.GONE) {
            if(activeAlertDialog!=null)
            {
                activeAlertDialog.dismiss();
            }
            activeAlertDialog = new AlertDialog.Builder(this).setMessage(
                    message + (message != null && message.length() > 0 ? ". " : "") +
                            "Quiz closed / You left the quiz, so we logged you out. Press OK to log in again.")
                    .setTitle("Bettercare")
                    .setNegativeButton("OK", new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            clearSession();
                            contentWebView.reload();
                        }
                    })
                    .setCancelable(false)
                    .create();
            activeAlertDialog.show();
        }
    }

    public void clearSession()
    {
        activeTime =0;
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
    }

    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            activeTime++;
            runOnUiThread(new Runnable(){

                @Override
                public void run() {
                if(activeTime > 60*10)
                {
                    activeTime = 0;
                    myTimer.cancel();
                    myTimer =null;
                    showConnectError("");
                }

            }});
        }

    }

    class ScreenActiveTask extends TimerTask {

        @Override
        public void run() {
            screenActiveTime++;
            runOnUiThread(new Runnable(){

                @Override
                public void run() {
                //show black screen after 10 minutes
                if(screenActiveTime > 60*10)
                {
                    screenActiveTime = 0;
                    screenSaverView.setVisibility(View.VISIBLE);
                    if(activeAlertDialog!=null) {
                        activeAlertDialog.dismiss();
                    }
                }

            }});
        }
    }
    public void showBookContent(){
        //first load a blank page to hide current content
        contentWebView.loadUrl("about:blank");
        ((RelativeLayout.LayoutParams) contentProgressBar.getLayoutParams()).leftMargin = getPixelConversion(300);
        ((RelativeLayout.LayoutParams) contentWebView.getLayoutParams()).width = getPixelConversion(980);
        menuWebView.setVisibility(View.VISIBLE);
    }

    public void showMainView(){
        menuWebView.setVisibility(View.GONE);
        ((RelativeLayout.LayoutParams) contentProgressBar.getLayoutParams()).leftMargin = 0;
        ((RelativeLayout.LayoutParams) contentWebView.getLayoutParams()).width = getPixelConversion(1280);
    }

    private void forceContentWebViewRedraw()
    {
        contentWebView.post(new Runnable() {
            int refreshCount = 0;
            @Override
            public void run()
            {
                refreshCount++;
                contentWebView.invalidate();
                //only redraw for the first 6 seconds
                if(!isFinishing() && refreshCount <6) {
                    contentWebView.postDelayed(this, 1000);
                }
                else {
                    contentWebView.removeCallbacks(this);
                }
            }
        });
    }

    private void forceMenuWebViewRedraw()
    {
        menuWebView.post(new Runnable() {
            int refreshCount = 0;
            @Override
            public void run()
            {
                refreshCount++;
                menuWebView.invalidate();
                //only redraw for the first 6 seconds
                if(!isFinishing() && refreshCount <6) {
                    menuWebView.postDelayed(this, 1000);
                }
                else {
                    menuWebView.removeCallbacks(this);
                }

            }
        });
    }

    public int getPixelConversion(int dpValue)
    {
        Resources r = getResources();
        int contentWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dpValue,
                r.getDisplayMetrics()
        );
        return contentWidth;
    }
    public void removeScreenSaver(View view)
    {
        screenActiveTime = 0;
        screenSaverView.setVisibility(View.GONE);
        clearSession();
        contentWebView.reload();
    }
    @Override
    public void onBackPressed() {
        finish();
    }

}
