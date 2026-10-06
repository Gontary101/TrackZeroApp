# TrackZero — Bugs to Fix

Issues to address in the `trackzero` branch of `Gontary101/TrackZeroApp`.

## Requirements for all changes

Preserve TrackZero’s existing design language and consistent UI. Aim for the best practical battery life without compromising usability, visual clarity, responsiveness, or the overall user experience.

**First-launch tutorial / onboarding:** Follow the same dark, clean, uncluttered design language as the rest of TrackZero. Make it primarily visual and intuitive: clear icons, short labels, and brief interactive demonstrations rather than text-heavy slides or long paragraphs. Introduce only essential actions in a few easily understood steps, with an obvious way to skip the tutorial and replay it later. Keep visuals accessible, with concise text or screen-reader descriptions where needed, and avoid heavyweight assets or ongoing battery impact.

**Shared navigation-profile requirement (Road, Gravel, Walking, and Hiking):** Profile selection happens before or at the start of navigation through one simple, consistent control; it must not add clutter, extra ride-time steps, or reduce responsiveness. Load and use **only the selected profile** for routing and guidance. Inactive profiles must do no background work, location sampling, route calculation, or periodic updates. Reuse the same CoMaps routing/navigation pipeline, GPS sampling policy, and shared UI components; do not run multiple routing engines or keep unused profiles active. Compute a route or profile-dependent estimate only when requested or needed to start navigation, not continuously because multiple profiles exist. **Target zero measurable additional ride-time battery consumption and no usability regression solely from adding profile choices**, using the original single-profile Road experience as the baseline; validate this in the final testing phase rather than claiming literal zero processing cost.

## Open issues

### BUG-001 — No route when a destination isn't reachable by bike
When searching for a destination that cannot be reached entirely by bicycle, TrackZero displays “No bike route” and stops. It should instead route by bike to the nearest suitable reachable point, then provide a walking route for the remaining distance.

### BUG-002 — Route direction is unclear and the line is hard to see
While following a route, TrackZero does not clearly indicate the intended direction of travel and may appear to treat riding in the opposite direction as correct. Add directional arrows along the route line so the rider can see which way to go, and make the green route line thicker for better visibility, especially in daylight.

### BUG-003 — Ride controls sheet overlaps the bottom island
The three-dot Page Handle for the Ride Controls Sheet sometimes overlaps the bottom island, while leaving too much unused space on the ride screen. Position the handle lower, near the bottom edge and below the island, so it no longer overlaps the island and the ride screen uses more of the available space.

### BUG-004 — Ride start shows unstable map instead of the active route
When TrackZero is launched with GPS/location enabled and a ride is started, the expected green active-route line may not appear immediately. Instead, the map can jump or zoom in and out across seemingly unrelated locations. Navigating to other app screens and returning to the ride screen makes the map/route return to normal. This has been observed with mobile data enabled; behavior without data has not yet been confirmed.

**Root-cause requirement:** reproduce and investigate this as a real startup/state/rendering bug and fix the underlying cause directly. Do **not** ship a workaround that merely hides or recovers from the symptom—for example, forced refresh/reload, arbitrary delay or retry, automatic screen switching, repeated recentering, or recreating the view until it happens to render correctly. Trace the relevant ride-start lifecycle, route readiness/state propagation, map camera state, location updates, and route-overlay rendering to identify why the initial ride view enters the invalid/unstable state. After the fix, starting a ride must deterministically show the correct active route and appropriate map framing without requiring the rider to leave and return to the screen. Also test startup with and without network/data connectivity so the fix does not accidentally depend on an online state.

## Feature requests

### FEAT-001 — Immersive full-screen mode
During a ride, hide the phone’s top status bar and bottom system navigation bar to maximize screen space, while keeping system gestures available when needed. Restore the system bars outside the ride screen.

### FEAT-002 — Plan bicycle routes directly on the map
Let riders tap the map to choose a destination and optionally add, move, or remove waypoints, then preview and follow the route without searching for a place or importing a GPX file. Take inspiration from BRouter's waypoint-planning experience, but **do not integrate BRouter or replace CoMaps' router**: keep this a small extension of TrackZero's existing offline routing. Planning should be responsive and on demand, without degrading app performance or increasing battery consumption during rides. Let riders save planned routes in the route library and export them as GPX using TrackZero’s existing GPX-sharing functionality.

### FEAT-003 — Gravel cycling profile and low-traffic routing
Add a **Gravel** profile alongside the existing bicycle profile. Favor quiet minor roads, suitable one-way streets (following permitted cycling directions), gravel roads, and rideable unpaved paths or trails. Avoid very rough, rocky, or technical mountain-bike terrain. Both bicycle profiles should prefer lower-traffic routes where the available map data permits, without requiring live traffic data. Reuse the existing CoMaps routing engine and preserve offline operation, responsiveness, and battery efficiency.

### FEAT-004 — Suggest cycling loops by distance and elevation
Add a **Propose a Loop** option that generates a few round-trip routes from the rider’s current location or a chosen starting point. Let the rider choose a target distance (for example, 20, 30, or 40 km) and approximate elevation gain (D+) and loss (D−), with optional waypoints. Accept a generous distance tolerance of roughly **±20–30%** and a similarly flexible elevation target so useful loops can be found without an exact match. **Loop suggestions must form genuine circuits: the outbound and return portions should use meaningfully different roads, paths, or trail segments wherever the map network permits, rather than simply retracing the same route in reverse. Minimize duplicated/retraced distance so the rider sees new terrain and scenery throughout the loop; only reuse a segment when there is no reasonable alternative needed to keep the loop feasible and within the requested profile, distance, and elevation constraints.** Show estimated distance and elevation for each suggestion; the rider can preview, select, and follow one like an imported GPX route. Respect the selected road or gravel profile. Reuse the existing CoMaps offline router, calculate loops **only on request**, and stop planning once a route is selected so there is no ongoing processing or extra battery drain during rides. Let riders save selected loops and export them as GPX, reusing the existing GPX functionality.

### FEAT-005 — Research and improve route visibility in daylight
**Research phase:** compare route-line colors, contrast, thickness, outlines, and directional-arrow visibility under bright outdoor daylight, including on phone screens. **Implementation goal:** use the findings to make the route easier to see at a glance, potentially changing its color, width, and edge treatment. Limit changes to the **map route overlay**; keep TrackZero’s black background and the rest of its dark UI unchanged. Coordinate with BUG-002’s thicker line and direction arrows, and avoid unnecessary rendering or battery overhead.

### FEAT-006 — Preview route surface quality
Before starting a route, show the available surface information along it (such as paved, gravel, unpaved, or rough sections) so riders can judge whether it is suitable for a gravel bike. Clearly indicate sections where the offline map has no surface information, and keep the preview lightweight.

### FEAT-007 — Find water and bike repair along a route
Let riders find drinking-water points and bicycle repair facilities along or near a planned route, using the available offline map data. **During an active ride**, let the rider select a result and add it to the current route as an intermediate waypoint or a temporary detour, with a simple preview of the extra distance where available. Recalculate only when the rider confirms, using the existing CoMaps router; preserve the remaining route and resume navigation toward the original destination after the stop. If the detour cannot be routed, leave the current route unchanged. Keep the interaction quick, offline-capable, and battery-efficient.

### FEAT-008 — Direction-specific audible navigation alerts
Play short, clearly distinguishable **tones through the phone speaker** for different instructions—left, right, continue straight, and other relevant maneuvers—so riders can understand the next action without relying on spoken directions or vibration. In the first-launch tutorial, present each tone with a clear directional or warning icon, a very short label, and a tap-to-play audio example so its meaning is immediately understandable without reading paragraphs; allow the rider to skip the tutorial and replay it later. Add a setting to enable or disable tone alerts; keep spoken guidance available separately. Add distinct warning sounds for going off-route and losing GPS, distinguishable from turn tones, without repeated nuisance alerts. Explain these sounds in the tutorial and allow users to disable the warnings. Preserve battery efficiency.

### FEAT-009 — Personalized ride-time estimate at ride start
At the start of a ride, estimate the total ride time using the rider’s previously recorded cycling performance and the planned route. Calculate the estimate **once, when the ride starts**, and do not recalculate or continuously update it during the ride. Use data TrackZero already collects; do not add GPS sampling, background processing, or ongoing battery consumption. Keep the estimate available to view during the ride.

### FEAT-010 — Find verified bivouac areas nearby
Add a **Bivouac** category alongside water and bicycle-repair stops. List the nearest places to spend the night, based on the rider’s current position or planned route, with their locations and distance. Show a place as **verified** only when a trustworthy source confirms bivouacking is permitted there; surface any known restrictions and do not present unverified locations as authorized. Allow the rider to add a selected bivouac to the active route as a waypoint or detour, using the same lightweight, on-demand route-update flow as FEAT-007; preserve the original route and its remaining stops.

### FEAT-011 — Extreme battery saver for multi-day rides
Add an optional **Extreme Battery Saver** mode for very long and multi-day rides, accessible from ride controls and easy to turn off. Prioritize essential offline GPS navigation, route progress, and the short directional audio cues and critical off-route/GPS-loss warnings from FEAT-008, while suspending nonessential animations, map rendering and screen refreshes, live statistics, and background work where safe. Dim the app display when it is visible and support continued navigation with the phone's screen off or normally locked; do not require intrusive device-administrator permissions or replace Android's normal lock screen. Reduce location and processing overhead only as far as reliable turn alerts and route tracking permit, and avoid unnecessary rerouting or network activity. Keep the current route and ride state intact so unlocking the phone or leaving the mode immediately restores the regular interface. Make any loss of functionality clear before activation, preserve normal safety-critical guidance, and compare battery use and navigation reliability during realistic long rides; never promise a fixed battery-life improvement without measurement.

### FEAT-012 — Walking navigation profile
Add a **Walking** profile alongside the existing road-bicycle and gravel profiles. Reuse CoMaps’ existing pedestrian routing where available; do not add another routing engine or expand the cycling profiles. Plan walkable routes that favor pedestrian paths, sidewalks, footpaths, and suitable hiking trails, respect mapped pedestrian access restrictions, and avoid roads or terrain unsuitable for walking. Make Walking selectable through the existing route-planning UI, including map-selected destinations and waypoints, and use walking-appropriate route guidance and time estimates rather than cycling assumptions. Keep offline navigation, the existing TrackZero design language, and the same performance and battery-efficiency requirements. Reuse existing route saving and GPX export where applicable; implement this as a small, isolated extension rather than a UI overhaul.

### FEAT-013 — Hiking navigation profile
Add a **Hiking** profile alongside Road, Gravel, and Walking, focused on trails and longer routes on foot rather than everyday pedestrian navigation. Reuse CoMaps’ existing offline pedestrian routing and available map metadata; prefer legally accessible hiking trails, footpaths, and suitable unpaved tracks over urban streets where the existing routing capabilities allow a small, maintainable preference adjustment. Respect mapped access restrictions and avoid known impassable or hazardous terrain; do not claim that a route is safe or that an unmapped trail is accessible. Show estimated distance, duration, elevation gain (D+), and elevation loss (D−) where reliable elevation data exists, with clearly labeled unknown values otherwise. Allow map-selected destinations and waypoints, and reuse existing route preview, guidance, route saving, GPX export, and the optional Extreme Battery Saver mode. Use walking/hiking-appropriate pace estimates, not cycling estimates. Keep Hiking distinct from Walking without introducing another routing engine, large dependencies, continuous computations, or an interface redesign; preserve offline operation, responsiveness, and battery efficiency.

### FEAT-014 — One-tap emergency location sharing
Add a small **Share my location** action, accessible during a ride and in Extreme Battery Saver, for emergencies or when the phone is running critically low on battery. When the rider taps it, prepare a short, editable message with a choice of a general check-in or a low-battery/emergency wording, the **latest reliable GPS coordinates**, a tappable map link, the battery percentage, and the nearby street/place name **only if reliably available** from existing map/location data. Include the location fix's age or a clear warning if it is stale; if no reliable position is available, say so rather than suggesting the coordinates are current. Use **Android's native text Sharesheet** so the rider can choose an installed app (such as WhatsApp, Telegram, or messaging) and the recipient, then personally confirm sending. Do not implement app-specific messaging integrations, read contacts, silently send messages, or continuously share or track location. Reuse the GPS fix already obtained for ride navigation and the system's on-demand battery reading; perform any optional place-name lookup only when the action is opened, with a fast fallback to coordinates alone. Inactive, the feature must perform **no ongoing work, additional GPS sampling, polling, background networking, or meaningful battery consumption**. Opening the sharing flow naturally incurs a small, one-time cost; do not claim literal zero energy use. If internet is unavailable, do not promise delivery through internet messaging apps; where feasible offer copying the prepared text or sharing through another available channel. Keep the action clear, fast, privacy-conscious, and consistent with TrackZero's existing design. Do not market the feature as guaranteed emergency communication or as a replacement for contacting emergency services.

### FEAT-015 — Light mode for maximum daylight visibility

Add a first-class **Light** appearance alongside the existing Dark appearance, optimized for maximum readability and glanceability in bright outdoor/daylight conditions.

- Preserve TrackZero's exact design language rather than treating Light mode as a redesign: same information hierarchy, typography, spacing, radii, component geometry, interaction patterns, restrained accent usage, and uncluttered character.
- Adapt surfaces, text, map/UI contrast, icons, states, and other theme-dependent colors intelligently so the interface remains immediately legible in strong daylight while still unmistakably feeling like TrackZero.
- Do not add theme controls, icons, toggles, or shortcuts to the main Map, Routes, Search, ride, or navigation surfaces.
- The **Light / Dark** appearance control belongs **only in the More menu** as a normal preference/setting.
- Switching themes must be simple and immediate and must not introduce additional runtime/background work or a meaningful battery or usability regression.
- Apply the selected appearance consistently across the whole app, including newly added features, dialogs, onboarding/tutorial UI, navigation, maps, and utility screens.
- Validate both themes for contrast, readability, state clarity, and outdoor use, with particular attention to Light mode under bright daylight.

## Final phase — Bug hunt, regression testing, and battery validation

After completing the fixes and features above, systematically test TrackZero and **fix every confirmed issue**, especially edge cases involving screen transitions; starting, pausing, resuming, and ending rides; unavailable, delayed, inaccurate, or lost GPS and its recovery; missing offline maps; failed or canceled routes; and interruptions such as backgrounding and reopening the app. Check that navigation, recording, controls, and the UI remain consistent throughout, including Extreme Battery Saver with the display dimmed, off, or locked and upon return to normal mode. Retest affected flows after fixes, then validate battery consumption during realistic rides against the current app. Specifically compare the Road profile against the original single-profile cycling baseline, verify that inactive profiles create no ongoing work, and check battery use and usability for each active profile under comparable navigation conditions. Test emergency location sharing with fresh, stale, and unavailable GPS; missing street names; low battery; absent internet; and canceled shares, confirming that the feature has no idle battery impact. Resolve regressions without sacrificing responsiveness, usability, or TrackZero’s design language.

## Optional final phase — Commercialization and legal audit

**Run only if TrackZero is being prepared for a public or commercial release, after implementation, the final bug hunt, regression testing, and battery validation**, and before publishing any commercial launch materials. Audit the complete distributable app and its source, dependencies, bundled resources, data, services, and release materials for legal and licensing issues. In particular:

- Verify CoMaps' actual upstream licenses, copyright and NOTICE requirements, modification notices, and obligations for every third-party library or component; check whether any copyleft or other terms affect the planned distribution model. Preserve all required notices, licenses, attributions, and source-code offers, where applicable.
- Check OpenStreetMap and other map, routing, elevation, POI, and location-data licenses (including attribution, applicable database/share-alike conditions, and permitted offline or commercial use), as well as the terms and capacity of any map-download or online service infrastructure. Do not assume that permission to use code also grants permission to use hosted services.
- Audit the licenses and permitted commercial use of all fonts, icons, illustrations, images, audio cues, bundled assets, and any other incorporated resources. Replace an asset or font with a suitable commercially usable alternative if its rights cannot be established.
- Review the **TrackZero** name, app icon, logo, slogans, color palette, typography, visual identity, and store listing for potential trademark conflicts, confusing similarity, or other protectable branding/design issues in the intended release markets. Colors alone are not automatically prohibited, but the overall presentation and distinctive combinations should be checked. If necessary, adjust branding, fonts, colors, or other visual details as narrowly as possible while preserving TrackZero's overall design language, accessibility, and daylight readability.
- Review the app's handling of precise location, route history, optional analytics, and other personal data, and prepare any necessary privacy disclosures, user controls, policies, and app-store declarations. Check applicable consumer, payment/subscription, and distribution rules if those business models are used.

**Agent discretion:** Research uncertain rights and choose the simplest compliant option. The agent may make minimal, well-justified replacements or design adjustments when needed for lawful distribution; do not redesign the app speculatively or add unnecessary dependencies. Document each finding, its source, required action, and any residual uncertainty. Do not claim that an automated check guarantees legal compliance: flag unresolved or material issues for qualified legal review before commercial launch.

## Final creative phase — TrackZero marketing video

Once the app is complete and ready to showcase, create a **sleek, professional marketing video lasting 1–2 minutes** that presents TrackZero beautifully and conveys its experience and distinctive capabilities. Give the creative agent broad freedom to develop the concept, visual direction, pacing, animation, sound, and editing style. It may use **any software, tools, or workflows it considers appropriate**—for example, Blender (including Blender MCP), DaVinci Resolve, or other tools—and should not be constrained by a prescribed storyboard, production method, or technical stack. For creative inspiration, study the clean, high-production-value launch films of **OpenAI, Airbnb, and other leading brands**—their restrained visual language, purposeful motion, refined typography, pacing, and clear storytelling. Use those references to understand the desired level of polish, **not to copy their footage, music, graphics, branding, or specific creative execution**. These are inspirations, not a prescribed style or storyboard: the agent retains full creative freedom to give TrackZero its own distinctive identity. **Make TrackZero’s actual interface the primary visual reference:** inspect the latest app screenshots and other imagery in the GitHub repository, as well as the finished app itself, before defining the video’s visual direction. The film should feel like a natural extension of TrackZero’s clean, minimalist, uncluttered design—reflecting its dark backgrounds, existing color accents, typography, spacing, and restrained visual hierarchy. Make **exceptional motion design a defining feature** of the video: aim for striking, cinematic choreography with impeccably timed transitions, elegant UI animation, expressive camera work, sophisticated typography in motion, and sound design synchronized to the visuals, as the creative concept warrants. Treat transitions as a standout part of the motion design: make scene changes, product reveals, and movement between UI views feel seamless, inventive, and precisely timed, choosing the techniques freely to serve the story rather than relying on generic preset effects. Pursue a genuinely memorable, high-end launch-film standard while keeping every movement purposeful, legible, and faithful to TrackZero’s minimalist identity—never adding clutter, gratuitous effects, or misleading app interactions. The agent retains full creative freedom over the techniques and execution. Show the real app accurately, without inventing UI or implying unfinished features are available. Aim for a polished, cohesive result worthy of a real app launch, using accurate representations of the finished app and appropriately licensed or original assets. Deliver the finished playable video; retain editable project/source files where practical. If preparing a commercial launch, resolve relevant rights and branding questions from the optional legal-audit phase before publication.

## Observed during v2, for the Phase 38 bug hunt

- **Leg back onto the track reads "Head to start".** When the router starts a leg on a road more than 60 m from the
  rider (seen on the emulator's off-road detour, Road and Walk), the leg engine shows HEAD_TO_START: "Head to start"
  with an arrow to the leg's first point. The wording is about the route's start, not the leg's (D50).
- **Stops on a free ride.** S1 lists water and repair within 5 km of a free rider and S2 shows the place, but a free
  ride has no route, so ADD STOP is not offered (D55). Guiding a free ride to a place mid-ride would need the ride
  screen to switch between free and guided; decide with the owner whether it is wanted.
- **A stop leg ignored reads as off route.** A rider who keeps to the route past a stop's turn-off gets "off route"
  from the stop's leg until the stop is 500 m behind and is dropped (D55). Removing it in S1 is the quick way out.
- **Bivouac: no access information offline.** The maps carry no access tag for campsites or huts (no metadata, no
  type), so "private access overrides VERIFIED" can only work if a place carries `hwtag-private`; in practice it
  never does (D56). Shelters on a bus stop or platform are left out; a few named after a stop may remain.

## Fixed and verified

_No bugs marked as fixed yet._

### Fixed, awaiting field check

- **BUG-004** — Phase 17, commit 459a123. Causes found from traces and fixed at the source (D42); emulator check 59/60
  cold starts across mobile data / Wi-Fi / no network (the miss: emulator engine latency, passed with a longer window).
  Field check: NEEDS OWNER N17 in `TZ_PROGRESS.md`.
- **FEAT-001** — Phase 18. Bars hidden on the ride, back elsewhere; touch targets above the gesture strip (D43).
  Field check: NEEDS OWNER N18.
- **BUG-003** — Phase 19. Material's sheet moved the handle up into the island (D44); one bottom layout now, handle
  under the island on every page. Field check: NEEDS OWNER N18 (handle part).
- **FEAT-005** — Phase 20. Research in `TZ_RESEARCH_ROUTE_VISIBILITY.md`; 8dp line with a black casing (D45).
  Field check: NEEDS OWNER N20 (choose among four candidates).
- **First-launch tutorial** (the requirement above, and FEAT-008's tutorial part) — Phase 37. T1–T4, four visual
  steps before B1 the first time, SKIP on each, replay from More › Show the tutorial; tones to tap on T3 (D62).
  Field check: NEEDS OWNER N37.
- **FEAT-015** — Phase 36. Light appearance: More › Appearance toggles Dark / Light (the rider's choice, Dark by
  default); the same layouts with the Light colour resources and a light cycling map (D61). Field check: NEEDS OWNER
  N36.
- **FEAT-011** — Phase 35. Extreme battery saver: R6 SAVER › X1 › TURN ON; X2 black screen with the turn, tones
  and warnings kept, map/voice/rerouting/barometer paused, GPS every 3 s away from turns; one tap to exit (D60).
  Field check: NEEDS OWNER N35.
- **FEAT-014** — Phase 34. Share my location: R6 SHARE opens E1 with the last good fix as an editable message
  (check-in, low battery, I need help), COPY or Android's share sheet; TrackZero sends nothing itself (D59). Field
  check: NEEDS OWNER N34.
- **FEAT-009** — Phase 33. A personal ride-time estimate made once at the start from the last 12 rides of the same
  kind (typical pace with fewer than 3), shown in R6 and as the planner's TIME (D58). Field check: NEEDS OWNER N33.
- **FEAT-008** — Phase 32. Direction tones and warnings, generated in the app, through the phone speaker; R6
  Turn tones toggle, More › Sounds (M1) with the tones to hear, NO GPS card (D57). The first-launch tutorial part
  is T3 in Phase 37. Field check: NEEDS OWNER N32.
- **FEAT-010** — Phase 31. Bivouac places from the map: campsites and huts VERIFIED, shelters NOT CONFIRMED, with
  hours and fee when the map has them; S1 chip and B2 chip (5 km, 20 km when none) (D56). Field check: NEEDS
  OWNER N31.
- **FEAT-007** — Phase 30. Water and bicycle repair along the route from the offline map (S1, from R6 and B5); add
  one to the ride (S2): routed at once, led there 2 km before it, back onto the route after (D55). Field check:
  NEEDS OWNER N30.
- **FEAT-004** — Phase 29. Propose a loop: distance, climbing, profile, a point to pass through; three round trips
  in different directions with little retracing; preview, save, export, ride as a planned route (D54). Field check:
  NEEDS OWNER N29.
- **FEAT-002** — Phase 28. Plan a route on the map: destination, points on the way, drag, reorder, undo; preview,
  ride, save, export (D53). Field check: NEEDS OWNER N28.
- **BUG-001** — Phase 27. A place a bike cannot reach: ride to the nearest point a bike can reach, then walk
  (B5W, D52). Field check: NEEDS OWNER N27.
- **FEAT-013** — Phase 26. Hiking profile: trails first, expert paths avoided where possible, hiking times with
  D+ / D−, a line for demanding sections per the map; no safety claims (D51). Field check: NEEDS OWNER N26.
- **FEAT-012** — Phase 25. Walking profile: pedestrian router, walking turns, foot auto-pause, ETA and zoom, a
  walking time on the place preview (D50). Field check: NEEDS OWNER N25.
- **FEAT-003** — Phase 24. Gravel profile (minor roads, gravel, easy tracks; rough avoided) and quieter roads for
  Road, checked on ten trips (D49). Field check: NEEDS OWNER N24.
- **FEAT-006** — Phase 23. Surface bar and legend on the route preview, read from the downloaded maps and cached
  (D48). Field check: NEEDS OWNER N23.
- **BUG-002** — Phase 21. Direction chevrons inside the line, and a wrong-way warning (R3W) when the route is ridden
  backwards (D46); the thicker line came with FEAT-005. Field check: NEEDS OWNER N21.
