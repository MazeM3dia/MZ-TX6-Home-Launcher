# Waktu Solat

JAKIM's prayer times for one zone, full screen on the TV, in Malay: Imsak, Subuh, Syuruk, Dhuha, Zohor, Asar,
Maghrib and Isyak, the next prayer highlighted and counted down, and today's Gregorian and Hijri dates. MZ TX6 Home
reads the same data for its header (Hijri date, next prayer) and sets the zone from its Customize panel.

Rewritten from the [Prayer Times](https://github.com/Gathin23/cleanyourtv/tree/main/addons/prayer-times) add-on by
[@majnioui](https://github.com/majnioui) (MIT): same page layout; the prayer icons are now all Lucide (ISC), and the icon and banner are the project's own.

## Data

- **JAKIM e-Solat**, `https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=duration&zone=<zone>`,
  POST `datestart`/`dateend`. JAKIM's own API, unofficial and undocumented (guide:
  [acfatah/jakim-esolat-api](https://github.com/acfatah/jakim-esolat-api)); it may change without notice.
- **One request fetches 35 days**, stored on the box (`files/jakim.json`). A new request only when fewer than
  7 days remain, the zone changes or today is missing; after a failure, not again for 15 minutes. Offline, the
  stored days are shown. No background service: the page or the launcher reading the data starts a refresh.
- **Sent to JAKIM:** the zone code and the dates. Nothing else; no account, no telemetry.
- **Zones**: JAKIM's 60 (October 2026), in `Zones.java`, generated from e-solat.gov.my's zone list.

## Zone

Chosen in MZ TX6 Home's Customize panel (Waktu solat, Zon): a state and zone, or **Auto**. Auto asks for the
location permission when this page opens, takes the box's last known location and picks the zone with the
nearest main town (island and summit zones are left to manual choice). Many TV boxes have no location at all;
then the page says so and the zone is chosen by hand. The location never leaves the box.

## For the launcher (PrayerProvider)

`ContentResolver.call(content://com.mazem3dia.waktusolat, method, arg, null)`:

| Method | Answer |
|---|---|
| `today` | `state` (ok, loading, error, nozone, locating, nolocation), `setting`, `zone`, `negeri`, `area`, and with times: `hijri` ("16 Jamadilawal 1448H"), `times` and `tomorrow` (minutes after midnight: Imsak, Subuh, Syuruk, Dhuha, Zohor, Asar, Maghrib, Isyak), `day` (yyyymmdd), `updated` |
| `zones` | `codes`, `states`, `areas` |
| `setZone` (arg: zone code or `AUTO`) | as `today` |

New times or a new zone are announced with `notifyChange` on the same URI.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | JAKIM e-Solat |
| `ACCESS_COARSE_LOCATION` | Auto zone only, asked for when Auto is chosen |

## Build

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew --no-daemon testReleaseUnitTest assembleRelease
```

Font: Inter Display (SIL Open Font License, `FONT-LICENSE-OFL.txt`).
