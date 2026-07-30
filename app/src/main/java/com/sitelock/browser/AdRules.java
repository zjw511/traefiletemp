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

    /**
     * 覆盖层广告 / 跳转遮罩隐藏 CSS。
     * 针对覆盖在网页之上的：全屏遮罩、弹窗、引导跳转层、插页广告、抽奖浮层等。
     * 通过 class/id 关键词匹配，避免误伤正常 fixed 导航栏。
     */
    public static final String OVERLAY_CSS =
        "[class*='overlay-ad'],[id*='overlay-ad']," +
        "[class*='ad-overlay'],[id*='ad-overlay']," +
        "[class*='mask-ad'],[id*='mask-ad']," +
        "[class*='ad-mask'],[id*='ad-mask']," +
        "[class*='modal-ad'],[id*='modal-ad']," +
        "[class*='ad-modal'],[id*='ad-modal']," +
        "[class*='interstitial'],[id*='interstitial']," +
        "[class*='popup-box'],[id*='popup-box']," +
        "[class*='popup-wrap'],[id*='popup-wrap']," +
        "[class*='popbox'],[id*='popbox']," +
        "[class*='pop-box'],[id*='pop-box']," +
        "[class*='lightbox-ad'],[id*='lightbox-ad']," +
        "[class*='ad-lightbox'],[id*='ad-lightbox']," +
        "[class*='fullscreen-ad'],[id*='fullscreen-ad']," +
        "[class*='ad-fullscreen'],[id*='ad-fullscreen']," +
        "[class*='splash-ad'],[id*='splash-ad']," +
        "[class*='preroll'],[id*='preroll']," +
        "[class*='coupon-mask'],[id*='coupon-mask']," +
        "[class*='lottery-pop'],[id*='lottery-pop']," +
        "[class*='red-envelope'],[id*='red-envelope']," +
        "[class*='redirect-mask'],[id*='redirect-mask']," +
        "[class*='jump-mask'],[id*='jump-mask']," +
        "[class*='guide-mask'],[id*='guide-mask']," +
        "[class*='download-mask'],[id*='download-mask']," +
        "[class*='app-pop'],[id*='app-pop']," +
        "[class*='open-app'],[id*='open-app']," +
        "[class*='openapp'],[id*='openapp']" +
        "{display:none!important;visibility:hidden!important;height:0!important;width:0!important;opacity:0!important}";

    /**
     * 覆盖层移除 / 跳转拦截 JS 脚本。
     * 在页面加载后执行，并设置定时巡检（应对动态注入的弹窗）：
     * 1. 移除匹配广告/遮罩关键词的 fixed/absolute 覆盖层
     * 2. 移除面积接近全屏、且内部含 iframe / 广告关键词的高 z-index 遮罩
     * 3. 拦截 window.open / 弹窗跳转
     * 4. 恢复页面滚动锁定（很多遮罩会给 body 加 overflow:hidden）
     */
    public static final String OVERLAY_REMOVER_JS =
        "(function(){"
        + "if(window.__sitelockOverlayGuard)return;window.__sitelockOverlayGuard=true;"
        // 关键词：命中即视为广告/跳转遮罩层
        + "var KW=/ad|adv|advert|banner|sponsor|popup|popunder|popbox|interstitial|"
        + "preroll|overlay|mask|modal|lightbox|splash|coupon|lottery|red-envelope|"
        + "redirect|jump|guide|download|openapp|open-app|app-pop|fullscreen/i;"
        // 白名单关键词：避免误伤正常功能
        + "var SAFE=/nav|menu|header|footer|toolbar|sidebar|breadcrumb|pagination|"
        + "toast|tooltip|dropdown|select|combobox|calendar|datepicker|search|"
        + "comment|reply|form|login|signup|cart|drawer|sheet|snackbar/i;"
        + "function isFullScreen(el){"
        + "var r=el.getBoundingClientRect();"
        + "var vw=window.innerWidth||document.documentElement.clientWidth;var vh=window.innerHeight||document.documentElement.clientHeight;"
        + "return (r.width>=vw*0.8&&r.height>=vh*0.8);}"
        + "function hide(el){try{el.style.setProperty('display','none','important');"
        + "el.style.setProperty('visibility','hidden','important');"
        + "el.style.setProperty('opacity','0','important');}catch(e){}}"
        + "function scan(){try{var els=document.querySelectorAll('div,section,aside,iframe');"
        + "for(var i=0;i<els.length;i++){var el=els[i];if(el.dataset&&el.dataset.sitelockSafe)continue;"
        + "var st=window.getComputedStyle(el);var pos=st.position;var z=parseInt(st.zIndex||'0',10);"
        + "if(pos!=='fixed'&&pos!=='absolute')continue;"
        + "var idc=(el.id||'')+' '+(el.className&&el.className.toString?el.className.toString():'');"
        + "var full=isFullScreen(el);"
        // 命中关键词且非白名单
        + "if(KW.test(idc)&&!SAFE.test(idc)){hide(el);continue;}"
        // 全屏 + 高 z-index + 内含广告 iframe 或关键词
        + "if(full&&z>=1000){"
        + "var innerAd=(el.querySelector('iframe[src*=\"doubleclick\"],iframe[src*=\"ads\"],iframe[src*=\"adnxs\"],ins.adsbygoogle,[id^=\"div-gpt-ad\"]')!=null);"
        + "if(innerAd||KW.test(idc)){hide(el);continue;}"
        + "}"
        + "}}catch(e){}}"
        + "function unlockScroll(){try{"
        + "document.documentElement.style.setProperty('overflow','auto','important');"
        + "document.body.style.setProperty('overflow','auto','important');"
        + "document.documentElement.style.setProperty('position','static','important');"
        + "document.body.style.setProperty('position','static','important');"
        + "document.documentElement.style.setProperty('height','auto','important');"
        + "document.body.style.setProperty('height','auto','important');"
        + "}catch(e){}}"
        + "scan();unlockScroll();"
        + "if(window.__sitelockOverlayTimer)clearInterval(window.__sitelockOverlayTimer);"
        + "window.__sitelockOverlayTimer=setInterval(function(){scan();},1500);"
        // 10 分钟后停止巡检，避免长期占用
        + "setTimeout(function(){if(window.__sitelockOverlayTimer)clearInterval(window.__sitelockOverlayTimer);},600000);"
        + "})();";

    public static boolean isAdRequest(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String kw : AD_DOMAIN_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
