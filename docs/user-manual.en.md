# User manual

## Purpose and installation

Sports Activity Assistant is a standalone local football and climbing log for Android 8.0 (API 26) and later. No account, server or internet connection is needed. The app does not send data elsewhere. Operations run on the device; automatic system backup is disabled and backups are created manually as files.

Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk`, built as described in README. Android may ask you to allow installation from the selected source. The debug build is intended for local testing, not publication. A fresh installation creates a local profile and initial reference data, but no sample workouts.

Before uninstalling the app or clearing its data, save a backup: both actions remove the local database. Do not install test builds over your only copy of important history without first saving a file.

Upgrading to the current database schema (version 4) preserves local keys, UUIDs, workouts, historical route repeat counts, centers, plans and their relationships. Inherited external profile identifiers are removed; an existing local name is retained. Reference initialization completes before data reads and writes without overwriting existing records. Broken references abort the migration; the database is not automatically recreated.

## Recording a workout

On «Расписание» (Schedule), press the add button and select «Записать результат» (Record result). Choose a sport, date and sports center that offers that sport. The current date field uses `YYYY-MM-DD`.

For football, enter team goals scored/conceded, personal goals and assists. Zero results are valid. Optionally enter distance in kilometers, players per team and game duration in minutes; optional fields may be left empty. Distance accepts a decimal point or comma. Negative values are invalid; players and duration must be positive when supplied.

Personal goals and assists, each considered separately, cannot exceed the team's goals scored. A football record cannot contain climbing routes, and a climbing record cannot contain football metrics. The common record and all sport details are saved together: an error does not leave an incomplete workout. Two identical workouts on the same day can be recorded manually; each has its own stable identifier within the database.

For climbing, add the required number of routes. Select a discipline and completed/not-completed result for each. Lead («Трудность») uses the French scale (`3a`–`9c`); boulder («Болдер») uses Fontainebleau (`1B`–`9A`), including intermediate `+` grades within the supported range. These are the app catalog's ranges, not federation-declared difficulty limits. Changing discipline resets the grade: identical labels in different scales do not mean identical difficulty. For speed («Скорость»), explicitly choose «Эталонная 15 м» (Standard 15 m) or «Иная трасса» (Other course); no difficulty grade applies. Speed ascent time is not recorded yet. Save the workout. Its details appear in the calendar and statistics. [Catalog sources and scope](climbing-grades.md) are included in the project; the app needs no internet to use the catalog.

Database upgrades and older JSON imports retain the original difficulty label. Only unambiguous old lead grades `5a`–`9c` are recognized as French. Old boulders, speed routes, coarse labels `3`, `4`, `4+`, `5` and unknown values are marked «историческая: шкала/трасса не подтверждена» (historical: scale/course unconfirmed); they are not rounded to a nearby grade. Results and repeat counts remain in the overall counters, but unconfirmed grades do not contribute to highest-grade metrics. You cannot yet manually confirm an old record's scale; keep the source file.

Saved workouts cannot yet be edited or deleted. Check the form before saving; after a save error some fields may need to be entered again. The route-list draft is not yet protected against loss on screen rotation.

## Schedule

Switch months and select a day to see saved results and plans. The add button also offers «Запланировать» (Plan), where you can select a date, sport and center. One-off plans and weekly repetition with an interval and optional end date are supported.

Plans do not count toward completed-workout statistics. Saved plans cannot yet be canceled or edited, and you cannot record a result directly from a plan: the result is created separately and the plan may remain visible. Recurring-series exceptions are not implemented yet.

## Statistics

«Статистика» (Statistics) displays the total workout count and monthly groups. Open a sport to see its metrics and workout list; expand a workout card for details.

Football: games, wins/draws/losses, total team score, personal goals and assists; supplied distances are summed, and duration is averaged over supplied values. Climbing: attempts including historical repeats, successful ascents, success percentage and discipline breakdown. The highest successfully completed grade is computed separately for lead (French) and boulder (Fontainebleau), using confirmed grades only. No highest difficulty is shown for speed. Unconfirmed historical attempts have a separate counter. Period and center filters are not available yet.

## Sports centers

On «Центры» (Centers), add a center with its name, optional city and available sports. Existing centers can be edited. The workout form filters centers by the selected sport. Center archiving and deletion are not available yet.

## Import and backup

On «Данные» (Data), press «Сохранить копию в файл» (Save backup to file), choose a local folder in Android's system file dialog and confirm. Version 3 JSON contains sports, centers and their associations, workouts with sport-specific details, one-off plans and recurrence rules, plus grading system, grade code, original label and speed course type. The file is not encrypted. The app does not upload it to a server; if you select a cloud provider in the system dialog, file handling depends on that third-party app. Choose device storage for fully local operation.

To import, press «Выбрать файл» (Choose file). JSON backup versions 1–3 are supported (older versions are explicitly adapted while retaining original grades), as are football CSV files with UTF-8/UTF-8 BOM or Windows-1251 encoding and `;` or `,` delimiters. Do not import a new backup into an older app build. After import, a warning is shown if the file contains unconfirmed historical attempts. Example CSV:

```csv
user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists;distance_km
1;2025-12-14;2;5;3;2;1;7,35
```

The center ID must already exist in the app. Initial centers: `1` — «Энергия Высоты» (climbing), `2` — «Фабрика Футбола» (football), `3` — «Арена на горе» (football). CSV does not yet import players per team, duration or climbing data. `user_id` is only a field in the older file format; data always goes into the local profile. Do not combine different people's histories in an imported file.

Import merges records with existing data without clearing the database. Content-identical workouts are skipped, which may collapse two real identical workouts. There is no preview or unknown-center mapping yet. JSON does not preserve a planned occurrence's association with a series and does not guarantee full restoration of every state. Keep the source file and check workout counts, details and calendar after import. An import-success message does not replace verification of restored data.

An unsupported sport, mixed sport details, a scale incompatible with its discipline, an invalid attempt count or an unknown discipline without explicit historical-record metadata causes an error without partially saving that file. An unknown discipline stored in an old database is not replaced with lead; version 3 JSON preserves it as explicitly historical, without comparing its grade. Unsupported historical sports already in the database are not deleted: common information remains viewable, but new results cannot be added for them. Version 3 JSON does not yet transfer stable identifiers; content-based deduplication limitations still apply. The initial numeric center IDs in the example apply only to a fresh installation and may differ after migration.

## Known limitations

Safe identifier exchange, complete restoration, historical-scale confirmation, result editing, linking plans to results and advanced filters are planned, not available features. Other scales, such as V-scale or UIAA, cannot yet be selected for new routes. Compatibility with your multi-year history and applicability of the selected scales to your gyms require anonymized examples and confirmation. No external connection is required to transfer files.

## Build and verification for developers

Install a JDK and Android SDK as described in README, then configure `JAVA_HOME` and the local SDK path. Library versions are pinned in `gradle/libs.versions.toml`; the Wrapper uses Gradle 9.8.0 with a checksum.

```powershell
.\gradlew.bat verifyOfflinePolicy testDebugUnitTest lintDebug assembleDebug
```

The first build may download tools and libraries. Once the cache is populated, repeat the command with `--offline`; the app itself needs no network. `verifyOfflinePolicy` rejects network permissions in merged debug/release manifests and enabled automatic backup. `connectedDebugAndroidTest` requires a running emulator or test device. The release APK is not yet signed with a distribution key; use the debug APK for installation.
