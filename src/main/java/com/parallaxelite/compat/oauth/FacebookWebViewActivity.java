package com.parallaxelite.compat.oauth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Isolated in-app browser for Facebook OAuth.
 *
 * <p>The original authorization URL is loaded unchanged and the page stays in
 * this WebView until the already-registered custom-scheme callback is reached.
 * This Activity does not install a JavaScript interface, inspect form fields,
 * read cookies, or persist credentials/tokens. A fresh WebView cookie/storage
 * session is prepared before every new login attempt.</p>
 */
@Obfuscate
public final class FacebookWebViewActivity extends Activity {
    private static final String WEBVIEW_PROFILE_SUFFIX = "parallax_facebook_auth";
    private static final String STATE_SESSION_READY =
            "com.parallaxelite.facebookweb.SESSION_READY";
    private static final int MAX_URL_LENGTH = 16_384;

    static final String EXTRA_AUTH_URL =
            "com.parallaxelite.facebookweb.AUTH_URL";
    static final String EXTRA_REDIRECT_URI =
            "com.parallaxelite.facebookweb.REDIRECT_URI";

    private WebView webView;
    private ProgressBar progress;
    private Uri authUri;
    private Uri expectedRedirectUri;
    private boolean completed;
    private boolean sessionReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 9+ requires each WebView process to use its own data directory.
        // This call must happen before CookieManager or WebView is initialized.
        configureIsolatedWebViewProfile();

        Intent launch = getIntent();
        authUri = parseUri(launch == null ? null : launch.getStringExtra(EXTRA_AUTH_URL));
        expectedRedirectUri = parseUri(
                launch == null ? null : launch.getStringExtra(EXTRA_REDIRECT_URI));

        if (!isTrustedFacebookHttps(authUri)
                || !isSupportedCustomRedirect(expectedRedirectUri)) {
            cancelAndFinish();
            return;
        }

        createContentView();

        sessionReady = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SESSION_READY, false);
        if (sessionReady) {
            restoreOrLoad(savedInstanceState);
        } else {
            prepareFreshFacebookSession();
        }
    }

    private void createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        configureWebView(webView);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        root.addView(progress, progressParams);

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(30f);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close Facebook login");
        close.setBackgroundColor(0xCCFFFFFF);
        close.setOnClickListener(v -> cancelAndFinish());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.topMargin = dp(10);
        closeParams.rightMargin = dp(10);
        root.addView(close, closeParams);

        setContentView(root);
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSaveFormData(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView source, int newProgress) {
                if (progress == null) {
                    return;
                }
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100
                        ? ProgressBar.GONE : ProgressBar.VISIBLE);
            }
        });

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView source, WebResourceRequest request) {
                if (request == null) {
                    return true;
                }
                if (!request.isForMainFrame()) {
                    return false;
                }
                return handleMainFrameNavigation(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView source, String url) {
                return handleMainFrameNavigation(parseUri(url));
            }
        });
    }

    private void configureIsolatedWebViewProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            WebView.setDataDirectorySuffix(WEBVIEW_PROFILE_SUFFIX);
        } catch (IllegalStateException ignored) {
            // This process is declared only for Facebook auth. If an OEM eagerly
            // initialized WebView, the explicit storage cleanup below still keeps
            // a previous Facebook session from being reused.
        } catch (Throwable ignored) {
        }
    }

    private void prepareFreshFacebookSession() {
        if (webView == null || isFinishing()) {
            cancelAndFinish();
            return;
        }

        clearNonCookieWebState();

        final CookieManager cookies;
        try {
            cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable error) {
            cancelAndFinish();
            return;
        }

        try {
            cookies.removeAllCookies(removed -> runOnUiThread(() -> {
                if (isFinishing() || completed || webView == null) {
                    return;
                }
                try {
                    cookies.flush();
                } catch (Throwable ignored) {
                }
                clearNonCookieWebState();
                sessionReady = true;
                webView.loadUrl(authUri.toString());
            }));
        } catch (Throwable error) {
            // Reusing an unknown cookie state would defeat the requested fresh
            // in-app login, so a broken WebView implementation fails closed.
            cancelAndFinish();
        }
    }

    private void clearNonCookieWebState() {
        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Throwable ignored) {
        }

        try {
            WebViewDatabase database = WebViewDatabase.getInstance(this);
            database.clearFormData();
            database.clearHttpAuthUsernamePassword();
        } catch (Throwable ignored) {
        }

        if (webView != null) {
            try {
                webView.stopLoading();
                webView.clearCache(true);
                webView.clearHistory();
                webView.clearFormData();
            } catch (Throwable ignored) {
            }
        }
    }

    private void restoreOrLoad(Bundle state) {
        boolean restored = false;
        try {
            restored = webView.restoreState(state) != null;
        } catch (Throwable ignored) {
        }
        if (!restored) {
            webView.loadUrl(authUri.toString());
        }
    }

    private boolean handleMainFrameNavigation(Uri candidate) {
        if (candidate == null || completed) {
            return true;
        }

        if (OAuthCallbackValidator.matches(authUri, expectedRedirectUri, candidate)) {
            completed = true;
            Intent result = new Intent(Intent.ACTION_VIEW, candidate);
            result.addCategory(Intent.CATEGORY_DEFAULT);
            result.addCategory(Intent.CATEGORY_BROWSABLE);
            setResult(RESULT_OK, result);
            finish();
            return true;
        }

        // Unknown schemes and non-Facebook top-level URLs are blocked here. They
        // are never handed to Chrome, the Facebook app, or another external app.
        return !isTrustedFacebookHttps(candidate);
    }

    static boolean isTrustedFacebookHttps(Uri uri) {
        return uri != null
                && uri.toString().length() <= MAX_URL_LENGTH
                && "https".equalsIgnoreCase(uri.getScheme())
                && FacebookAuthHost.matches(uri);
    }

    static boolean isSupportedCustomRedirect(Uri uri) {
        if (uri == null || uri.toString().length() > MAX_URL_LENGTH) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.US);
        if (scheme.isEmpty()
                || "http".equals(scheme)
                || "https".equals(scheme)
                || "file".equals(scheme)
                || "content".equals(scheme)
                || "javascript".equals(scheme)
                || "data".equals(scheme)
                || "intent".equals(scheme)) {
            return false;
        }
        return scheme.matches("^[a-z][a-z0-9+.-]{1,127}$");
    }

    static Uri parseUri(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > MAX_URL_LENGTH) {
            return null;
        }
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        cancelAndFinish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_SESSION_READY, sessionReady);
        if (webView != null) {
            try {
                webView.saveState(outState);
            } catch (Throwable ignored) {
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }

    private void cancelAndFinish() {
        if (!completed) {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
