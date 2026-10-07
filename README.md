# Préstecs — Android app

Native Android client (Kotlin) for the FP Mislata laptop loan application
(*Préstec portàtils*). Teachers use it to scan the QR codes on laptops and
student cards to register loans and returns from their phone, instead of the
web UI.

The app is a frontend only: all data and business rules live in the existing
PHP application (`fpmislata-aplicacions/prestecs`), which it talks to through
its JSON API.

## Features

- **Sign in** with the Moodle account. The app keeps only a Moodle mobile token,
  encrypted with an Android Keystore key; the password is never stored.
- **Loan list**: newest first, 50 per page (more load at the end of the list),
  filter by state (*Prestat*, *No retornat*, *Retornat*), pull to refresh.
- **New loan**: scan a laptop (checked: code format, not already in the batch,
  not already lent) → scan the student card → repeat; then confirm and save the
  batch (up to 50). Rows that fail stay in the list with the reason.
- **New return**: scan laptops one after another; each is looked up at once to
  show who has it. Laptops without an active loan are left out; confirm and save.
- Scanning with the phone camera (CameraX + ML Kit, works offline), beep and
  vibration per scan, flashlight, and a field to type unreadable codes.
- Valencian and Spanish (per-app language on Android 13+), light and dark theme.

## Backend API

Base URL per environment:

| Environment | Base URL |
|---|---|
| Production | `https://www.fpmislata.com/moodle/fpmislata/aplicacions/prestecs/api/` |
| Staging | `https://www.fpmislata.com/moodle/fpmislata/aplicacions_test/prestecs/api/` |
| Local | `http://localhost/www/prestecs/api/` |

| Endpoint | Method | Purpose |
|---|---|---|
| `prestecs.php?estat=&page=&per_page=` | GET | List loans (paginated) |
| `prestecs.php` | POST | Register a batch of loans (`{"rows": [{"portatil", "estudiant"}]}`, max 50) |
| `devolucions.php` | POST | Register a batch of returns (`{"portatils": [...]}`, max 50) |
| `lookup.php?portatil=` | GET | Is this laptop currently lent, and to whom? |

Authentication: `Authorization: Bearer <token>`, where the token comes from
Moodle's `login/token.php` with `service=moodle_mobile_app`. A `401` means the
token expired or was revoked and the user must sign in again.

The full API reference is in the backend repository: `docs/index.html`.

### Data formats

- **Laptop code** (QR sticker): `CARRO - PORTATIL`, e.g. `C1 - P01`.
- **Student card** (QR): JSON `{"version", "nia", "name", "surname", "espec"}`,
  sent to the API as `NIA - SURNAME, NAME`. Older cards and manual entries are
  plain text and are sent as-is.
- **Dates**: `YYYY-MM-DD hh:mm:ss`, Europe/Madrid time.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Retrofit + OkHttp +
kotlinx.serialization · CameraX + ML Kit barcode scanning · DataStore ·
Hilt · Coroutines/Flow.

## Building

Requirements: JDK 17+ and the Android SDK (platform 37). Android Studio
(latest stable) provides both; on the command line, point `local.properties`
to the SDK (`sdk.dir=/path/to/Android/Sdk`) or set `ANDROID_HOME`.

The app has two distribution flavors: `github` (signed APK published on GitHub
Releases) and `play` (bundle for Google Play).

```sh
./gradlew assembleGithubDebug      # debug APK → app/build/outputs/apk/github/debug/
./gradlew testGithubDebugUnitTest  # unit tests
./gradlew lintGithubDebug          # lint
./gradlew spotlessApply            # format Kotlin (ktlint); spotlessCheck to verify
```

Debug builds install as `com.fpmislata.prestecs.debug`, next to the release app.

## Development

### Environments

Release builds always use production. Debug builds start on staging and have
an environment picker on the login screen:

| Environment | Backend | Login |
|---|---|---|
| Producció | production `prestecs/api/` | Moodle account |
| Proves | staging `prestecs/api/` | Moodle account |
| Local | `http://10.0.2.2:8000/www/prestecs/api/` | none (fixed token `mock-ws-token`) |

For **Local**, run the backend from `fpmislata-aplicacions` with
`docker compose up -d aplicacions` (see that repo's README for the first-time
database setup; note that its `bin/setup-dev.sh` recreates the schemas).
`10.0.2.2` is the host machine as seen from the Android emulator; on a real
phone, change the URL in `core/config/Environment.kt` to the PC's LAN address.
Plain HTTP is only allowed in debug builds.

### Code layout

```
app/src/main/java/com/fpmislata/prestecs/
  core/      environments, session and encrypted token, API result/error mapping
  data/      Retrofit services and DTOs, auth and prestecs repositories
  domain/    laptop code and student card QR rules (same as the web)
  ui/        Compose screens: login, loans list, new loan, new return,
             scanner (camera, permission, typed entry), shared batch UI
```

Screens follow the same rules as the web forms (`prestec.js`, `devolucio.js`)
in the backend repository.

## Releases

Release versions come from the git tag: `./gradlew -PappVersion=1.2.3 ...`
gives version name `1.2.3` and version code `10203`, shared by both flavors.

Release builds are signed when these environment variables are set (otherwise
they are built unsigned):

| Variable | Value |
|---|---|
| `PRESTECS_KEYSTORE_FILE` | path to the release keystore (`.jks`) |
| `PRESTECS_KEYSTORE_PASSWORD` | keystore password |
| `PRESTECS_KEY_ALIAS` | key alias |
| `PRESTECS_KEY_PASSWORD` | key password |

Never commit the keystore. The same key must sign every GitHub release and,
later, be uploaded to Google Play (Play App Signing → use existing key), or
installed apps can't be updated. See `PLAN.md` §1.2.

The GitHub APK includes only ARM libraries (all real phones); debug builds
and the Play bundle keep every ABI.
