IN=aprsdroid.svg

inkscape -z -f $IN -C -d 60 -e aprsdroid48.png
cp aprsdroid48.png ../res/drawable-ldpi/icon.png

inkscape -z -f $IN -C -d 80 -e aprsdroid64.png
cp aprsdroid64.png ../res/drawable/icon.png

inkscape -z -f $IN -C -d 90 -e aprsdroid72.png
cp aprsdroid72.png ../res/drawable-hdpi/icon.png

inkscape -z -f $IN -C -d 120 -e ../res/drawable-xhdpi/icon.png

inkscape -z -f aprsdroid-v11-notification.svg -C -d 60 -e ../res/drawable-xhdpi-v11/ic_status.png
inkscape -z -f aprsdroid-v11-notification.svg -C -d 45 -e ../res/drawable-hdpi-v11/ic_status.png
inkscape -z -f aprsdroid-v11-notification.svg -C -d 40 -e ../res/drawable-v11/ic_status.png

