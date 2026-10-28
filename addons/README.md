# Add-ons

Small separate TV apps. Waktu Solat and Capacity Stats are copied from the cleanyourtv repo's `addons/` (MIT, see
`LICENSE-cleanyourtv.txt`; each add-on's own README lists its author and credits); Device Report is new. They
are ordinary apps, each built and installed on its own, and MZ TX6 Home works without any of them. In MZ TX6
Home, Waktu Solat has a tile and feeds the header and Customize through its provider; Device Report and
Capacity Stats have no tile and open from Customize > Add-ons. Other launchers list all three as normal apps.

| Add-on | Package | What it does | Permissions |
|---|---|---|---|
| [capacity-stats](capacity-stats/) | `com.lionlev.tv.capacity` | Free/total RAM and storage | None |
| [waktu-solat](waktu-solat/) | `com.mazem3dia.waktusolat` | JAKIM's eight times for a zone, in Malay, next prayer counted down, Hijri date; feeds the launcher's header and Customize | `INTERNET`, coarse location (Auto zone only) |
| [device-report](device-report/) | `com.mazem3dia.devicereport` | Everything the box tells an app (hardware, codecs, Home apps, every app and its permissions), saved as .txt and .json | Network/Wi-Fi/Bluetooth state, storage (Android 9), optional location; logcat/dumpsys only if granted over adb |

## Changes from the originals

- Build tools aligned with the launcher, so they build offline with what is already on this PC: AGP 8.7.3,
  Gradle 8.11.1 (wrapper copied from the launcher; capacity-stats shipped without one), compileSdk 35.
  minSdk 26 and targetSdk 34 are unchanged.
- waktu-solat: rewritten from @majnioui's Prayer Times for Malaysia. Data from JAKIM e-Solat instead of Aladhan,
  Malay names and dates, Inter Display font, a provider for the launcher, Lucide card icons. See its README.
- All three: the project's own icon and TV banner (adaptive icon from 432 px art, 640x360 banner), and a label on
  the launcher activity so simple launchers don't show the package name.

## Build and install

From an add-on's folder, with the launcher's JDK:

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

In Android Studio, open the add-on's folder as its own project (Gradle JDK: jbr-21).
