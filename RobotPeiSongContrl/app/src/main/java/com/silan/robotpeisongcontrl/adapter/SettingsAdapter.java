package com.silan.robotpeisongcontrl.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.silan.robotpeisongcontrl.R;

public class SettingsAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final String[] titles;

    public SettingsAdapter(Context context, String[] titles) {
        super(context, R.layout.item_settings_menu, titles);
        this.context = context;
        this.titles = titles;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View rowView = inflater.inflate(R.layout.item_settings_menu, parent, false);

        TextView titleView = rowView.findViewById(R.id.tv_menu_title);
        titleView.setText(titles[position]);

        return rowView;
    }
}