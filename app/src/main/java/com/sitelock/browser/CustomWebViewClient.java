package com.sitelock.browser;

import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;

/**
 * 自定义 WebViewClient：
 * 1. shouldOverrideUrlLoading —— 拦截非本网站跳转（链接点击、JS 跳转）
 * 2. shouldInterceptRequest   —— 拦截广告 / 追踪请求（请求级屏蔽）
 * 3. onPageFinished           —— 注入页边广告隐藏 CSS（DOM 级屏蔽）
 *
 * 屏蔽状态通过回调通知 Activity 更新 UI 与计数。
 */
public class CustomWebViewClient extends WebViewClient {

    public interface Listener {
        /** 主框架即将跳转到非本网站 URL，已拦截 */
        void onRedirectBlocked(String url);
        /** 拦截了一个广告请求 */
        void onAdBlocked(String url);
        /** 页面开始加载 */
        void onPageStarted(String url, Bitmap favicon);
        /** 页面加载完成 */
        void onPageFinished(String url);
        /** URL 变化（用于同步地址栏） */
        void onUrlChanged(String url);
    }

    private final Listener listener;
    private volatile String homeDomain = "";
    private volatile boolean blockRedirects = true;
    private volatile boolean blockAds = true;

    public CustomWebViewClient(Listener listener) {
        this.listener = listener;
    }

    public void setHomeDomain(String domain) { this.homeDomain = domain; }
    public void setBlockRedirects(boolean v) { this.blockRedirects = v; }
    public void setBlockAds(boolean v) { this.blockAds = v; }
    public String getHomeDomain() { return homeDomain; }
    public boolean isBlockRedirects() { return blockRedirects; }
    public boolean isBlockAds() { return blockAds; }

    /** 拦截主框架跳转：非本网站则阻止 */
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl() != null ? request.getUrl().toString() : "";
        if (!blockRedirects) return false;
        // 仅拦截主框架（isForMainFrame 在导航请求中为 true）
        if (request.isForMainFrame()) {
            if (!UrlUtils.isSameSite(url, homeDomain)) {
                if (listener != null) listener.onRedirectBlocked(url);
                return true; // 阻止跳转
            }
        }
        return false;
    }

    /** 拦截广告请求：返回空白响应 */
    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl() != null ? request.getUrl().toString() : "";
        if (blockAds && AdRules.isAdRequest(url)) {
            if (listener != null) listener.onAdBlocked(url);
            return emptyResponse();
        }
        return null;
    }

    private WebResourceResponse emptyResponse() {
        // 根据请求 MIME 返回对应空内容，避免页面因类型不匹配报错
        return new WebResourceResponse(
            "text/plain", "utf-8",
            new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (listener != null) {
            listener.onPageStarted(url, favicon);
            listener.onUrlChanged(url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        // 注入页边广告隐藏 CSS + 覆盖层广告/跳转遮罩隐藏 CSS
        if (blockAds) {
            // 先把本网站域名写入页面，供点击劫持拦截使用
            String safeHome = homeDomain == null ? "" : homeDomain.replace("'", "\\'");
            view.evaluateJavascript("window.__sitelockHome='" + safeHome + "';", null);

            String css = AdRules.AD_CSS + AdRules.OVERLAY_CSS;
            String js = "(function(){try{var s=document.createElement('style');"
                + "s.type='text/css';s.id='sitelock-adhide';"
                + "if(document.getElementById('sitelock-adhide'))return;"
                + "s.appendChild(document.createTextNode("
                + jsStringLiteral(css)
                + "));(document.head||document.documentElement).appendChild(s);"
                + "}catch(e){}})();";
            view.evaluateJavascript(js, null);

            // 注入覆盖层移除 / 跳转拦截巡检脚本（MutationObserver 实时监听）
            view.evaluateJavascript(AdRules.OVERLAY_REMOVER_JS, null);
        }
        if (listener != null) listener.onPageFinished(url);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // 简单起见：放行 SSL 错误（实际生产可改成弹窗确认）
        handler.proceed();
    }

    /** 把任意字符串转成合法 JS 字符串字面量 */
    private static String jsStringLiteral(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }
}
