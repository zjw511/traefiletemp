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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private UserScriptManager userScriptManager;

    private EditText urlInput;
    private View toolbar, toolbarRowB;
    private com.qmdeve.liquidglass.widget.LiquidGlassView toolbarBlur;
    private Button btnBack, btnForward, btnReload, btnHome, btnHistory, btnScripts, btnGo;
    private TextView homeDomainText, redirectCountText, adCountText, statusText, lockIcon;
    private SwitchCompat toggleRedirects, toggleAds, toggleDesktop;
    private View countsBox;

    private int redirectCount = 0;
    private int adCount = 0;
    private boolean userTyping = false;
    private String currentTitle = "";

    // 工具栏收拢状态：true=已收拢（仅保留地址栏行）
    private boolean toolbarCollapsed = false;
    private int rowBHeight = 0; // 第二行实际高度（含上边距）
    private android.animation.ValueAnimator collapseAnimator;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 沉浸式：内容延伸到状态栏与导航栏后方（edge-to-edge）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        bindViews();

        // 工具栏顶部留出状态栏高度（用 margin 让玻璃片悬浮在状态栏下方，露出顶部玻璃边）
        ViewCompat.setOnApplyWindowInsetsListener(toolbarBlur, (v, insets) -> {
            int sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = sb + dp(6);
            v.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });

        // 液态玻璃：仅设置圆角，不绑定采样源（避免 bind() 干扰 WebView 硬件加速渲染）
        toolbarBlur.post(() -> {
            toolbarBlur.setCornerRadius(dp(22));
        });

        historyDb = new HistoryDbHelper(this);
        userScriptManager = new UserScriptManager(this);

        // WebView 初始化（使用可监听滚动的子类，用于上滑收拢工具栏）
        webView = new ObservableWebView(this);
        webView.setBackgroundColor(0xFFFFFFFF);
        android.widget.FrameLayout.LayoutParams webLp =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        ((android.widget.FrameLayout) findViewById(R.id.webContainer)).addView(webView, webLp);

        // 滚动监听：上滑收拢、下滑展开（在页面顶部强制展开）
        ((ObservableWebView) webView).setOnScrollChangeListener((view, scrollY, dy) -> {
            if (scrollY <= dp(2)) {
                setToolbarCollapsed(false);
                return;
            }
            if (dy > dp(6)) setToolbarCollapsed(true);
            else if (dy < -dp(6)) setToolbarCollapsed(false);
        });

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

        // 默认开启桌面模式：伪装成电脑浏览器，网站返回无广告的桌面版页面
        applyDesktopMode(true);

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
        webViewClient.setUserScriptManager(userScriptManager);
        webView.setWebViewClient(webViewClient);

        setupListeners();

        // 起始页
        webView.loadDataWithBaseURL(null, START_PAGE_HTML, "text/html", "utf-8", null);
        setStatus(getString(R.string.status_ready));
    }

    private void bindViews() {
        urlInput = findViewById(R.id.urlInput);
        toolbar = findViewById(R.id.toolbarContent);
        toolbarBlur = findViewById(R.id.toolbarBlur);
        toolbarRowB = findViewById(R.id.toolbarRowB);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        btnHome = findViewById(R.id.btnHome);
        btnHistory = findViewById(R.id.btnHistory);
        btnScripts = findViewById(R.id.btnScripts);
        btnGo = findViewById(R.id.btnGo);
        homeDomainText = findViewById(R.id.homeDomain);
        toggleRedirects = findViewById(R.id.toggleRedirects);
        toggleAds = findViewById(R.id.toggleAds);
        toggleDesktop = findViewById(R.id.toggleDesktop);
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
        btnScripts.setOnClickListener(v -> showScriptsDialog());

        toggleRedirects.setOnCheckedChangeListener((b, checked) -> {
            webViewClient.setBlockRedirects(checked);
            setStatus(checked ? R.string.redirects_on : R.string.redirects_off);
        });
        toggleAds.setOnCheckedChangeListener((b, checked) -> {
            webViewClient.setBlockAds(checked);
            setStatus(checked ? R.string.ads_on : R.string.ads_off);
        });
        toggleDesktop.setOnCheckedChangeListener((b, checked) -> {
            applyDesktopMode(checked);
            webViewClient.setDesktopMode(checked);
            // 刷新当前页面使 UA 和 viewport 生效
            if (webView != null) webView.reload();
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
        redirectCountText.setText(String.valueOf(redirectCount));
        adCountText.setText(String.valueOf(adCount));
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 收拢/展开工具栏：通过动画改变 rowB 高度，LinearLayout 自动重新布局 */
    private void setToolbarCollapsed(boolean collapsed) {
        if (toolbarCollapsed == collapsed) return;
        toolbarCollapsed = collapsed;
        if (collapseAnimator != null && collapseAnimator.isRunning()) collapseAnimator.cancel();

        // 首次记录 rowB 内容高度（不含外边距）
        if (rowBHeight == 0) {
            int w = toolbarRowB.getWidth() > 0 ? toolbarRowB.getWidth()
                    : android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels;
            toolbarRowB.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            rowBHeight = toolbarRowB.getMeasuredHeight();
            if (rowBHeight <= 0) rowBHeight = dp(32);
        }

        int start = collapsed ? rowBHeight : 0;
        int end = collapsed ? 0 : rowBHeight;
        collapseAnimator = android.animation.ValueAnimator.ofInt(start, end).setDuration(220);
        collapseAnimator.addUpdateListener(anim -> {
            int h = (int) anim.getAnimatedValue();
            android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) toolbarRowB.getLayoutParams();
            lp.height = h;
            lp.topMargin = h == 0 ? 0 : dp(8);
            toolbarRowB.setLayoutParams(lp);
            toolbarRowB.setAlpha(rowBHeight == 0 ? 0f : Math.min(1f, h / (float) rowBHeight));
        });
        collapseAnimator.start();
    }

    /** 桌面版 Chrome User-Agent（Windows） */
    private static final String DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    /** 切换桌面/移动模式 */
    @SuppressLint("SetJavaScriptEnabled")
    private void applyDesktopMode(boolean desktop) {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (desktop) {
            settings.setUserAgentString(DESKTOP_UA);
            // 桌面 UA + 手机 viewport：让网站返回电脑版页面但缩放到手机屏幕宽度
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(true);
        } else {
            // 恢复默认移动 UA（null 让 WebView 用系统默认）
            settings.setUserAgentString(null);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        }
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
            if (userTyping) return;
            // 仅网络页面回填地址栏；起始页等非 http(s) 页面保持地址栏为空
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                urlInput.setText(url);
            } else {
                urlInput.setText("");
            }
        });
    }

    // ===== 油猴脚本 =====
    /** 打开脚本管理对话框 */
    private void showScriptsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_userscripts, null);
        ListView listView = dialogView.findViewById(R.id.scriptList);
        View emptyView = dialogView.findViewById(R.id.scriptEmpty);
        Button btnAdd = dialogView.findViewById(R.id.btnAddScript);

        java.util.List<UserScript> items = userScriptManager.getAll();
        UserScriptAdapter adapter = new UserScriptAdapter(this, items,
                userScriptManager.getDb(), () -> userScriptManager.reload());
        listView.setAdapter(adapter);

        refreshScriptEmpty(listView, emptyView, adapter);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        // 新建脚本：打开编辑器（id 为 0 表示新增）
        btnAdd.setOnClickListener(v -> showScriptEditor(0, null, adapter, listView, emptyView, dialog));

        // 点击编辑
        listView.setOnItemClickListener((parent, view, position, id) -> {
            UserScript s = adapter.getItem(position);
            if (s != null) showScriptEditor(s.id, s.code, adapter, listView, emptyView, dialog);
        });

        // 长按删除
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            UserScript s = adapter.getItem(position);
            if (s != null) {
                new AlertDialog.Builder(this)
                        .setTitle("删除脚本")
                        .setMessage(s.name)
                        .setPositiveButton("删除", (d, w) -> {
                            userScriptManager.getDb().delete(s.id);
                            userScriptManager.reload();
                            adapter.remove(s);
                            adapter.notifyDataSetChanged();
                            refreshScriptEmpty(listView, emptyView, adapter);
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
            return true;
        });

        dialog.show();
    }

    /** 脚本编辑器：新建或编辑现有脚本（id<=0 表示新建） */
    private void showScriptEditor(long id, String existingCode,
                                  UserScriptAdapter adapter, ListView listView,
                                  View emptyView, AlertDialog parentDialog) {
        View editorView = LayoutInflater.from(this).inflate(R.layout.dialog_script_editor, null);
        EditText codeInput = editorView.findViewById(R.id.scriptCode);
        if (existingCode != null) codeInput.setText(existingCode);
        else codeInput.setText("// ==UserScript==\n" +
                "// @name        新脚本\n" +
                "// @match       *://*/*\n" +
                "// @run-at      document-end\n" +
                "// ==/UserScript==\n\n" +
                "console.log('SiteLock UserScript loaded');");

        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle(id <= 0 ? "新建脚本" : "编辑脚本")
                .setView(editorView)
                .setPositiveButton("保存", (d, w) -> {
                    String code = codeInput.getText().toString();
                    if (TextUtils.isEmpty(code.trim())) {
                        Toast.makeText(this, "代码为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (id <= 0) {
                        userScriptManager.getDb().insert(code);
                    } else {
                        userScriptManager.getDb().update(id, code);
                    }
                    userScriptManager.reload();
                    // 刷新列表
                    adapter.clear();
                    adapter.addAll(userScriptManager.getAll());
                    adapter.notifyDataSetChanged();
                    refreshScriptEmpty(listView, emptyView, adapter);
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                    // 重新加载当前页使脚本生效
                    if (webView != null) webView.reload();
                })
                .setNegativeButton("取消", null)
                .create();
        editor.show();
    }

    private void refreshScriptEmpty(ListView listView, View emptyView, UserScriptAdapter adapter) {
        if (adapter.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
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
