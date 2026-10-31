# Changes (v1 → v1.1)

**Build/Gradle**
- Added `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9, matches AGP 8.7.3), `gradlew`, `gradlew.bat`.
  *`gradle-wrapper.jar` is missing* (binary; no network) — `gradlew` is a stub, see README.
- Added GitHub Actions workflow to build debug APK in the cloud (untested).

**API**
- Model ID verified: `claude-sonnet-5-5`.
- Errors parsed from API JSON; friendly messages for 401/403/404/429; auto-retry (429/5xx) twice.
- Network errors (IOException) handled separately; history trimmed to 40 entries (never starts with a tool_result).
- Safer tool-call parsing (no `!!` crashes).

**Security**
- API key now encrypted (AES-256-GCM, Android Keystore) before going into DataStore (`SecureBox.kt`).
- Manifest: cleartext traffic disabled, backup disabled, microphone `uses-feature` optional.

**Voice**
- STT language selectable (hi-IN / en-IN), recognizer destroyed after each result, clear error messages
  (permission, network, language pack).
- TTS: Devanagari text → Hindi (India) voice, else English (India); warns once if Hindi voice data missing.
- System prompt asks for Devanagari Hindi when spoken replies are on.

**Phone commands**
- Input validation (alarm 0–23:0–59, timer 1–86400 s, phone number sanitising, empty query), exact-name app match first,
  `ActivityNotFoundException` handled.

**Not done / unverified**: compilation, running on a device, APK generation.


## Google Gemini provider (2026-10-25)
- Added AI Provider choice in Settings: Claude / Google Gemini.
- Added Gemini generateContent REST client and model `gemini-2.5-flash`.
- Provider selection is saved in DataStore; API key remains encrypted using existing SecureBox.
- Gemini supports text chat and the existing speech input/output flow. Phone-action function calling is not implemented for Gemini in this version; use Claude mode for alarms, timers, app launching, search and dialer.
- Build was not run here because the project still lacks `gradle-wrapper.jar` and the launcher is a stub.


## v1.2 (2026-10-25) — Phase 1-3 of the LARA AI plan
**Fixed (found by code reading, not by compiling)**
- `ChatViewModel.kt` used `buildJsonArray` without importing it (would not compile). Imported.
- One shared API key meant switching provider sent the wrong key; keys are now stored per provider.

**Added**
- `SettingsStore`: separate encrypted key + Model ID per provider (old single key is migrated to the provider that was selected when it was saved).
- `GeminiClient.generate()`: function calling (same 5 phone tools as Claude), retry on 429/5xx, clearer errors, model ID configurable. `maxOutputTokens` cap removed (it can starve Gemini 2.5 "thinking" models of output).
- `ClaudeClient.send(..., model)`: model ID configurable (default unchanged).
- Settings dialog: per-provider key, Model ID, **Connection test** (real API call), scrollable.
- Separate conversation history per provider (switching provider starts that provider's own thread).

**Still NOT done (from the LARA AI plan)**: reminders, Home dashboard / navigation, Call Assistant, Message Assistant, notification/quiet-hours/appearance settings, OpenAI provider, Gradle wrapper jar.
**Verification**: nothing here has been compiled or run. No Android SDK/Gradle/network was available.
