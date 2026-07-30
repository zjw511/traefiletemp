package com.sitelock.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * 可监听滚动的 WebView：暴露 onScrollChanged 回调，
 * 供 Activity 据此判断上滑/下滑以收拢/展开工具栏。
 */
public class ObservableWebView extends WebView {

    public interface OnScrollChangeListener {
        /**
         * @param scrollY 当前竖直滚动位置
         * @param dy      自上次回调的增量（>0 表示内容上移/页面向下滚动，<0 表示反向）
         */
        void onScrollChanged(WebView view, int scrollY, int dy);
    }

    private OnScrollChangeListener listener;
    private int lastScrollY = 0;

    public ObservableWebView(Context context) {
        super(context);
    }

    public ObservableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnScrollChangeListener(OnScrollChangeListener l) {
        this.listener = l;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) {
            listener.onScrollChanged(this, t, t - lastScrollY);
            lastScrollY = t;
        }
    }
}
