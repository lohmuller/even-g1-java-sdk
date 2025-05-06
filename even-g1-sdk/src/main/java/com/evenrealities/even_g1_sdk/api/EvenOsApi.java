/**
 * EvenOsBase defines the abstract API surface for interacting with Even Realities smart glasses firmware.
 * It includes command definitions like brightness control, image/text transfer, system info queries, and gesture handlers.
 * Subclasses like Even_Os_1_5_0 should implement these methods for specific firmware versions.
 */

package com.evenrealities.even_g1_sdk.api;

import com.evenrealities.even_g1_sdk.api.EvenOsCommand;
import com.evenrealities.even_g1_sdk.api.EvenOsEventListener;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import android.graphics.Bitmap;

public interface EvenOsApi {
    public enum Sides {
        LEFT, RIGHT, BOTH, EITHER;
    }

    public enum DashboardMode {
        FULL(0),
        DUAL(1),
        MINIMAL(2);
        private final int value;
        DashboardMode(int value) {this.value = value;}
        public int getValue() {return value;}
    }

    public enum DashboardSubMode {
        NOTES(0),
        STOCK(1),
        NEWS(2),
        CALENDAR(3),
        NAVIGATION(4),
        EMPTY1(5),
        EMPTY2(6);
        private final int value;
        DashboardSubMode(int value) {this.value = value;}
        public int getValue() {return value;}
    }

    /**
     * Sets the brightness level of the glasses.
     * @param level Brightness level (0-100)
     * @param auto Whether to enable automatic brightness adjustment
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setBrightness(int level, boolean auto);

    /**
     * Enables or disables silent mode.
     * @param silent true to enable silent mode, false to disable
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setSilentMode(boolean silent);

    /**
     * Enables or disables the microphone.
     * @param enabled true to enable microphone, false to disable
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setMicrophoneEnabled(boolean enabled);

    /**
     * Sends a heartbeat command to the glasses.
     * @param seq Sequence number for the heartbeat
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> heartbeat(int seq);

    /**
     * Tells the glasses to exit the current app and return to the dashboard.
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> exitApp();

    /**
     * Initializes the glasses or session.
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> initialize();

    /**
     * Gets the firmware version string from the glasses.
     * @return Command that returns the firmware version as a String
     */
    EvenOsCommand<String> getFirmwareInfo();

    /**
     * Enables or disables wear detection.
     * @param enabled true to enable, false to disable
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setWearDetection(boolean enabled);

    /**
     * Gets the battery info for the specified side.
     * @param side Side to query (LEFT, RIGHT, BOTH)
     * @return Command that returns battery info (type depends on implementation)
     */
    EvenOsCommand<?> getBatteryInfo(Sides side);

    /**
     * Gets the device uptime.
     * @return Command that returns the uptime (type depends on implementation)
     */
    EvenOsCommand<?> getDeviceUptime();

    /**
     * Gets usage information from the glasses.
     * @return Command that returns usage info (type depends on implementation)
     */
    EvenOsCommand<?> getUsageInfo();

    /**
     * Sets a quick note on the glasses.
     * @param note The note text
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setQuickNote(String note);

    /**
     * Sets the head-up angle for the glasses.
     * @param angle Angle in degrees (0-60)
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setHeadUpAngle(int angle);

    /**
     * Sets the notification configuration using a JSON string.
     * @param jsonData JSON configuration data
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setNotificationConfig(String jsonData);

    /**
     * Sets the dashboard mode and submode.
     * @param mode Dashboard mode
     * @param subMode Dashboard submode
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> setDashboardMode(DashboardMode mode, DashboardSubMode subMode);

    /**
     * Sends a text message to the glasses.
     * @param text The text to display
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> sendText(String text);

    /**
     * Sends a BMP image to the glasses.
     * @param bmpData Byte array of BMP image data
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> sendBmp(byte[] bmpData);

    /**
     * Ends a BMP image transfer and displays the image.
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> endTransferBmp();

    /**
     * Performs a CRC check on the provided BMP data.
     * @param bmpData Byte array of BMP image data
     * @return Command that returns true if successful
     */
    EvenOsCommand<Boolean> crcCheck(byte[] bmpData);

    /**
     * Listener for double-tap gesture events.
     * @return Event listener for double-tap events
     */
    EvenOsEventListener<Boolean> onDoubleTap();

    /**
     * Listener for single-tap gesture events.
     * @return Event listener for single-tap events
     */
    EvenOsEventListener<Boolean> onSingleTap();

    /**
     * Listener for triple-tap gesture events.
     * @return Event listener for triple-tap events
     */
    EvenOsEventListener<Boolean> onTripleTap();

    /**
     * Listener for long-press held gesture events.
     * @return Event listener for long-press held events
     */
    EvenOsEventListener<Boolean> onLongPressHeld();

    /**
     * Listener for long-press release gesture events.
     * @return Event listener for long-press release events
     */
    EvenOsEventListener<Boolean> onLongPressRelease();

    /**
     * Listener for BLE pairing success events.
     * @return Event listener for BLE pairing success
     */
    EvenOsEventListener<Boolean> onBlePairedSuccess();

    /**
     * Listener for case battery events.
     * @return Event listener for case battery level
     */
    EvenOsEventListener<Integer> onCaseBattery();

    /**
     * Listener for case charging events.
     * @return Event listener for case charging
     */
    EvenOsEventListener<Boolean> onCaseCharging();
    
    /**
     * Listener for case closed events.
     * @return Event listener for case closed
     */
    EvenOsEventListener<Boolean> onCaseClosed();

    /**
     * Listener for case open events.
     * @return Event listener for case open
     */
    EvenOsEventListener<Boolean> onCaseOpen();

    /**
     * Listener for glasses battery events.
     * @return Event listener for glasses battery level
     */
    EvenOsEventListener<Integer> onGlassesBattery();
}