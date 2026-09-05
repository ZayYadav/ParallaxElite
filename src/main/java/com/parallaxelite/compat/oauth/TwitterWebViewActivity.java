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

import java.util.Locale;

/**
 * Private fallback browser for Twitter/X OAuth.
 *
 * The original OAuth URL is loaded unchanged so client_id, state, PKCE,
 * oauth_token and redirect parameters remain owned by the calling application.
 * No JavaScript bridge is exposed and no form values, cookies or tokens are
 * read by the SDK.
 */
public final class TwitterWebViewActivity extends Activity {
    static final String EXTRA_AUTH_URL =
            "com.parallaxelite.twitterweb.AUTH_URL";
    static final String EXTRA_REDIRECT_URI =
            "com.parallaxelite.twitterweb.REDIRECT_URI";

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

        if (!isTrustedTwitterHttps(authUri)
                || !TwitterOAuthSessionStore.isHostCaptureSupported(expectedRedirectUri)) {
            cancelAndFinish();
            return;
        }

        createContentView();
        if (savedInstanceState == null) {
            webView.loadUrl(authUri.toString());
        } else {
            try {
                if (webView.restoreState(savedInstanceState) == null) {
                    webView.loadUrl(authUri.toString());
                }
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        root.addView(progress, progressParams);

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(30f);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close Twitter login");
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
                if (progress == null) return;
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100
                        ? ProgressBar.GONE : ProgressBar.VISIBLE);
            }
        });

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView source, WebResourceRequest request) {
                if (request == null) return true;
                if (!request.isForMainFrame()) return false;
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
            Intent result = new Intent(Intent.ACTION_VIEW, candidate);
            result.addCategory(Intent.CATEGORY_DEFAULT);
            result.addCategory(Intent.CATEGORY_BROWSABLE);
            setResult(RESULT_OK, result);
            finish();
            return true;
        }

        // Never escape the fallback into Chrome or another arbitrary application.
        // Normal X/Twitter HTTPS navigation stays inside this WebView.
        return !isTrustedTwitterHttps(candidate);
    }

    private static boolean isTrustedTwitterHttps(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        host = host == null ? "" : host.toLowerCase(Locale.US);
        return "x.com".equals(host)
                || "www.x.com".equals(host)
                || "mobile.x.com".equals(host)
                || "api.x.com".equals(host)
                || "oauth.x.com".equals(host)
                || "twitter.com".equals(host)
                || "www.twitter.com".equals(host)
                || "mobile.twitter.com".equals(host)
                || "api.twitter.com".equals(host)
                || "oauth.twitter.com".equals(host)
                || host.endsWith(".x.com")
                || host.endsWith(".twitter.com");
    }

    private static Uri parseUri(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 16_384) {
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
        if (!completed) setResult(RESULT_CANCELED);
        finish();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
