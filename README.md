# Even G1 Java SDK

This is an **unofficial Java SDK** for interacting with Even Realities smart glasses (G1 series).

## About

This SDK is based on:
- The code of the **AugmentOS**
- Analysis of the **Even Realities DemoApp**
- A reverse engineering document shared by the community on the **Even Realities Discord**

The goal is to provide developers with a high-level, open-source API to communicate with Even Realities smart glasses, enabling features like brightness control, silent mode, image/text transfer, system info queries, and gesture handling.

## Features
- Support for Even Os firmware 1.5.0
- BLE (Bluetooth Low Energy) communication with G1 smart glasses
- Command queueing and response parsing
- High-level API for common device functions
- Event listener support for gestures and device events

## Getting Started

1. Clone this repository
2. Add the SDK to your Java/Android project
3. See the example usage in the source code

## Contributing
Contributions, bug reports, and suggestions are welcome! Please open an issue or pull request.


## Protocol (Commands)

## Table A: (APP → Glasses)



| ID  | Command (HEX) | Name              | Parameters                                  | Expected Response (ID) | Parameter Examples                        |
|-----|---------------|-------------------|---------------------------------------------|-------------------------|-------------------------------------------|
| A01 | `0x6E 0x74`    | Firmware Request  | None                                        | G01                     | N/A                                       |
| A02 | `0x4D 0xFB`    | Initialize        | None                                        | None                    | N/A                                       |
| A03 | `0x2C <type>`  | Battery Query     | type: Android(0x01), iOS(0x02)              | G02                     | `01`, `02`                                |
| A04 | `0x25 <seq><00><04><seq>` | Heartbeat    | seq: 0-255                                | G03                     | `01 00 04 01`, `FF 00 04 FF`               |
| A05 | `0x4E <header><text>` | Text Display    | header: seq(1), chunks(1), index(1), status(1), pos(2), page(2) | G04                     | `01 01 00 71 00 00 00 01 "Text"`           |
| A06 | `0x15 <header><data>` | Bitmap Transfer  | header: First(addr[4]), Other(none)         | G05                     | `00 1C 00 00 [data]`                      |
| A07 | `0x16 <crc32>`  | Bitmap CRC        | crc32: 4 bytes                              | G06                     | `12 34 56 78`                             |
| A08 | `0x0E <state>`  | Mic Enable        | state: enable(0x01), disable(0x00)           | G07                     | `01`, `00`                                |
| A09 | `0x18`          | Clear Screen / Exit      | None                                        | None                    | N/A                                       |
| A10 | `0x01 <level><auto>` | Brightness     | level: 0-63, auto: 0/1                      | None                    | `3F 01` (max, auto), `20 00` (mid, manual) |
| A11 | `0x0B <angle><level>` | Set Head Angle    | angle: 0-60, level: 1                       | None                    | `1E 01` (30°), `3C 01` (60°)               |
| A12 | `0x03 0x0A`     | Silent Mode       | None                                        | None                    | N/A                                       |
| A13 | `0x27 <state>`  | Wear Detection    | state: enable(0x01), disable(0x00)           | None                    | `01`, `00`                                |
| A14 | `0x23 0x72`     | Quick Restart     | None                                        | None                    | N/A                                       |
| A15 | `0x04 <header><json>` | Whitelist     | header: total(1), index(1)                  | None                    | `01 00 {"app":{"list":[...]}}`             |
| A16 | `0x4B <header><json>` | Notifications | header: total(1), index(1)                  | None                    | `01 00 {"ncs_notification":{...}}`         |

---

## Table G: (Glasses → APP)

| ID  | Command (HEX)        | Name                      | Data Format                      | Response To (ID) |  Data Examples                      |
|-----|----------------------|----------------------------|-----------------------------------|------------------|------------------------------------|
| G01 | [undocumented]        | Firmware Info              | unknown                           | A01              |  [unknown]                         |
| G02 | `0x2C 0x66 <level>`   | Battery Response           | level: 100% `64`, 50% `32`, 10% `0A`  | A03              |  `0x2C 0x66 64`                   |
| G03 | `0x25`                | Heartbeat Response         | none                              | A04              |  N/A                                |
| G04 | `0x4E <status>` | Text Display Response    | status: success(0xC9) / fail(0x00) | A05              |  `4E C9`, `4E 00`                   |
| G05 | `0x15 <status>` | Bitmap Transfer Response | status: success(0xC9) / fail(0x00) | A06              |  `15 C9`, `15 00`                   |
| G06 | `0x16 <status>` | Bitmap CRC Response       | status: success(0xC9) / fail(0x00) | A07              |  `16 C9`, `16 00`                   |
| G07 | `0xF1 <seq><data>`    | Audio Stream               | seq: 0-255, data: LC3(200)         | A08              |  `01 [LC3 data]`                    |
| G08 | `0xF5 <pos>`          | State Change Event        | pos: `0x02` UP, `0x03` DOWN         | (event)          |  `0xF5 0x02`              |

---

## Notes:
1. Command format uses `<>` to denote parameter fields.
2. Parameter sizes are indicated in parentheses.
3. JSON data must be chunked into 176-byte pieces.
4. Bitmap data must be in 1-bit BMP format.
5. LC3 audio packets are 200 bytes.
6. All values shown are hexadecimal (hex).
7. Initialization sequence: `A01 → A02 → A13 → A12` (when required).