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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Private in-app Facebook OAuth browser.
 *
 * This activity never exposes a JavaScript bridge and never reads login form
 * values, cookies, passwords, or OAuth tokens. It only observes top-level
 * navigation so the already-declared OAuth callback can be returned to the
 * host bridge after strict callback/state validation.
 */
public final class FacebookWebViewActivity extends Activity {
    static final String EXTRA_AUTH_URL =
            "com.parallaxelite.facebookweb.AUTH_URL";
    static final String EXTRA_REDIRECT_URI =
            "com.parallaxelite.facebookweb.REDIRECT_URI";

    private WebView webView;
    private ProgressBar progress;
    private Uri authUri;
    private Uri expectedRedirectUri;
    private boolean completed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        if (savedInstanceState == null) {
            webView.loadUrl(authUri.toString());
        } else {
            try {
                webView.restoreState(savedInstanceState);
            } catch (Throwable ignored) {
                webView.loadUrl(authUri.toString());
            }
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3));
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

    private boolean handleMainFrameNavigation(Uri candidate) {
        if (candidate == null || completed) {
            return true;
        }

        if (OAuthCallbackValidator.matches(authUri, expectedRedirectUri, candidate)) {
            completed = true;
            Intent result = new Intent();
            result.setData(candidate);
            setResult(RESULT_OK, result);
            finish();
            return true;
        }

        // Keep credentials inside this private WebView. Do not hand unknown
        // navigations to Chrome, another browser, or an arbitrary external app.
        return !isTrustedFacebookHttps(candidate);
    }

    private boolean isTrustedFacebookHttps(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && FacebookAuthHost.matches(uri);
    }

    private boolean isSupportedCustomRedirect(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(java.util.Locale.US);
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

    private Uri parseUri(String value) {
        if (value == null || value.trim().isEmpty()) {
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
