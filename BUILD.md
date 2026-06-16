# Spares — Build Instructions

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Android Studio | Hedgehog 2023.1+ | https://developer.android.com/studio |
| JDK | 17 or 21 | Bundled with Android Studio |
| Android SDK | API 34 | Via SDK Manager in Android Studio |
| Gradle | 8.2 (auto-downloaded) | Via wrapper |

---

## Option A — Build with Android Studio (Recommended)

1. **Open the project**
   - Launch Android Studio
   - File → Open → select the `Spares/` folder
   - Wait for Gradle sync to complete (~2 min first run)

2. **Build the APK**
   - Menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - Output: `app/build/outputs/apk/debug/app-debug.apk`

3. **Sideload to device**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   Or copy the APK file to your Android device and open it (enable "Install from unknown sources" first).

---

## Option B — Build via Command Line

```bash
# 1. Navigate to project root
cd Spares/

# 2. Make wrapper executable
chmod +x gradlew

# 3. Build debug APK
./gradlew assembleDebug

# 4. Find the APK
ls app/build/outputs/apk/debug/
# → app-debug.apk
```

To build a release APK (needs a keystore):
```bash
./gradlew assembleRelease
```

---

## Setting Up for Sideloading

1. On your Android device: **Settings → Security → Install unknown apps**
   - Enable for Files / your file manager

2. Transfer `app-debug.apk` to device via USB, WhatsApp, or Google Drive

3. Tap the APK in Files to install

4. On first launch:
   - Grant **SMS permissions** when prompted (required for transaction detection)
   - Grant **battery optimization exemption** when prompted (keeps listener alive)

---

## First-Run Flow

1. App opens → **Goal Setup screen**
2. Enter goal name (e.g. "Mechanical Keyboard") and target amount (≥ ₹100)
3. Tap **Set My Goal** → Dashboard appears
4. Send yourself a test SMS matching the pattern below to verify parsing

### Test SMS Format (send to yourself)
```
Your a/c XXXX debited by Rs.247.00 on 16-Jun-26. VPA: merchant@upi
```
Expected: round-up of **₹3.00** saved (247 → 250)

```
Rs.120.00 debited from your account. Used at swiggy.
```
Expected: round-up of **₹10.00** saved (exact multiple override)

---

## Architecture Overview

```
SmsReceiver (static manifest receiver)
    ↓  fires instantly on SMS_RECEIVED
SmsParsingService (foreground service — survives Doze)
    ↓  runs regex pipeline
SmsParser.parse()
    ↓  returns ParseResult{valid, originalAmount, roundUpAmount}
SpareDatabase (Room SQLite)
    ├── goals table
    └── transactions table
        ↓  LiveData streams
MainActivity (UI)
    ├── ProgressBar + Goal card
    ├── RecyclerView ledger
    └── Transfer Button → UPI deep-link
```

---

## UPI Transfer Configuration

In `MainActivity.java`, find this line and replace with your savings UPI ID:
```java
String vpa = "savings@upi"; // ← replace with real UPI VPA
```

Example: `"yourname@okicici"`, `"savings@ybl"`, etc.

The UPI deep-link format used:
```
upi://pay?pa=<VPA>&pn=SparesGoal&am=<AMOUNT>&cu=INR&tn=Spares+Round-Up
```
This opens PhonePe / GPay / Paytm with the amount pre-filled and locked.

---

## Edge Cases Handled (per PRD §6)

| Risk | Resolution |
|------|-----------|
| Dual-network duplicate SMS | 5-second atomic dedup window on `original_amount` |
| "Transaction Failed" messages | `"failed"` added to exclusion dictionary |
| Malformed/null SMS payloads | `try/catch` safe discard — no crashes |
| Amount = exact multiple of 10 | Round-up forced to ₹10.00 (not ₹0.00) |
| OTP / spam messages | Exclusion: `otp`, `code`, `secret`, `credited`, `received` |

---

## Permissions Required

| Permission | Why |
|-----------|-----|
| `RECEIVE_SMS` | Intercept incoming bank SMS |
| `READ_SMS` | Parse SMS content |
| `FOREGROUND_SERVICE` | Keep parsing service alive |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Doze from killing listener |
| `POST_NOTIFICATIONS` | Show persistent "Spares is running" notification |
