# IEM Studio — Type-C IEM Companion App

A Kotlin + Jetpack Compose companion app for a USB-C in-ear monitor with a built-in DAC.
It controls the device's hardware over **USB Host** (vendor control transfers + USB Audio
Class) and an optional **Bluetooth LE** companion link — with a built-in **demo mode**
so the whole UI can be explored without hardware.

```
Home        → connection status, volume ring, quick presets, BT companion scan
EQ Studio   → 10-band graphic EQ (-12..+12 dB, 0.5 dB steps), presets, save/manage
Audio Output→ sample rate (44.1k–192k), bit depth (16/24/32), DAC gain, LED
Settings    → demo mode, auto-connect, volume steps, BT UUIDs, about/reset
```

Theme: dark audiophile, amber/gold accents. Min SDK 26 (Android 8.0), target/compile SDK 35.

---

## 1. Build & install

**Requirements:** Android Studio Ladybug+ (or any machine with JDK 17+), an Android
phone/tablet with USB Host (virtually all modern phones) and a USB-C OTG cable.

```bash
# from the project root
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

A prebuilt `iem-studio-debug.apk` is included in the project root.

> **Right after installing, open the app:** it starts in **Demo mode** so every screen
> works with a simulated device. To use real hardware, plug in the IEM, grant USB
> access, then (optionally) turn Demo mode off in Settings.

**Permissions used**

| Permission | Why | When |
|---|---|---|
| USB Host (`android.hardware.usb.host`) | Talk to the DAC over the USB bus | System dialog on plug-in |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (Android 12+) | LE scan + GATT control link | Runtime dialog from Home |
| `ACCESS_FINE_LOCATION` (Android 8–11 only) | LE scanning on legacy Android | Runtime dialog from Home |

---

## 2. Adapting the protocol to YOUR IEM  ← do this before real hardware

The vendor protocol lives in **`app/src/main/java/com/iemstudio/app/data/transport/IemProtocol.kt`**
and is a *documented, self-consistent example*. Real IEMs ship their own command set, so
map the fields below to your device's firmware spec (usually provided by the chip vendor:
XMOS, Thesycon, C-Media, ESS, etc.).

### 2a. Device identification

1. Plug the IEM into your computer and run `lsusb` — note the `ID xxxx:yyyy` (VID:PID in hex).
2. **`app/src/main/res/xml/device_filter.xml`** — replace the placeholder VID/PID
   (decimal!). This makes Android launch the app when the IEM is plugged in.
3. `UsbIemTransport.isCandidate()` currently accepts any device exposing an *audio* or
   *vendor-specific* interface — tighten it to your VID/PID if you want.

### 2b. Command mapping (IemProtocol.kt)

All commands are **control transfers** (`UsbDeviceConnection.controlTransfer`):

| Request (wRequest) | wValue | wIndex | Meaning |
|---|---|---|---|
| `0x01` SET_VOLUME | 0–100 | 0 | Digital volume |
| `0x02` SET_MUTE | 0/1 | 0 | Mute |
| `0x03` SET_EQ_BAND | gain step* | 0–9 | One EQ band |
| `0x04` SET_GAIN | gain step* | 0 | DAC/amp gain |
| `0x05` SET_LED | 0/1 | 0 | LED on/off |
| `0x06` SET_SAMPLE_RATE | rate code** | 0 | Sample rate |
| `0x07` SET_BIT_DEPTH | 16/24/32 | 0 | Bit depth |
| `0x80` GET_STATUS (IN) | 0 | 0 | 16-byte status struct |

\* gain step = `gain/0.5 + 24` → 0 dB = 24, +12 dB = 48, −12 dB = 0.
\*\* rate code = index into `SAMPLE_RATES` (44.1k, 48k, 88.2k, 96k, 176.4k, 192k).

The status struct layout parsed by `parseStatus()` is documented in the file. Adjust
the request codes, offsets and payload layout to your firmware. If your device uses
**HID feature reports** instead, replace `sendVendor()` with writes to the HID OUT
endpoint and set the report ID.

Sample-rate changes additionally try a **USB Audio Class 2.0 SET_CUR** request
(`UsbAudioClass.setSamplingFrequency`) on the isochronous OUT endpoint before falling
back to the vendor command — useful because Android's USB audio stack may honor it.

### 2c. Bluetooth companion (BleIemTransport.kt)

The BLE link is a JSON-over-GATT control channel, configurable in-app:

| Setting | Default |
|---|---|
| Name prefix for scanning | `IEM-` |
| GATT service UUID | `0000FEE0-…` |
| GATT characteristic UUID | `0000FEE1-…` |

Commands written as JSON: `{"cmd":"set_volume","value":60}`,
`{"cmd":"set_eq_band","band":3,"gain":-2.5}`, `{"cmd":"set_sample_rate","value":96000}`, …
Notifications are parsed for `battery`, `volume`, `muted`, `gain`, `led`, `sample_rate`,
`bit_depth`, `fw` and merged into the UI state. Match the JSON keys / UUIDs to your
firmware. The app prefers **USB over Bluetooth** when both are connected.

---

## 3. Architecture

```
┌─ UI (Compose, MVVM) ─────────────────────────────────────────────┐
│ HomeScreen · EqStudioScreen · AudioOutputScreen · SettingsScreen │
│   │  ViewModels (thin adapters over the controller)              │
└───┼───────────────────────────────────────────────────────────────┘
    ▼
┌─ IemController (single source of truth) ─────────────────────────┐
│ StateFlow<DeviceUiState> · transport selection · polling · prefs │
│ USB > Bluetooth > Demo                                            │
└───┬───────────────┬───────────────┬──────────────────────────────┘
    ▼               ▼               ▼
UsbIemTransport  BleIemTransport  DemoIemTransport
(IemProtocol)    (GATT + JSON)    (simulator)
    │
SettingsRepository (Jetpack DataStore: presets, volume, EQ curve, prefs)
```

* **`IemController`** — the only place that mutates `DeviceUiState`. Picks the active
  transport (USB takes precedence, then BLE, then demo), pushes the current config on
  (re)connect, polls USB status every 3 s, debounces persistence of volume/EQ.
* **`IemTransport`** — common interface; commands throw `IOException` on failure, which
  the controller surfaces as an error banner.
* **Persistence** — DataStore stores custom EQ presets (JSON), the last-used curve and
  volume, plus all settings. The EQ curve is restored on next launch.

---

## 4. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| App doesn't launch on plug-in | `device_filter.xml` VID/PID wrong (use decimal!) |
| "No device" on Home | IEM not detected → check cable/OTG; check `adb shell dumpsys usb` |
| Volume slider snaps back | Vendor command failed → adapt `IemProtocol` request codes; check error banner |
| Sample rate won't change | Phone/DAC combo doesn't support it — Android's USB audio stack owns the stream; try a different rate or driver |
| BT scan finds nothing | Enable location permission on Android 8–11; check the name prefix in Settings |
| GATT write errors | Service/characteristic UUIDs don't match your device — update in Settings → Bluetooth companion |

---

## 5. Customization

* **App name** — `app/src/main/res/values/strings.xml` (`app_name`).
* **Icon** — `res/drawable/ic_launcher_foreground.xml` (headphone glyph, amber on dark).
* **Theme** — `ui/theme/Color.kt` (one palette for the whole app).
* **EQ bands/frequencies** — `data/model/Models.kt` (`EQ_BAND_FREQUENCIES`).
* **Volume step / gain range** — Settings screen + `Models.kt` constants.

## 6. Project structure

```
app/src/main/
├── AndroidManifest.xml              # USB attach intent-filter, BT perms
├── res/xml/device_filter.xml        # ← your VID/PID
└── java/com/iemstudio/app/
    ├── IemApp.kt                    # Application + manual DI container
    ├── IemController.kt             # state, transports, commands
    ├── MainActivity.kt              # USB receiver, BT permission launcher
    ├── data/
    │   ├── model/                   # DeviceUiState, EqPreset, constants
    │   ├── prefs/SettingsRepository.kt
    │   └── transport/               # IemProtocol, Usb/Ble/Demo transports
    └── ui/
        ├── theme/                   # dark amber Material 3 theme
        ├── navigation/IemNavHost.kt # bottom nav + NavHost
        ├── components/              # VolumeRing, EQ chart, status card…
        ├── screens/                 # the 4 screens
        └── viewmodels/              # 4 thin ViewModels
```
