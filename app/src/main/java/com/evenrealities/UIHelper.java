// UIHelper.java  –  versão compacta
package com.evenrealities;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.evenrealities.BluetoothHelper.PairingStatus;
import com.evenrealities.BluetoothHelper.ConnectionStatus;
import com.evenrealities.even_g1_sdk.api.EvenOsApi;

public final class UIHelper {

    /* ---------- widgets ---------- */
    private static Activity mainActivity;
    private static LinearLayout root;
    private static TextView logTextView;
    private static ScrollView scrollView;
    private static TextView btLeft, btRight;
    private static LinearLayout filterLayout;

    private static final String TAG = "UIHelper";

    private static class DeviceStatus {
        public PairingStatus bonded;
        public ConnectionStatus connected;

        public DeviceStatus(PairingStatus bonded, ConnectionStatus connected) {
            this.bonded = bonded;
            this.connected = connected;
        }
    }

    private static final DeviceStatus statusLeft = new DeviceStatus(
        PairingStatus.NOT_BONDED,
        ConnectionStatus.DISCONNECTED
    );

    private static final DeviceStatus statusRight = new DeviceStatus(
        PairingStatus.NOT_BONDED,
        ConnectionStatus.DISCONNECTED
    );

    /* ---------- setupUI ---------- */
    public static void setupUI(Activity activity) {
        mainActivity = activity;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        mainActivity.setContentView(root);

        /* painel de status (seu XML) */
        View status = mainActivity.getLayoutInflater()
                         .inflate(R.layout.status_panel, root, false);
        root.addView(status);
        btLeft = status.findViewById(R.id.bluetoothLeftStatus);
        btRight = status.findViewById(R.id.bluetoothRightStatus);

        /* área de log */
        scrollView = new ScrollView(mainActivity);
        logTextView = new TextView(mainActivity);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setTextSize(12);
        logTextView.setTextColor(Color.WHITE);
        logTextView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        scrollView.addView(logTextView);
        root.addView(scrollView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        /* área de filtros */
        filterLayout = new LinearLayout(mainActivity);
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(filterLayout);
    }

    /* ---------- createButton ---------- */
    public static Button createButton(Activity activity, String label, View.OnClickListener onClick) {
        Button btn = new Button(activity);
        btn.setText(label);
        btn.setOnClickListener(onClick);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(Color.parseColor("#E0E0E0"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        root.addView(btn, 1, lp);
        return btn;
    }

    /* ---------- addButtonRow ---------- */
    public static void addButtonRow(Activity activity, Button... buttons) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        for (Button btn : buttons) {
            // Remove the button from its current parent if it has one
            if (btn.getParent() != null) {
                ((ViewGroup) btn.getParent()).removeView(btn);
            }
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, // width
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f // weight
            );
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(btn, lp);
        }

        root.addView(row, 1); // Add after status panel
    }

    /* ---------- appendLog ---------- */
    public static void appendLog(String tag, String message) {
        if (mainActivity == null || logTextView == null || scrollView == null) {
            throw new IllegalStateException("UIHelper not properly initialized");
        }
        
        String line = tag + ": " + message + "\n";
        mainActivity.runOnUiThread(() -> {
            boolean atBottom = isAtBottom();
            logTextView.append(line);
            if (atBottom) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    /* ---------- clearLog ---------- */
    public static void clearLog() {
        if (logTextView != null) {
            mainActivity.runOnUiThread(() -> logTextView.setText(""));
        }
    }

    /* ---------- setBleStatus ---------- */
    public static void setBleStatus(EvenOsApi.Sides side, PairingStatus status) {
        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;
        sideStatus.bonded = status;
        updateBleStatus(side);
    }

    public static void setBleConnectionStatus(EvenOsApi.Sides side, ConnectionStatus status) {
        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;
        sideStatus.connected = status;
        updateBleStatus(side);
    }

    private static void updateBleStatus(EvenOsApi.Sides side) {
        TextView target = (side == EvenOsApi.Sides.LEFT) ? btLeft : btRight;
        if (target == null) return;

        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;

        String bondedStatus = sideStatus.bonded.name();
        String connectedStatus = sideStatus.connected.name();

        String emoji = "⚪";
        int color = Color.GRAY;

        switch (sideStatus.bonded) {
            case NOT_BONDED:     emoji = "🔴"; color = 0xFFE53935; break;
            case BONDING:        emoji = "🟡"; color = 0xFFFFB300; break;
            case BONDING_FAILED: emoji = "🟡"; color = 0xFFFFB300; break;
            case ERROR:          emoji = "🔴"; color = 0xFFE53935; break;
        }

        if (sideStatus.connected == ConnectionStatus.CONNECTED) {
            switch (sideStatus.connected) {
                case CONNECTED:   emoji = "🟡"; color = 0xFF43A047; break;
                case INITIALIZED: emoji = "🟢"; color = 0xFF43A047; break;
                case DISCONNECTED: emoji = "🔴"; color = 0xFFE53935; break;
            }
        }

        target.setText(String.format("%s %s %s", emoji, bondedStatus, connectedStatus));
        target.setTextColor(color);
    }

    /* ---------- updateBluetoothStatus ---------- */
    public static void updateBluetoothStatus(EvenOsApi.Sides side, String status) {
        if (mainActivity == null) return;
        mainActivity.runOnUiThread(() -> {
            TextView target = (side == EvenOsApi.Sides.LEFT) ? btLeft : btRight;
            if (target != null) {
                target.setText(status);
            }
        });
    }

    /* ---------- util ---------- */
    private static boolean isAtBottom() {
        return scrollView.getScrollY() + scrollView.getHeight() >= logTextView.getHeight();
    }

    private static int dp(int v) {
        return (int) (v * mainActivity.getResources().getDisplayMetrics().density);
    }

    private UIHelper() {}
}
