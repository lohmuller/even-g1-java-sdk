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

    EvenOsCommand<Boolean> setBrightness(int level, boolean auto);
    EvenOsCommand<Boolean> setSilentMode(boolean silent);
    EvenOsCommand<Boolean> setMicrophoneEnabled(boolean enabled);
    EvenOsCommand<Boolean> heartbeat(int seq);
    EvenOsCommand<Boolean> exitApp();
    EvenOsCommand<Boolean> initialize();
    EvenOsCommand<String> getFirmwareInfo();
    EvenOsCommand<Boolean> setWearDetection(boolean enabled);
    EvenOsCommand<?> getBatteryInfo(Sides side);
    EvenOsCommand<?> getDeviceUptime();
    EvenOsCommand<?> getUsageInfo();
    EvenOsCommand<Boolean> setQuickNote(String note);
    EvenOsCommand<Boolean> setHeadUpAngle(int angle);
    EvenOsCommand<Boolean> setNotificationConfig(String jsonData);
    EvenOsCommand<Boolean> setDashboardMode(DashboardMode mode, DashboardSubMode subMode);
    EvenOsCommand<Boolean> sendText(String text);
    EvenOsCommand<Boolean> sendBmp(byte[] bmpData);
    EvenOsCommand<Boolean> endTransferBmp();
    EvenOsCommand<Boolean> crcCheck(byte[] bmpData);
    EvenOsEventListener<Boolean> onDoubleTap();
    EvenOsEventListener<Boolean> onSingleTap();
    EvenOsEventListener<Boolean> onTripleTap();
    EvenOsEventListener<Boolean> onLongPressHeld();
    EvenOsEventListener<Boolean> onLongPressRelease();
    EvenOsEventListener<Boolean> onBlePairedSuccess();
    EvenOsEventListener<Integer> onCaseBattery();
}