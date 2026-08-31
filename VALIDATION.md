# Validation record — GeoMeasure 0.2.1

## Executed in this development environment

### Pure Kotlin compilation
The non-Android GNSS/geodesy/survey/RTK source set was compiled with `kotlinc` after the review corrections.

Result: `PURE_KOTLIN_COMPILE_PASS`

The local compilation substitutes tiny API stubs only for GeographicLib so it checks Kotlin source compatibility; numerical GeographicLib behavior is covered by the project dependency/tests when Gradle resolves the real library.

### Point occupation / uncertainty
A synthetic occupation with three distinct monotonic fixes was processed after explicit ENU recentering.

Result: `CAPTURE_021_PASS`

The test verified finite positive dispersion and a finite 95% ellipse. Duplicate-fix suppression and conservative quality metadata also have JUnit tests in `app/src/test`.

### UTM projection
Campanha/MG test point `(-21.833, -45.4)`:

- zone: 23S
- E: 458663.25085 m
- N: 7585603.75024 m

Independent EPSG:4674 -> EPSG:31983 reference used during development:

- E: 458663.25085 m
- N: 7585603.75025 m

This validates the implemented GRS80 UTM projection at the test location. It does **not** prove a rigorous centimeter-level WGS84/ITRF-to-SIRGAS2000 frame/epoch transformation.

### Review regression tests included in source
- stale/mock GNSS fix rejection
- repeated metadata update does not count as a new occupation sample
- conservative worst-quality point metadata
- near-antipodal geodesic remains finite with GeographicLib
- two points treated as one segment, not a fake closed perimeter
- bow-tie/self-intersecting parcel rejected
- RTK FIXED GGA parsing
- Galileo/BeiDou-style GGA talker IDs accepted
- malformed NMEA checksum/coordinates rejected
- RTCM CRC-24Q validation and recovery after corrupt input

### NTRIP
A local in-process caster harness was executed against the reviewed client for both `HTTP/1.1 200 OK` and classic `ICY 200 OK`, verifying that the first correction payload bytes are preserved.

Result: `NTRIP_021_PASS`

The reviewed client additionally contains safeguards for:
- TLS endpoint verification
- connect/read timeouts
- cancellation via socket close
- `HTTP ... 200` and `ICY 200` success forms
- preserving RTCM bytes after classic ICY status lines
- periodic rover GGA for VRS/NRTK services
- CR/LF header-injection rejection
- Basic authentication over cleartext disabled unless explicitly opted in

## Not executed here
- Full Android Gradle build: this container does not provide the Android SDK/Mapbox Maven credentials required by this project.
- Mapbox runtime rendering with a real public `pk.*` access token.
- Real Bluetooth/USB GNSS receiver communication.
- Real production NTRIP caster/RTCM relay to a rover.
- Field accuracy comparison against surveyed control monuments.

The GitHub Actions workflow is configured to run `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleDebug` using JDK 17 and Gradle 9.5.0 once the repository has the required Mapbox download secret.
