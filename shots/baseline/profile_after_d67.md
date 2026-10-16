# Profile run 2026-10-16 12:10:02, sdk_gphone64_x86_64, app.trackzero.debug, 60 s windows

Note: in this run the first page swipe was not taken (fixed in profile_run.sh since): "R4 speed page" below is
still the map page, and "R5 climb page" is the speed page. The climb page was not measured.

### Cold start
- am start -W TotalTime, best of 3: 577 ms

### B1 standing still (GPS jitter 3 m) (60.1 s)
- CPU: 38.1 % of one core
- Busiest threads (% of one core):
    36.9 Thread-20
- Frames presented (per layer):
    1106 SurfaceView[app.trackzero.debug/app.trackzero.base.TzActivity](BLAST)#1319
    59 app.trackzero.debug/app.trackzero.base.TzActivity#1314
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/EEE92F22 Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.organicmaps.sdk.location.SensorHelper | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Rotation Vector Sensor 0x5f726f76 | status: active | pending flush events 0 

### B1 walking 5 km/h (no ride) (60.1 s)
- CPU: 45.1 % of one core
- Busiest threads (% of one core):
    43.4 Thread-20
    0.8 RenderThread
    0.5 trackzero.debug
- Frames presented (per layer):
    1011 SurfaceView[app.trackzero.debug/app.trackzero.base.TzActivity](BLAST)#1319
    52 app.trackzero.debug/app.trackzero.base.TzActivity#1314
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/EEE92F22 Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.organicmaps.sdk.location.SensorHelper | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Rotation Vector Sensor 0x5f726f76 | status: active | pending flush events 0 

### Background, no ride (60.1 s)
- CPU: 0.1 % of one core
- Busiest threads (% of one core):
- Frames presented (per layer):
      (none)
- Alarms listed for the app: 0 -> 0
- Location requests:
      (none)
- Sensors:
      (none)

### R2 ride, map page (25 km/h) (60.1 s)
- CPU: 100.4 % of one core
- Busiest threads (% of one core):
    96.8 Thread-20
    2.8 RenderThread
    0.7 trackzero.debug
- Frames presented (per layer):
    1164 SurfaceView[app.trackzero.debug/app.trackzero.base.TzActivity](BLAST)#1344
    61 app.trackzero.debug/app.trackzero.base.TzActivity#1341
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/8ED5D17B Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.trackzero.ride.BarometerSource | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Goldfish Pressure sensor 0x00000007 | status: active | pending flush events 0 

### R4 speed page (60.1 s)
- CPU: 100.2 % of one core
- Busiest threads (% of one core):
    97.2 Thread-20
    2.4 RenderThread
    0.6 trackzero.debug
- Frames presented (per layer):
    1197 SurfaceView[app.trackzero.debug/app.trackzero.base.TzActivity](BLAST)#1344
    60 app.trackzero.debug/app.trackzero.base.TzActivity#1341
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/8ED5D17B Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.trackzero.ride.BarometerSource | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Goldfish Pressure sensor 0x00000007 | status: active | pending flush events 0 

### R5 climb page (60.1 s)
- CPU: 6.4 % of one core
- Busiest threads (% of one core):
    4.6 RenderThread
    1.3 trackzero.debug
- Frames presented (per layer):
    686 app.trackzero.debug/app.trackzero.base.TzActivity#1341
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/8ED5D17B Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.trackzero.ride.BarometerSource | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Goldfish Pressure sensor 0x00000007 | status: active | pending flush events 0 

### Screen off (ride recording) (60.1 s)
- CPU: 0.2 % of one core
- Busiest threads (% of one core):
- Frames presented (per layer):
      (none)
- Alarms listed for the app: 0 -> 0
- Location requests:
    10194/app.trackzero.debug/8ED5D17B Request[@+1s0ms HIGH_ACCURACY, WorkSource{10194 app.trackzero.debug}]
- Sensors:
    app.trackzero.ride.BarometerSource | WakeLockRefCount 0 | uid 10194 | cache size 0 | max cache size 0
    Goldfish Pressure sensor 0x00000007 | status: active | pending flush events 0 

