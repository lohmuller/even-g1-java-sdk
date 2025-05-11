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
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, // height
            1f // weight
        ));
        scrollView.setFillViewport(true); // Garante que o conteúdo ocupe todo o espaço
        scrollView.setScrollbarFadingEnabled(false); // Mantém a barra de rolagem sempre visível
        scrollView.setVerticalScrollBarEnabled(true); // Garante que a barra vertical está habilitada
        scrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY); // Estilo da barra de rolagem

        logTextView = new TextView(mainActivity);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setTextSize(12);
        logTextView.setTextColor(Color.WHITE);
        logTextView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        logTextView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        scrollView.addView(logTextView);
        root.addView(scrollView);

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

    /* ---------- setBlePairingStatus ---------- */
    public static void setBlePairingStatus(EvenOsApi.Sides side, PairingStatus status) {
        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;
        sideStatus.bonded = status;
        updateBleStatus(side);
    }

    /* ---------- setBleConnectionStatus ---------- */
    public static void setBleConnectionStatus(EvenOsApi.Sides side, ConnectionStatus status) {
        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;
        sideStatus.connected = status;
        updateBleStatus(side);
    }

    private static void updateBleStatus(EvenOsApi.Sides side) {
        TextView target = (side == EvenOsApi.Sides.LEFT) ? btLeft : btRight;
        if (target == null) return;

        DeviceStatus sideStatus = (side == EvenOsApi.Sides.LEFT) ? statusLeft : statusRight;

        String emoji = "⚪";

        // Primeiro verifica o status de conexão se estiver BONDED
        if (sideStatus.bonded == PairingStatus.BONDED) {
            switch (sideStatus.connected) {
                case CONNECTING:
                    emoji = "🟡";
                    break;
                case CONNECTED:
                    emoji = "🟡";
                    break;
                case INITIALIZED:
                    emoji = "🟢";
                    break;
                case DISCONNECTED:
                    emoji = "⚪";
                    break;
                default:
                    emoji = "⚪";
                    break;
            }
        } else {
            // Se não estiver BONDED, verifica o status de pairing
            android.util.Log.d(TAG, "Status is not BONDED, checking pairing status");
            switch (sideStatus.bonded) {
                case NOT_BONDED:
                    emoji = "⚪";
                    break;
                case BONDING:
                    emoji = "🟡";
                    break;
                case BONDING_FAILED:
                case ERROR:
                    emoji = "🔴";
                    break;
                default:
                    emoji = "⚪";
                    break;
            }
        }

        int color = Color.parseColor("#808080");
        if (emoji == "🟡") {
            color = Color.parseColor("#FFD700");
        } else if (emoji == "🟢") {
            color = Color.parseColor("#008000");
        } else if (emoji == "🔴") {
            color = Color.parseColor("#FF0000");
        }

        target.setText(String.format("%s %s / %s",
            emoji, sideStatus.bonded.name(), sideStatus.connected.name()));
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
