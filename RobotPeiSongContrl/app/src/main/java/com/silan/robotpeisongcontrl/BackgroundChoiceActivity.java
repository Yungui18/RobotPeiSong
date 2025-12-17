package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.adapter.BackgroundAdapter;

public class BackgroundChoiceActivity extends BaseActivity {
    private static final int[] BACKGROUNDS = {
            R.drawable.bg_default,
            R.drawable.bg_nature,
            R.drawable.bg_abstract,
            R.drawable.bg_tech,
            R.drawable.bg_city,
            R.drawable.bg_gradient
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_choice);
        GridView gridView = findViewById(R.id.grid_backgrounds);
        BackgroundAdapter adapter = new BackgroundAdapter(this, BACKGROUNDS);
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                saveBackgroundPreference(BACKGROUNDS[position]);
                setResult(RESULT_OK);
                finish();
            }
        });
    }
    private void saveBackgroundPreference(int bgResId) {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("background_res", bgResId).apply();
    }

    @Override
    protected boolean isAdminPage() {
        return true;
    }
}