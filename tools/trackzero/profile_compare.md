# Compare profiles (Phase 24)

A check with data that Gravel and Road route as intended. It runs inside the app, on the downloaded Puy-de-Dôme
map, with the same router the rides use.

## Running it

1. About › tap the version 7 times (tracing on; debug builds always trace) › **Compare profiles**.
2. The row shows "Running…". It routes ten fixed trips (`routes/ProfileCompare.TRIPS`: town to village, village to a
   summit road, across the Chaîne des Puys…), each with Road and then with Gravel. Every profile switch rebuilds the
   router, so it takes a few minutes on the emulator.
3. When the row shows "Done", read the lines:
   ```
   adb logcat -d -s TzProfileCompare
   ```
   or About › Share trace (the lines are in the trace, tag `compare`).

## Reading a line

```
trip | profile | km | D+ m | paved/poor/gravel/rough/none % | none/trunk/primary/secondary/tertiary/minor/track/path %
```

- Surface shares are those of the route preview (Phase 23): closest road within 15 m, every max(50 m, length / 3000).
  "gravel" is `unpaved_good`, "rough" `unpaved_bad`, "none" no surface tag.
- Road-class shares come from the same samples (`Framework.nativeRoadClasses`): trunk includes motorways, minor is
  unclassified, residential, living street, service and road, path includes cycleways, bridleways, footways,
  pedestrian streets and steps. "none" is no road within 15 m.

## Pass (plan v2 Phase 24, step 8)

- Where tracks exist, Gravel has clearly more gravel share and less primary + secondary than Road.
- Gravel's rough share stays under 10 % unless the trip cannot avoid it.
- No Gravel route is longer than 1.6 × the Road route.
- Road never uses a trunk when another way is within 1.3 × (checked against the Gravel route, which avoids trunks
  harder: if Road has trunk and Gravel has none at no more than 1.3 × Road's length, it fails).

The results and the final weights are in `TZ_DECISIONS.md` (D49).
