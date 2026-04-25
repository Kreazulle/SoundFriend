# Sound Friend 🎧⌚

**Sound Friend** is a professional Wear OS companion application designed for musicians and sound engineers. It provides seamless integration with **Behringer WING** mixing consoles, allowing real-time tempo synchronization and instant remote communication directly from your wrist.

## 🚀 Key Features

*   **Bidirectional BPM Sync**: 
    *   Tap the watch screen to set the mixer's master clock tempo.
    *   Receive instant updates on the watch when the tempo is changed physically on the console.
    *   Visual pulse animation perfectly mapped to the current BPM.
*   **Remote Alert System**:
    *   Receive urgent text notifications from the FOH or stage team.
    *   Supports raw **UDP (Port 5005)** and **OSC (Port 5006)** protocols.
    *   Alerts trigger a persistent **60 BPM rhythmic vibration** until manually acknowledged.
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
| **OSC (WING)** | `10023` | Main communication port for tempo and discovery. |
| **UDP Alerts** | `5005` | Receives raw text messages for instant display. |
| **OSC Alerts** | `5006` | Receives OSC formatted messages (`/alert` path). |

### Alert Trigger Examples

**Via UDP (Terminal):**
```bash
echo "Solo Section" > /dev/udp/[WATCH_IP]/5005
```

**Via OSC:**
*   **Port**: `5006`
*   **Address**: `/alert`
*   **Value (String)**: `"Backing Track Stop"`

## 📖 How to Use

1.  **Discovery**: Ensure your watch and mixer are on the same Wi-Fi network. Tap **Scan** in the setup menu.
2.  **Setup**: Select your mixer from the list. The app will automatically switch to the main BPM screen.
3.  **Interaction**:
    *   **Tap**: Set a new BPM.
    *   **Long Press**: Return to the settings/setup menu.
    *   **OK Button**: Stop alert vibration and dismiss messages.

## 📜 Credits

*   **Copyright**: © MULTIGRAMM Technology
*   **Developer**: Daniel Meșteru

---
*Developed with ❤️ for the live sound community.*
