# GeoMeasure 0.2.1

Android-native GNSS land-measurement app focused on measurement quality, auditability and a path to professional external-RTK operation.

## Implemented

### Phone GNSS
- Kotlin + Jetpack Compose, Android 10+.
- Android GPS fixes through `LocationManager`.
- Horizontal/vertical reported accuracy, used-in-fix satellite count and C/N0 telemetry.
- Android raw GNSS measurement callback and per-satellite multi-frequency detection.
- Mock/stale fix rejection and monotonic fix ordering.
- Continuous raw GNSS CSV session logging on a dedicated IO executor.
- Quality gate: excellent / good / moderate / poor / rejected.
- Repeated-fix point occupation with duplicate suppression, robust outlier rejection and minimum observation time.
- ENU dispersion and 95% horizontal precision ellipse calculated from centered accepted samples.
- Final point metadata is conservative; it does not simply advertise the best instant observed during the occupation.

### Map
- Mapbox Maps SDK Android 11.28.3.
- Standard Satellite and Standard styles.
- Terrain DEM v1 with 3D pitch.
- Survey vertices, boundary, valid polygon fill and live GNSS fix.
- 3D on/off and satellite/map toggles.
- Invalid/self-intersecting boundaries remain visible as warning geometry but are not filled as a valid parcel.
- Map geometry is visual only: it never snaps or moves measured GNSS coordinates.

### Projects / persistence
- Local SQLite project database without a network dependency.
- Multiple land-survey projects.
- Automatic vertex persistence.
- Undo last vertex and clear current survey.
- Foreign keys enabled and DB operations serialized on an IO dispatcher.

### Geodesy
- WGS84 geodetic -> ECEF -> ENU.
- Karney/GeographicLib ellipsoidal distances.
- GeographicLib geodesic polygon area/perimeter.
- Rejection of self-crossing/touching/overlapping parcel boundaries, repeated vertices and near-zero edges.
- GRS80/SIRGAS2000-compatible UTM projection with automatic zone/hemisphere.
- UTM formula checked against EPSG:31983 for a Campanha/MG sample.

Android phone fixes are normally supplied in a WGS84-compatible terrestrial frame. In phone mode the displayed SIRGAS2000/UTM value is an operational approximation appropriate to meter-level phone positioning, not a rigorous centimeter-level frame/epoch transformation.

### Professional RTK foundation
- NMEA GGA parser with RTK FIXED / FLOAT / DGPS / autonomous states.
- Multi-constellation GGA talker handling and checksum validation.
- NTRIP v1/v2 streaming foundation with TLS, classic ICY/HTTP success handling and VRS GGA uplink.
- RTCM 3.x framing, CRC-24Q validation and resynchronization.
- Receiver transport interface ready for Bluetooth/USB implementations.

## Mapbox configuration

No real API credential is committed to the project.

For runtime map loading, provide a public `pk.*` token named `MAPBOX_ACCESS_TOKEN` using one of:

- `local.properties` in the project root: `MAPBOX_ACCESS_TOKEN=pk...`
- Gradle property: `MAPBOX_ACCESS_TOKEN=pk...`
- environment variable: `MAPBOX_ACCESS_TOKEN=pk...`

For resolving the Mapbox Maven dependency, provide the separate secret download token as `MAPBOX_DOWNLOADS_TOKEN` in `~/.gradle/gradle.properties`, the environment, or a GitHub Actions repository secret. Do not commit a secret `sk.*` token.

Without the public runtime token, the app displays a setup placeholder instead of creating the MapView.

## Build / CI

The repository workflow pins:

- JDK 17
- Gradle 9.5.0
- Android Gradle Plugin 9.3.2
- Kotlin/Compose Compiler 2.3.21
- compile/target SDK 37

CI runs unit tests, Android lint and `assembleDebug`. A Mapbox downloads secret is required for dependency resolution.

The current source package does not bundle the Gradle Wrapper JAR; CI uses `gradle/actions/setup-gradle` with the pinned Gradle version.

## Accuracy boundary

Phone-only mode is not centimeter-grade. A 3D basemap improves field context, not GNSS accuracy. Centimeter targets are reserved for an external multi-frequency RTK receiver, valid RTCM corrections, stable FIXED solution, antenna-height handling, control checks and rigorous datum/reference-frame treatment.

## Next engineering milestone

- Bluetooth Classic/BLE and USB serial GNSS receiver adapters.
- NTRIP -> RTCM -> receiver relay service.
- Receiver RTK solution routed through a shared `PositionSource` abstraction.
- FIXED-only professional capture gate, correction age, baseline and antenna-height metadata.
- Offline map regions.
- GeoJSON/KML/CSV/DXF export.
- hgeoHNOR2020 geoid/normal-height pipeline.
- RINEX/PPK workflow and control-monument field validation.
