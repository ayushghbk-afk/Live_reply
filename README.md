# Live AI Reply 🤖💬

**Live AI Reply** is an intelligent, real-time conversational assistant for Android. It operates seamlessly alongside apps such as WhatsApp, Telegram, Instagram, Discord, and browser-based chats (including Character.AI). With your explicit permission, the app monitors incoming messages, understands multi-turn conversational context, generates human-like replies using OpenAI-compatible models (including OpenRouter), and optionally inserts or sends responses.

---

## 🌟 Core Features

- **5-Step On-Screen AI Pipeline 📸 ➔ 📝 ➔ 🗑️ ➔ 🧠 ➔ 🚀**:
  1. **Take Screenshot**: Automatically captures the active screen buffer via MediaProjection without user interruption.
  2. **Convert to Text**: Processes the frame locally using on-device Google ML Kit Text Recognition into chronologically sorted conversation blocks.
  3. **Delete Screenshot Immediately**: The bitmap in memory is recycled and deleted immediately (0 images saved to disk, 0 images sent over the network).
  4. **Feed to AI**: Feeds the parsed incoming message and recent conversation context to your configured AI model with persona styling and formatting rules.
  5. **Send Reply**: Automatically inserts the reply into the chat input field and clicks Send (in AUTO mode) or presents a floating 1-tap Send chip over the active chat.
- **Google Translate-Style Live Screen Sight (Auto-See & Live Reply) 👁✨**:
  - Continuously or on-demand "sees" chat messages right on your screen across any app (WhatsApp, Instagram, Telegram, Discord, Character.AI, Chrome).
  - Floating Live Reply Chip pops up right over your active conversation, displaying the latest incoming message and the smart AI suggestion.
  - One-tap `[🚀 Send / Insert]` button to paste the response into the chat or auto-send without switching apps.
  - Floating `[👁 Scan Now]` quick lens for instant on-demand screen reading.
- **Three Operating Modes**:
  - **SUGGEST**: Detects messages, generates replies in a floating overlay, and lets you review, edit, regenerate, copy, or reject them. Nothing is sent automatically.
  - **APPROVE**: Displays the AI response and waits for a single tap on `Send` before inserting and submitting.
  - **AUTO**: Detects incoming messages, generates the reply, and automatically inserts and sends it after a natural configurable delay.
  - **Large & Obvious STOP AI Button**: Immediate emergency override on the dashboard, floating overlay, and persistent notification.
- **Privacy & Safety by Design**:
  - **Never captures**: Passwords, PINs, CVVs, credit cards, bank account fields, or authentication codes.
  - **On-Device OCR Fallback**: Powered by Google ML Kit Text Recognition; frames are processed locally in RAM and immediately discarded.
  - **Per-Conversation Pause**: Mute specific chats while keeping others active.
- **Loop & Duplicate Prevention**:
  - Tracks cryptographic hashes of processed incoming messages and sent AI replies so the assistant never replies to its own messages or gets trapped in infinite conversational loops.
  - Debounces rapid UI changes (configurable from 200ms to 2000ms).
- **AI Model Flexibility & Fallback Chaining**:
  - Built-in support for **OpenRouter** (`https://openrouter.ai/api/v1`) and custom OpenAI-compatible endpoints.
  - Fallback model chaining: Automatically falls back to secondary models on rate limits (HTTP 429), timeouts, or server errors.
  - Model discovery & latency measurement.
- **Rich Persona & Roleplay Engine**:
  - Pre-built personalities: *Friendly*, *Casual*, *Funny*, *Flirty*, *Professional*, *Short & Snappy*, *Detailed*.
  - Full Character Roleplay creator: configure Character Name, Personality, Background Lore, Relationship, Speaking Style, and Rules.
- **Floating Overlay Control**:
  - Compact, draggable floating bubble that expands into a live assistant dashboard displaying incoming text, generated reply, editable text box, and quick-action buttons.
- **Simulation Playground (Test Mode)**:
  - Test prompts, personas, context lengths, and model latencies inside the app without needing another chat app open.

---

## 📋 Requirements

- **Android Version**: Android 8.0 (API 26) or higher. Recommended: Android 12+ (API 31–36).
- **Permissions**:
  1. `Accessibility Service`: Required to detect incoming message bubbles, find input fields, and perform text insertion.
  2. `Display Over Other Apps (SYSTEM_ALERT_WINDOW)`: Required for the floating assistant overlay.
  3. `Notifications (POST_NOTIFICATIONS)`: Android 13+ persistent service notification with Pause and Emergency Stop controls.
  4. `Screen Capture (MediaProjection)` *(Optional)*: Required only when falling back to OCR on apps that do not expose text to accessibility services.

---

## 🛠️ How to Build & Install

### Automatic APK Building with GitHub Actions CI/CD 🚀
This repository includes a preconfigured GitHub Actions workflow (`.github/workflows/build-apk.yml`) that automatically builds, tests, and packages your APK:
- **Automatic Triggers**: Runs on every `push` to `main` / `master`, every Pull Request, and on tag pushes (`v*`).
- **Manual Trigger**: Can be manually triggered from the GitHub **Actions** tab with a choice between `debug` and `release` APK builds.
- **Artifact Downloads**: After each build, the APK is uploaded to the workflow run summary under **Artifacts** (`live-ai-reply-debug-apk`).
- **Automated GitHub Releases**: Pushing a tag like `git tag v1.0.0 && git push origin v1.0.0` will automatically create a GitHub Release with the APKs attached.

### Building Locally with Gradle
```bash
# Build the Debug APK
gradle assembleDebug

# Run Unit and Robolectric Tests
gradle :app:testDebugUnitTest
```

### APK Output Location
The built APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 First-Run Setup & Configuration

1. **Launch Live AI Reply**: The 8-step setup wizard will guide you through:
   - Understanding capabilities and privacy guarantees.
   - Enabling the **Live AI Reply** Accessibility Service in Android Settings.
   - Granting the **Floating Overlay** permission.
   - Entering your **OpenRouter** or **OpenAI** API key.
   - Selecting your primary AI model (e.g., `google/gemini-2.5-flash`).
   - Selecting or crafting your active persona.
   - Choosing your initial operating mode (*Suggest*, *Approve*, or *Auto*).
   - Running a live test message simulation.

2. **Configuring OpenRouter**:
   - Obtain an API key from [openrouter.ai/keys](https://openrouter.ai/keys).
   - Open **AI Settings** in the app.
   - Set **Base URL** to `https://openrouter.ai/api/v1`.
   - Paste your key into **API Key** (stored securely in hardware-backed encrypted preferences).
   - Set **Primary Model** to `google/gemini-2.5-flash`.
   - Set **Fallback Models** to `meta-llama/llama-3.3-70b-instruct,openai/gpt-4o-mini`.
   - Tap **Test Connection** to verify.

---

## 🛡️ Operating Instructions

### Mode 1 — Suggest
- When a new message arrives in an enabled app (e.g., WhatsApp), the floating bubble glows cyan.
- Tap the bubble to see the latest incoming message and the generated response.
- Tap **Send** to insert and transmit, **Edit** to adjust wording, **Regenerate** for another take, or **Reject** to dismiss.

### Mode 2 — Approve
- Functions identically to Suggest mode with a streamlined single-tap **Send** approval flow.

### Mode 3 — Auto
- When a new message arrives, the app waits for your configured delay (e.g., 3 seconds) to ensure natural typing cadence.
- If you start typing manually, **Human Override** triggers and automatic sending is cancelled.
- If uninterrupted, the assistant inserts the reply into the chat input field and taps send.
- You can halt auto-reply at any instant via the red **STOP AI** button or the persistent notification.

---

## 🔒 Privacy & Security Architecture

- **No Remote Telemetry**: The app contains zero analytics SDKs or third-party tracking scripts.
- **Local Key Storage**: API keys are stored with AES-256 GCM encryption via `EncryptedSharedPreferences`.
- **Node-Level Password Shielding**: Accessibility events containing `isPassword == true` or sensitive keyword attributes (`password`, `pin_code`, `cvv`, `otp`) are rejected before text inspection.
- **Scrubbed Diagnostics**: The internal diagnostic logger automatically redacts API keys, Bearer tokens, and authentication codes.

---

## ⚠️ Known Limitations & Troubleshooting

- **Custom Keyboard Obstruction**: Some proprietary keyboards that run non-standard accessibility overlays may block input field detection. If this occurs, switch to Gboard or use *Suggest* mode.
- **Custom Chat UIs**: Games or chat apps that render via OpenGL/Vulkan canvas without accessibility semantics cannot be read via Accessibility. For these, enable **On-Device OCR Fallback** and define the conversation screen area.
