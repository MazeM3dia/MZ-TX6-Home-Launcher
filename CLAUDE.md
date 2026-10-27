# TX6 launcher

A new, lightweight Android home-screen launcher for a Tanix TX6 box, written from scratch.
The cleanyourtv copy in ../cleanyourtv-main is REFERENCE ONLY: read it, borrow ideas, don't edit it.

## Target device
- Tanix TX6, Allwinner H6, Mali-T720 (GLES 2.0), Android 9 (API 28), ~2 GB RAM (~688 MB free), 32-bit userspace
- 1920x1080 at 213 dpi (1442x811 dp); D-pad remote, no touch
- Firmware: p281-userdebug test-keys; reports manufacturer "Amlogic" (wrong)
- Inputs: HDMI out, AV out, Wi-Fi, Ethernet, USB. No tuner or HDMI in, so no Inputs/Live TV feature
- Stock launcher: AliceUX (package to be confirmed)

## Code
- Package `com.mazem3dia.mztx6home`. Plain Java, Android framework only: no AndroidX, no app dependencies
  (JUnit is test-only)
- `MainActivity`: Home screen (clock, date, network label, Customize and Settings buttons, Favourites row,
  Apps grid), tile menu (Move, favourite, Hide, App info), Customize menu (Theme, Hidden apps, Reset order)
- `AppLoader`: finds LAUNCHER and LEANBACK_LAUNCHER apps; `AppEntry`: one tile
- `Arrangement`: order and move logic on package names, unit-tested in `app/src/test`
- `HomePrefs`: owner's choices in SharedPreferences; `Palette`: 5 colour themes drawn in code
- `NetworkStatus`: Wi-Fi/Ethernet/Offline label, listens only between onStart and onStop
- `ClockDream`: screensaver (Settings > Screen saver > MZ TX6 Clock)
- Tile width comes from the screen width and `res/values/integers.xml` columns, so any density fills the row
- Tile menu opens on OK *release* after a hold (first key repeat), or on Menu: a dialog shown while a key is
  still down never gets key focus on Android 9
- Permissions: ACCESS_NETWORK_STATE only (read-only network state). Never INTERNET

## Testing
- Unit tests: `./gradlew testReleaseUnitTest`
- Emulator: `old_android_tv` (API 28 TV), `adb shell wm density 213` to match the TX6; reset afterwards
  with `adb shell wm density reset`. Hold OK: `adb shell input keyevent --longpress DPAD_CENTER`
- Screensaver: `settings put secure screensaver_components com.mazem3dia.mztx6home/.ClockDream`, then
  `am start -n com.android.systemui/.Somnambulator`; put the old value back afterwards

## Build rules
- minSdk 26, targetSdk 34, compileSdk 35 (installed)
- Gradle 8.11.1 + AGP 8.7.3 on JDK 21 (`~/.jdks/jbr-21.0.11`). Android Studio's bundled JDK 25 is too new
  for Gradle 8.11: in Android Studio set Settings > Build Tools > Gradle > Gradle JDK to jbr-21
- Command line, from this folder:
  `JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon testReleaseUnitTest assembleRelease lintRelease`
- Lint must stay at zero errors. No INTERNET permission, no background services, no blur/video, minimal deps

## Device rules
- Ask before running any command that changes the device
- Disable, never uninstall; always keep a working Home app
- Never disable AliceUX until the new launcher is confirmed as Home

## Useful references
- ../cleanyourtv-main/launcher/app/src/main/AndroidManifest.xml, MainActivity.java, GridNavigation.java
- ../cleanyourtv-main/examples/alwtniet-hy320-mini/, ../cleanyourtv-main/examples/portronics-beem-470/
- ../cleanyourtv-main/docs/TROUBLESHOOTING.md
