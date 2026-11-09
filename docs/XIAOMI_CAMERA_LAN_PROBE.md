# Xiaomi Camera local read-only diagnostic

This replaces the legacy MacroDroid / IntentTask UI. The user requested no additional Android apps.

Known first camera model: Mi Camera 2K Magnetic Mount (MJSXJ03HL), MIoT `isa.camera.hlc7`.
MIoT camera_control.on is SIID 2, PIID 1, but its existence does not prove local access.

## Test
1. Connect the phone to the same Wi-Fi as the camera.
2. Open CuponApp > Settings > AlarmGuard > آدرس محلی دوربین شیائومی.
3. Enter the private IPv4 address from Xiaomi Home > Network info.
4. Tap تست اتصال مستقیم دوربین.

The app sends only a 32-byte MIIO 'hello' UDP datagram to port 54321.
It validates the response header. This does not fetch credentials, modify camera settings, or prove Wake/Sleep support.

## Known limits
- Cannot access the user's physical LAN from CI, ChatGPT or GitHub.
- Even a successful hello cannot perform authenticated writes without a device token and MIoT response.
- Real mobile internet/outside-home control requires a different approved cloud route.
- A negative hello could indicate network isolation or disabled local protocol; it is not proof the camera is broken.
- No automatic SMS-to-camera control has been implemented or activated in this diagnostic version.

Resources: https://python-miio.readthedocs.io/en/stable/_modules/miio/protocol.html
https://home.miot-spec.com/spec?type=urn:miot-spec-v2:device:camera:0000A01C:isa-hlc7:2