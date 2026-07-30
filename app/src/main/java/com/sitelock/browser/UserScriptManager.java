package com.sitelock.browser;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 用户脚本管理器：
 * - 从 DB 加载启用的脚本
 * - 按 URL 和注入时机（document-start / document-end）筛选
 * - 生成包含 GM_ API 的完整注入 JS
 *
 * 实现的 GM_ API（轻量版）：
 * - GM_setValue / GM_getValue / GM_deleteValue（基于 localStorage）
 * - GM_getResourceText / GM_getResourceURL（占位，返回空）
 * - GM_addStyle（注入 <style>）
 * - GM_log（console.log）
 * - unsafeWindow（指向 window）
 *
 * 不支持：GM_xmlhttpRequest（跨域请求，需原生桥接）、@require 外部依赖下载
 */
public class UserScriptManager {
    private final UserScriptDbHelper db;
    private List<UserScript> cache;

    public UserScriptManager(Context context) {
        this.db = new UserScriptDbHelper(context.getApplicationContext());
        reload();
    }

    /** 重新加载缓存（脚本变更后调用） */
    public void reload() {
        cache = db.getEnabled();
    }

    public List<UserScript> getAll() {
        return db.getAll();
    }

    public UserScriptDbHelper getDb() {
        return db;
    }

    /**
     * 生成在指定时机注入的脚本 JS。
     * @param url 当前页面 URL
     * @param runAt 时机：document-start | document-end | document-idle
     * @return 拼接好的 JS（已包裹 IIFE + GM_ API 注入），无匹配脚本时返回空串
     */
    public String buildInjection(String url, String runAt) {
        if (cache == null || cache.isEmpty() || TextUtils.isEmpty(url)) return "";
        StringBuilder sb = new StringBuilder();
        for (UserScript s : cache) {
            if (!s.enabled) continue;
            if (!s.matchesUrl(url)) continue;
            // document-idle 视为 document-end
            String scriptRunAt = s.runAt == null ? "document-end" : s.runAt;
            if ("document-idle".equals(scriptRunAt)) scriptRunAt = "document-end";
            if (!scriptRunAt.equals(runAt)) continue;

            String body = s.getBody();
            if (TextUtils.isEmpty(body)) continue;

            sb.append("(function(){\n");
            sb.append(buildGmApi(s, url));
            sb.append("try{\n");
            sb.append(body);
            sb.append("\n}catch(e){console.error('[SiteLock UserScript] ")
              .append(escapeJs(s.name)).append(" error:',e);}\n");
            sb.append("})();\n");
        }
        return sb.toString();
    }

    /** 为单个脚本生成 GM_ API 注入代码 */
    private String buildGmApi(UserScript s, String url) {
        String scriptId = "sitelock_us_" + s.id;
        StringBuilder sb = new StringBuilder();
        // 脚本独立命名空间前缀，避免多个脚本 GM_setValue 冲突
        String ns = scriptId + "_";

        sb.append("var GM=(function(){\n");
        sb.append("var NS='").append(ns).append("';\n");
        // GM_setValue / GM_getValue / GM_deleteValue（基于 localStorage）
        sb.append("function setValue(k,v){try{localStorage.setItem(NS+k,JSON.stringify(v));}catch(e){}}\n");
        sb.append("function getValue(k,d){try{var r=localStorage.getItem(NS+k);return r===null?d:JSON.parse(r);}catch(e){return d;}}\n");
        sb.append("function deleteValue(k){try{localStorage.removeItem(NS+k);}catch(e){}}\n");
        sb.append("function listValues(){var r=[];try{for(var i=0;i<localStorage.length;i++){var key=localStorage.key(i);if(key&&key.indexOf(NS)===0)r.push(key.substring(NS.length));}}catch(e){}return r;}\n");
        // GM_addStyle
        sb.append("function addStyle(css){try{var s=document.createElement('style');s.type='text/css';s.appendChild(document.createTextNode(css));(document.head||document.documentElement).appendChild(s);return s;}catch(e){return null;}}\n");
        // GM_log
        sb.append("function log(){try{console.log.apply(console,arguments);}catch(e){}}\n");
        // GM_getResourceText / GM_getResourceURL（占位）
        sb.append("function getResourceText(n){return '';}\n");
        sb.append("function getResourceURL(n){return '';}\n");
        // GM_xmlhttpRequest（不支持跨域，仅同源 fetch 包装）
        sb.append("function xmlhttpRequest(opts){try{if(!opts||!opts.url)return;fetch(opts.url,{method:opts.method||'GET',headers:opts.headers||{}}).then(function(r){return r.text();}).then(function(t){if(opts.onload)opts.onload({responseText:t,response:t,status:200});}).catch(function(e){if(opts.onerror)opts.onerror(e);});}catch(e){if(opts.onerror)opts.onerror(e);}}\n");
        // GM_info
        sb.append("var info={script:{name:'").append(escapeJs(s.name)).append("',version:''},scriptHandler:'SiteLockBrowser',version:'1.0'};\n");
        sb.append("return{setValue:setValue,getValue:getValue,deleteValue:deleteValue,listValues:listValues,addStyle:addStyle,log:log,getResourceText:getResourceText,getResourceURL:getResourceURL,xmlhttpRequest:xmlhttpRequest,info:info};\n");
        sb.append("})();\n");

        // 暴露 GM_ 前缀全局函数 + GM 对象 + unsafeWindow
        sb.append("var GM_setValue=GM.setValue,GM_getValue=GM.getValue,GM_deleteValue=GM.deleteValue,GM_listValues=GM.listValues,GM_addStyle=GM.addStyle,GM_log=GM.log,GM_getResourceText=GM.getResourceText,GM_getResourceURL=GM.getResourceURL,GM_xmlhttpRequest=GM.xmlhttpRequest,GM_info=GM.info;\n");
        sb.append("var unsafeWindow=window;\n");
        return sb.toString();
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
