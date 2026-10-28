# Device Report

One button that reads everything Android lets an ordinary app see about the box, and saves it twice in
`Download/`: a `.txt` to read and a `.json` for tools or for comparing two boxes. No root, no internet.

## What is in it

1. **Summary**: model, Android, chip, CPU, RAM, storage, screen, GPU, key features, Widevine level, Home apps
   with their priority, the current Home, Settings apps, app counts
2. **Device**: build, fingerprint, ABIs, kernel, Treble
3. **System properties**: the full `getprop` list
4. **CPU**: cores, frequencies, governors, temperatures, `/proc/cpuinfo`
5. **Memory**: totals, app heap limits, `/proc/meminfo`, zram
6. **Storage**: partitions, volumes (USB sticks, SD cards), mounts
7. **Display**: resolution, density, refresh rates and modes, HDR
8. **Graphics**: OpenGL ES and EGL versions, GPU, extensions, Vulkan
9. **Features**: Bluetooth, Wi-Fi, Ethernet, USB, HDMI-CEC and every system feature
10. **Media**: every video and audio codec with limits, Widevine and ClearKey DRM, audio outputs
11. **Network**: networks, addresses, DNS, routes, Wi-Fi details, listening ports
12. **Bluetooth**: adapter, state, paired devices
13. **Input**: remotes, keyboards and gamepads with their special keys
14. **Default apps and intents**: Home, Settings, browser, players, installer, keyboards, screensavers,
    accessibility, device admins
15. **Apps**: every app with version, state, TV or phone entry, installer, dates, size, signer,
    permissions (granted or not) and components
16. **Settings**: the global, system and secure settings
17. **Power**: uptime, boot count, power state
18. **Security**: SELinux, debuggable, verified boot, root signs, CA certificates
19. **Extras**: logcat, dumpsys and app usage, once granted from a PC (below)

The report is about 250 KB to 1 MB.

## Use

Open **Device Report** and press OK on **Create report**. Allow location (it shows the Wi-Fi name) and storage
(it saves to Download); both are optional. The summary appears on screen. Copy the files off with a USB stick
or `adb pull /sdcard/Download/<file>`.

For the system log, dumpsys and app usage, run these once from a PC, then create the report again:

```bash
adb shell pm grant com.mazem3dia.devicereport android.permission.READ_LOGS
adb shell pm grant com.mazem3dia.devicereport android.permission.DUMP
adb shell appops set com.mazem3dia.devicereport GET_USAGE_STATS allow
```

## Build

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon testReleaseUnitTest assembleRelease lintRelease
```

Package `com.mazem3dia.devicereport`. Font: Inter Display (SIL OFL, `FONT-LICENSE-OFL.txt`). Icon: Lucide cpu (ISC).
