// UIHelper.java
package com.evenrealities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import java.util.HashSet;
import java.util.Set;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;

public class UIHelper {
    public static LinearLayout root;
    public static TextView logTextView;
    public static ScrollView scrollView;
    public static Button retryButton;
    private static Set<String> enabledTags = new HashSet<>();
    private static SpannableStringBuilder logBuffer = new SpannableStringBuilder();

    public static TextView bluetoothLeftStatus, bluetoothRightStatus;

    public static void setupUI(Activity activity) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        activity.setContentView(root);

        View statusPanel = activity.getLayoutInflater().inflate(R.layout.status_panel, root, false);
        root.addView(statusPanel);

        bluetoothLeftStatus = statusPanel.findViewById(R.id.bluetoothLeftStatus);
        bluetoothRightStatus = statusPanel.findViewById(R.id.bluetoothRightStatus);

        // Create log section with better visibility
        scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        
        logTextView = new TextView(activity);
        logTextView.setPadding(16, 16, 16, 16);
        logTextView.setTextSize(12);
        logTextView.setBackgroundColor(Color.parseColor("#F5F5F5"));
        logTextView.setTextColor(Color.BLACK);
        logTextView.setFocusable(true);
        logTextView.setFocusableInTouchMode(true);
        logTextView.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        
        scrollView.addView(logTextView);
        
        // Add a title for the log section
        TextView logTitle = new TextView(activity);
        logTitle.setText("Log Messages");
        logTitle.setTextSize(14);
        logTitle.setPadding(16, 16, 16, 8);
        root.addView(logTitle);
        
        // Add filter buttons
        LinearLayout filterLayout = new LinearLayout(activity);
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setPadding(16, 0, 16, 8);
        
        // Add filter buttons for each tag
        String[] tags = {"System", "EVEN_G1_Debug", "BluetoothHelper"};
        for (String tag : tags) {
            Button filterBtn = new Button(activity);
            filterBtn.setText(tag);
            filterBtn.setTextSize(10);
            filterBtn.setPadding(8, 1, 8, 1);
            
            // Set button style with reduced height
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnParams.height = (int) (activity.getResources().getDisplayMetrics().density * 24);
            btnParams.setMargins(4, 0, 4, 0);
            filterBtn.setLayoutParams(btnParams);
            
            filterBtn.setOnClickListener(v -> toggleTag(tag, filterBtn));
            
            // Set initial state (enabled)
            enabledTags.add(tag);
            updateButtonStyle(filterBtn, true);
            
            filterLayout.addView(filterBtn);
        }
        
        root.addView(filterLayout);

        // Add the scroll view with proper layout params to take remaining space
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, // Height will be determined by weight
            1f // Take all remaining space
        );
        params.setMargins(8, 0, 8, 8);
        root.addView(scrollView, params);

    }

    private static void toggleTag(String tag, Button button) {
        if (enabledTags.contains(tag)) {
            enabledTags.remove(tag);
            updateButtonStyle(button, false);
        } else {
            enabledTags.add(tag);
            updateButtonStyle(button, true);
        }
        refreshLogDisplay();
    }

    private static void updateButtonStyle(Button button, boolean enabled) {
        if (enabled) {
            button.setBackgroundColor(Color.parseColor("#E0E0E0")); // Light gray when enabled
            button.setTextColor(Color.parseColor("#212121")); // Dark gray text
            button.setAlpha(1.0f); // Fully opaque
        } else {
            button.setBackgroundColor(Color.parseColor("#F5F5F5")); // Very light gray when disabled
            button.setTextColor(Color.parseColor("#9E9E9E")); // Medium gray text
            button.setAlpha(0.7f); // Slightly transparent
        }
    }

    private static void refreshLogDisplay() {
        if (logTextView != null) {
            logTextView.post(() -> {
                // Clear current text
                logTextView.setText("");
                
                // Get all lines from buffer
                String[] lines = logBuffer.toString().split("\n");
                SpannableStringBuilder filteredBuilder = new SpannableStringBuilder();
                
                // Process each line
                for (String line : lines) {
                    for (String tag : enabledTags) {
                        if (line.contains(tag + ":")) {
                            // Extract tag and message
                            int tagEnd = line.indexOf(":") + 1;
                            String tagPart = line.substring(0, tagEnd);
                            String messagePart = line.substring(tagEnd);
                            
                            // Create tag span
                            SpannableString tagStr = new SpannableString(tagPart);
                            tagStr.setSpan(new ForegroundColorSpan(Color.BLUE), 0, tag.length(), 0);
                            filteredBuilder.append(tagStr);
                            
                            // Create message span
                            SpannableString msgStr = new SpannableString(messagePart);
                            if (messagePart.toLowerCase().contains("error")) {
                                msgStr.setSpan(new ForegroundColorSpan(Color.RED), 0, messagePart.length(), 0);
                            } else if (messagePart.toLowerCase().contains("success")) {
                                msgStr.setSpan(new ForegroundColorSpan(Color.GREEN), 0, messagePart.length(), 0);
                            }
                            filteredBuilder.append(msgStr);
                            filteredBuilder.append("\n");
                            break;
                        }
                    }
                }
                
                // Set the filtered text
                logTextView.setText(filteredBuilder);
                
                // Scroll to bottom if needed
                if (isScrolledToBottom()) {
                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        }
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

    private static boolean isScrolledToBottom() {
        if (scrollView == null) return false;
        int scrollY = scrollView.getScrollY();
        int height = scrollView.getHeight();
        int viewHeight = logTextView.getHeight();
        return (scrollY + height) >= viewHeight;
    }

    public static void appendLog(String tag, String message) {
        // Log to Android system log
        android.util.Log.d(tag, message);

        // Create formatted log entry
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // Add tag
        SpannableString tagStr = new SpannableString(tag + ": ");
        tagStr.setSpan(new ForegroundColorSpan(Color.BLUE), 0, tag.length(), 0);
        builder.append(tagStr);

        // Add message
        SpannableString msgStr = new SpannableString(message);
        if (message.toLowerCase().contains("error")) {
            msgStr.setSpan(new ForegroundColorSpan(Color.RED), 0, message.length(), 0);
        } else if (message.toLowerCase().contains("success")) {
            msgStr.setSpan(new ForegroundColorSpan(Color.GREEN), 0, message.length(), 0);
        }
        builder.append(msgStr);
        builder.append("\n");

        // Add to buffer
        logBuffer.append(builder);

        // Update UI if tag is enabled
        if (enabledTags.contains(tag)) {
            refreshLogDisplay();
        }
    }

    public static void clearLog() {
        if (logTextView != null) {
            logTextView.setText("");
            logBuffer.clear();
        }
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