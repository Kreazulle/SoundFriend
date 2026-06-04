# Sound Friend 🎧⌚

**Sound Friend** is a professional Wear OS companion application designed for musicians and sound engineers. It provides seamless integration with **Behringer WING** mixing consoles, allowing real-time tempo synchronization and instant remote communication directly from your wrist.

## 🎯 Purpose & Philosophy

Traditional mixing on a physical console allows a sound engineer to keep their eyes fixed on the stage, relying on tactile feedback to adjust faders and knobs. However, the shift towards tablet-based mixing has introduced a significant challenge: the touch interface demands constant visual attention, pulling the engineer's focus away from the performers.

In this environment, communication between the stage and the engineer becomes slow and cumbersome. **Sound Friend** was created to bridge this gap. By moving critical communication and tempo control to a wearable device, it restores the engineer's ability to stay connected with the scene, providing an efficient and non-intrusive way to handle stage requests and technical synchronization.

## 🚀 Key Features

*   **Bidirectional BPM Sync**: 
    *   Tap the watch screen to set the mixer's master clock tempo.
    *   Receive instant updates on the watch when the tempo is changed physically on the console.
    *   Visual pulse animation perfectly mapped to the current BPM.
*   **Intelligent FX Delay Control**:
    *   **Automatic Recognition**: Automatically identifies delay effects across all 16 FX slots, including `ST-DL` (Stereo Delay), `TAP-DL` (Tap Delay), `TAPE-DL` (Tape Delay), and `OILCAN` (Oil Can Delay).
    *   **Individual Selection**: Choose a specific FX slot to control its time parameters with precision.
    *   **Global Sync Mode**: Use the "Global Only" option to synchronize **all** identified delay slots on the console simultaneously with a single tap.
    *   **Hardware-Accurate Mapping**: Implements specific OSC mappings for complex effects (e.g., `TAPE-DL` mapped to 60-650ms, `OILCAN` scaled 0-10.0) and supports multi-parameter sync (L/R/Feedback).
*   **Remote Alert System**:
    *   Receive urgent text notifications from the FOH or stage team.
    *   Supports raw **UDP (Port 5005)** and **OSC (Port 5006)** protocols.
    *   **Urgent Alerts** (`/SoundFriend/alerts`): Trigger a persistent **60 BPM rhythmic vibration** and a red "HELP NEEDED" overlay until manually acknowledged.
    *   **Info Messages** (`/SoundFriend/messages`): Display long messages on a blue scrollable overlay, also with rhythmic vibration until dismissed.
*   **Smart Connectivity**:
    *   Automatic console discovery using the `/xinfo` protocol.
    *   **"No Mixer" Mode**: Use the app as a standalone communication tool (alerts and manual BPM) without a console connection.
*   **Professional Watch UI**:
    *   Native curved clock integration.
    *   Curved footer displaying connection status and Mixer IP.
    *   "Always On" display mode to prevent the watch from entering standby during a performance.
    *   Hidden settings menu accessible via a **long-press** gesture to keep the interface clean.

## 🛠 Technical Specifications

### Network Ports
| Protocol | Port | Description |
| :--- | :--- | :--- |
| **OSC (WING)** | `10023`, `2223` | Main communication ports for tempo and discovery. |
| **UDP Alerts** | `5005` | Receives raw text messages for urgent display (Type: ALERT). |
| **OSC Alerts** | `5006` | Receives OSC formatted messages. |

### OSC Notification Paths
| Path | Type | Background | Header |
| :--- | :--- | :--- | :--- |
| `/SoundFriend/alerts` | Urgent Alert | Red | "HELP NEEDED:" |
| `/SoundFriend/messages` | Info Message | Blue | (None) |

### Alert Trigger Examples

**Via UDP (Terminal):**
```bash
echo "Drums Help" > /dev/udp/[WATCH_IP]/5005
```

**Via OSC:**
*   **Port**: `5006`
*   **Path**: `/SoundFriend/alerts`
*   **Value (String)**: `"Guitar String Broken"`

*   **Path**: `/SoundFriend/messages`
*   **Value (String)**: `"New Setlist: 1. Start, 2. Middle, 3. End"`

## 📖 How to Use

### Navigation & Gestures
*   **↓ Swipe Down**: Open the **Help** screen from any menu.
*   **↑ Swipe Up**: 
    *   From **Tap Screen** -> Go back to **FX Selection**.
    *   From **FX Selection** -> Go back to **Mixer Selection**.
*   **Tap (Main Screen)**: Set a new BPM (Tap Tempo).
*   **Long Press**: Return to the **FX Selection** screen and reset current selection.

### Setup Flow
1.  **Mixer Selection**: Tap **Scan** to find WING consoles on your network. Select your mixer from the list.
2.  **FX Selection**: The app will query the mixer for compatible Delay/Tempo effects. Select an effect slot to sync or choose **"Use Global Only"**.
3.  **Tap Tempo**: Once configured, you are taken to the main interface to control the tempo.

> [!WARNING]
> **Battery Usage**: This application keeps the screen active and network listeners running at all times to ensure instant access to tap-tempo and real-time alerts without any delay or interaction barriers. While this is essential for live performances, it results in significant battery consumption. It is recommended to start with a full charge before your session.

## 📜 Credits

*   **Copyright**: © MULTIGRAMM Technology
*   **Developer**: Daniel Meșteru

---
*Developed with ❤️ for the live sound community.*
