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
sudo apt-get install -y git openjdk-8-jdk vim-nox wget unzip

cmdline_tool_file="commandlinetools-linux-6609375_latest.zip"
export ANDROID_SDK_ROOT="$(pwd)/android"
mkdir -p "${ANDROID_SDK_ROOT}"
wget "https://dl.google.com/android/repository/${cmdline_tool_file}"
unzip "${cmdline_tool_file}" -d "${ANDROID_SDK_ROOT}/cmdline-tools"
rm -f "${cmdline_tool_file}"
export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${PATH}"
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${PATH}"
export PATH="${ANDROID_SDK_ROOT}/emulator:${PATH}"
mkdir "${ANDROID_SDK_ROOT}/licenses"
echo 24333f8a63b6825ea9c5514f83c2829b004d1fee > "${ANDROID_SDK_ROOT}/licenses/android-sdk-license"
echo 84831b9409646a918e30573bab4c9c91346d8abd > "${ANDROID_SDK_ROOT}/licenses/android-sdk-preview-license"
sdkmanager --install emulator 'system-images;android-24;default;armeabi-v7a'

git clone https://github.com/na7q/aprsdroid/
cd aprsdroid
git submodule update --init --recursive
# replace AI... with your API key:
echo "mapsApiKey=AI..." > local.properties
# for a debug build:
./gradlew assembleDebug
# for a release build:
./gradlew assembleRelease
```


