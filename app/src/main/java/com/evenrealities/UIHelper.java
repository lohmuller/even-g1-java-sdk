/**
 * UIHelper is a utility class that manages the user interface for the Even G1 SDK Demo App.
 * It handles all UI-related operations including:
 * - Log message display and filtering
 * - Bluetooth status updates
 * - Dynamic button creation and management
 * - UI layout and styling
 */
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
    // Core UI elements
    public static LinearLayout root;              // Root layout containing all UI elements
    public static TextView logTextView;           // Text view for displaying log messages
    public static ScrollView scrollView;          // Scroll view for log messages
    public static Button retryButton;             // Button for retrying operations
    
    // Tag management
    private static Set<String> knownTags = new HashSet<>();    // Set of all tags that have appeared in logs
    private static Set<String> activeTags = new HashSet<>();   // Set of tags that are currently enabled
    private static SpannableStringBuilder logBuffer = new SpannableStringBuilder();  // Buffer for all log messages

    // Bluetooth status displays
    public static TextView bluetoothLeftStatus, bluetoothRightStatus;  // Status displays for left and right devices

    // Filter button management
    private static LinearLayout filterLayout;     // Layout containing filter buttons
    private static Activity mainActivity;         // Reference to main activity for UI operations

    /**
     * Sets up the main UI layout and initializes all UI components.
     * Creates a vertical layout with:
     * - Status panel at the top
     * - Log title and filter buttons
     * - Scrollable log display
     * 
     * @param activity The main activity instance
     */
    public static void setupUI(Activity activity) {
        mainActivity = activity;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        activity.setContentView(root);

        // Add status panel for Bluetooth status
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
        
        // Add filter buttons layout
        filterLayout = new LinearLayout(activity);
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setPadding(16, 0, 16, 8);
        
        // Filter buttons will be added dynamically as logs come in
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

    /**
     * Debug method to list all currently visible filter buttons.
     * This helps diagnose issues with button creation and visibility.
     */
    private static void debugListVisibleButtons() {
        if (filterLayout == null) {
            android.util.Log.e("UIHelper", "debugListVisibleButtons: filterLayout is null");
            return;
        }

        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("Current filter buttons:\n");
        debugInfo.append("Known tags: ").append(knownTags).append("\n");
        debugInfo.append("Active tags: ").append(activeTags).append("\n");
        debugInfo.append("Button count: ").append(filterLayout.getChildCount()).append("\n");
        
        // List all buttons
        for (int i = 0; i < filterLayout.getChildCount(); i++) {
            View child = filterLayout.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                debugInfo.append(String.format("Button[%d]: text='%s', visible=%b, enabled=%b\n",
                    i, btn.getText(), btn.getVisibility() == View.VISIBLE, btn.isEnabled()));
            }
        }
        
        android.util.Log.d("UIHelper", debugInfo.toString());
    }

    /**
     * Creates and adds a new filter button for a tag.
     * The button allows users to toggle visibility of messages with that tag.
     * 
     * @param tag The tag to create a button for
     */
    private static void addFilterButton(String tag) {
        if (mainActivity == null || filterLayout == null) {
            android.util.Log.e("UIHelper", "Cannot add button: mainActivity or filterLayout is null");
            return;
        }
        
        // Don't add if button already exists
        if (knownTags.contains(tag)) {
            android.util.Log.d("UIHelper", "Button already exists for tag: " + tag);
            return;
        }
        
        android.util.Log.d("UIHelper", "Creating button for tag: " + tag);
        
        // Create and add button on UI thread
        mainActivity.runOnUiThread(() -> {
            try {
                Button filterBtn = new Button(mainActivity);
                filterBtn.setText(tag);
                filterBtn.setTextSize(10);
                filterBtn.setPadding(8, 1, 8, 1);
                
                // Set button style with reduced height
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                btnParams.height = (int) (mainActivity.getResources().getDisplayMetrics().density * 24);
                btnParams.setMargins(4, 0, 4, 0);
                filterBtn.setLayoutParams(btnParams);
                
                filterBtn.setOnClickListener(v -> toggleTag(tag, filterBtn));
                
                // Add to known tags and active tags
                knownTags.add(tag);
                activeTags.add(tag);
                updateButtonStyle(filterBtn, true);
                
                // Add button to layout
                filterLayout.addView(filterBtn);
                android.util.Log.d("UIHelper", "Button added for tag: " + tag);
                
                // Debug info after adding
                debugListVisibleButtons();
            } catch (Exception e) {
                android.util.Log.e("UIHelper", "Error creating button for tag: " + tag, e);
            }
        });
    }

    /**
     * Toggles the visibility of messages with a specific tag.
     * Updates button style and refreshes the log display.
     * 
     * @param tag The tag to toggle
     * @param button The button associated with the tag
     */
    private static void toggleTag(String tag, Button button) {
        if (activeTags.contains(tag)) {
            activeTags.remove(tag);
            updateButtonStyle(button, false);
        } else {
            activeTags.add(tag);
            updateButtonStyle(button, true);
        }
        refreshLogDisplay();
    }

    /**
     * Updates the visual style of a filter button based on its state.
     * 
     * @param button The button to update
     * @param enabled Whether the button is enabled
     */
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

    /**
     * Refreshes the log display based on active tags.
     * Shows only messages from active tags and messages without tags.
     * Applies color formatting to messages based on content.
     */
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
                    String tag = null;
                    boolean shouldHide = false;
                    
                    // Check if line starts with any known tag
                    for (String possibleTag : knownTags) {
                        // Check for exact tag match at start of line
                        if (line.startsWith(possibleTag + ": ")) {
                            tag = possibleTag;
                            shouldHide = !activeTags.contains(tag);
                            break;
                        }
                    }
                    
                    // Always show lines that don't match any known tag
                    if (tag == null) {
                        filteredBuilder.append(line);
                        filteredBuilder.append("\n");
                        continue;
                    }
                    
                    // For lines with known tags, check if they should be shown
                    if (!shouldHide) {
                        // Extract tag and message
                        int tagEnd = line.indexOf(": ") + 2; // +2 to include the space after colon
                        String tagPart = line.substring(0, tagEnd);
                        String messagePart = line.substring(tagEnd);
                        
                        // Create tag span - always blue
                        SpannableString tagStr = new SpannableString(tagPart);
                        tagStr.setSpan(new ForegroundColorSpan(Color.BLUE), 0, tag.length(), 0);
                        filteredBuilder.append(tagStr);
                        
                        // Create message span with color based on content
                        SpannableString msgStr = new SpannableString(messagePart);
                        if (messagePart.toLowerCase().contains("error")) {
                            msgStr.setSpan(new ForegroundColorSpan(Color.RED), 0, messagePart.length(), 0);
                        } else if (messagePart.toLowerCase().contains("success")) {
                            msgStr.setSpan(new ForegroundColorSpan(Color.GREEN), 0, messagePart.length(), 0);
                        }
                        filteredBuilder.append(msgStr);
                        filteredBuilder.append("\n");
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

    /**
     * Creates a button with consistent styling.
     * 
     * @param ctx The context to create the button in
     * @param text The text to display on the button
     * @param listener The click listener for the button
     * @return The created button
     */
    public static Button createButton(Context ctx, String text, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(params);
        return btn;
    }

    /**
     * Adds a row of buttons to the root layout.
     * 
     * @param ctx The context to create the buttons in
     * @param buttons The buttons to add
     */
    public static void addButtonRow(Context ctx, Button... buttons) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button btn : buttons) {
            row.addView(btn);
        }
        root.addView(row);
    }

    /**
     * Checks if the log view is scrolled to the bottom.
     * Used to determine if we should auto-scroll on new messages.
     */
    private static boolean isScrolledToBottom() {
        if (scrollView == null) return false;
        int scrollY = scrollView.getScrollY();
        int height = scrollView.getHeight();
        int viewHeight = logTextView.getHeight();
        return (scrollY + height) >= viewHeight;
    }

    /**
     * Appends a log message with the specified tag.
     * Creates a new filter button if the tag is new.
     * Applies color formatting based on message content.
     * 
     * @param tag The tag for the log message
     * @param message The message to log
     */
    public static void appendLog(String tag, String message) {
        // Log to Android system log
        android.util.Log.d(tag, message);
        android.util.Log.d("UIHelper", "appendLog called with tag: " + tag + ", knownTags: " + knownTags);

        // Add new tag button if it's a new tag
        if (!knownTags.contains(tag)) {
            android.util.Log.d("UIHelper", "Adding new tag: " + tag);
            addFilterButton(tag);
            // Force layout update
            if (mainActivity != null && filterLayout != null) {
                mainActivity.runOnUiThread(() -> {
                    filterLayout.invalidate();
                    android.util.Log.d("UIHelper", "Layout invalidated after adding button for tag: " + tag);
                });
            }
        }

        // Create formatted log entry
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // Add tag
        SpannableString tagStr = new SpannableString(tag + ": ");
        tagStr.setSpan(new ForegroundColorSpan(Color.BLUE), 0, tag.length(), 0);
        builder.append(tagStr);

        // Add message with color based on content
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
        if (activeTags.contains(tag)) {
            android.util.Log.d("UIHelper", "Refreshing display for tag: " + tag);
            refreshLogDisplay();
        } else {
            android.util.Log.d("UIHelper", "Tag not active, not refreshing: " + tag);
        }
    }

    /**
     * Clears all log messages and resets the display.
     */
    public static void clearLog() {
        if (logTextView != null) {
            logTextView.setText("");
            logBuffer.clear();
        }
    }

    /**
     * Updates the Bluetooth status display for a specific side.
     * 
     * @param side The side to update (LEFT or RIGHT)
     * @param status The new status to display
     */
    public static void updateBluetoothStatus(EvenOsApi.Sides side, String status) {
        if (side == EvenOsApi.Sides.LEFT && bluetoothLeftStatus != null) {
            updateBluetoothStatusWithStyle(bluetoothLeftStatus, status);
        } else if (side == EvenOsApi.Sides.RIGHT && bluetoothRightStatus != null) {
            updateBluetoothStatusWithStyle(bluetoothRightStatus, status);
        }
    }

    /**
     * Updates the style of a Bluetooth status display based on the status.
     * Uses different colors and emojis to indicate different states.
     * 
     * @param statusView The TextView to update
     * @param status The new status to display
     */
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
            emoji = "🟡";
            color = 0xFFFFB300; // Yellow
        } else if (status.contains("Bonded (Initialized)")) {
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

    /**
     * Shows a button to open the app settings.
     * Used when Bluetooth permissions are needed.
     * 
     * @param activity The activity to show the button in
     */
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