# Capacity Stats

Memory and storage in use on the box, opened from MZ TX6 Home's Customize > Add-ons. Package
`com.lionlev.tv.capacity`, kept so the launcher finds it.

Started as the cleanyourtv add-on by [lionlev21](https://github.com/lionlev21) (MIT, see
../LICENSE-cleanyourtv.txt). Version 1.1 (3 Nov 2026) is redrawn in the Device Report look: Inter Display
(Light, Regular, Bold; FONT-LICENSE-OFL.txt), a light title and two cards, Memory and Storage, each with
"0.7GB / 2.0GB" in use of total, a bar and "1.3GB free · 35% used". The memory bar turns orange above 85 %,
like the line on Home. Icons are the launcher's Lucide memory-stick and hard-drive. English only.

Same figures as the launcher's line under the buttons: memory from ActivityManager (total minus available),
storage from StatFs on the data partition. Read every ten seconds on the main thread, only while the screen is
on top; no thread, no service. Back closes it.

Build from this folder: `./gradlew testReleaseUnitTest lintRelease assembleRelease` (JDK 21, see ../../CLAUDE.md).

## Permissions

None. No network, telemetry, ads or background service.
