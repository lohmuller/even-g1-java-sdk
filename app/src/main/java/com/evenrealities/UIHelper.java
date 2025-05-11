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
import android.util.Log;
import android.text.style.StyleSpan;
import java.util.HashSet;
import java.util.Set;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;

public class UIHelper {
    private static final String TAG = "UIHelper";
    private static final int MAX_RETRY_ATTEMPTS = 10;  // Maximum number of retry attempts
    private static final long RETRY_DELAY_MS = 500;  // Delay between retries in milliseconds

    // Core UI elements
    public static LinearLayout root;              // Root layout containing all UI elements
    public static TextView logTextView;           // Text view for displaying log messages
    public static ScrollView scrollView;          // Scroll view for log messages
    public static Button retryButton;             // Button for retrying operations
    
    // Tag management
    private static Set<String> knownTags = new HashSet<>();    // Set of all tags that have appeared in logs
    private static Set<String> activeTags = new HashSet<>();   // Set of tags that are currently enabled
    private static Set<String> pendingTags = new HashSet<>();  // Tags waiting for UI to be ready
    private static SpannableStringBuilder logBuffer = new SpannableStringBuilder();  // Buffer for all log messages
    private static Handler retryHandler = new Handler(Looper.getMainLooper());  // Handler for delayed retries

    // Bluetooth status displays
    public static TextView bluetoothLeftStatus, bluetoothRightStatus;  // Status displays for left and right devices

    // Filter button management
    private static HorizontalScrollView filterScrollView;  // Scrollable container for filter buttons
    private static LinearLayout filterLayout;     // Layout containing filter buttons
    private static Activity mainActivity;         // Reference to main activity for UI operations

    /**
     * Sets up the main UI layout and initializes all UI components.
     * After setup, processes any pending tags that arrived before UI was ready.
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
        scrollView.setVerticalScrollBarEnabled(true);
        
        logTextView = new TextView(activity);
        logTextView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        logTextView.setTextSize(12);
        logTextView.setTypeface(Typeface.MONOSPACE);  // Use monospace font for better readability
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
        logTitle.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(4));
        root.addView(logTitle);
        
        // Add scrollable container for filter buttons
        filterScrollView = new HorizontalScrollView(activity);
        filterScrollView.setHorizontalScrollBarEnabled(true);
        filterScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        
        // Add filter buttons layout
        filterLayout = new LinearLayout(activity);
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(4));
        filterLayout.setVisibility(View.VISIBLE);
        
        // Set layout parameters for filter layout
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        filterLayout.setLayoutParams(filterParams);
        
        // Add filter layout to scroll view
        filterScrollView.addView(filterLayout);
        root.addView(filterScrollView);

        // Add the scroll view with proper layout params to take remaining space
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, // Height will be determined by weight
            1f // Take all remaining space
        );
        params.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(8));
        root.addView(scrollView, params);

        // Process any pending tags that arrived before UI was ready
        processPendingTags();
    }

    /**
     * Converts dp to pixels for consistent sizing across devices
     */
    private static int dpToPx(int dp) {
        return (int) (dp * mainActivity.getResources().getDisplayMetrics().density);
    }

    /**
     * Processes any tags that arrived before the UI was ready.
     * Creates buttons for all pending tags.
     */
    private static void processPendingTags() {
        if (mainActivity == null || filterLayout == null) {
            Log.e(TAG, "Cannot process pending tags: UI not ready");
            return;
        }

        mainActivity.runOnUiThread(() -> {
            for (String tag : pendingTags) {
                addFilterButton(tag);
            }
            pendingTags.clear();
        });
    }

    /**
     * Attempts to create a button for a tag, with retry mechanism if UI is not ready.
     * 
     * @param tag The tag to create a button for
     * @param attempt Current retry attempt number
     */
    private static void tryCreateButton(String tag, int attempt) {
        if (mainActivity == null || filterLayout == null) {
            if (attempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "UI not ready for tag: " + tag + ", attempt " + attempt + " of " + MAX_RETRY_ATTEMPTS);
                retryHandler.postDelayed(() -> tryCreateButton(tag, attempt + 1), RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Failed to create button for tag: " + tag + " after " + MAX_RETRY_ATTEMPTS + " attempts");
                pendingTags.add(tag);
            }
            return;
        }

        mainActivity.runOnUiThread(() -> addFilterButton(tag));
    }

    /**
     * Creates and adds a new filter button for a tag.
     * The button allows users to toggle visibility of messages with that tag.
     * 
     * @param tag The tag to create a button for
     */
    private static void addFilterButton(String tag) {
        if (mainActivity == null || filterLayout == null) {
            Log.e(TAG, "Cannot add button: mainActivity or filterLayout is null");
            return;
        }
        
        // Create and add button on UI thread
        mainActivity.runOnUiThread(() -> {
            try {
                // Add to known tags and check if it was already ther
                
                Log.d(TAG, "Starting button creation for tag: " + tag);
                
                // Create the button with rounded corners
                Button filterBtn = new Button(mainActivity);
                filterBtn.setText(tag);
                filterBtn.setTextSize(10);
                filterBtn.setPadding(dpToPx(8), dpToPx(1), dpToPx(8), dpToPx(1));
                filterBtn.setVisibility(View.VISIBLE);
                
                // Set button style with reduced height and rounded corners
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                int buttonHeight = dpToPx(24);
                btnParams.height = buttonHeight;
                btnParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
                filterBtn.setLayoutParams(btnParams);
                
                // Set rounded corners
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setCornerRadius(dpToPx(4));
                shape.setColor(Color.parseColor("#E0E0E0"));
                filterBtn.setBackground(shape);
                
                filterBtn.setOnClickListener(v -> toggleTag(tag, filterBtn));
                
                // Add to active tags
                activeTags.add(tag);
                updateButtonStyle(filterBtn, true);
                
                // Add button to layout
                filterLayout.addView(filterBtn);
                
                // Force layout updates
                filterLayout.invalidate();
                filterLayout.requestLayout();
                if (root != null) {
                    root.invalidate();
                    root.requestLayout();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating button for tag: " + tag, e);
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
        GradientDrawable shape = (GradientDrawable) button.getBackground();
        if (enabled) {
            shape.setColor(Color.parseColor("#E0E0E0")); // Light gray when enabled
            button.setTextColor(Color.parseColor("#212121")); // Dark gray text
            button.setAlpha(1.0f); // Fully opaque
        } else {
            shape.setColor(Color.parseColor("#F5F5F5")); // Very light gray when disabled
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
                
                // Scroll only if we were at the bottom before
                if (isScrolledToBottom()) {
                    scrollView.postDelayed(() -> {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }, 100);
                }
            });
        }
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
        if (mainActivity == null || filterLayout == null) {
            throw new IllegalStateException("UIHelper not properly initialized");
        }
        
        boolean isNewTag = knownTags.add(tag);
        activeTags.add(tag);

        if (isNewTag) {
            if (mainActivity != null && filterLayout != null) {
                mainActivity.runOnUiThread(() -> addFilterButton(tag));
                appendLog("UIHelper", "Added new tag: " + tag);
            } else {
                Log.d(TAG, "UI not ready for tag: " + tag + ", will retry");
                tryCreateButton(tag, 0);
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
        builder.append(msgStr).append("\n");

        // Add to buffer
        logBuffer.append(builder);

        // Refresh the log display
        if (logTextView != null && scrollView != null) {
            refreshLogDisplay();
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
        
        // Set button style with rounded corners
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dpToPx(4));
        shape.setColor(Color.parseColor("#E0E0E0"));
        btn.setBackground(shape);
        
        // Set layout parameters
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
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
}