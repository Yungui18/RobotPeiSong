package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.widget.ArrayAdapter;

import android.widget.ListView;


import java.util.LinkedHashMap;

import java.util.Map;

public class LanguageSettingsActivity extends BaseActivity {
    private static Map<String, String> LANGUAGES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_settings);

        initLanguagesMap();

        ListView listView = findViewById(R.id.language_list);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, LANGUAGES.values().toArray(new String[0])
        );
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position,  id) -> {
            String selected = adapter.getItem(position);
            String langCode = getKeyByValue(LANGUAGES, selected);

            saveLanguagePreference(langCode);
            restartApp();
        });
    }
    private void initLanguagesMap() {
        LANGUAGES = new LinkedHashMap<>();
        LANGUAGES.put("zh", getString(R.string.language_simplified_chinese));
        LANGUAGES.put("zh_rTW", getString(R.string.language_traditional_chinese));
        LANGUAGES.put("en", getString(R.string.language_english));
        LANGUAGES.put("ko", getString(R.string.language_korean));
        LANGUAGES.put("ja", getString(R.string.language_japanese));
    }


    private void saveLanguagePreference(String langCode) {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("selected_language", langCode).apply();
    }

    private void restartApp() {
        // 完全重启应用
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        System.exit(0);
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