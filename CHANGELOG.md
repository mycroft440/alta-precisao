# Changelog

## 0.2.1 — audit correction release

- Migrated build configuration to AGP 9 built-in Kotlin model.
- Corrected GNSS duplicate-fix counting and stale location ordering.
- Added mock-location rejection and recoverable GNSS startup errors.
- Made GNSS snapshot metadata updates atomic.
- Made final occupation quality/accuracy metadata conservative.
- Moved RAW logging off callback thread and preserved queued rows on shutdown.
- Recentered ENU sample cloud before dispersion/covariance calculation.
- Added circular longitude averaging for dateline-safe survey origins.
- Replaced Vincenty/local-area production calculations with GeographicLib/Karney geodesics.
- Added invalid parcel topology detection.
- Hardened NMEA GGA, NTRIP TLS/headers/VRS GGA and RTCM resynchronization.
- Hardened DB threading and migration behavior.
- Reduced Mapbox annotation churn/flicker and fixed 2D/3D camera pitch/project recenter behavior.
- Expanded regression tests around the above defects.
