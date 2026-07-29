package com.sitelock.browser;

import android.net.Uri;
import android.webkit.URLUtil;
import android.text.TextUtils;

/** URL 工具：域名提取、同站判断、地址规范化 */
public final class UrlUtils {
    private UrlUtils() {}

    /** 提取主域名（去掉 www. 前缀） */
    public static String getDomain(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return "";
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 判断目标 URL 是否与「本网站」同站。
     * - 本网站未设定或无法解析域名时不拦截
     * - 完全相同、或为目标为 homeDomain 的子域时视为同站
     */
    public static boolean isSameSite(String url, String homeDomain) {
        if (TextUtils.isEmpty(homeDomain)) return true;
        String target = getDomain(url);
        if (TextUtils.isEmpty(target)) return true;
        if (target.equals(homeDomain)) return true;
        if (target.endsWith("." + homeDomain)) return true;
        return false;
    }

    /** 规范化用户输入：补 https://，校验有效性 */
    public static String normalize(String input) {
        if (TextUtils.isEmpty(input)) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
        if (!URLUtil.isNetworkUrl(s)) return "";
        return s;
    }

    /** 是否为合法网络 URL */
    public static boolean isValidUrl(String url) {
        return !TextUtils.isEmpty(url) && URLUtil.isNetworkUrl(url);
    }
}
