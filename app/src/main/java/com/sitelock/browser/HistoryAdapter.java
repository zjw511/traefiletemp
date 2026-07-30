package com.sitelock.browser;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

/** 历史记录列表适配器 */
public class HistoryAdapter extends ArrayAdapter<HistoryDbHelper.HistoryItem> {

    public HistoryAdapter(@NonNull Context context, @NonNull List<HistoryDbHelper.HistoryItem> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_history, parent, false);
        }
        HistoryDbHelper.HistoryItem item = getItem(position);
        TextView title = convertView.findViewById(R.id.histTitle);
        TextView url = convertView.findViewById(R.id.histUrl);
        TextView time = convertView.findViewById(R.id.histTime);

        title.setText(item.title != null && !item.title.isEmpty() ? item.title : item.url);
        url.setText(item.url);
        time.setText(DateFormat.format("yyyy-MM-dd HH:mm", item.time));
        return convertView;
    }
}
