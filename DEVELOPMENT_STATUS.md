# Development status — GeoMeasure 0.2.2

## Foundation
- [x] Kotlin-native Android project
- [x] Jetpack Compose UI
- [x] Android 10+ minimum
- [x] AGP 9 built-in Kotlin migration
- [x] Compose Compiler plugin aligned to Kotlin 2.3.21
- [x] GitHub Actions: unit tests + lint + debug APK
- [x] Mapbox dependency and token-safe configuration
- [ ] Gradle Wrapper binary committed locally (CI currently pins Gradle 9.5.0 through setup-gradle)

## Phone GNSS
- [x] GPS location callback
- [x] Horizontal/vertical accuracy
- [x] Satellite visibility / used-in-fix count
- [x] Used-in-fix C/N0 telemetry
- [x] Raw GNSS measurement callback
- [x] Per-satellite multi-frequency detection
- [x] Mock-location rejection
- [x] Monotonic fix ordering and stale-fix rejection
- [x] Measurement quality gate
- [x] Android 12+ precise/coarse location requested together
- [x] Survey capture restricted to GOOD/EXCELLENT fixes
- [x] MODERATE/POOR samples rejected during an active occupation
- [x] Multi-observation point occupation
- [x] Duplicate-fix suppression
- [x] MAD-style outlier rejection and ENU dispersion
- [x] Explicit ENU centroid removal before covariance/dispersion
- [x] Final spatial-stability gate: maximum RMS dispersion and 95% sample-cloud ellipse
- [x] 95% sample-cloud ellipse clearly labelled as repeatability, not absolute truth accuracy
- [x] Conservative final point quality/accuracy metadata
- [x] Raw GNSS CSV logger on dedicated IO executor
- [x] Queued RAW events preserved during session shutdown
- [x] Recoverable GNSS startup failure state

## Geodesy
- [x] WGS84 geodetic -> ECEF
- [x] ECEF -> local ENU
- [x] Karney/GeographicLib ellipsoidal distance
- [x] Karney/GeographicLib polygon perimeter and area
- [x] Self-intersection / touching / overlap rejection
- [x] Repeated-vertex and near-zero-edge rejection
- [x] Circular longitude handling for local survey origins
- [x] GRS80/SIRGAS2000-compatible UTM projection
- [x] Automatic UTM zone and hemisphere
- [x] Reference check against EPSG:31983 sample
- [ ] Rigorous WGS84/ITRF -> SIRGAS2000 epoch transformation for centimeter work
- [ ] hgeoHNOR2020 normal altitude

## Projects / storage
- [x] SQLite project database
- [x] Foreign-key enforcement
- [x] Create project
- [x] Reopen project
- [x] Auto-save vertices
- [x] Undo last point
- [x] Clear current survey
- [x] Database work serialized on IO dispatcher
- [x] Explicit migration failure instead of silent schema reinterpretation

## Map
- [x] Mapbox Standard Satellite
- [x] Standard map style
- [x] Mapbox Terrain DEM v1 3D layer
- [x] Survey point annotations
- [x] Boundary polyline
- [x] Polygon fill only for valid boundaries
- [x] Live GNSS position marker
- [x] 3D / map style controls
- [x] Correct camera pitch when toggling 2D/3D
- [x] Reduced annotation rebuild/flicker on metadata-only GNSS updates
- [x] Camera key includes project identity
- [ ] Draw precision ellipse geospatially on map
- [ ] Offline region download UI
- [ ] Production APK with a configured MAPBOX_ACCESS_TOKEN secret

## Professional RTK foundation
- [x] RTK FIXED/FLOAT solution model
- [x] NMEA GGA parser for multi-constellation talkers
- [x] NMEA checksum/coordinate validation
- [x] NTRIP v1/v2 client foundation
- [x] HTTP 200 and classic ICY 200 handling
- [x] TLS socket setup with endpoint verification
- [x] Basic-auth protection on cleartext connections
- [x] VRS GGA periodic uplink
- [x] RTCM3 framing + CRC-24Q
- [x] CRC failure resynchronization
- [x] Generic receiver transport interface
- [x] Android 12+ BLUETOOTH_SCAN/CONNECT manifest permissions prepared
- [ ] Bluetooth receiver implementation
- [ ] USB serial receiver implementation
- [ ] Runtime nearby-device permission flow for RTK screen
- [ ] NTRIP -> RTCM -> receiver relay service
- [ ] Receiver solution -> survey PositionSource
- [ ] FIXED/correction-age capture gate
- [ ] Antenna height / pole height
- [ ] Raw/RINEX persistence
- [ ] PPK workflow

## Validation still required on real infrastructure
- [ ] Latest 0.2.2 GitHub Actions run: unit tests + lint + debug APK
- [ ] Runtime Mapbox 3D rendering on representative Android 10–17 devices with a real public token
- [ ] Real Bluetooth/USB RTK receiver integration
- [ ] NTRIP field test against production caster
- [ ] Accuracy validation against surveyed control monuments
