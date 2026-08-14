package com.whappy.chat;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String WHAPPY_URL = "https://whappy-chat.docile-mesa-9203.chatgpt.site/";
    private static final int FILE_CHOOSER_REQUEST = 41;
    private static final int MEDIA_PERMISSION_REQUEST = 42;

    private WebView webView;
    private View splash;
    private ProgressBar pageProgress;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 92, 22));
        getWindow().setNavigationBarColor(Color.WHITE);
        buildInterface();
        configureWebView();
        requestNotificationPermission();

        if (savedInstanceState == null) webView.loadUrl(WHAPPY_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(19, 215, 19)));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        root.addView(pageProgress, progressParams);

        LinearLayout splashLayout = new LinearLayout(this);
        splashLayout.setOrientation(LinearLayout.VERTICAL);
        splashLayout.setGravity(Gravity.CENTER);
        splashLayout.setBackgroundColor(Color.WHITE);

        ImageView icon = new ImageView(this);
        icon.setImageResource(com.whappy.chat.R.drawable.whappy_icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(116), dp(116));
        splashLayout.addView(icon, iconParams);

        TextView brand = new TextView(this);
        brand.setText("WHAPPY");
        brand.setTextColor(Color.rgb(7, 92, 22));
        brand.setTextSize(25);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandParams.topMargin = dp(18);
        splashLayout.addView(brand, brandParams);

        TextView tagline = new TextView(this);
        tagline.setText("Messages • Appels • Opportunités");
        tagline.setTextColor(Color.rgb(96, 124, 102));
        tagline.setTextSize(12);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dp(7);
        splashLayout.addView(tagline, taglineParams);

        ProgressBar loader = new ProgressBar(this);
        LinearLayout.LayoutParams loaderParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        loaderParams.topMargin = dp(25);
        splashLayout.addView(loader, loaderParams);

        splash = splashLayout;
        root.addView(splashLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " WHAPPY-Android/1.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (ActivityNotFoundException ignored) { Toast.makeText(MainActivity.this, "Application compatible introuvable", Toast.LENGTH_SHORT).show(); }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                splash.animate().alpha(0f).setDuration(220).withEndAction(() -> splash.setVisibility(View.GONE)).start();
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                Toast.makeText(MainActivity.this, "Navigation bloquée pour votre sécurité", Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                pageProgress.setProgress(progress);
                pageProgress.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Sélecteur de fichiers indisponible", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException ignored) { Toast.makeText(this, "Téléchargement indisponible", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void handleWebPermission(PermissionRequest request) {
        List<String> permissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.CAMERA);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (permissions.isEmpty()) request.grant(request.getResources());
        else {
            pendingWebPermission = request;
            requestPermissions(permissions.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MEDIA_PERMISSION_REQUEST || pendingWebPermission == null) return;
        boolean granted = true;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        if (granted) pendingWebPermission.grant(pendingWebPermission.getResources());
        else pendingWebPermission.deny();
        pendingWebPermission = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        fileCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
