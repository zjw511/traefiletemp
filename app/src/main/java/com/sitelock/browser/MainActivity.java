package com.sitelock.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

/**
 * 主界面：顶部工具栏（地址栏 / 导航 / 本网站 / 开关 / 计数 / 状态）+ WebView。
 *
 * 关键能力：
 * - 地址栏输入网址回车 → 该域名自动设为「本网站」
 * - 「跳转屏蔽」开启时，跳转/重定向到非本网站的页面被拦截
 * - 「广告屏蔽」开启时，页边广告请求与 DOM 广告被屏蔽
 */
public class MainActivity extends AppCompatActivity implements CustomWebViewClient.Listener {

    private WebView webView;
    private CustomWebViewClient webViewClient;
    private HistoryDbHelper historyDb;

    private EditText urlInput;
    private Button btnBack, btnForward, btnReload, btnHome, btnHistory, btnGo;
    private TextView homeDomainText, redirectCountText, adCountText, statusText, lockIcon;
    private SwitchCompat toggleRedirects, toggleAds;
    private View countsBox;

    private int redirectCount = 0;
    private int adCount = 0;
    private boolean userTyping = false;
    private String currentTitle = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();

        historyDb = new HistoryDbHelper(this);

        // WebView 初始化
        webView = new WebView(this);
        ((android.widget.FrameLayout) findViewById(R.id.webContainer)).addView(webView,
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        // 关键：新建窗口（target=_blank）交给当前 WebView 处理，不弹系统浏览器
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // 拒绝新窗口请求，避免通过弹窗绕过跳转屏蔽
                return false;
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // 更新当前页面标题，供历史记录使用
                if (title != null) currentTitle = title;
            }
        });

        webViewClient = new CustomWebViewClient(this);
        webView.setWebViewClient(webViewClient);

        setupListeners();

        // 起始页
        webView.loadDataWithBaseURL(null, START_PAGE_HTML, "text/html", "utf-8", null);
        setStatus(getString(R.string.status_ready));
    }

    private void bindViews() {
        urlInput = findViewById(R.id.urlInput);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        btnHome = findViewById(R.id.btnHome);
        btnHistory = findViewById(R.id.btnHistory);
        btnGo = findViewById(R.id.btnGo);
        homeDomainText = findViewById(R.id.homeDomain);
        toggleRedirects = findViewById(R.id.toggleRedirects);
        toggleAds = findViewById(R.id.toggleAds);
        redirectCountText = findViewById(R.id.redirectCount);
        adCountText = findViewById(R.id.adCount);
        countsBox = findViewById(R.id.countsBox);
        statusText = findViewById(R.id.statusText);
        lockIcon = findViewById(R.id.lockIcon);
    }

    private void setupListeners() {
        btnGo.setOnClickListener(v -> doNavigate());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                doNavigate();
                return true;
            }
            return false;
        });
        urlInput.setOnFocusChangeListener((v, hasFocus) -> userTyping = hasFocus);

        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnReload.setOnClickListener(v -> webView.reload());
        btnHome.setOnClickListener(v -> {
            String home = webViewClient.getHomeDomain();
            if (!TextUtils.isEmpty(home)) {
                webView.loadUrl("https://" + home);
            } else {
                Toast.makeText(this, R.string.home_none, Toast.LENGTH_SHORT).show();
            }
        });
        btnHistory.setOnClickListener(v -> showHistoryDialog());

        toggleRedirects.setOnCheckedChangeListener((b, checked) -> {
            webViewClient.setBlockRedirects(checked);
            setStatus(checked ? R.string.redirects_on : R.string.redirects_off);
        });
        toggleAds.setOnCheckedChangeListener((b, checked) -> {
            webViewClient.setBlockAds(checked);
            setStatus(checked ? R.string.ads_on : R.string.ads_off);
        });

        countsBox.setOnClickListener(v -> {
            redirectCount = 0;
            adCount = 0;
            updateCounts();
            setStatus(R.string.counts_reset);
        });
    }

    /** 执行地址栏导航：规范化 → 设定本网站 → 加载 */
    private void doNavigate() {
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        String url = UrlUtils.normalize(raw);
        if (TextUtils.isEmpty(url)) {
            setStatus(R.string.invalid_url);
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }
        String domain = UrlUtils.getDomain(url);
        webViewClient.setHomeDomain(domain);
        homeDomainText.setText(TextUtils.isEmpty(domain) ? getString(R.string.home_none) : domain);
        webView.loadUrl(url);
    }

    private void updateCounts() {
        redirectCountText.setText("跳转 " + redirectCount);
        adCountText.setText("广告 " + adCount);
    }

    private void setStatus(CharSequence text) {
        statusText.setText(text);
    }
    private void setStatus(int resId) {
        statusText.setText(resId);
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    // ===== CustomWebViewClient.Listener =====
    @Override
    public void onRedirectBlocked(String url) {
        redirectCount++;
        runOnUiThread(() -> {
            updateCounts();
            setStatus(getString(R.string.redirect_blocked_prefix) + truncate(url, 50));
        });
    }

    @Override
    public void onAdBlocked(String url) {
        adCount++;
        runOnUiThread(() -> {
            updateCounts();
            setStatus(getString(R.string.ad_blocked_prefix) + truncate(url, 50));
        });
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        runOnUiThread(() -> setStatus(R.string.loading));
    }

    @Override
    public void onPageFinished(String url) {
        // 记录浏览历史（仅网络页面）
        if (!TextUtils.isEmpty(url) && UrlUtils.isValidUrl(url)) {
            String title = TextUtils.isEmpty(currentTitle) ? url : currentTitle;
            historyDb.record(url, title);
        }
    }

    @Override
    public void onUrlChanged(String url) {
        runOnUiThread(() -> {
            lockIcon.setText(url != null && url.startsWith("https://") ? "🔒" : "🔓");
            if (!userTyping) urlInput.setText(url);
        });
    }

    // ===== 历史记录 =====
    /** 打开历史记录对话框 */
    private void showHistoryDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_history, null);
        ListView listView = dialogView.findViewById(R.id.historyList);
        View emptyView = dialogView.findViewById(R.id.historyEmpty);
        Button btnClear = dialogView.findViewById(R.id.btnClearHistory);

        List<HistoryDbHelper.HistoryItem> items = historyDb.getAll();
        HistoryAdapter adapter = new HistoryAdapter(this, items);
        listView.setAdapter(adapter);

        if (items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // 点击加载
        listView.setOnItemClickListener((parent, view, position, id) -> {
            HistoryDbHelper.HistoryItem item = adapter.getItem(position);
            if (item != null) {
                loadFromHistory(item.url);
                dialog.dismiss();
            }
        });

        // 长按删除单条
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            HistoryDbHelper.HistoryItem item = adapter.getItem(position);
            if (item != null) {
                new AlertDialog.Builder(this)
                        .setTitle("删除该记录")
                        .setMessage(item.url)
                        .setPositiveButton("删除", (d, w) -> {
                            historyDb.delete(item.url);
                            adapter.remove(item);
                            adapter.notifyDataSetChanged();
                            if (adapter.isEmpty()) {
                                emptyView.setVisibility(View.VISIBLE);
                                listView.setVisibility(View.GONE);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
            return true;
        });

        // 清空全部
        btnClear.setOnClickListener(v -> {
            if (adapter.isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setTitle("清空历史")
                    .setMessage("确定清空全部历史记录？")
                    .setPositiveButton("清空", (d, w) -> {
                        historyDb.clear();
                        adapter.clear();
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.GONE);
                        Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        dialog.show();
    }

    /** 从历史记录加载 URL：沿用原域名作为本网站 */
    private void loadFromHistory(String url) {
        if (TextUtils.isEmpty(url)) return;
        String domain = UrlUtils.getDomain(url);
        if (!TextUtils.isEmpty(domain)) {
            webViewClient.setHomeDomain(domain);
            homeDomainText.setText(domain);
        }
        urlInput.setText(url);
        webView.loadUrl(url);
    }

    // ===== 返回键：先退网页历史 =====
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ((android.widget.FrameLayout) findViewById(R.id.webContainer)).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /** 起始页（未输入网址时显示） */
    private static final String START_PAGE_HTML =
        "<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'>" +
        "<meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1'>" +
        "<style>" +
        "*{box-sizing:border-box;margin:0;padding:0}" +
        "body{font-family:-apple-system,'Segoe UI','Microsoft YaHei',sans-serif;" +
        "min-height:100vh;display:flex;align-items:center;justify-content:center;" +
        "background:#f5f6f8;color:#333;padding:24px}" +
        ".card{text-align:center;max-width:90%}" +
        ".logo{font-size:48px;margin-bottom:14px}" +
        "h1{font-size:20px;margin-bottom:12px;color:#2563eb}" +
        "p{font-size:14px;line-height:1.8;color:#666;margin-bottom:6px}" +
        "b{color:#3730a3}code{background:#eef2ff;color:#3730a3;padding:2px 6px;border-radius:4px}" +
        "</style></head><body>" +
        "<div class='card'>" +
        "<div class='logo'>🌐</div>" +
        "<h1>站点锁浏览器</h1>" +
        "<p>在上方地址栏输入网址并回车，该网址的域名将自动设为「本网站」。</p>" +
        "<p>开启<b>跳转屏蔽</b>后，浏览时跳转 / 重定向到<b>非本网站</b>的页面将被拦截。</p>" +
        "<p>开启<b>广告屏蔽</b>后，页边广告与常见广告请求将被屏蔽。</p>" +
        "<p style='margin-top:16px'>示例：<code>example.com</code></p>" +
        "</div></body></html>";
}
