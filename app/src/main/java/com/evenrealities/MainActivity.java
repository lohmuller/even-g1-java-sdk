package com.evenrealities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.evenrealities.even_g1_sdk.connection.ConnectionManager;

public class MainActivity extends Activity {
    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logTextView = new TextView(this);
        logTextView.setText("App started\n");
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logTextView);
        setContentView(scrollView);

        // Exemplo de uso do SDK
        ConnectionManager dummy = null;
        appendLog("SDK imported: " + ConnectionManager.class.getSimpleName());
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            logTextView.append(message + "\n");
        });
    }
} 