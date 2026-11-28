# Reference integration plan

This branch evaluates six public Android location projects and records a license-aware integration plan for LocationSp_Next. The goal is a coherent, maintainable location simulation and QA tool, not a collection of unrelated hooks.

## Reference review

| Project | License observed in repository | Useful design ideas | Integration treatment |
|---|---|---|---|
| [Lecheeel/Portal](https://github.com/Lecheeel/Portal) | Apache-2.0 | Layered location backends, runtime status, route controls, GNSS/NMEA domain separation, diagnostics | Reuse architectural ideas; independently implement against the existing backend interfaces |
| [Cobylinweiqi/FakeLoc](https://github.com/Cobylinweiqi/FakeLoc) | MIT | Coordinate paste parsing, common map-link formats, signal parameter presets, clear configuration UX | Add an independent coordinate parser and tests; preserve source coordinate-system ambiguity rather than guessing |
| [Elysia-SHY/FakeGPS-next](https://github.com/Elysia-SHY/FakeGPS-next) | Apache-2.0 | Per-app configuration model, permission coordination, offline motion simulation, structured release/update metadata | Adopt explicit routing/configuration boundaries and testable motion concepts where compatible with the existing architecture |
| [cxOrz/anywhere](https://github.com/cxOrz/anywhere) | GPL-3.0 | Lightweight interaction patterns and map-first workflow | Reference only; do not copy or derive code into this Apache-2.0 repository |
| [noellegazelle6/kail_location](https://github.com/noellegazelle6/kail_location) | GPL-3.0 (declared in README) | Broad feature organization, environment data management and documentation structure | Reference only; do not copy or derive code into this Apache-2.0 repository |
| [languang666/geoshift](https://github.com/languang666/geoshift) | Apache-2.0 | Pure-Kotlin domain core, deterministic simulation clock, explicit process boundaries, test/probe separation, offline route preparation | Adopt compatible architectural patterns through independent implementation; retain attribution and review notices before any source reuse |

License status is a preliminary repository-level review, not legal advice. Before copying any source file, inspect that file's copyright header, bundled dependencies, NOTICE files, and current license. GPL-3.0 code is intentionally excluded from this Apache-2.0 codebase unless the project owner separately chooses and documents a compatible relicensing strategy.

## Product principles

1. Keep coordinate math, route geometry, simulation time, and validation in pure Kotlin modules with JVM tests.
2. Keep map SDKs, Android services, storage, and Xposed integration behind adapters. A map provider must not leak its coordinate system into persisted domain data.
3. Make the selected coordinate system explicit at import boundaries. Never silently interpret ambiguous pasted coordinates as WGS-84, GCJ-02, or BD-09.
4. Keep the standard Android mock-location provider as the predictable baseline. Optional system integration must remain separately configured, observable, and reversible.
5. Use one immutable scenario snapshot for a running session. Editing saved content must not mutate a session already in progress.
6. Treat diagnostic data as first-class: show backend, permission state, last update, dropped samples, and stop/cleanup outcomes.
7. Prefer offline playback once a route is saved. Network routing and geocoding are optional preparation steps, not runtime dependencies.
8. Keep app-scoped configuration explicit and default to affecting only the app/session selected by the user.

## Integration backlog

### P0 — input and data correctness

- [x] Pure-Kotlin parser for decimal coordinate pairs, geo: URIs, Google Maps @lat,lng links, !3d/!4d links, and common coordinate query parameters.
- [x] Reject non-finite and out-of-range coordinates; preserve input values without guessing a datum conversion.
- [x] Wire parser into the coordinate-entry UI with a clipboard paste action and localized feedback. A coordinate-system selector remains required before any automatic datum conversion.
- [ ] Add GPX import/export with GPX 1.1 validation, segment preservation, timestamps, elevation, and bounded file sizes.
- [ ] Add versioned JSON backup migration tests, partial-import preview, and atomic restore/rollback.
- [ ] Add duplicate-name handling, route metadata, tags, favorites, and stable ordering.

### P1 — route authoring and playback

- [ ] Unify map tap, address search, waypoint list, and freehand drawing into one route-draft model.
- [ ] Add undo/redo, draft autosave, route simplification with a documented error tolerance, and elevation profile display.
- [ ] Add deterministic walk/cycle/drive motion profiles with acceleration, deceleration, dwell points, and a reproducible random seed.
- [ ] Add pause/resume/seek/speed control and explicit single-run, loop, and out-and-back modes.
- [ ] Add dateline, polar, duplicate-point, zero-length, and very-long-route regression coverage.

### P2 — scenario and test tooling

- [ ] Introduce a scenario bundle containing start point, route, playback profile, coordinate-system metadata, and expected checkpoints.
- [ ] Add a local test receiver/sample app that reports Android mock-provider metadata and callback timing for QA.
- [ ] Add a replay report comparing expected route samples with observed callback samples (distance, time drift, dropped callbacks).
- [ ] Add backend capability diagnostics and a clear stop/restore state machine.
- [ ] Add accessibility checks, UI tests, and Android-version/ROM compatibility matrix.

### Explicit non-goals

This integration does not add stealth, mock-indicator removal, third-party app anti-detection bypasses, hidden device-fingerprint changes, or covert alteration of Wi-Fi/cell/Bluetooth identity. The product remains a transparent location-testing tool using supported Android mock-location interfaces and explicit user-selected test scopes.

## Current branch changes

- CoordinateInputParser.kt: independent, pure-Kotlin parser for common coordinate text and map links.
- CoordinateInputParserTest.kt: parser regression tests for supported formats, malformed input, and range validation.
- CoordinateInputCard.kt: clipboard import action with localized English, Chinese, and Arabic feedback.

No reference repository's source files have been copied into this branch.
