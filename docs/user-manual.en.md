# User manual

## Purpose and installation

Sports Activity Assistant is a standalone local football and climbing log for Android 8.0 (API 26) and later. No account, server or internet connection is needed. The app does not send data elsewhere. Operations run on the device; automatic system backup is disabled and backups are created manually as files.

Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk`, built as described in README. Android may ask you to allow installation from the selected source. The debug build is intended for local testing, not publication. A fresh installation creates a local profile and initial reference data, but no sample workouts.

Before uninstalling the app or clearing its data, save a backup: both actions remove the local database. Do not install test builds over your only copy of important history without first saving a file.

Upgrading to the current database schema (version 6) preserves local keys, UUIDs, workouts, historical route repeat counts, centers, plans and their relationships. Inherited external profile identifiers are removed; an existing local name is retained. Reference initialization completes before data reads and writes without overwriting existing records. Broken references abort the migration; the database is not automatically recreated.

## Recording a workout

On «Расписание» (Schedule), press the add button and select «Записать результат» (Record result). Choose a sport, date and sports center that offers that sport. The current date field uses `YYYY-MM-DD`, with years 0001–9999.

For football, enter team goals scored/conceded, personal goals and assists. Zero results are valid. Optionally enter distance in kilometers, players per team and game duration in minutes; optional fields may be left empty. Distance accepts a decimal point or comma, up to 16 significant digits and 6 decimal places. Negative values are invalid; players and duration must be positive when supplied.

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

On «Центры» (Centers), press «+» and enter a name, optional city and one or more available sports. Name and city are limited to 200 characters each. For new centers, whitespace including non-breaking spaces is collapsed to one space and trimmed; an empty city is stored as absent. Duplicate matching ignores case and uses Unicode normalization. You cannot create the same name/city again, even if the existing center is archived; the same name in different cities is allowed.

Press «Изменить» (Edit) to change the name, city or sports. UUID and original creation time are retained. On failure the form remains open with your entries; repeated save taps are blocked. Long names wrap and the sports list/form scrolls. In the directory, center fields and selected sports survive rotation. Migration does not merge similar historical centers: their sports can be edited or they can be archived while retaining their original name/city; renaming to a conflicting name requires a different name or city.

«В архив» (Archive) requires confirmation. The «Архив» tab shows closed centers; «Вернуть» (Restore) makes a center available again. Archiving or removing an offered sport prevents new workouts and plans for that combination without deleting history, favorites, plans or series. Archiving does not cancel existing events. Center deletion is not provided.

Both training forms show only active centers offering the selected sport. Changing the sport or directory clears an incompatible selection, selecting the first available center or offering «Создать центр» (Create center). Creation from the form preselects the current sport without closing the training form. An empty list explains that you should add a center or restore one from the archive. The nested center-creation draft is not yet protected when rotating the training form; save the center before rotating.

## Import and backup

On «Данные» (Data), press «Сохранить копию в файл» (Save backup to file) and choose a local folder in Android's system dialog. Version 5 JSON preserves UUIDs and creation times for profiles, centers (including archive state), workouts, routes, plans and series; sport details, statuses and plan-to-series links; favorite centers and confirmed legacy-file mappings. The snapshot is consistent, and the written file is read back and checked. Only successful verification produces «Копия сохранена и проверена чтением файла» (Backup saved and verified by reading the file). Canceling the dialog is not success. A failed file may be incomplete: do not replace your previous verified copy with it.

The file is not encrypted. The app does not upload it; cloud entries in the system dialog belong to third-party apps. Choose device storage for fully local operation. Broad storage permissions are not required. Import and export are limited to 16 MiB and 100000 objects in total, including routes and reference records.

To import, press «Выбрать файл» (Choose file). Supported formats are JSON 1–5 with valid UTF-8 and football CSV with UTF-8/BOM or Windows-1251 encoding and `;` or `,` delimiters. CSV supports quoted fields, doubled quotes and embedded newlines. Example including optional fields:

```csv
user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists;distance_km;players_per_team;duration_minutes
1;2025-12-14;2;5;3;2;1;7,35;5;60
1;2025-12-21;2;1;1;0;1;;;
```

Distance, players-per-team and duration columns may be omitted; empty values remain absent. With a comma delimiter, quote a number containing a decimal comma. Climbing CSV is not supported yet; multiple routes in one workout are transferred through JSON.

«Проверка импорта» (Import review) first shows format, counts, errors, warnings and center mappings. A CSV center number is only a hint, not a reliable ID on a new installation: confirm an existing center or creation of a historical center. Mapping to a user center preserves its name, creation time, archive state and current offerings. JSON 1–4 had no archive flag: newly imported centers are active, but a previously archived local center is not reopened. A different archive state for the same UUID in JSON 5 requires confirmation to keep the local record. Similar historical JSON 4/5 centers with different UUIDs may be restored separately with a warning; ambiguous names are not automatically matched. Historical workouts may refer to a sport no longer offered there; new workouts and plans still require the sport to be available.

If the file contains several `user_id` values or local profiles, choose one for the current personal history or «Сохранить все отдельно» (Keep all separately). In the latter case, the primary profile corresponds to current personal statistics; other profiles are retained separately and included in the next backup. Their results are not mixed into the current calendar or statistics; profile switching is not available in the UI yet. `user_id` is only a file field, not an external account. Old JSON did not contain owners, so original user separation cannot be recovered from it.

The center directory is shared: the file's entire directory is imported even when selecting one profile. Other profiles' history and favorites are not imported in that mode. If different profiles were already explicitly merged into the personal history by earlier imports, the app cannot split them automatically: the keep-all-separately mode is blocked for that mapping. You can import a selected profile separately.

An existing UUID is skipped. If the same UUID contains different data, explicitly confirm «Сохранить локальную запись» (Keep local record): import will not overwrite a workout, plan, series, center or profile metadata. Identical real workouts with different UUIDs are both retained. Content matches are suggestions only: select «Пропустить запись из файла» (Skip file record) only after checking. This skip is not a permanent prohibition on later imports.

For older files without UUIDs, identity depends on exact file bytes and record position. An unchanged file can be imported again without duplicates; changing encoding, whitespace or rows creates a different source. Keep original files. JSON 1–3 did not preserve creation times or one-off-plan-to-series links: missing information is not guessed, and creation times are assigned during import.

Press «Применить импорт» (Apply import) after reviewing. Until then, the database is unchanged; «Отмена» (Cancel) closes the preview. Any file or write error rolls back the entire operation; existing data is never cleared. Reading and parsing run in the background, and repeated file operations are blocked. Preview and backup snapshot survive rotation, but process termination requires restarting the operation; an interrupted export file may be empty.

Unknown JSON versions/fields, unsupported sports, mixed sport details, invalid scale/grade, date, number, status, frequency or missing reference cause an error without partial writes. Explicitly historical unknown disciplines and original categories are retained without highest-grade comparisons. Data from an inconsistent old database is exported without silent removal, but validation may reject reimport; retain the original and do not manually alter your only copy.

Check workout counts, details and calendar after transfer. New backups cannot be imported into older app builds. A [format and limitations reference](backup-format.md) is included in the project.

## Known limitations

Historical-scale confirmation, result editing, linking plans to results and advanced filters are planned, not available features. Other scales, such as V-scale or UIAA, cannot yet be selected for new routes. Compatibility with your multi-year history and applicability of the selected scales to your gyms require anonymized examples and confirmation. No external connection is required to transfer files.

## Build and verification for developers

Install a JDK and Android SDK as described in README, then configure `JAVA_HOME` and the local SDK path. Library versions are pinned in `gradle/libs.versions.toml`; the Wrapper uses Gradle 9.8.0 with a checksum.

```powershell
.\gradlew.bat verifyOfflinePolicy testDebugUnitTest lintDebug assembleDebug
```

The first build may download tools and libraries. Once the cache is populated, repeat the command with `--offline`; the app itself needs no network. `verifyOfflinePolicy` rejects network permissions in merged debug/release manifests and enabled automatic backup. `connectedDebugAndroidTest` requires a running emulator or test device. The release APK is not yet signed with a distribution key; use the debug APK for installation.
