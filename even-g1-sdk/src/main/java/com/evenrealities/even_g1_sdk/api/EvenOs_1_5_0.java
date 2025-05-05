/**
 * Implementation of EvenOsBase for Even Realities smart glasses (firmware 1.5.0).
 * 
 * This class defines supported commands with request structures, expected response headers,
 * and response parsers. It abstracts BLE communication into a high-level API (e.g. brightness, 
 * silent mode, image/text transfer).
 * 
 * Based on reverse-engineering of AugmentOS, the Even Realities DemoApp, and shared docs.
 */

package com.evenrealities.even_g1_sdk.api;

import java.util.EnumMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.BiConsumer;

import com.evenrealities.even_g1_sdk.api.EvenOsApi;
import com.evenrealities.even_g1_sdk.api.EvenOsCommand;
import com.evenrealities.even_g1_sdk.api.EvenOsEventListener;

public class EvenOs_1_5_0 implements EvenOsApi {

    /**
     * Set brightness
     * @param level (0-100)
     * @param auto (true/false)
     */
    public EvenOsCommand<Boolean> setBrightness(int level, boolean auto) {
        int fallbackLevel = 30;
        int safeLevel = (level >= 0 && level <= 100) ? level : fallbackLevel;

        // Scale the level to 0-63
        int scaledLevel = (safeLevel * 63) / 100;

        // Validate brightness rang
        byte[] requestBytes = new byte[] {
            (byte) 0x01,
            (byte) scaledLevel,
            (byte)(auto ? 1 : 0)
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); //Success or failure response
        });
    }

    /**
     * Set silent mode
     * @param silent (true/false)
     */
    public EvenOsCommand<Boolean> setSilentMode(boolean silent) {
        byte[] requestBytes = new byte[] {
            (byte) 0x03,
            (byte)(silent ? 1 : 0)
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); //Success or failure response
        });
    }   

    /** 
     * Set microphone enabled
     * @param enabled (true/false)
     */
    public EvenOsCommand<Boolean> setMicrophoneEnabled(boolean enabled) {
        byte[] requestBytes = new byte[] {
            (byte) 0x0E,            //opcode
            (byte)(enabled ? 1 : 0)
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); //Success or failure response
        });
    }


    /**
     * Heartbeat
     * @param seq (sequence number)
     * @param length (length of the heartbeat)
     */
    public EvenOsCommand<Boolean> heartbeat(int seq) {
        int length = 6;

        byte[] requestBytes = new byte[] {
            (byte) 0x25,                        // Opcode for heartbeat
            (byte) (length & 0xFF),             // Length LSB (little-endian)
            (byte) ((length >> 8) & 0xFF),      // Length MSB (normally 0)
            (byte) (seq & 0xFF),                // Sequence number (first instance)
            (byte) 0x04,                        // Fixed value, what is it?
            (byte) ((seq + 1) & 0xFF)           // Sequence number (second instance). Maybe can split in two packets?
        };
        
        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); //Success or failure response
        });
    }

    /**
     * Exit app
     * tell the glasses to exit function to dashboard
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> exitApp() {
        byte[] requestBytes = new byte[] {
            (byte) 0x18
        };
        byte[] responseHeader = { requestBytes[0] };
        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); 
        });
    }

    public EvenOsCommand<Boolean> initialize() {
        byte[] requestBytes = new byte[] {
            (byte) 0x4D,
            (byte) 0xFB // Maybe there is more options to send?
        };
        byte[] responseHeader = { requestBytes[0] };
        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9); 
        });
    }

    /**
     * Get firmware info
     * @return (EvenOsCommand)
     */
    public EvenOsCommand<String> getFirmwareInfo() {
        byte[] requestBytes = new byte[] {
            (byte) 0x23,
        };
        byte[] responseHeader = new byte[] {
            (byte) 0x6E,
            (byte) 0x65,
            (byte) 0x74,
            (byte) 0x20,
            (byte) 0x62,
            (byte) 0x75,
            (byte) 0x69,
            (byte) 0x6C,
            (byte) 0x64
        };
        return new EvenOsCommand<String>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            if (data == null || data.length < 4) return "unknown";
            return data[0] + "." + data[1] + "." + data[2] + "." + data[3];
        });
    } 

    /**
     * Set wear detection
     * @param enabled (true/false)
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> setWearDetection(boolean enabled) {
        byte[] requestBytes = new byte[] {
            (byte) 0x27,
            (byte) (enabled ? 1 : 0)
        };
        byte[] responseHeader = { requestBytes[0] };
        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }
    
    public static class BatteryInfo {
        public final int batteryLevel;
        
        public BatteryInfo(int batteryLevel) {
            this.batteryLevel = batteryLevel;
        }
    }

    /**
     * Get battery info for both arms
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<BatteryInfo> getBatteryInfo(EvenOsApi.Sides side) {
        byte[] requestBytes = new byte[] {
            (byte) 0x2C,
        };
        byte[] responseHeader = { requestBytes[0] };
        return new EvenOsCommand<BatteryInfo>(requestBytes, responseHeader, side, (byte[] data) -> {
            int batteryLevel = data[2];
            return new BatteryInfo(batteryLevel);
        });
    }

    /**
    * Get device (glasses) uptime
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> getDeviceUptime() {
        byte[] requestBytes = new byte[] {
            (byte) 0x37,
        };
        byte[] responseHeader = { requestBytes[0] };
        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    /**
     * Fetch buried point data, which is essentially user usage tracking
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> getUsageInfo() {
        byte[] requestBytes = new byte[] {
            (byte) 0x3E,
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }
    

    /**
     * Set quick note
     * @TODO: Need more information about this command!
     * @param note (String)
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> setQuickNote(String note) {
        throw new UnsupportedOperationException("Not implemented yet");
        /*
        return new byte[] {
            (byte) 0x1E,
            (byte) note.getBytes().length,
            note.getBytes(),
        };
        */
    }
    

    /**
     * Set head up angle
     * @param angle (0-60)
     */
    public EvenOsCommand<Boolean> setHeadUpAngle(int angle) {

        // Validate angle range (0 ~ 60)
        int clamped = Math.max(0, Math.min(angle, 60));
        byte[] requestBytes = new byte[] {
            (byte) 0x0B,
            (byte) clamped,
            (byte) 0x01 
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }
    
    
    /**
     * Set notification config  
     * @param jsonData (json data)
     * @return chunks (byte[][] array of chunks) Multiple sends
     */
    public EvenOsCommand<Boolean> setNotificationConfig(String jsonData) {
        
        int maxSize = 180;
        byte[] jsonBytes = jsonData.getBytes();
        int totalChunks = (int) Math.ceil(jsonBytes.length / (double) maxSize);
        byte[][] chunks = new byte[totalChunks][];

        if (totalChunks > 255) {
            throw new IllegalArgumentException("json data is too large to send");
        }
        
        for (int i = 0; i < totalChunks; i++) {
            int start = i * maxSize;
            int end = Math.min(start + maxSize, jsonBytes.length);
            int chunkSize = end - start;
            
            byte[] data = new byte[chunkSize + 2];
            data[0] = 0x04; 
            data[1] = (byte) chunkSize; //total chunks
            data[2] = (byte) i; //current chunk
            System.arraycopy(jsonBytes, start, data, 2, chunkSize); //append jsonbytes to data
        
            chunks[i] = data;
        }

        byte[] responseHeader = { 0x04 };
        
        return new EvenOsCommand<Boolean>(chunks, responseHeader, EvenOsApi.Sides.LEFT, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    /**
     * Set dashboard mode
     * @param mode DashboardMode enum value
     * @param subMode DashboardSubMode enum value
     */
    public EvenOsCommand<Boolean> setDashboardMode(DashboardMode mode, DashboardSubMode subMode) {
        if (mode == DashboardMode.MINIMAL && subMode != DashboardSubMode.NOTES) {
            throw new IllegalArgumentException("SubMode not supported for MINIMAL mode");
        }
        byte[] requestBytes = new byte[] {
            (byte) 0x06,
            (byte) 0x07,
            (byte) 0x00, //seq ?
            (byte) 0x00, //seq ?
            (byte) 0x06, // API?
            (byte) mode.getValue(),
            (byte) subMode.getValue()
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH,(byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    public EvenOsCommand<Boolean> sendText(String text) {
        final int maxPayloadPerPacket = 180; // @TODO check the correct max value

        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int totalLength = textBytes.length;
        int totalPackets = (int) Math.ceil((double) totalLength / maxPayloadPerPacket);

        byte[][] packets = new byte[totalPackets][];
        for (int i = 0; i < totalPackets; i++) {
            int start = i * maxPayloadPerPacket;
            int end = Math.min(start + maxPayloadPerPacket, totalLength);
            byte[] chunk = Arrays.copyOfRange(textBytes, start, end);

            ByteBuffer buffer = ByteBuffer.allocate(9 + chunk.length);
            buffer.put((byte) 0x4E);                          // Command ID
            buffer.put((byte) i);                             // Sequence Number
            buffer.put((byte) totalPackets);                  // Total packages
            buffer.put((byte) i);                             // Current package number
            buffer.put((byte) 0x71);                          // newscreen: 0x70 (Text) + 0x01 (New content)
            buffer.put((byte) 0);                             // new_char_pos0
            buffer.put((byte) 0);                             // new_char_pos1
            buffer.put((byte) (i + 1));                       // current page
            buffer.put((byte) totalPackets);                  // max page
            buffer.put(chunk);                                // Actual text chunk

            packets[i] = buffer.array();
        }

        byte[] responseHeader = { 0x04 };

        return new EvenOsCommand<Boolean>(packets, responseHeader, EvenOsApi.Sides.LEFT, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

 
    /**
     * Transfer bmp
     * @param bmpData (byte[] array of bytes)
     * @return (byte[][] array of chunks)
     */
    public EvenOsCommand<Boolean> sendBmp(byte[] bmpData) {
        int PACKET_SIZE = 194;
        byte[] ADDRESS_HEADER = new byte[]{0x00, 0x1C, 0x00, 0x00}; 
        int totalChunks = (int) Math.ceil((double) bmpData.length / PACKET_SIZE);

        if (totalChunks > 255) {
            throw new IllegalArgumentException("bmp data is too large to send");
        }

        byte[][] result = new byte[totalChunks][];

        for (int i = 0; i < totalChunks; i++) {
            int start = i * PACKET_SIZE;
            int end = Math.min(start + PACKET_SIZE, bmpData.length);
            byte[] chunk = Arrays.copyOfRange(bmpData, start, end);

            ByteBuffer buffer;
            if (i == 0) {
                buffer = ByteBuffer.allocate(2 + ADDRESS_HEADER.length + chunk.length); //create buffer
                buffer.put((byte) 0x15);    //opcode
                buffer.put((byte) i);       //seq
                buffer.put(ADDRESS_HEADER); //address header

            } else {
                buffer = ByteBuffer.allocate(2 + chunk.length); //create buffer
                buffer.put((byte) 0x15); //opcode
                buffer.put((byte) i);   //seq
            }

            buffer.put(chunk);
            result[i] = buffer.array();
        }

        byte[] responseHeader = { 0x15 };

        return new EvenOsCommand<Boolean>(result, responseHeader, EvenOsApi.Sides.LEFT, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    /**
     * End transfer bmp, and show the image
     * @return (byte[][] array of chunks)
     */
    public EvenOsCommand<Boolean> endTransferBmp() {
        byte[] requestBytes = new byte[] {
            (byte) 0x20, 
            (byte) 0x0D, 
            (byte) 0x0E, 
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    /**
     * CRC check
     * @param bmpData (byte[] array of bytes)
     * @return (byte[] array of bytes)
     */
    public EvenOsCommand<Boolean> crcCheck(byte[] bmpData) {
        byte[] ADDRESS_HEADER = new byte[]{0x00, 0x1C, 0x00, 0x00}; 
        // CRC
        byte[] withAddress = new byte[ADDRESS_HEADER.length + bmpData.length];
        System.arraycopy(ADDRESS_HEADER, 0, withAddress, 0, ADDRESS_HEADER.length);
        System.arraycopy(bmpData, 0, withAddress, ADDRESS_HEADER.length, bmpData.length);

        // Calculate CRC
        CRC32 crc32 = new CRC32(); //Maybe we can use CRC32-XZ instead
        crc32.update(withAddress);
        int crc = (int) crc32.getValue();

        // Calculate CRC
        byte[] requestBytes = new byte[] {
            (byte) 0x16,                   
            (byte) ((crc >> 24) & 0xFF),   //crc part 1
            (byte) ((crc >> 16) & 0xFF),   //crc part 2
            (byte) ((crc >> 8) & 0xFF),    //crc part 3
            (byte) (crc & 0xFF)            //crc part 4
        };

        byte[] responseHeader = { requestBytes[0] };

        return new EvenOsCommand<Boolean>(requestBytes, responseHeader, EvenOsApi.Sides.BOTH, (byte[] data) -> {
            return (data[0] == 0xC9);
        });
    }

    public EvenOsEventListener<Boolean> onDoubleTap() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x00;
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x00;
            }
        };
    }

    public EvenOsEventListener<Boolean> onSingleTap() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x01;
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x00;
            }
        };
    }

    public EvenOsEventListener<Boolean> onTripleTap() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x05;
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x00;
            }
        };
    }

    public EvenOsEventListener<Boolean> onLongPressHeld() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && (data[1] == 0x17 || data[1] == 0x18);
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x17 || data[1] == 0x18;
            }
        };
    }

    public EvenOsEventListener<Boolean> onLongPressRelease() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x18;
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x18;
            }
        };
    }

    public EvenOsEventListener<Boolean> onBlePairedSuccess() {
        return new EvenOsEventListener<Boolean>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x11;
            }
            @Override
            public Boolean parse(byte[] data, EvenOsApi.Sides side) {
                return data[1] == 0x11;
            }
        };
    }

    public EvenOsEventListener<Integer> onCaseBattery() {
        return new EvenOsEventListener<Integer>() {
            @Override
            public boolean matches(byte[] data, EvenOsApi.Sides side) {
                return data.length > 1 && data[0] == (byte) 0xF5 && data[1] == 0x0F;
            }
            @Override
            public Integer parse(byte[] data, EvenOsApi.Sides side) {
                int rawValue = data[2] & 0xFF; //mask the value to 0-255
                int percentage = Math.min(rawValue, 64); //No more than 100%  
                return (percentage * 100) / 64; //scale to 0-100
            }
        };
    }
    


}
