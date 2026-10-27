# Add-ons

Small separate TV apps, copied from the cleanyourtv repo's `addons/` (MIT, see `LICENSE-cleanyourtv.txt`;
each add-on's own README lists its author and credits). They are ordinary apps: MZ TX6 Home shows each as a
normal tile and works without any of them; each is built and installed on its own. Waktu Solat also feeds the
launcher's header and Customize panel through its provider, when it is installed.

| Add-on | Package | What it does | Permissions |
|---|---|---|---|
| [capacity-stats](capacity-stats/) | `com.lionlev.tv.capacity` | Free/total RAM and storage | None |
| [waktu-solat](waktu-solat/) | `com.mazem3dia.waktusolat` | JAKIM's eight times for a zone, in Malay, next prayer counted down, Hijri date; feeds the launcher's header and Customize | `INTERNET`, coarse location (Auto zone only) |

## Changes from the originals

- Build tools aligned with the launcher, so they build offline with what is already on this PC: AGP 8.7.3,
  Gradle 8.11.1 (wrapper copied from the launcher; capacity-stats shipped without one), compileSdk 35.
  minSdk 26 and targetSdk 34 are unchanged.
- waktu-solat: rewritten from @majnioui's Prayer Times for Malaysia. Data from JAKIM e-Solat instead of Aladhan,
  Malay names and dates, Inter Display font, a provider for the launcher, new banner. See its README.

## Build and install

From an add-on's folder, with the launcher's JDK:

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

In Android Studio, open the add-on's folder as its own project (Gradle JDK: jbr-21).
