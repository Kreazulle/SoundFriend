# SoundFriend - Technical Documentation & System Architecture

## 1. Executive Summary
**SoundFriend** is a specialized Wear OS application designed for live sound reinforcement environments. It bridges the gap between physical mixing consoles (Behringer WING, Midas M32, Behringer X32) and the user via a wearable interface. Its primary goals are real-time tempo synchronization (Tap Tempo) and a low-latency haptic alert system.

---

## 2. System Architecture
The application follows a **Modular Clean Architecture** with a focus on **Kotlin Multiplatform (KMP)** readiness.

### 2.1 Core Logic (`com.soundffriend.core`)
*   **WingProtocol**: Pure Kotlin implementation of the OSC (Open Sound Control) binary protocol. Handles byte-level manipulation, float-to-byte conversions, and OSC string padding.
*   **MixerHandlers**: Implements the *Strategy Pattern* to handle console-specific differences:
    *   **WingHandler**: 16 FX slots, direct millisecond mapping.
    *   **X32/M32Handlers**: 4/8 FX slots, normalized (0.0 - 1.0) parameter mapping.
*   **MixerHandlerFactory**: Dynamically selects the correct communication strategy based on the detected hardware via the `/xinfo` protocol.

### 2.2 UI Layer (Wear OS)
*   Built entirely with **Jetpack Compose for Wear OS**.
*   Uses a **MVVM (ViewModel)** pattern to maintain state during network discovery and synchronization.
*   Implements a custom **Canvas-based landing animation** and a **BPM-synced pulse animation** using the `withFrameMillis` clock.

---

## 3. Communication Protocols

### 3.1 Console Discovery
*   **Protocol**: UDP Broadcast & TCP Scan.
*   **Ports**: `2222` (Native), `2223` (OSC), `10023` (OSC).
*   **Mechanism**: Sends `WING?` and `/xinfo` packets. Parses binary responses to extract Brand, Model, and Console Name.

### 3.2 Tempo Synchronization
*   **BPM to OSC**: Converts Tap intervals into frequency (BPM) and period (ms).
*   **Parameter Mapping**: 
    *   **WING**: Sends to `/fx/[id]/[1-4]` (Value: ms).
    *   **X32/M32**: Sends to `/fx/[id]/par/[01-02]` (Value: ms / 3000).

### 3.3 Notification System (The "Backchannel")
*   **UDP Listener (Port 5005)**: Listens for raw string payloads.
*   **OSC Listener (Port 5006)**: Listens for `/SoundFriend/alerts` (String) and `/SoundFriend/messages` (String).

---

## 4. Professional Use Cases

### 4.1 FOH & Monitor Engineering
*   **Tempo Master**: The engineer sets the tempo for the entire band from their wrist while walking the stage or venue, ensuring time-based effects (Delays) are always musical.
*   **Stage Communication**: A stage technician can trigger a "HELP" alert via a simple UDP command from a tablet, vibrating the engineer's watch during a loud show.

### 4.2 System Engineering (Integration with Meyer Sound RMS)
**Scenario**: Real-time health monitoring of a large-scale PA system.

Meyer Sound systems use **RMS (Remote Monitoring System)** via **Compass** software to monitor speaker health (temperature, voltage, limiting).

*   **Implementation**: A middleware script (Python or Node.js) monitors the Meyer Sound Compass API or log files.
*   **Trigger**: If a subwoofer reaches a thermal threshold (e.g., 65°C) or an amplifier enters a "Protect" state.
*   **Redirection**: The script sends a UDP packet to the System Engineer's watch:
    ```bash
    echo "SUB L1: OVERHEAT 68C" | nc -u -w1 [WATCH_IP] 5005
    ```
*   **Result**: The System Engineer receives a **haptic pulse** and a **high-visibility alert** on their watch instantly, allowing them to react (e.g., pull back the master fader) without needing to be in front of a laptop.

---

## 5. Technical Specifications Table

| Feature | Detail |
| :--- | :--- |
| **Minimum Android SDK** | 26 (Android 8.0) |
| **Networking** | UDP Multicast / Unicast |
| **OSC Ports** | 2223, 10023, 5006 |
| **Alert Port** | 5005 (Raw UDP) |
| **BPM Range** | 20 - 350 BPM |
| **Vibration Pattern** | 60 BPM Rhythmic Pulse (Alert Mode) |

---

## 6. Future Portability
The `core` package is 100% compatible with **SwiftUI (watchOS)** via Kotlin Multiplatform. The OSC binary generator and Mixer Handlers can be linked as a framework in Xcode, allowing for an identical feature set on Apple Watch.

---
**© 2024 MULTIGRAMM Technology**
*Lead Architect: Daniel Meșteru*
