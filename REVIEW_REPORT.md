# GeoMeasure 0.2.1 — code review and correction report

## Scope reviewed
- Android/Gradle configuration
- GNSS lifecycle and Android fix handling
- raw GNSS logging
- quality gate and point occupation
- WGS84/ENU/SIRGAS2000 UTM math
- parcel area/perimeter/topology
- SQLite persistence and ViewModel concurrency
- Mapbox annotation/camera lifecycle
- NMEA GGA, NTRIP and RTCM framing
- unit-test coverage and CI definition

## Corrected defects
1. Repeated GNSS metadata events could be counted as new position samples.
2. Older/last-known fixes could overwrite a newer location.
3. Mock locations were not explicitly excluded from survey capture.
4. GNSS snapshot fields could suffer lost updates across independent callbacks.
5. Point metadata could overstate quality by keeping the best instant rather than the conservative result.
6. RAW GNSS disk work could run too aggressively on callback paths and shutdown could lose queued rows.
7. Capture cancellation/restart could allow stale work to interfere with a new occupation.
8. ENU dispersion/covariance was not explicitly recentered at the sample centroid.
9. The initial occupation longitude mean was not dateline-safe.
10. Vincenty/local planar production geometry was replaced by Karney/GeographicLib geodesics.
11. Self-crossing/touching/overlapping or duplicate parcel boundaries could yield misleading numerical area.
12. SQLite work was not isolated enough from UI concurrency and future migrations needed an explicit failure policy.
13. GGA parsing was too narrow and malformed checksum/coordinate cases needed stronger validation.
14. NTRIP TLS socket setup had a compile/runtime-risk path; TLS identity verification was hardened.
15. NTRIP VRS GGA uplink, ICY response handling and cancellation were hardened.
16. RTCM framing now recovers after corrupt frames rather than discarding potentially valid following data.
17. Map updates recreated survey annotations for GNSS metadata-only changes, causing avoidable churn/flicker.
18. 2D/3D toggles and project changes could leave stale camera state.
19. README Mapbox token instructions did not match the actual Gradle configuration.
20. AGP 9 build configuration was migrated to the built-in Kotlin model while keeping Kotlin/Compose Compiler aligned.

## Validation executed in this environment
- `PURE_KOTLIN_COMPILE_PASS`
- `CAPTURE_021_PASS`
- `NTRIP_021_PASS`
- `XML_PARSE_PASS`
- `YAML_PARSE_PASS`

## Remaining external validation
A complete Android build still requires an Android SDK 37 environment and the Mapbox Maven download credential. Professional RTK accuracy additionally requires real receiver hardware, a real correction service and comparison against surveyed control points.

## Known packaging limitation
The source package includes `gradle/wrapper/gradle-wrapper.properties` pinned to Gradle 9.5.0, but does not bundle the binary `gradle-wrapper.jar` or wrapper launch scripts. GitHub Actions does not depend on them: it installs Gradle 9.5.0 with `gradle/actions/setup-gradle` and runs tests/lint/build directly.
