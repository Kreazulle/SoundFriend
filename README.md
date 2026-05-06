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

1.  **Discovery**: Ensure your watch and mixer are on the same Wi-Fi network. Tap **Scan** in the setup menu.
2.  **Setup**: Select your mixer from the list. The app will automatically switch to the main BPM screen.
3.  **Interaction**:
    *   **Tap**: Set a new BPM.
    *   **Long Press**: Return to the settings/setup menu.
    *   **OK Button**: Stop alert vibration and dismiss messages.

> [!WARNING]
> **Battery Usage**: This application keeps the screen active and network listeners running at all times to ensure instant access to tap-tempo and real-time alerts without any delay or interaction barriers. While this is essential for live performances, it results in significant battery consumption. It is recommended to start with a full charge before your session.

## 📜 Credits

*   **Copyright**: © MULTIGRAMM Technology
*   **Developer**: Daniel Meșteru

---
*Developed with ❤️ for the live sound community.*
