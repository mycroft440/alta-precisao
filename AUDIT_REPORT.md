# GeoMeasure 0.2.1 — audit correction report

This release is the corrected result of the full-code review performed after 0.2.0.

## High-impact defects corrected

1. **Duplicate GNSS fixes counted as new samples** — satellite/raw callbacks could make one position look like many observations. Occupation now advances only on a new monotonic location fix.
2. **Stale location overwrite** — old cached fixes can no longer replace a newer fix.
3. **Mock location accepted** — simulated locations are now explicitly rejected by the measurement quality gate.
4. **Optimistic occupation quality** — final metadata is now conservative across accepted observations.
5. **RAW logger callback IO** — disk writes are off the GNSS callback thread and queued rows drain safely on stop.
6. **Fragile geodesics** — production distance/area uses Karney/GeographicLib instead of relying on Vincenty/local planar area.
7. **Invalid parcel polygons reported as valid** — self-intersection, non-adjacent touch/overlap, repeated vertices and near-zero edges are rejected before area output.
8. **NTRIP/TLS issues** — socket wrapping, endpoint identification, HTTP/ICY handling, credentials policy and VRS GGA uplink were hardened.
9. **NMEA GGA compatibility/validation** — common multi-GNSS talkers and stricter coordinate/checksum validation are supported.
10. **RTCM corruption recovery** — framing resynchronizes one byte at a time after invalid candidates.
11. **Database threading/migration safety** — DB work is serialized on IO and unexpected schema upgrades fail explicitly.
12. **Map lifecycle/performance** — annotations are not rebuilt on every telemetry update, invalid polygons are not filled, and 2D/3D camera pitch/recentering were corrected.
13. **AGP 9 configuration** — removed the legacy Android Kotlin plugin pattern in favor of AGP 9 built-in Kotlin configuration.

## Validation performed in this environment

- RTK/NTRIP/NMEA/RTCM pure JVM sources: compile pass.
- Local fake NTRIP caster: HTTP 200 pass.
- Local fake NTRIP caster: ICY 200 pass.
- Android manifest/resources: XML parse pass.
- GitHub Actions workflow: YAML parse pass.
- Source scan: no remaining `TODO`, `FIXME`, `0.2.0` release labels, legacy Vincenty documentation, or the old vertical `±` accuracy presentation.

## Validation that still requires external environment/hardware

- Full AGP/Android compilation with Android SDK and Mapbox download credentials.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` in the prepared GitHub Actions workflow.
- Runtime map/terrain testing on Android hardware.
- GNSS behavior across real single/dual-frequency chipsets.
- Real NTRIP caster + Bluetooth/USB RTK receiver integration (the receiver adapters/relay are the next development milestone).
- RTK FIXED field comparison against surveyed control monuments.

No centimeter-accuracy claim is made for phone-only GNSS.
