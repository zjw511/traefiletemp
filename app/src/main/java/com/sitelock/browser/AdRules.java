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
     * 覆盖层移除 / 跳转拦截 JS 脚本（通用增强版）。
     * 不依赖具体 class 名，而是基于结构特征检测 + MutationObserver 实时监听，
     * 通杀各类动态注入的广告覆盖层、弹窗、页边浮动广告、点击劫持跳转：
     *
     * 1. MutationObserver 监听 DOM 变化，新增节点实时检测
     * 2. 移除固定定位 + 高 z-index 的全屏遮罩 / 含 iframe / 纯广告图覆盖层
     * 3. 移除贴边的页边浮动广告条（含 img/a 且文字占比低）
     * 4. capture 阶段拦截点击非本站链接 / onclick 跳转
     * 5. 重写 window.open 阻止弹窗跳转
     * 6. 恢复被遮罩锁定的页面滚动
     */
    public static final String OVERLAY_REMOVER_JS =
        "(function(){"
        // 清理上一次注入的旧实例（避免监听器/MO 重复）
        + "try{if(window.__sitelockMO)window.__sitelockMO.disconnect();}catch(e){}"
        + "try{if(window.__sitelockTimer)clearInterval(window.__sitelockTimer);}catch(e){}"
        + "try{if(window.__sitelockClick&&document.removeEventListener)document.removeEventListener('click',window.__sitelockClick,true);}catch(e){}"

        // ===== 关键词 =====
        // 命中即视为广告/遮罩层
        + "var KW=/ad|adv|advert|banner|sponsor|popup|popunder|popbox|interstitial|"
        + "preroll|overlay|mask|modal|lightbox|splash|coupon|lottery|red-envelope|"
        + "redirect|jump|guide|download|openapp|open-app|app-pop|fullscreen|"
        + "float|sticky|layer|cover|dialog|toast-ad|tip-ad/i;"
        // 白名单关键词：避免误伤正常功能
        + "var SAFE=/nav|menu|header|footer|toolbar|sidebar|breadcrumb|pagination|"
        + "toast|tooltip|dropdown|select|combobox|calendar|datepicker|search|"
        + "comment|reply|form|login|signup|cart|drawer|sheet|snackbar|"
        + "captcha|verify|slider|content|article|main|body|wrap|container/i;"

        // ===== 工具函数 =====
        + "function vp(){return{w:window.innerWidth||document.documentElement.clientWidth,"
        + "h:window.innerHeight||document.documentElement.clientHeight};}"
        + "function rect(el){try{return el.getBoundingClientRect();}catch(e){return{width:0,height:0,top:0,left:0};}}"
        + "function isFull(el){var r=rect(el);var v=vp();return r.width>=v.w*0.8&&r.height>=v.h*0.8;}"
        + "function idc(el){return(el.id||'')+' '+((el.className&&el.className.toString)?el.className.toString():'');}"
        + "function hide(el){try{"
        + "el.style.setProperty('display','none','important');"
        + "el.style.setProperty('visibility','hidden','important');"
        + "el.style.setProperty('opacity','0','important');"
        + "el.style.setProperty('pointer-events','none','important');"
        + "}catch(e){}}"
        // 元素内文字占比：文字少、图/iframe多 → 像广告
        + "function textRatio(el){var t=(el.innerText||'').replace(/\\s/g,'').length;"
        + "var imgs=el.querySelectorAll('img,iframe,svg,canvas,video').length;"
        + "if(imgs===0&&t>0)return 1;return t/(t+imgs*40);}"
        + "function hasAdIframe(el){return !!el.querySelector('iframe[src]');}"
        + "function hasImgLink(el){return !!el.querySelector('a[href] img, img, a[href]');}"
        + "function isStickyNav(el){var s=idc(el);return SAFE.test(s);}"
        // 贴边检测：fixed 元素贴顶/底/左/右 → 页边广告嫌疑
        + "function isEdge(r,v){return r.top<=4||r.left<=4||(r.top+r.height)>=v.h-4||(r.left+r.width)>=v.w-4;}"

        // ===== 单元素判定：是否应隐藏 =====
        + "function shouldHide(el){"
        + "if(!el||el.nodeType!==1)return false;"
        + "if(el.tagName==='HTML'||el.tagName==='BODY')return false;"
        + "if(el.dataset&&el.dataset.sitelockSafe)return false;"
        + "var st;try{st=window.getComputedStyle(el);}catch(e){return false;}"
        + "var pos=st.position;if(pos!=='fixed'&&pos!=='absolute')return false;"
        + "var z=parseInt(st.zIndex||'0',10);"
        + "var s=idc(el);if(isStickyNav(el))return false;"
        + "var full=isFull(el);var r=rect(el);var v=vp();var edge=isEdge(r,v);"
        + "var kw=KW.test(s);var safe=SAFE.test(s);"

        // 规则1：命中广告关键词且非白名单 → 隐藏
        + "if(kw&&!safe)return true;"
        // 规则2：全屏覆盖 + 高 z-index → 插页/遮罩广告
        + "if(full&&z>=1000)return true;"
        // 规则3：全屏覆盖 + 内含 iframe/图片且文字占比低 → 广告层
        + "if(full&&(hasAdIframe(el)||textRatio(el)<0.3))return true;"
        // 规则4：贴边 + 含 iframe → 页边/悬浮广告
        + "if(edge&&hasAdIframe(el))return true;"
        // 规则5：贴边 + 高 z-index + 文字占比低（纯图/纯链接）→ 浮动广告条
        + "if(edge&&z>=100&&textRatio(el)<0.25&&hasImgLink(el))return true;"
        // 规则6：fixed + 尺寸很小（图标广告位）+ 含 a/img
        + "if(pos==='fixed'&&(r.width<200||r.height<200)&&z>=100&&hasImgLink(el)&&textRatio(el)<0.2)return true;"
        + "return false;"
        + "}"

        // ===== 扫描全文档 =====
        + "function scan(){try{"
        + "var els=document.querySelectorAll('div,section,aside,iframe,span,a,ul,li,table,p,img,ins');"
        + "for(var i=0;i<els.length;i++){if(shouldHide(els[i]))hide(els[i]);}"
        + "}catch(e){}}"

        // ===== 解除滚动锁定 =====
        + "function unlockScroll(){try{"
        + "document.documentElement.style.setProperty('overflow','auto','important');"
        + "document.body.style.setProperty('overflow','auto','important');"
        + "document.documentElement.style.setProperty('position','static','important');"
        + "document.body.style.setProperty('position','static','important');"
        + "document.documentElement.style.setProperty('height','auto','important');"
        + "document.body.style.setProperty('height','auto','important');"
        + "document.documentElement.style.setProperty('touch-action','auto','important');"
        + "document.body.style.setProperty('touch-action','auto','important');"
        + "}catch(e){}}"

        // ===== 拦截点击跳转（capture 阶段，阻止非本站 a 和 onclick 跳转劫持） =====
        + "function hijackClick(){try{"
        + "var home=window.__sitelockHome||'';"
        + "var handler=function(e){"
        + "var n=e.target;var hops=0;"
        + "while(n&&hops<8){if(n.tagName==='A'&&n.href){"
        + "var host='';try{host=new URL(n.href).hostname.replace(/^www\\./,'');}catch(ex){}"
        + "if(host&&home&&host!==home&&!host.endsWith('.'+home)"
        + "&&n.href.indexOf('javascript:')!==0){"
        + "e.preventDefault();e.stopPropagation();return false;}"
        + "}n=n.parentElement;hops++;}"
        + "};"
        + "window.__sitelockClick=handler;"
        + "document.addEventListener('click',handler,true);"
        + "}catch(e){}}"

        // ===== 重写 window.open 阻止弹窗 =====
        + "try{window.open=function(){return null;};}catch(e){}"

        // ===== 启动 =====
        + "scan();unlockScroll();hijackClick();"

        // MutationObserver：实时检测新注入的节点
        + "try{"
        + "var mo=new MutationObserver(function(muts){"
        + "for(var i=0;i<muts.length;i++){"
        + "var mr=muts[i];"
        + "if(mr.addedNodes){for(var j=0;j<mr.addedNodes.length;j++){"
        + "var n=mr.addedNodes[j];"
        + "if(n.nodeType===1){"
        + "if(shouldHide(n))hide(n);"
        + "var cs=n.querySelectorAll?n.querySelectorAll('div,section,aside,iframe,span,a,ul,li,table,p,img,ins'):[];"
        + "for(var k=0;k<cs.length;k++){if(shouldHide(cs[k]))hide(cs[k]);}"
        + "}}}}});"
        + "mo.observe(document.documentElement||document.body,{childList:true,subtree:true});"
        + "window.__sitelockMO=mo;"
        + "}catch(e){}"

        // 定时巡检兜底（4 秒一次，3 分钟后停）
        + "if(window.__sitelockTimer)clearInterval(window.__sitelockTimer);"
        + "window.__sitelockTimer=setInterval(function(){scan();},4000);"
        + "setTimeout(function(){if(window.__sitelockTimer)clearInterval(window.__sitelockTimer);},180000);"
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
