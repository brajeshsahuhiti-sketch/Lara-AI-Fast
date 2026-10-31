# Mera Assistant — Galaxy S24 AI Assistant (Kotlin + Jetpack Compose)

> **Build status: NOT built/tested.** Ye project ek aise environment me fix kiya gaya jisme Android SDK,
> Gradle aur internet nahi tha. Isliye koi APK nahi bana aur code compile nahi hua. Neeche ke steps se
> aap apne PC par build karoge; pehle build par koi chhota error aaye to wo bhej dena.

## Requirements
- Android Studio Ladybug (2024.2) ya naya (JDK 17 ke saath aata hai)
- Galaxy S24 (Android 14+), USB debugging on (Settings → About phone → Software information → Build number 7 baar tap → Developer options → USB debugging)

## Build & install (Android Studio — sabse aasaan)
1. ZIP extract karo, Android Studio → **Open** → `AIAssistant` folder.
2. Gradle sync khud Gradle 8.9 download karega (`gradle/wrapper/gradle-wrapper.properties` se). Sync ke baad SDK Platform 35 install karne ko bole to Accept karo.
3. S24 USB se jodo, "Allow USB debugging" accept karo, upar device chuno, **Run ▶**.
4. Ya APK banao: **Build → Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`; phone par copy karke install karo ("Install unknown apps" allow karna padega).

## Command line (optional)
`gradle-wrapper.jar` binary hai aur offline nahi ban saka, isliye `./gradlew` abhi stub hai. Ek baar:
```
gradle wrapper --gradle-version 8.9      # Gradle 8.9+ installed ho
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## PC ke bina APK (GitHub Actions)
Project ko GitHub repo me push karo → Actions → **Build debug APK** → Run workflow → artifact `app-debug` download karo.
(Workflow `.github/workflows/build-apk.yml` likha hai, par abhi chala ke test nahi kiya.)

## AI provider aur API key
- App → ⚙️ Settings → AI Provider me **Claude** ya **Google Gemini** select karo.
- Gemini key: https://aistudio.google.com/apikey se banao. Claude key: https://console.anthropic.com/ se banao.
- Har provider ki key aur Model ID alag save hoti hai; provider badalne par key dobara paste nahi karni padti. Model ID khud badal sakte ho.
- Settings me **Connection test** button asli API call karta hai; ✅ tabhi dikhta hai jab provider ne sach me jawab diya. Error ho to wajah (401/403/404/429) saaf likhi aati hai.
- Key AES-256-GCM se encrypt hokar save hoti hai; encryption key Android Keystore me rehti hai. Key kabhi source code ya Git me mat daalo.
3. Key leak ho jaye to console se turant revoke karo.
4. Dhyan: phone root ho ya app reverse ho to key phir bhi nikal sakti hai. Personal use ke liye theek hai; Play Store/public app ke liye key backend proxy me rakho.

## Hindi voice setup
- Bolna (STT): Settings me bhasha "हिन्दी" chuno. Phone me Google voice typing ka Hindi pack chahiye.
- Sunna (TTS): Phone Settings → General management → Text-to-speech output → Hindi (India) voice data download karo.
- App Hindi jawab Devanagari me likhwata hai taaki voice sahi bole.

## Supported phone commands
Claude aur Gemini dono me: alarm, timer, app kholna (naam se), Google search, dialer (number bhar ke; call aap dabate ho).
Gemini me ye Gemini function calling se chalta hai (code likha hai, par device par abhi test nahi hua).
Example: "सुबह 6:30 का अलार्म लगाओ", "10 minute ka timer", "YouTube kholo".

## Models
- Gemini: `GeminiClient.MODEL = "gemini-2.5-flash"`
- Claude: `ClaudeClient.MODEL`
