# APRSdroid

[APRSdroid](https://aprsdroid.org/) is an Android application for Amateur
Radio operators. It allows reporting your position to the
[APRS (Automatic Packet Reporting System)](http://aprs.org/)
network, displaying of nearby amateur radio stations and the exchange of APRS
messages.

APRSdroid is Open Source Software written in Scala and licensed under the GPLv2.

Quick links:

- [Google Play](https://play.google.com/store/apps/details?id=org.aprsdroid.app)
- [Twitter](http://twitter.com/aprsdroid)
- [FAQ](https://github.com/ge0rg/aprsdroid/wiki/Frequently-Asked-Questions)
- [Configuration](https://github.com/ge0rg/aprsdroid/wiki/Settings)

# Compilation

APRSdroid is written in Scala and uses
[gradle-android-scala-plugin](https://github.com/AllBus/scala-plugin) to
compile the source. It mostly works, but takes roughly three minutes for a
full build, and often produces non-working APKs on incremental builds.

**Google Maps:** you need to
[obtain a Maps API key](https://developers.google.com/maps/documentation/android-sdk/start)
for your signing key, or the map view will remain blank.

Having the Maps API key, do the following to compile and install an APK:

```bash
git clone https://github.com/ge0rg/aprsdroid/
cd aprsdroid
git submodule update --init --recursive
# replace AI... with your API key:
echo "mapsApiKey=AI..." > local.properties
# for a debug build:
./gradlew installDebug
# for a release build:
./gradlew installRelease
```


