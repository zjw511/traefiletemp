package com.sitelock.browser;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

/** 用户脚本列表适配器 */
public class UserScriptAdapter extends ArrayAdapter<UserScript> {
    private final UserScriptDbHelper db;
    private final Runnable onToggleChanged;

    public UserScriptAdapter(@NonNull Context context, @NonNull List<UserScript> items,
                             UserScriptDbHelper db, Runnable onToggleChanged) {
        super(context, 0, items);
        this.db = db;
        this.onToggleChanged = onToggleChanged;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_userscript, parent, false);
        }
        UserScript s = getItem(position);
        TextView name = convertView.findViewById(R.id.scriptName);
        TextView desc = convertView.findViewById(R.id.scriptDesc);
        TextView match = convertView.findViewById(R.id.scriptMatch);
        SwitchCompat toggle = convertView.findViewById(R.id.scriptToggle);

        name.setText(s.name);
        desc.setText(TextUtils.isEmpty(s.description) ? "（无描述）" : s.description);
        String matchInfo = s.matches.isEmpty() && s.includes.isEmpty()
                ? "匹配：所有页面"
                : "匹配：" + TextUtils.join(", ", s.matches) + TextUtils.join(", ", s.includes);
        match.setText(matchInfo + "  |  时机：" + s.runAt);

        // 切换前移除监听，避免回填触发
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(s.enabled);
        toggle.setOnCheckedChangeListener((v, checked) -> {
            s.enabled = checked;
            db.setEnabled(s.id, checked);
            if (onToggleChanged != null) onToggleChanged.run();
        });
        return convertView;
    }
}
