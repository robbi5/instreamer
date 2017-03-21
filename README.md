# HDMI IN Streamer
Android TV app for streaming HDMI input of Zidoo X9S and Zidoo X8 compatible TV boxes with ffmpeg.

## Installation
* Copy [.apk from releases](https://github.com/robbi5/instreamer/releases) to your device and install it.
* Copy [ffmpeg binary](https://github.com/WritingMinds/ffmpeg-android/releases) to `/mnt/sdcard/`

## Running
* Start app from menu
* Press menu button and edit and confirm all settings
* Start streaming by pressing Play/Pause button on remote

## Configuration
* Valid ffmpeg commands:
* streaming to network in MPEG-TS:
```
ffmpeg -i - -codec:v copy -codec:a copy -bsf:v dump_extra -f mpegts udp://[IP]:1234
```
* streaming to web (e.g. YouTube):
```
ffmpeg  -i - -strict -2 -codec:v copy -codec:a aac -b:a 128k -f flv rtmp://a.rtmp.youtube.com/live2/[Stream name/key]
```

## Compilation
Open in Android studio and compile as usual.

## Features
* streaming as MPEG-TS to network (unicast/multicast)
* streaming in FLV format to RTMP server (e.g. Youtube)
* no need for intermediate recording file - thus no length limit 
* streaming runs in background
* HDMI out is usable as passthrough

## TODO
* automatic download of needed ffmpeg binary on first start
* automatic restart of ffmpeg after network/encoding failure

## Thanks
Thanks to [danman.eu](https://blog.danman.eu/using-tronsmart-pavo-m9-for-hdmi-input-streaming/) for [ZidoStreamer](https://github.com/danielkucera/ZidoStreamer), which this project is roughly based on.

Thanks to zidootech for [VideoAndHdmiIN](https://github.com/zidootech/VideoAndHdmiIN), which contained the needed libraries.