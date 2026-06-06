# Sound Friend 🎧⌚

**Sound Friend** is a professional Wear OS companion application designed for musicians and sound engineers. It provides seamless integration with **Behringer WING** mixing consoles, allowing real-time tempo synchronization and instant remote communication directly from your wrist.

## 🎯 Purpose & Philosophy

Traditional mixing on a physical console allows a sound engineer to keep their eyes fixed on the stage, relying on tactile feedback to adjust faders and knobs. However, the shift towards tablet-based mixing has introduced a significant challenge: the touch interface demands constant visual attention, pulling the engineer's focus away from the performers.

**Sound Friend** restores this connection by moving critical communication and tempo control to a wearable device, providing an efficient and non-intrusive way to handle stage requests and technical synchronization.

## 🚀 Key Features

*   **Remote BPM Control**: 
    *   Tap the watch screen to set the mixer's master clock tempo instantly.
    *   Visual pulse animation perfectly mapped to the current BPM.
*   **Intelligent FX Delay Control**:
    *   **Automatic Recognition**: Identifies delay effects across all 16 FX slots (`ST-DL`, `TAP-DL`, `TAPE-DL`, `BBD-DL`, `OILCAN`).
    *   **Individual Selection**: Control a specific FX slot with precision.
    *   **Global Sync Mode**: Synchronize **all** delay slots on the console simultaneously with a single tap.
    *   **Hardware-Accurate Mapping**: Custom OSC scaling for complex effects (e.g., Oilcan 0-10.0 scale, BBD 1001ms scale).
*   **Remote Alert System**:
    *   Receive urgent text notifications from the FOH or stage team.
    *   Supports raw **UDP (Port 5005)** and **OSC (Port 5006)** protocols.
    *   Urgent alerts trigger rhythmic vibrations and high-visibility overlays.
*   **Smart Connectivity**:
    *   Automatic console discovery using the `/xinfo` protocol.
    *   Standalone "No Mixer" mode for standalone use.

## 📱 Cross-Platform Architecture (KMP Ready)

The project has been refactored to support a **Cross-Platform architecture**. The "brain" of the application is isolated from the Android UI, making it ready for porting to other platforms.

*   **Shared Core Logic**: All OSC protocol formatting, BPM calculations, and hardware-specific mappings are contained in a pure Kotlin `core` package.
*   **Xcode / Apple Watch Ready**: The core logic is designed to be easily bridged into an Xcode project (SwiftUI) via Kotlin Multiplatform (KMP).
*   **Platform-Specific UI**:
    *   **Wear OS**: Fully implemented using Jetpack Compose for Wear.
    *   **watchOS**: Ready for implementation using SwiftUI, utilizing the shared Kotlin protocol logic.

## 🛠 Technical Specifications

### Network Ports
| Protocol | Port | Description |
| :--- | :--- | :--- |
| **OSC (WING)** | `10023`, `2223` | Main communication for tempo and discovery. |
| **UDP Alerts** | `5005` | Receives raw text messages for urgent display. |
| **OSC Alerts** | `5006` | Receives OSC formatted messages. |

## 📖 How to Use

### Navigation & Gestures
*   **↓ Swipe Down**: Open the **Help** screen.
*   **↑ Swipe Up**: Navigate back through menus.
*   **Tap (Main Screen)**: Set a new BPM (Tap Tempo).
*   **Long Press**: Reset current selection and return to FX list.

### Setup Flow
1.  **Mixer Selection**: Scan and find WING consoles.
2.  **FX Selection**: Choose a specific Delay slot or "Global Only".
3.  **Tap Tempo**: Control the pulse of the show.

> [!WARNING]
> **Battery Usage**: This application uses "Always On" display and constant network listeners for instant performance. This results in high battery consumption; start with a full charge.

## 📜 Credits

*   **Copyright**: © MULTIGRAMM Technology
*   **Developer**: Daniel Meșteru

---
*Developed with ❤️ for the live sound community.*
