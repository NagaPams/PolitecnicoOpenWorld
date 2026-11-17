# English QA Report — Politecnico Open World

Date: 2026-11-16 (America/Mexico_City)
Branch: `qa/english-2026-11-16`
Base commit: `ad6a825ee4e2119c7b9ee07690e55ccddbfe7ec9` plus the existing uncommitted working tree.
Requested destination: https://github.com/NagaPams/PolitecnicoOpenWorld
Scope confirmed by the owner: the local working copy, including Titan del Bienestar, the printer bonus, and the team battle mode.

## Reproducibility and submission

The local branch has 4 commits absent from NagaPams/main and is missing 44 commits present there (comparison performed on 2026-11-16). This is not a clean checkout of NagaPams/main. Existing staged/unstaged/untracked work must be preserved. No merge, commit, push, or pull request was performed by this QA task. A QA result for this working copy must not be presented as a result for the unmodified NagaPams branch.

## Test environment

Windows host; Gradle 9.5.0; Android SDK installed. Pixel_6 AVD is installed; hardware acceleration check passed. No Android device was initially connected. The UI automation inventory timed out. Runtime cases below remain unverified until the emulator boots and the new APK is installed.

## Automated localization audit

- Base strings: 374; English strings: 374.
- Missing English keys: 0.
- Duplicate English keys: 0.
- Format placeholder mismatches: 0.
- Evidence: `localization-audit.json`. Equal keys and placeholders do not prove translation quality or visual fit.

## Findings

### EN-001 — Tutorial action labels are hard-coded in Spanish

Priority: P2. Status: confirmed by source inspection; runtime reproduction pending.
Location: `PolitecnicoOpenWorld/shared/src/commonMain/kotlin/ovh/gabrielhuav/pow/features/streetfighter/viewmodel/StreetFighterTutorial.kt`, function `actionLabel`.
Evidence: labels include `PUÑO LIGERO (X)`, `SALTAR (JOYSTICK ↑)`, and `AGACHARSE (JOYSTICK ↓)` without a language branch in that function.
Steps: select English in Settings; open Combos & Tutorial; start the light punch/jump lessons.
Expected: action instructions appear in English and button highlighting still works.
Source observation: action labels are returned as Spanish literals. Confirm their visible rendering on device before closing this case.
Suggested fix: localize the labels and decouple tutorial highlighting from matching Spanish words; current source comments identify those strings as control/highlight inputs.

## Manual test cases (English interface required)

| ID | Area | Steps | Expected result | Status |
|---|---|---|---|---|
| EN-UI-01 | Language selection | Settings → English; restart the app | Language persists; translated menus use English | Not executed |
| EN-UI-02 | Main menu | Inspect all menu items with English selected | No unintended Spanish fallback, clipping or overlap | Not executed |
| EN-UI-03 | Tutorial | Open controls and each lesson | English instructions, correct buttons and highlighting | Not executed |
| SF-01 | Practice | Select Titan and enter Practice | Correct character, responsive controls, stable body proportions | Not executed |
| SF-02 | Normal attacks | X/Y/B, forward + Y, crouching upper | Standing attacks start standing; no head enlargement or floor displacement | Not executed |
| SF-03 | Specials | Quarter-circle + X/Y/B; R2 with full meter | Full effects, readable motion, consistent anchor and body scale | Not executed |
| SF-04 | Victory | Win a round as Titan | Complete seven-pose skull/petal celebration appears | Not executed |
| SF-05 | Arcade bonus | Beat the second opponent | Printer bonus starts, English instructions, score and continuation work | Not executed |
| SF-06 | Team mode | Select two teams of three; finish a bout | Correct roster, transitions, remaining fighters and English results | Not executed |
| PERF-01 | Performance | Play Practice and Arcade with FPS enabled | Record device model, sustained FPS and input latency; no unmeasured pass claim | Not executed |
| COMP-01 | Android 6 | Install and launch on API 23 | App starts and core flows work without unsupported API crashes | Not executed |

## Current assessment

This document is a QA record, not a release approval. Runtime English-interface coverage and gameplay checks are still open. Prior results from earlier chats are not counted as a fresh run. Automated build/test results will be recorded separately when the current run completes.

## Current automated execution

Gradle finished with BUILD SUCCESSFUL. Tasks: `:shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug`. Test tasks were UP-TO-DATE and reused results for unchanged inputs; they were not all freshly executed.

- shared: 249 tests, 0 failures, 0 errors, 0 skipped.
- app: 129 tests, 0 failures, 0 errors, 0 skipped.

Evidence: `automated-results.json`, including the generated APK hash. Re-run localization checks with `python QA/english-2026-11-16/audit_english_resources.py` from the repository root.

Runtime attempt: the original emulator became unresponsive and was closed. A second cold boot using software graphics is being checked; do not count connection detection as a completed UI test.

## Fighter atlas audit
The existing audit tool checked 20 dedicated fighter atlases and returned exit code 0 with no hard errors. Ten warnings were reported: eight repeated-frame pairs in Alexis Syntaxis, unused bonus frames in La Presidenta, and a missing BONUS_POWER_3 animation in La Tzitzimime. These are source/asset findings, not proof that their gameplay is visually correct.

### SF-002 — La Tzitzimime has no BONUS_POWER_3 animation
Priority: P2 candidate; runtime impact pending reproduction. Steps: select La Tzitzimime, cycle the P button to the third bonus power, and activate it. Expected: a matching animation and responsive recovery. Asset audit observation: missing BONUS_POWER_3. Confirm whether the engine intentionally gates or substitutes this state before treating it as a player-facing failure.

Branded Spanish game/mode names are not automatically treated as translation defects; product naming policy needs to be respected.

## Final status: BLOCKED for runtime English UI QA
Automated localization checks, build and unchanged-input unit-test results passed. The Pixel_6 API 33 emulator booted on the second attempt, but installation of the 409,487,587-byte APK did not complete during the observed QA window. Package checks did not confirm the app installed. Storage was not exhausted (approximately 8.4 GB available). Windows UI automation also timed out twice. The test emulator was stopped after these unsuccessful attempts. No English-screen or gameplay case is marked passed.

Resume steps: start a responsive emulator or connect an Android device; install PolitecnicoOpenWorld/app/build/outputs/apk/debug/app-debug.apk; select English in Settings; execute the manual cases above and attach screenshots and device/FPS details. Do not release based on this report alone.

Suggested PR title: QA: English localization audit and gameplay test matrix
Suggested PR summary: Records automated resource checks, build/test evidence, reproducible English UI and gameplay cases, and outstanding localization/asset findings for the local Titan/team-mode working copy. Runtime validation remains blocked by the emulator installation environment.
