---
title: APRSdroid in Kenwood GPS Mode
author: Georg Lukas
---
## Using APRSdroid with a Kenwood APRS radio (D7x0)

![Screenshot of APRSdroid Kenwood mode](https://aprsdroid.org/kenwood/kenwoodroid.png){#qr}
It is possible to use APRSdroid as the GPS receiver for a Kenwood D7x0 mobile
rig. This way, you do not need a dedicated GPS receiver, and you can configure
the rig to display received positions on the Android device. To connect the
two, you will need a Bluetooth RS232 adapter that you can connect to the D7x0
GPS port (2.5mm at the side of the control unit).

However, APRSdroid will only display information. Own position beacons,
Messaging and other APRS functions will not work from APRSdroid. The displayed
information only consists of callsigns and positions (you can use the map in
that way).

## Configuration of APRSdroid

You need to set Connection Type to Kenwood GPS port and configure the correct
Bluetooth device in the Android preferences.

Please also set Position Source to SmartBeaconing (or to Periodic GPS Position
with "High (always on)" GPS precision). This is required to keep permanent GPS
reception for the D7x0.

## Configuration of the D7x0

You need to set the D7x0 to output WAYPOINT data via the GPS port, with the
suggested format being KENWOOD, 9-char:

It was also reported that the FTM-350 transceiver supports APRSdroid in its
"waypoint" mode.

![Photo of Kenwood D7x0 config](https://aprsdroid.org/kenwood/kenwood-config-aprsdroid.jpg)
