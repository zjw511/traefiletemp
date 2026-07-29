package com.sitelock.browser;

/**
 * 广告 / 追踪域名与选择器规则。
 * - AD_DOMAIN_KEYWORDS：用于 shouldInterceptRequest 请求级屏蔽
 * - AD_CSS：用于 evaluateJavascript 注入到页面，隐藏页边 / 浮动 / 弹窗广告
 */
public final class AdRules {
    private AdRules() {}

    public static final String[] AD_DOMAIN_KEYWORDS = {
        "doubleclick.net", "googlesyndication.com", "googletagservices.com",
        "googleadservices.com", "googletagmanager.com", "google-analytics.com",
        "adservice.google.", "adsystem.com", "adnxs.com", "adsrvr.org",
        "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "mgid.com",
        "adsafeprotected.com", "scorecardresearch.com", "quantserve.com",
        "hotjar.com", "segment.io", "mixpanel.com", "connect.facebook.net",
        "amazon-adsystem.com", "advertising.com", "smartadserver.com",
        "moatads.com", "adroll.com", "krxd.net", "demdex.net",
        "bidswitch.net", "casalemedia.com", "3lift.com", "contextweb.com",
        "revcontent.com", "propellerads.com", "popads.net", "popcash.net",
        "adsterra.com", "popunder.", "popupad.", "/ads/", "/ad/",
        "/bannerad", "/adserver", "/advert/", "/adrotate", "/adframe",
        "/adimage", "/adplugin"
    };

    /** 注入到页面的页边广告隐藏 CSS（通过 JS 插入 <style>） */
    public static final String AD_CSS =
        "ins.adsbygoogle,[data-ad-slot],[data-ad-client],[id^='div-gpt-ad']," +
        "iframe[id^='google_ads_iframe'],iframe[src*='doubleclick.net']," +
        "iframe[src*='googlesyndication.com'],iframe[src*='googleads.g.doubleclick.net']," +
        "iframe[src*='amazon-adsystem.com'],iframe[src*='adnxs.com']," +
        "iframe[src*='adserv'],iframe[src*='/ads/'],iframe[src*='/ad/']," +
        "[class*='ad-banner'],[id*='ad-banner'],[class*='banner-ad'],[id*='banner-ad']," +
        "[class*='ad-slot'],[id*='ad-slot'],[class*='advert-slot'],[id*='advert-slot']," +
        "[class*='float-ad'],[id*='float-ad'],[class*='side-ad'],[id*='side-ad']," +
        "[class*='top-ad'],[id*='top-ad'],[class*='bottom-ad'],[id*='bottom-ad']," +
        "[class*='sticky-ad'],[id*='sticky-ad'],[class*='fixed-ad'],[id*='fixed-ad']," +
        "[class*='popunder'],[id*='popunder'],[class*='popup-ad'],[id*='popup-ad']," +
        "[class*='interstitial-ad'],[id*='interstitial-ad']" +
        "{display:none!important;visibility:hidden!important;height:0!important;width:0!important}";

    public static boolean isAdRequest(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String kw : AD_DOMAIN_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
