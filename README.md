# FTQR

![FTQR](art/store/feature_graphic_1024x500.png)

FTQR is an Android app that scans QR codes and barcodes and shows what each one contains. It gives you the right action for each type: open a link, join a Wi-Fi network, save a contact, add an event to your calendar, or pay with a UPI app. It can also create QR codes.

Everything runs on the phone. FTQR has no account and no backend, and it doesn't request internet access.

## Features

### Scanning
- Live camera scanning of QR codes and common barcodes (EAN, UPC, Code 128, Data Matrix, PDF417, Aztec and others)
- Scan from a gallery image, such as a screenshot of a QR code
- Torch, pinch-to-zoom, a zoom slider and tap-to-focus
- A short vibration when a code is found
- If camera permission is refused, the app explains why it needs it, and gallery scanning still works

### What it does with each type
| Type | Shows | Actions |
|---|---|---|
| Link | Domain in large type, full URL, safety warnings | Open (asks to confirm if there are warnings), copy, share |
| UPI payment | Payee name, UPI ID, amount, note | Choose app (Android's picker lists the installed UPI apps: GPay, PhonePe, Paytm, BHIM…), copy UPI ID |
| Wi-Fi | Network name, security type, password (hidden until tapped) | Connect, copy password |
| Contact (vCard / MECARD) | Name, organisation, phones, emails, address | Save contact, call, email |
| Phone | Number | Call, message, save |
| Email | Recipient, subject, message | Write email, copy address |
| SMS | Recipient, message | Send SMS |
| Location (`geo:`) | Place and coordinates | Open in maps |
| Event (iCalendar) | Title, start and end times, location | Add to calendar |
| Product barcode | Code and format | Search the web, copy |
| Text | The text | Copy, share, search |

You can see and copy the raw scanned content for every type.

### Link safety
FTQR never opens a link by itself. It warns when a link:
- uses `http://` (not encrypted)
- goes through a link shortener (bit.ly, tinyurl.com and others), which hides the real destination
- points to an IP address instead of a domain name
- uses look-alike characters in the domain (punycode / non-ASCII), which can imitate another site
- has text before an `@` that can disguise the real domain
- uses an unusual port, or is not a web link at all

### UPI payments
- Reads plain `upi://pay` QR codes as well as EMVCo / BharatQR merchant codes, finding the UPI ID inside them.
- **Paying:** FTQR never handles money. It passes the payment details to the UPI app you choose, and you confirm and enter your PIN there.
- **Fallback:** some UPI apps refuse payments started from another app, for example for personal (non-merchant) QR codes. When that happens, use **Copy UPI ID** and pay from inside the UPI app.

### History
- Every scan is saved on the phone. Scanning the same code again within a minute doesn't add a duplicate.
- You can search, filter by type, delete a scan (with undo) or clear everything.

### Create QR
- Build a QR code for text, a link, Wi-Fi, a contact, a phone number, an email, an SMS or a UPI payment.
- The preview updates as you type.
- You can share the image, save it to the gallery (`Pictures/FTQR`) or copy the text it encodes.

## Tech stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Camera | CameraX 1.7 (`LifecycleCameraController`, `MlKitAnalyzer`) |
| Decoding | ML Kit Barcode Scanning 17.3 (bundled model, works offline) |
| QR generation | ZXing core 3.5 |
| Storage | Room 2.8 |
| Build | AGP 9.5, Kotlin 2.3, Gradle 9.9, KSP |
| SDK | minSdk 26 (Android 8.0), target and compile SDK 37 |

## Building

You need JDK 17 or later and the Android SDK with platform 37. Put your SDK path in `local.properties`:

```properties
sdk.dir=C\:\\Android\\sdk
```

Then run:

```powershell
.\gradlew assembleDebug          # debug APK    -> build\Debug\FTQR-<version>-debug.apk
.\gradlew assembleRelease        # signed APK   -> build\Release\FTQR-<version>-release.apk
.\gradlew installDebug           # build and install on a connected device
.\gradlew testDebugUnitTest      # run the unit tests
```

Each output folder keeps the 3 most recent builds and deletes older ones.

### Versioning
The version is stored in `version.properties` as `major.minor.patch`, with these limits: major 1–99, minor 1–9, patch 1–9. The `versionCode` is calculated from it as `major × 100 + minor × 10 + patch`, so 1.1.1 is 111. Bump the patch for every build you hand over.

```powershell
.\gradlew bumpPatch    # 1.1.1 -> 1.1.2 (after .9 it rolls to the next minor: 1.1.9 -> 1.2.1)
.\gradlew bumpMinor    # 1.1.5 -> 1.2.1 (after minor 9 it rolls to the next major: 1.9.4 -> 2.1.1)
.\gradlew bumpMajor    # 1.4.2 -> 2.1.1
```

### Release signing
Release builds are signed with the key in `keystore/ftqr-release.jks`. Its passwords are in `keystore.properties` in the project root. Git ignores both files, and `assembleRelease` stops with an error if `keystore.properties` is missing.

**Back up both files somewhere safe.** Every future update must be signed with the same key, or phones won't accept it as an update of FTQR.

## Project layout

```
app/src/main/java/com/hari/ftqr/
├── MainActivity.kt   Activity and navigation between screens
├── model/            ScanContent and the other shared data types
├── parse/            Turns raw scanned text into ScanContent, plus link safety checks
├── scanner/          Camera screen, viewfinder, gallery decoding, haptics, permission panel
├── result/           Result screen and the actions that open other apps
├── history/          History screen
├── generator/        Create QR screen, QR encoder, payload builders, image export
├── data/             Room database and repository
└── ui/               Theme, icons for each content type
```

Only the raw text and barcode format are saved in history. The details are worked out again from the raw text each time a scan is opened, so any parser improvement also applies to old scans.

## Tests

Unit tests for the parsers are in `app/src/test/java/com/hari/ftqr/parse/`. They cover every content type, escaping edge cases, UPI decoding, EMVCo payloads and every link-safety warning. The parsers use only plain Kotlin and Java, so the tests run on the JVM with no device needed.

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Live scanning. It's optional: gallery scanning works without it. |
| `VIBRATE` | The short vibration when a code is found |
| `WRITE_EXTERNAL_STORAGE` (Android 8–9 only) | Saving a created QR code to the gallery |

The manifest also declares `<queries>` for the `upi` scheme, so the app can check whether a UPI app is installed on the phone.

## Artwork

| Folder | Contents |
|---|---|
| `art/source/` | The original asset pages |
| `art/store/` | Play Store icon (512×512) and feature graphic (1024×500) |

In-app illustrations are in `app/src/main/res/drawable-nodpi/`, and the launcher icon is in the `mipmap-*` folders.
