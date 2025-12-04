package com.example.linuxsimulator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class BrowserActivity extends AppCompatActivity {
    private WebView webView;
    private EditText urlBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnBookmarks, btnSettings;
    private ProgressBar progressBar;
    private TextView pageTitle;
    private LinearLayout toolbarLayout;
    String currentUrl = "https://www.google.com";
    String homePage = "https://www.google.com";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        initializeViews();
        setupWebView();
        setupListeners();

        // Load home page or URL from intent
        String intentUrl = getIntent().getStringExtra("url");
        if (intentUrl != null && !intentUrl.isEmpty()) {
            loadUrl(intentUrl);
        } else {
            loadUrl(homePage);
        }
    }

    private void initializeViews() {
        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.et_url_bar);
        pageTitle = findViewById(R.id.tv_page_title);
        progressBar = findViewById(R.id.progress_bar);
        toolbarLayout = findViewById(R.id.toolbar_layout);

        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnHome = findViewById(R.id.btn_home);
        btnBookmarks = findViewById(R.id.btn_bookmarks);
        btnSettings = findViewById(R.id.btn_settings);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Set user agent
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");

        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new CustomWebChromeClient());
        webView.setDownloadListener(new CustomDownloadListener());
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> webView.reload());

        btnHome.setOnClickListener(v -> loadUrl(homePage));

        btnBookmarks.setOnClickListener(v -> showBookmarksDialog());

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String url = urlBar.getText().toString().trim();
                loadUrl(url);
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlBar.selectAll();
            }
        });
    }

    void loadUrl(String url) {
        if (url == null || url.isEmpty()) {
            url = homePage;
        }

        // Add protocol if missing
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            // Check if it looks like a URL
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                // Treat as search query
                url = "https://www.google.com/search?q=" + Uri.encode(url);
            }
        }

        webView.loadUrl(url);
        currentUrl = url;
    }

    private void updateNavigationButtons() {
        btnBack.setEnabled(webView.canGoBack());
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.5f);

        btnForward.setEnabled(webView.canGoForward());
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.5f);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void showBookmarksDialog() {
        String[] bookmarks = {
                "Google - https://www.google.com",
                "GitHub - https://www.github.com",
                "Stack Overflow - https://stackoverflow.com",
                "Wikipedia - https://www.wikipedia.org",
                "YouTube - https://www.youtube.com",
                "Reddit - https://www.reddit.com"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("📚 Bookmarks");
        builder.setItems(bookmarks, (dialog, which) -> {
            String selected = bookmarks[which];
            String url = selected.substring(selected.indexOf("https://"));
            loadUrl(url);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("⚙️ Browser Settings");

        String[] options = {
                "Set Home Page",
                "Clear History",
                "Clear Cache",
                "Clear Cookies",
                "About Browser"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    setHomePage();
                    break;
                case 1:
                    clearHistory();
                    break;
                case 2:
                    clearCache();
                    break;
                case 3:
                    clearCookies();
                    break;
                case 4:
                    showAbout();
                    break;
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void setHomePage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("🏠 Set Home Page");

        EditText input = new EditText(this);
        input.setText(homePage);
        input.setHint("Enter URL");
        input.setPadding(24, 16, 24, 16);

        builder.setView(input);
        builder.setPositiveButton("Set", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                homePage = url;
                Toast.makeText(this, "✓ Home page set", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void clearHistory() {
        webView.clearHistory();
        Toast.makeText(this, "✓ History cleared", Toast.LENGTH_SHORT).show();
    }

    void clearCache() {
        webView.clearCache(true);
        Toast.makeText(this, "✓ Cache cleared", Toast.LENGTH_SHORT).show();
    }

    void clearCookies() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        Toast.makeText(this, "✓ Cookies cleared", Toast.LENGTH_SHORT).show();
    }

    void showAbout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("ℹ️ About Browser");
        builder.setMessage("Linux Simulator Browser\nVersion 1.0\n\nA simple web browser for the Linux Simulator app.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            currentUrl = url;
            urlBar.setText(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            updateNavigationButtons();
            currentUrl = url;
            urlBar.setText(url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                Toast.makeText(BrowserActivity.this, "Error loading page", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Handle special URLs
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "Cannot open link", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            return false;
        }
    }

    private class CustomWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (title != null && !title.isEmpty()) {
                pageTitle.setText(title);
            }
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this);
            builder.setTitle("Alert");
            builder.setMessage(message);
            builder.setPositiveButton("OK", (dialog, which) -> result.confirm());
            builder.setOnCancelListener(dialog -> result.cancel());
            builder.show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this);
            builder.setTitle("Confirm");
            builder.setMessage(message);
            builder.setPositiveButton("OK", (dialog, which) -> result.confirm());
            builder.setNegativeButton("Cancel", (dialog, which) -> result.cancel());
            builder.setOnCancelListener(dialog -> result.cancel());
            builder.show();
            return true;
        }
    }

    private class CustomDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);

                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(filename);
                request.setDescription("Downloading file...");

                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(BrowserActivity.this, "📥 Downloading: " + filename, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(BrowserActivity.this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    public static void launch(Activity context) {
        Intent intent = new Intent(context, BrowserActivity.class);
        context.startActivity(intent);
    }

    public static void launch(Activity context, String url) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }
}