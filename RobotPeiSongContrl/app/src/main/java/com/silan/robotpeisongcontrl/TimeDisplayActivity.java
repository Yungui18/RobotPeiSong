package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeDisplayActivity extends BaseActivity {
    private static final Map<String, String> TIME_ZONES = new HashMap<String, String>() {{
        put("Asia/Shanghai", "中国");
        put("Asia/Seoul", "韩国");
        put("Asia/Tokyo", "日本");
        put("America/New_York", "美国");
        put("Europe/Moscow", "俄罗斯");
    }};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_display);
        ListView listView = findViewById(R.id.timezone_list);
        List<String> timezoneNames = new ArrayList<>(TIME_ZONES.values());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, timezoneNames
        );
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selected = adapter.getItem(position);
                String timezoneId = getKeyByValue(TIME_ZONES, selected);

                saveTimezonePreference(timezoneId);
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void saveTimezonePreference(String timezoneId) {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("selected_timezone", timezoneId).apply();
    }

    private static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

}