# Préstecs — Android app

Native Android client (Kotlin) for the FP Mislata laptop loan application
(*Préstec portàtils*). Teachers use it to scan the QR codes on laptops and
student cards to register loans and returns from their phone, instead of the
web UI.

The app is a frontend only: all data and business rules live in the existing
PHP application (`fpmislata-aplicacions/prestecs`), which it talks to through
its JSON API.

## Features

- **Sign in** with the Moodle account (Moodle mobile token, no password stored).
- **Loan list**: paginated, newest first, filterable by state
  (`prestat`, `no-retornat`, `retornat`).
- **New loan**: scan a laptop → check it is available → scan the student card →
  repeat; then confirm and register the whole batch.
- **New return**: scan laptops one after another, see who had each one, then
  confirm and register the batch.

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

Requirements: Android Studio (latest stable), JDK 17, Android SDK 36.

```sh
./gradlew assembleDebug        # build debug APK
./gradlew test                 # unit tests
./gradlew connectedCheck       # instrumented tests (device/emulator needed)
```

Debug builds point to staging by default; release builds point to production.
