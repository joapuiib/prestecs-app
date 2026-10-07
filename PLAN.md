# Implementation plan — Préstecs Android app

Source of truth for behaviour: the web app in `~/fpmislata-aplicacions/prestecs`
(`index.php`, `prestec/javascript/prestec.js`, `devolucio/javascript/devolucio.js`)
and its API docs (`docs/index.html`). The app must reproduce the web flows,
adapted to a phone camera instead of a USB/Bluetooth QR scanner.

## 1. Decisions

| Topic | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, single Activity | Navigation Compose |
| Min / target SDK | minSdk 26, target/compile 37 (Android 17) | |
| Architecture | MVVM + unidirectional state (`StateFlow<UiState>`), repository layer | Single `:app` module to start; split later if it grows |
| DI | Hilt | |
| Networking | Retrofit + OkHttp + kotlinx.serialization | One interceptor adds `Authorization: Bearer` |
| Token storage | DataStore, value encrypted with an Android Keystore AES/GCM key | Never store the password |
| QR scanning | Phone camera only: CameraX + ML Kit barcode scanning (bundled model, QR only) | No hardware-scanner support. Manual text entry kept only as fallback for damaged stickers/cards |
| Environments | `prod` / `staging` / `local` as `BuildConfig` fields; release = prod only, debug can switch | See §1.1 |
| App ID | `com.fpmislata.prestecs` | |
| Batch size | Hard limit of 50 rows per batch, loans and returns | Same as the API limit; no automatic splitting |
| Distribution | Signed APK on GitHub Releases now; built so Google Play can be added later without a reinstall | See §1.2 |
| UI language | Valencian (`values/`, default) and Spanish (`values-es/`), both in v1 | Mirror `lang/ca.yml` / `lang/es.yml`; per-app language picker (Android 13+ `locales_config`) |

### 1.1 Environments

| Env | API base URL | Login |
|---|---|---|
| prod | `https://www.fpmislata.com/moodle/fpmislata/aplicacions/prestecs/api/` | Moodle `https://www.fpmislata.com/moodle/login/token.php` |
| staging | `https://www.fpmislata.com/moodle/fpmislata/aplicacions_test/prestecs/api/` | Same Moodle `token.php` (staging shares the production Moodle) |
| local | `http://10.0.2.2:8000/www/prestecs/api/` (emulator → host; on a real phone use the PC's LAN IP) | **No Moodle.** Locally the backend uses `MockMoodleProvider`, which accepts only the fixed token `mock-ws-token`. The app skips the login screen and uses that token |

Local backend = `docker compose up aplicacions` in `fpmislata-aplicacions` (XAMPP,
host port `8000`). Cleartext HTTP is allowed only in the debug build
(`network_security_config` in `src/debug/`), only for `10.0.2.2` and LAN hosts.

### 1.2 Distribution: GitHub APK now, Google Play later

Product flavor dimension `distribution` with two flavors, so switching is a
build choice and not a code change:

| | `github` (now) | `play` (later) |
|---|---|---|
| Output | Signed APK, attached to a GitHub Release | AAB uploaded to Play Console |
| Updates | In-app check against the latest release (`version.json` / Releases API); on a newer `versionCode`, a dialog opens the APK download link in the browser | Play In-App Updates API (`app-update-ktx`). Self-updating outside Play is against Play policy, so the GitHub checker is **not** compiled into this flavor |
| Code | `src/github/` implements `UpdateChecker` | `src/play/` implements `UpdateChecker` |

Things that make the later switch seamless:

- **Same `applicationId`** (`com.fpmislata.prestecs`) and **same signing key** in both
  channels. When enrolling in Play App Signing, upload the existing key (Play
  Console → "Use existing app signing key", exported with PEPK) instead of letting
  Google generate one. Otherwise Play builds can't update sideloaded installs and
  teachers would have to uninstall first.
- The release keystore is created once, backed up offline (two copies), and stored
  in GitHub Actions secrets (base64 keystore + passwords). Never committed.
  Losing it means no more updates for installed apps.
- **`versionCode` always increases**, derived from the tag (`v1.2.3` → `10203`),
  shared by both channels. `versionName` = tag without `v`.
- CI builds both `githubRelease` APK and `playRelease` AAB on every tag, so the
  Play build is tested before it is needed.
- **Repo visibility**: `joapuiib/prestecs-app` is public, so release APKs can be
  downloaded without login and the update checker reads
  `https://api.github.com/repos/joapuiib/prestecs-app/releases/latest` with no
  token. The URL is a `BuildConfig` field so it can be pointed elsewhere later.
  Never commit the keystore, `local.properties`, or any credentials.
- Play requirements prepared from the start: target the current required SDK,
  privacy policy page (camera use, Moodle token, no analytics), Data safety
  answers written down in `docs/play-store.md`, no `REQUEST_INSTALL_PACKAGES`
  permission (the GitHub updater uses the browser, not an in-app installer).

## 2. Domain rules to port from the web app

- **Laptop code** valid iff it contains `" - "`; trim input. Carro = part before the separator.
- **Student QR**: if it starts with `{` and parses as JSON with `nia`, `name`,
  `surname` → `"$nia - $surname, $name"`; otherwise use the trimmed raw text.
- **Loan flow** (per row):
  1. Scan laptop. Reject if invalid format or already in the current batch.
  2. `GET lookup.php` → if `found`, reject ("Ja prestat a {estudiant}"); else accept.
  3. Scan student card. Reject empty, and reject if it equals the laptop code
     ("Això és el portàtil, escaneja el carnet de l'estudiant").
  4. Add row to batch, go back to step 1.
  - Rows can be removed; "cancel" resets the pending laptop.
- **Return flow**: each scanned laptop (deduplicated) is added and looked up in
  parallel; `found` → show student + loan date; not found / error → mark red
  and **exclude** from submission. Submit is disabled while lookups are pending.
- **Batch limit (loans and returns)**: max 50 rows, the API limit. At 50 the
  scanner stops accepting codes and shows "Màxim 50 per lot: guarda abans de
  continuar". No automatic splitting.
- **Submit**: confirmation dialog with count → POST batch → show `messages` (use `code` for logic, `message` for display).
  On `200` clear the batch; on `422` keep only the failed rows (by `portatil`/`row`)
  so the user can fix or discard them.
- **Errors**: `401` → clear token, go to login. `403` → "no permission" screen.
  `500` → show `error_id` so it can be reported. Network error → retry option.
- Estat (`prestat` / `no-retornat` / `retornat`) is computed server-side; dates are
  Europe/Madrid and shown as-is (parse with `java.time` for formatting only).

## 3. Screens

1. **Login** — username + password → `POST {moodle}/login/token.php`
   (`service=moodle_mobile_app`); handle `error` in the response. Env picker in debug.
2. **Loans list (home)** — paginated list (Paging 3, `per_page=50`), estat filter
   chips (multi-select → comma list), pull to refresh, colour per estat like the web
   table. FABs/buttons: *Nou préstec*, *Nova devolució*. Menu: logout, about/env.
   - The API only filters by `estat`; carro/text search would be client-side over
     loaded pages only → leave out of v1.
3. **New loan** — camera preview on top, two-step state indicator
   (laptop → student, yellow = waiting, green = done, as in the web), manual entry
   field with ⏎, batch list below with remove buttons, *Guarda* button.
4. **New return** — camera preview, continuous scanning, list with lookup result per
   row, *Guarda* button.
5. **Result sheet** — shared bottom sheet listing response messages by severity.

Scanner UX: debounce identical consecutive reads (~1.5 s), haptic + sound on
accept/reject, torch toggle, keep screen on. A small "type it" button opens a
text field as fallback for unreadable QR codes.

## 4. Project layout

```
app/src/main/java/com/fpmislata/prestecs/
  PrestecsApp.kt, MainActivity.kt
  core/        config (environments), di, network (Retrofit, auth interceptor,
               error mapping → sealed ApiResult), storage (TokenStore)
  data/        api DTOs, PrestecsApi, MoodleAuthApi, repositories
  domain/      PortatilCode, StudentQrParser, Estat, batch models
  ui/
    theme/, components/ (ScannerView, MessageList, EstatChip, ConfirmDialog)
    login/, loans/, loan/ (new loan), returns/
    navigation/
```

## 5. Milestones

Each milestone ends in a working, reviewable state (one PR / commit series each).

1. **Project skeleton** — Gradle (version catalog, KTS), Compose, Hilt, theme,
   navigation stub, `.gitignore`, CI (GitHub Actions: build + unit tests + lint).
2. **Networking & auth** — DTOs, Retrofit services, auth interceptor, error mapping,
   TokenStore, Login screen, 401 → logout handling. Tests with MockWebServer.
3. **Loans list** — Paging source, estat filters, refresh, empty/error states.
4. **Scanner component** — CameraX + ML Kit composable, camera permission flow,
   debounce, torch, manual entry fallback.
5. **New loan flow** — ViewModel state machine (WaitingPortatil → CheckingPortatil →
   WaitingEstudiant), batch, confirm, submit, 422 handling. Unit-test the state machine.
6. **New return flow** — parallel lookups, exclusion of not-found rows, submit.
7. **Polish** — Spanish strings, accessibility (TalkBack labels, contrast), dark
   theme, app icon, R8 rules, release signing config, README update.
8. **Release** — keystore generation and backup, GitHub Actions workflow on
   tag `v*`: test → build `githubRelease` APK + `playRelease` AAB → sign → create
   GitHub Release with the APK and `version.json`. Staging build for testing first,
   then production.

## 6. Testing

- Unit: `PortatilCode`, `StudentQrParser`, API error mapping, both ViewModels
  (Turbine + coroutines-test, fake repositories).
- Integration: repositories against MockWebServer using the JSON samples from
  `docs/index.html` (200, 422, 400, 401, 403, 500).
- UI: Compose tests for login and the loan/return screens with fake scanner input.
- Manual: against staging with real QR stickers and student cards.

## 7. Open questions

None at the moment.
