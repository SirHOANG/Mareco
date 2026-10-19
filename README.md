# Mareco - Mobile Operation Recorder

Free, no-ads macro recorder for Android, modelled on the "Operation Recorder" of emulators such as
MuMu / LDPlayer / BlueStacks, with a floating control bar for game AFK grinding.

**Record -> Stop -> Configure -> Execute.**
Record your taps, long presses, swipes, drags, multi-finger gestures and Back / Home / Recents presses,
name the macro, tune its execution settings (loops, interval, speed, simulate ...), press play.

Zero third-party libraries, no internet permission, about 4 MB (most of it is the 100 translations).
Speaks **101 languages** and starts in the language of your phone (section 8).

---------------------------------------------------------------------------------------------------

## 1. Build it (Windows)

| Tool | Version used |
|---|---|
| Android Studio | **Ladybug (2024.2.1)** or newer |
| Android Gradle Plugin | **9.1.0** (`gradle/libs.versions.toml`) |
| Gradle | **9.3.1** (`gradle/wrapper/gradle-wrapper.properties`) |
| JDK | 17+ (Android Studio's bundled JDK is fine) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 (Android 8.0+) |
| Kotlin | AGP 9 built-in Kotlin (do NOT add the `kotlin-android` plugin) |

### Option A - Android Studio
1. **File > Open** and choose this folder (the one that contains `settings.gradle.kts`).
2. Wait for **Gradle sync**. If Studio offers to install *Android SDK Platform 37* or *Build Tools 36.0.0*, accept.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. APK: `app\build\outputs\apk\debug\app-debug.apk`

### Option B - command line
```bat
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat assembleRelease     (minified; signed with the debug key so it installs right away)
```

### Option C - build in the cloud (best for low-RAM PCs)
Push the folder to GitHub, open **Actions > Build APK > Run workflow**, download the `Mareco-apk` artifact.
(`.github/workflows/build-apk.yml`)

### If your Android Studio is older than Ladybug
AGP 9.1 needs a recent Studio. Update Studio (Help > Check for Updates) - that is the clean fix.
Alternatively lower `agp` in `gradle/libs.versions.toml` to a version your Studio supports and set the
matching Gradle version in `gradle-wrapper.properties`.

### Low RAM
`gradle.properties` caps the Gradle heap at 2 GB. Close other apps while syncing, or use Option C.

---------------------------------------------------------------------------------------------------

## 2. Install and set up on the phone

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
(or copy the APK to the phone and open it). If the install says *signature mismatch*, uninstall any older
Mareco build first.

1. Open Mareco. The Home screen shows **Setup required**; tap it.
2. **Open Android Settings > Accessibility > Installed apps > Mareco > On.**
3. Switch greyed out? Android 13+ blocks sideloaded apps: **App info > (three dots) > Allow restricted settings**
   (the Setup screen has an *Open App info* button), then enable the service.
4. Back in Mareco: **Start Recording** (or **Show floating controls**).

Why accessibility? It is the only way Android lets an app tap and swipe for you (`dispatchGesture`) and draw
floating controls without "Display over other apps". Mareco cannot read the screen and has no internet permission.

---------------------------------------------------------------------------------------------------

## 3. Using the floating bar

The bar has four controls:

| Button | What it does |
|---|---|
| **X** | Closes the floating bar. Bring it back with **Start Recording** / **Show floating controls** in the app. It is dimmed and refuses while a recording or a macro is running (stop first). |
| Blue **record / play / stop** | *Record macro* mode shows the **record button ●** (blue ring, red dot): press it to start recording, then **■** stops it and asks for a name ("Record completed, saved as:"). *Start macro* mode shows **▶**: it runs your default macro, **■** stops it. After you save a recording the bar switches to Start macro mode (▶). |
| **Gear** | Mode switch (Record macro / Start macro), default macro, execution settings, all app settings (including Language). |
| Four arrows | Drag to move the bar. |

Hold the blue button to **pause / resume** (while recording or running); hold it while paused to stop
(a recording is saved). Long-press any button to see its name. Tap the small status text under the buttons for a summary.
While running, the status shows loop `24/inf` and the speed; while recording, `REC 00:13`.

**Start Recording in the app only shows the floating bar - it does not record.** Open your game first, then press the
blue **●** record button whenever you are ready; the 3-2-1 countdown (if enabled) runs after that.

**First time:** Home > **Start Recording** > open your game > blue **●** > do your actions > blue **■** > name it > *Save*.
Then Gear > *Start macro* > pick it (it becomes the default) > the sliders icon opens **Execution Settings** > Save > blue **▶**.

**Emergency stop:** the blue **■**, the STOP button in the app, or press **Volume Up + Volume Down together**.
Mareco also stops if the screen turns off, the accessibility service is switched off, the battery gets low
(optional), or a time / execution limit is reached (optional).

### Execution Settings (per macro)
Stop condition (infinitely / N times / for a set time), loop interval, speed (0.5x - 10x, presets
Slow / Normal / Fast / Custom), auto-execute upon starting the device, simulate execution (default on).
*More options*: action delay original/custom, randomized delay, humanize taps, coordinate scaling
(original / scale / keep aspect ratio), "only run while the recorded app is open".

### Live vs Plan recording
*Live* (default): your touches are recorded on a transparent layer and each finished gesture is replayed to the
app immediately, so the game reacts while you record. *Plan*: touches are only recorded.

---------------------------------------------------------------------------------------------------

## 4. Test it stage by stage (on a real phone)

1. **Setup** - Home shows no warning after enabling the service; floating bar appears.
2. **Bar** - drag it; long-press buttons; gear panel opens; switch modes; **X** closes the bar and Home brings it back.
3. **Record** - Home > Start Recording: the bar appears and NOTHING is recorded. Open a game, press the blue **●**, record
   3 taps and a swipe, press blue **■**: the name prompt appears; Save.
4. **Replay** - Start macro mode, blue **▶**, watch it repeat; blue **■** stops it.
5. **Settings** - speed 2x, loop interval 2 s, count mode with 3 runs.
6. **Editor** - My Macros > pencil: edit a delay, move a step, delete a step, add a wait.
7. **Share** - export / import a `.mareco` file; open it from another app.
8. **Limits** - maximum run time, Volume Up + Down, screen off.
9. **Language** - change the phone's language in Android Settings > System > Languages: Mareco follows it. In Mareco,
   Settings > Language (or the globe on Home) switches the language at once, bar included; *System default* goes back.

---------------------------------------------------------------------------------------------------

## 5. Android limitations (read this)

* Apps cannot watch touches that go to other apps. Mareco records on a transparent layer and, in Live mode,
  replays each gesture right after your finger lifts. A new touch during that split second can be missed:
  avoid extremely fast repeated taps while recording, or use Plan mode.
* Secure screens (passwords, many banking apps) and apps that block accessibility tools ignore injected gestures.
* Only Back / Home / Recents can be recorded as button presses (Android global actions). Volume etc. cannot be replayed.
* Nothing runs while the screen is off or locked. Mareco keeps the screen on while a macro runs.
* Record and replay in the same orientation; Mareco scales for different screens and warns.
* Multi-finger gestures: up to 10 fingers.
* Not included (the spec lists them as optional / later): action groups, "wait until screen changes" conditions.
  Not possible without extra privileges: key injection, reading the screen.

---------------------------------------------------------------------------------------------------

## 6. Project map

```
app/src/main/java/com/mareco/app/
  Lang.kt, Languages.kt   the chosen language (follows the phone by default) and the list of 101 languages
  data/     Models, MacroCodec (.mareco JSON), MacroRepository (files), AppSettings
  core/     TraceRecorder, GesturePlanner, MacroRunner, Scaler, Timing, Fmt, Platform,
            BarSymbols (which symbol the bar's main button shows: ● / ▶ / ■)   (pure logic, unit-tested)
  service/  MarecoAccessibilityService, MarecoController (brain), OverlayManager, ToolbarView,
            CaptureView / MarkerView, OverlayPanels (gear panel)
  ui/       MainActivity + screens (Home, Setup, My Macros, Editor, Settings, Help), Dialogs, SettingsView,
            MacroRow, PromptActivity (name prompt), UI toolkit (Ui.kt)
  share/    MacroShareProvider (share a .mareco file)
app/src/main/res/values/strings.xml          the English texts (364 strings, the source of every translation)
app/src/main/res/values-xx/strings.xml       one folder per language (vi, es, zh-rCN, pt, in, iw ...)
app/src/main/res/xml/locales_config.xml      the language list Android 13+ shows under "App languages" (generated)
app/src/test/   64 JVM unit tests (engine, planner, recorder, codec, repository, bar symbols, translations)
tools/          i18n.py (translation checker / generator) + languages.tsv (master list of languages)
docs/MARECO_REQUIREMENTS.txt   your whole prompt + checklist
```

The `.mareco` file is plain JSON (see the spec example); `MacroCodec` documents every field.

---------------------------------------------------------------------------------------------------

## 7. Troubleshooting

* **Sync fails: "AGP requires a newer Android Studio"** - update Studio (see section 1).
* **Sync fails: SDK platform / build tools missing** - accept Studio's install prompt, or SDK Manager > install Android 17 (API 37) and Build Tools 36.0.0.
* **Floating bar missing** - Home > *Start Recording* or *Show floating controls*; make sure the service is on (Setup). You may have closed it with the **X**.
* **Wrong language** - Settings > Language > *System default* follows the phone again; or pick the language you want from the list (section 8).
* **Taps do nothing in a game** - some games block accessibility gestures; try *Plan* off/on, check Help > limitations.
* **Macro stops by itself** - read the toast text (time limit, battery, app left the front, screen off ...).
* **Service switches itself off on some phones** - set Mareco's battery usage to *Unrestricted* and lock it in recents.
* **"Mareco has stopped" / the app closes** - open Mareco again. A **Mareco crashed** screen shows the exact error with *Copy* and *Share* buttons (it is also saved to `files/last_crash.txt`). Send that text and the cause can be fixed in one step.
* **Install says "App not installed" / signature mismatch** - an older Mareco build was signed with a different key. Uninstall it first (Settings > Apps > Mareco > Uninstall), then install again. Your macros live inside the app, so export the ones you want to keep first.
* **Building the APK yourself** - always use Gradle / Android Studio (sections 1A-1C). Gradle's D8 step converts Kotlin's lambdas into a form every Android version can run; a hand-assembled dex without that step can crash on launch.

---------------------------------------------------------------------------------------------------

## 8. Languages

Mareco speaks **101 languages**: English plus 100 translations.

* **The default is your phone's language.** If Android is set to Vietnamese, Mareco opens in Vietnamese - nothing to
  set up. If the phone's language is not in the list, Mareco shows English (like every Android app does).
* **Change it:** *Settings > Language* in Mareco (or the globe button on Home). Pick a language by its own name; the app,
  the floating bar and the gear panel switch immediately. *System default* goes back to following the phone.
  On Android 13+ you can also use Android Settings > Apps > Mareco > Language.
* Right-to-left languages (Arabic, Hebrew, Persian, Urdu, Pashto, Sindhi, Uyghur) mirror the whole layout.
* **Translation quality - please read.** English is the original text. Every translation is an AI-written first draft
  (Vietnamese got the most careful pass). They are checked automatically (every text present, same `%s` placeholders,
  same line breaks - unit test `StringsTest` and `python tools\i18n.py audit`) but no native speaker has proofread them.
  Large languages should read well; smaller ones may sound odd here and there or use a different word than Android
  itself for "Accessibility" or "Installed apps". Whatever is missing falls back to English.

**Fix a translation (no coding):** open `app/src/main/res/values-<code>/strings.xml`, change the text between the tags,
keep every `name="..."`, every `%s` / `%1$s` / `%2$s`, every `\n`, and write `\'` for an apostrophe. Then run
`python tools\i18n.py audit` (it checks all languages against the English file) and rebuild.
The folder code is the language code (`in` = Indonesian, `iw` = Hebrew, `pt` = Brazilian Portuguese, `zh-rCN` / `zh-rTW` = Chinese).

**Add a language:** add a line to `tools/languages.tsv` (tag, native name, resource folder suffix), copy
`values/strings.xml` to `values-<suffix>/strings.xml` and translate it, then run `python tools\i18n.py sync`
(it rewrites `Languages.kt` and `res/xml/locales_config.xml`) and `python tools\i18n.py audit`.
A language only appears in the picker once its strings file exists.

**Languages:** English `en` · Tiếng Việt `vi` · 简体中文 `zh-CN` · 繁體中文 `zh-TW` · Español `es` · हिन्दी `hi` · العربية `ar` · বাংলা `bn` · Português (Brasil) `pt-BR` · Português (Portugal) `pt-PT` · Русский `ru` · 日本語 `ja` · Deutsch `de` · Français `fr` · 한국어 `ko` · Türkçe `tr` · Italiano `it` · ไทย `th` · Bahasa Indonesia `id` · Bahasa Melayu `ms` · Filipino `fil` · Polski `pl` · Українська `uk` · Nederlands `nl` · Română `ro` · Ελληνικά `el` · Čeština `cs` · Svenska `sv` · Magyar `hu` · Български `bg` · Dansk `da` · Suomi `fi` · Norsk bokmål `nb` · Slovenčina `sk` · Hrvatski `hr` · Српски `sr` · Slovenščina `sl` · Lietuvių `lt` · Latviešu `lv` · Eesti `et` · עברית `he` · فارسی `fa` · اردو `ur` · தமிழ் `ta` · తెలుగు `te` · मराठी `mr` · ગુજરાતી `gu` · ಕನ್ನಡ `kn` · മലയാളം `ml` · ਪੰਜਾਬੀ `pa` · ଓଡ଼ିଆ `or` · অসমীয়া `as` · नेपाली `ne` · සිංහල `si` · မြန်မာ `my` · ខ្မែរ `km` · ລາວ `lo` · ქართული `ka` · Հայերեն `hy` · Azərbaycan dili `az` · Қазақ тілі `kk` · Oʻzbek tili `uz` · Кыргызча `ky` · Тоҷикӣ `tg` · Türkmençe `tk` · Монгол `mn` · Беларуская `be` · Македонски `mk` · Shqip `sq` · Bosanski `bs` · Íslenska `is` · Gaeilge `ga` · Cymraeg `cy` · Gàidhlig `gd` · Euskara `eu` · Català `ca` · Galego `gl` · Malti `mt` · Lëtzebuergesch `lb` · Afrikaans `af` · Kiswahili `sw` · isiZulu `zu` · isiXhosa `xh` · አማርኛ `am` · Hausa `ha` · Yorùbá `yo` · Igbo `ig` · Soomaali `so` · Malagasy `mg` · Kinyarwanda `rw` · chiShona `sn` · Sesotho `st` · پښتو `ps` · Kurdî `ku` · سنڌي `sd` · ئۇيغۇرچە `ug` · Esperanto `eo` · Cebuano `ceb` · Basa Jawa `jv` · Basa Sunda `su` · Kreyòl ayisyen `ht`
