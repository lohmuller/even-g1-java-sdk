// UIHelper.java
package com.evenrealities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;

public class UIHelper {
    public static LinearLayout root;
    public static TextView logTextView;
    public static ScrollView scrollView;
    public static Button retryButton;

    public static TextView bluetoothLeftStatus, bluetoothRightStatus;

    public static void setupUI(Activity activity) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        activity.setContentView(root);

        View statusPanel = activity.getLayoutInflater().inflate(R.layout.status_panel, root, false);
        root.addView(statusPanel);

        bluetoothLeftStatus = statusPanel.findViewById(R.id.bluetoothLeftStatus);
        bluetoothRightStatus = statusPanel.findViewById(R.id.bluetoothRightStatus);

        scrollView = new ScrollView(activity);
        logTextView = new TextView(activity);
        scrollView.addView(logTextView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public static Button createButton(Context ctx, String text, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(params);
        return btn;
    }

    public static void addButtonRow(Context ctx, Button... buttons) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button btn : buttons) {
            row.addView(btn);
        }
        root.addView(row);
    }

    public static void appendLog(String tag, String message) {
        android.util.Log.d(tag, message);
        logTextView.append(message + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    public static void clearLog() {
        logTextView.setText("");
    }

    public static void updateBluetoothStatus(EvenOsApi.Sides side, String status) {
        if (side == EvenOsApi.Sides.LEFT && bluetoothLeftStatus != null) {
            updateBluetoothStatusWithStyle(bluetoothLeftStatus, status);
        } else if (side == EvenOsApi.Sides.RIGHT && bluetoothRightStatus != null) {
            updateBluetoothStatusWithStyle(bluetoothRightStatus, status);
        }
    }

    private static void updateBluetoothStatusWithStyle(TextView statusView, String status) {
        String emoji;
        int color;
        
        if (status.contains("Not Bonded")) {
            emoji = "🔴";
            color = 0xFFE53935; // Red
        } else if (status.contains("Bonding")) {
            emoji = "🟡";
            color = 0xFFFFB300; // Yellow
        } else if (status.contains("Bonded (Not Connected)")) {
            emoji = "🟡";
            color = 0xFFFFB300; // Yellow
        } else if (status.contains("Bonded (Connecting")) {
            emoji = "🟡";
            color = 0xFFFFB300; // Yellow
        } else if (status.contains("Bonded (Connected)")) {
            emoji = "🟢";
            color = 0xFF43A047; // Green
        } else if (status.contains("Error") || status.contains("Timeout")) {
            emoji = "🔴";
            color = 0xFFE53935; // Red
        } else {
            emoji = "⚪";
            color = 0xFF757575; // Gray
        }

        String displayText = String.format("%s %s", emoji, status);
        statusView.setText(displayText);
        statusView.setTextColor(color);
    }

    public static void showOpenSettingsButton(Activity activity) {
        Button btn = new Button(activity);
        btn.setText("Open App Settings");
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        });
        root.addView(btn);
    }
}