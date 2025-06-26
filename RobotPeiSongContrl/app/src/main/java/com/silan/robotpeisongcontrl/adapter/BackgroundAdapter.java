package com.silan.robotpeisongcontrl.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

public class BackgroundAdapter extends BaseAdapter {
    private Context context;
    private int[] backgrounds;

    public BackgroundAdapter(Context context, int[] backgrounds) {
        this.context = context;
        this.backgrounds = backgrounds;
    }

    @Override
    public int getCount() {
        return backgrounds.length;
    }

    @Override
    public Object getItem(int position) {
        return backgrounds[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            imageView = new ImageView(context);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(300, 300));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            imageView = (ImageView) convertView;
        }

        imageView.setImageResource(backgrounds[position]);
        return imageView;
    }
}
