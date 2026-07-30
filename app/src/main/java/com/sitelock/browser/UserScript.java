package com.sitelock.browser;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 油猴脚本模型：解析 UserScript 头部元信息。
 *
 * 支持的元信息：
 * @name        脚本名称
 * @match       URL 匹配（glob，支持 * 通配）
 * @include     URL 包含（glob）
 * @exclude     URL 排除（glob）
 * @run-at      注入时机：document-start | document-end | document-idle
 * @require     外部依赖（本轻量版不下载，仅记录）
 * @description 描述
 *
 * 头部格式：
 * // ==UserScript==
 * // @name  xxx
 * // @match *://*.example.com/*
 * // ==/UserScript==
 */
public class UserScript {
    public long id;
    public String name;
    public String description;
    public String code;            // 完整脚本（含头部）
    public boolean enabled;
    public long updateTime;

    // 解析后的元信息
    public final List<String> matches = new ArrayList<>();
    public final List<String> includes = new ArrayList<>();
    public final List<String> excludes = new ArrayList<>();
    public final List<String> requires = new ArrayList<>();
    public String runAt = "document-end"; // 默认 document-end

    private static final Pattern META_BLOCK = Pattern.compile(
        "//\\s*==UserScript==([\\s\\S]*?)//\\s*==/UserScript==");
    private static final Pattern META_LINE = Pattern.compile(
        "//\\s*@([\\w-]+)\\s+(.+)");

    /** 从完整脚本代码解析元信息（不影响 body） */
    public void parseMeta() {
        matches.clear();
        includes.clear();
        excludes.clear();
        requires.clear();
        runAt = "document-end";

        if (TextUtils.isEmpty(code)) return;
        Matcher bm = META_BLOCK.matcher(code);
        String block = bm.find() ? bm.group(1) : "";

        for (String line : block.split("\n")) {
            Matcher lm = META_LINE.matcher(line);
            if (!lm.find()) continue;
            String key = lm.group(1).trim();
            String val = lm.group(2).trim();
            if (TextUtils.isEmpty(val)) continue;
            switch (key) {
                case "name": this.name = val; break;
                case "description": this.description = val; break;
                case "match": matches.add(val); break;
                case "include": includes.add(val); break;
                case "exclude": excludes.add(val); break;
                case "require": requires.add(val); break;
                case "run-at": runAt = val; break;
                default: break;
            }
        }
        // name 缺失时用第一行非空代码作名字
        if (TextUtils.isEmpty(this.name)) {
            this.name = "未命名脚本";
        }
    }

    /** 取脚本 body（去掉头部块后的可执行部分） */
    public String getBody() {
        if (TextUtils.isEmpty(code)) return "";
        return META_BLOCK.matcher(code).replaceFirst("").trim();
    }

    /**
     * 判断脚本是否应作用于给定 URL。
     * 规则：@exclude 优先排除；然后 @match 或 @include 命中即生效；
     * 都没配置则视为对所有页面生效。
     */
    public boolean matchesUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        // exclude 优先
        for (String p : excludes) {
            if (globMatch(p, url)) return false;
        }
        boolean hasRule = !matches.isEmpty() || !includes.isEmpty();
        if (!hasRule) return true; // 无规则则默认生效
        for (String p : matches) {
            if (matchPattern(p, url)) return true;
        }
        for (String p : includes) {
            if (globMatch(p, url)) return true;
        }
        return false;
    }

    /**
     * @match 语法（接近 Chrome MV3 match patterns）：
     * <scheme>://<host><path>
     * scheme 支持 *（任意）或 http/https
     * host 支持 *（所有）、*.example.com（子域）、example.com
     * path 支持 glob 通配 *
     */
    private static boolean matchPattern(String pattern, String url) {
        if (TextUtils.isEmpty(pattern) || TextUtils.isEmpty(url)) return false;
        String p = pattern.trim();
        // 简单回退：当 pattern 含 * 时按 glob 处理
        if (p.contains("*")) {
            return globMatch(p, url);
        }
        return url.contains(p);
    }

    /** glob 通配匹配：* 匹配任意字符序列 */
    private static boolean globMatch(String pattern, String text) {
        if (TextUtils.isEmpty(pattern)) return false;
        // 转成正则
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '.': case '\\': case '+': case '(': case ')':
                case '[': case ']': case '{': case '}':
                case '^': case '$': case '|': case '/':
                    sb.append('\\').append(c); break;
                default: sb.append(c);
            }
        }
        try {
            return java.util.regex.Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE)
                    .matcher(text).matches();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return name + (enabled ? "" : " (已禁用)");
    }
}
