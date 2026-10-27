# Add-ons

Small separate TV apps, copied from the cleanyourtv repo's `addons/` (MIT, see `LICENSE-cleanyourtv.txt`;
each add-on's own README lists its author and credits). They are ordinary apps: MZ TX6 Home shows each as a
normal tile, nothing in the launcher depends on them, and each is built and installed on its own.

| Add-on | Package | What it does | Permissions |
|---|---|---|---|
| [capacity-stats](capacity-stats/) | `com.lionlev.tv.capacity` | Free/total RAM and storage | None |
| [prayer-times](prayer-times/) | `com.majnioui.tv.prayer` | Today's prayer times for one city from Aladhan, next prayer with a countdown, Hijri date | `INTERNET` |

## Changes from the originals

- Build tools aligned with the launcher, so they build offline with what is already on this PC: AGP 8.7.3,
  Gradle 8.11.1 (wrapper copied from the launcher; capacity-stats shipped without one), compileSdk 35.
  minSdk 26 and targetSdk 34 are unchanged.
- prayer-times: the city and calculation method in `app/src/main/res/values/config.xml`.

## Build and install

From an add-on's folder, with the launcher's JDK:

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

In Android Studio, open the add-on's folder as its own project (Gradle JDK: jbr-21).
