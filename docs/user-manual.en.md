# User manual

## Purpose and installation

Sports Activity Assistant is a standalone local football and climbing log for Android 8.0 (API 26) and later. No account, server or internet connection is needed. The app does not send data elsewhere. Operations run on the device; automatic system backup is disabled and backups are created manually as files.

Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk`, built as described in README. Android may ask you to allow installation from the selected source. The debug build is intended for local testing, not publication. A fresh installation creates a local profile and initial reference data, but no sample workouts.

Before uninstalling the app or clearing its data, save a backup: both actions remove the local database. Do not install test builds over your only copy of important history without first saving a file.

Upgrading to the current database schema (version 7) preserves local keys, UUIDs, workouts, historical route repeat counts, centers, plans and their relationships. Inherited external profile identifiers are removed; an existing local name is retained. Reference initialization completes before data reads and writes without overwriting existing records. Broken references abort the migration; the database is not automatically recreated.

## Loading, errors and accessible actions

Reading the calendar, statistics and directory shows «Загрузка данных…» (Loading data). A read failure is not an empty history: an error offers «Повторить» (Retry). Retry reads the data again; it does not save a workout or start an import. If it still fails, close and reopen the app; do not clear its data or uninstall without a verified backup. A failed result/plan opening can be retried or the window closed. File-operation messages stay on «Данные» (Data) and are not dismissed by switching to the calendar; field-validation errors remain in their form.

Add buttons have accessible names «Добавить тренировку» (Add workout) and «Добавить спортивный центр» (Add sports center); they appear after the required reference data loads. Sport cards and result expansion are identified as actions. On a narrow screen, the calendar grid scrolls horizontally: swipe across it to reveal the remaining weekdays; pages and forms scroll vertically. Cell height accommodates increased font size. The selected calendar date survives rotation and system process restoration, but is not included in the JSON backup.

In landscape, navigation moves to a side rail: calendar, statistics, centers and data use icons with accessible names. Subtitles are hidden and dialogs use the available width. The entire dialog scrolls, including Save and Cancel: these actions remain reachable with the keyboard open. In other orientations, the body of a long form scrolls. The system Back button can hide the keyboard without closing the form.

The interface is still Russian. Interface, validation and file-operation texts are in Android resources. This English manual describes the same workflows and does not promise full English localization.

## Recording a workout

On «Расписание» (Schedule), press the add button and select «Записать результат» (Record result). Choose a sport, date and sports center that offers that sport. Enter `YYYY-MM-DD` (years 0001–9999) or use «Выбрать дату» (Choose date). New results cannot use a future date: use a plan instead. Imported history may contain a future date; editing may retain that original date or correct it to today/the past, but cannot replace it with another future date.

For football, enter team goals scored/conceded, personal goals and assists. Zero results are valid. Optionally enter distance in kilometers, players per team and game duration in minutes; optional fields may be left empty. Distance accepts a decimal point or comma: up to 16 significant digits, 6 decimal places after removing trailing zeros, and 22 digits in the integer part. Equivalent decimal representations retain the same numeric value. Negative values are invalid; players and duration must be positive when supplied.

Personal goals and assists, each considered separately, cannot exceed the team's goals scored. A football record cannot contain climbing routes, and a climbing record cannot contain football metrics. The common record and all sport details are saved together: an error does not leave an incomplete workout. Two identical workouts on the same day can be recorded manually; each has its own stable identifier within the database.

For climbing, add the required number of routes. Select a discipline and completed/not-completed result for each. Lead («Трудность») uses the French scale (`3a`–`9c`); boulder («Болдер») uses Fontainebleau (`1B`–`9A`), including intermediate `+` grades within the supported range. These are the app catalog's ranges, not federation-declared difficulty limits. Changing discipline resets the grade: identical labels in different scales do not mean identical difficulty. For speed («Скорость»), explicitly choose «Эталонная 15 м» (Standard 15 m) or «Иная трасса» (Other course); no difficulty grade applies. Speed ascent time is not recorded yet. The number of attempts with this result defaults to 1 and must be a positive integer. A value of 12 means 12 attempts with the same discipline, grade and result; put different results in separate rows. Historical repeats are not expanded into invented individual routes. Rows can be changed or removed, retaining at least one. Save the workout. Its details appear in the calendar and statistics. [Catalog sources and scope](climbing-grades.md) are included in the project; the app needs no internet to use the catalog.

Database upgrades and older JSON imports retain the original difficulty label. Only unambiguous old lead grades `5a`–`9c` are recognized as French. Old boulders, speed routes, coarse labels `3`, `4`, `4+`, `5` and unknown values are marked «историческая: шкала/трасса не подтверждена» (historical: scale/course unconfirmed); they are not rounded to a nearby grade. Results and repeat counts remain in the overall counters, but unconfirmed grades do not contribute to highest-grade metrics. You cannot yet manually confirm an old record's scale; keep the source file.

The form closes only after a successful write; errors remain in the form together with your entries. Numeric errors appear next to their field. Fields and repeated save taps are disabled while writing. The draft includes date, center, all football fields and routes with their results/repeat counts; it survives rotation and screen restoration after the system terminates a background process. Switching sports retains a separate draft for each sport. Explicit cancellation, closing the form and force-stopping the app do not promise draft retention; drafts are not included in backups.

## Editing and deleting a result

Select a calendar day, or open Statistics → the sport → expand a workout. Press «Изменить тренировку» (Edit workout) to change date, center and sport metrics. A saved workout's sport cannot be changed. UUID, creation time and identities of retained routes are preserved; new routes receive new UUIDs. The original center may remain even if archived or no longer offering this sport, but a different center must be active and offer it.

An ambiguous historical route's original discipline/grade is read-only; its result and number of identical attempts can be corrected, the row removed or a confirmed modern route added. The form does not automatically assign a modern scale to an old grade.

«Удалить тренировку» (Delete workout) first shows confirmation with date, sport and center. Cancel deletes nothing. Confirmation deletes the result and all of its sport details together without removing centers or plans. Its linked plan becomes planned again, or canceled if its series is canceled. Statistics and calendar update after an edit or deletion. Deletion cannot be undone inside the app; an older backup can restore the record. Reimporting an old copy can restore deleted workouts: no permanent prohibition on their restoration is stored.

If the workout changed or was deleted while the form was open (for example through import), stale saves/deletions are rejected. Close the form and reopen the record; inconsistent edits are not written.

## Schedule

Switch months and select a day: event cards and detailed results appear below the calendar. The legend distinguishes “✓ Completed”, “○ Planned”, “× Canceled”; accessible day labels include date, sport and state. Plans and cancellations do not count toward sport statistics. The calendar observes database changes and cancels obsolete month queries. Today's date updates after midnight (checked every 30 seconds and on screen resume); the selection follows only if you were viewing today, not another date.

Add → «Запланировать» (Plan) opens on the selected date. Choose sport and center, one-off or «По неделям» (Weekly), and a positive integer interval. The end date is inclusive; an empty field means unlimited repetition. Years are 0001–9999. Errors remain in the form; zero is not silently replaced with one. Date, selected fields and nested center creation survive screen restoration; the form closes only after a successful write. Explicit cancellation saves no plan.

Plan cards offer «Изменить план» (Edit plan), «Записать результат по плану» (Record result from plan), and «Отменить тренировку» (Cancel workout). Results are prefilled with sport, date and center; enter sport details and adjust actual date/center if necessary. The result's sport is locked. A future result is invalid; if the original center is archived or no longer offers this sport, choose an available center. Result and plan link are saved in one transaction. Only the result appears on its actual date, labeled «Результат связан с планом» (Result linked to plan), without an active duplicate plan.

Editing/canceling recurring workouts explicitly offers «Только это событие» (Only this event) or «Вся серия» (Entire series). Moving one event preserves its original occurrence date: no new plan appears on the old day, and the moved card says “Moved from …”. Canceling one occurrence requires confirmation and leaves other weeks unchanged. Canceled events remain visible but cannot be completed from their plan; undoing cancellation is not provided yet.

Editing an entire series changes start/end, interval, sport and center for events without individual exceptions. Moved, canceled and completed exceptions retain their attributes even when the original occurrence no longer falls within the changed schedule. Confirmed cancellation of the entire series marks all unfinished events, including moved ones, canceled; results are not deleted. Edit completed workouts using the result editor, not the plan.

Links and exceptions are included in JSON 6. Older plans with a recurrence parent retain their original links; if an old file/database contains several separate plans for one occurrence, all remain individual cards and no additional generated plan is added. If an occurrence or result is already linked to another local plan, import requires keeping the local record. Excluding a result referenced by a new imported plan blocks application of the file.

## Statistics

«Статистика» (Statistics) shows saved completed workouts, counts by sport and monthly groups. Plans and canceled events are excluded from results. Only the current local profile is counted, even when other profiles' histories are stored separately.

Press «Фильтры» (Filters): choose «Всё время» (All time), «Этот месяц» (This month), «Этот год» (This year), or uncheck All time and enter both dates as `YYYY-MM-DD` (0001–9999). Both boundaries are included; invalid dates or a start after the end are not applied. Choose «Все центры» (All centers) or one center, including an archived center. Press «Применить» (Apply); «Отмена» (Cancel) leaves the selection unchanged, and «Сбросить» (Reset) restores all time/all centers. The active selection is shown above the metrics and shared by overview, sport totals and history. It survives rotation and system process restoration; it is UI state, not part of the JSON backup.

Months run from newest to oldest, including months without workouts. In a selected period, the first and last month count only dates within the boundaries. For all time, the range extends from the first to the last matching workout; an empty database has no monthly cards. An explicit period still shows empty months with zero counts.

Open a sport for totals and workout history. History uses 20-record pages, newest dates first, with stable ordering for equal dates. «Предыдущая» (Previous)/«Следующая» (Next) switch pages; the caption shows the visible range and total matching records. Expand a card for details, editing or deletion. Database changes recalculate totals and the page; an empty final page falls back to the last available page. Changing filters returns to the first page. Totals cover the whole selection, not just the visible page.

Football: games, wins/draws/losses, total team score, personal goals and assists; total and average distance, total and average duration, average team size. Each optional metric has a count of games with supplied values out of all matching games. Missing values are not zeros and do not enter the average denominator; an entered distance of `0` does. If nothing was supplied, «Нет данных» (No data) is shown. Decimal totals and averages are rounded to two decimal places without trailing zeros; distances are summed exactly before display rounding.

Climbing: workout count, «Записи трасс» (Route records: rows, not a guaranteed count of unique real routes), «Попытки (с повторами)» (Attempts including repeats), successful ascents and integer success percentage. Discipline, confirmed grade and speed-course breakdowns are labeled «успешно / попытки» (successful / attempts) and include repeats. Highest successful grades are computed separately for lead (French) and boulder (Fontainebleau); «Нет успешных» (No successful ascents) appears when none qualify. A failed harder attempt does not raise the maximum. Speed has no highest difficulty. Unconfirmed historical attempts contribute to overall results and a separate counter, but not to confirmed-grade breakdowns or maxima.

## Sports centers

On «Центры» (Centers), press «+» and enter a name, optional city and one or more available sports. Name and city are limited to 200 characters each. For new centers, whitespace including non-breaking spaces is collapsed to one space and trimmed; an empty city is stored as absent. Duplicate matching ignores case and uses Unicode normalization. You cannot create the same name/city again, even if the existing center is archived; the same name in different cities is allowed.

Press «Изменить» (Edit) to change the name, city or sports. UUID and original creation time are retained. On failure the form remains open with your entries; repeated save taps are blocked. Long names wrap and the sports list/form scrolls. In the directory, center fields and selected sports survive rotation. Migration does not merge similar historical centers: their sports can be edited or they can be archived while retaining their original name/city; renaming to a conflicting name requires a different name or city.

Rotating during a center save does not start a second write: the restored form waits for the same request. If the write completed before system process restoration, retrying confirmation does not duplicate the center. Archive/restore also retain the selected action during rotation; an already archived center is not automatically reopened.

«В архив» (Archive) requires confirmation. The «Архив» tab shows closed centers; «Вернуть» (Restore) makes a center available again. Archiving or removing an offered sport prevents new workouts and plans for that combination without deleting history, favorites, plans or series. Archiving does not cancel existing events. Center deletion is not provided.

Both new-training forms show only active centers offering the selected sport. Changing the sport or directory clears an incompatible selection, selecting the first available center or offering «Создать центр» (Create center). Creation from the form preselects the current sport without closing the training form. An empty list explains that you should add a center or restore one from the archive. Both forms retain their nested center-creation draft on rotation. Editing a result or plan may retain its original historical reference to an archived center if the sport is unchanged.

## Import and backup

On «Данные» (Data), press «Сохранить копию в файл» (Save backup to file) and choose a local folder in Android's system dialog. Version 6 JSON preserves UUIDs and creation times for profiles, centers (including archive state), workouts, routes, plans and series; sport details, statuses, series exceptions and plan-to-series/result links; favorite centers and confirmed legacy-file mappings. The snapshot is consistent, and the written file is read back and checked. Only successful verification produces «Копия сохранена и проверена чтением файла» (Backup saved and verified by reading the file). Canceling the dialog is not success. A failed file may be incomplete: do not replace your previous verified copy with it.

The file is not encrypted. The app does not upload it; cloud entries in the system dialog belong to third-party apps. Choose device storage for fully local operation. Broad storage permissions are not required. Import and export are limited to 16 MiB and 100000 objects in total, including routes and reference records.

To import, press «Выбрать файл» (Choose file). Supported formats are JSON 1–6 with valid UTF-8 and football CSV with UTF-8/BOM or Windows-1251 encoding and `;` or `,` delimiters. CSV supports quoted fields, doubled quotes and embedded newlines. Example including optional fields:

```csv
user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists;distance_km;players_per_team;duration_minutes
1;2025-12-14;2;5;3;2;1;7,35;5;60
1;2025-12-21;2;1;1;0;1;;;
```

Distance, players-per-team and duration columns may be omitted; empty values remain absent. With a comma delimiter, quote a number containing a decimal comma. Climbing CSV is not supported yet; multiple routes in one workout are transferred through JSON.

«Проверка импорта» (Import review) first shows format, counts, errors, warnings and center mappings. A CSV center number is only a hint, not a reliable ID on a new installation: confirm an existing center or creation of a historical center. Mapping to a user center preserves its name, creation time, archive state and current offerings. JSON 1–4 had no archive flag: newly imported centers are active, but a previously archived local center is not reopened. A different archive state for the same UUID in JSON 5/6 requires confirmation to keep the local record. Similar historical JSON 4–6 centers with different UUIDs may be restored separately with a warning; ambiguous names are not automatically matched. Historical workouts may refer to a sport no longer offered there; new workouts and plans still require the sport to be available.

If the file contains several `user_id` values or local profiles, choose one for the current personal history or «Сохранить все отдельно» (Keep all separately). In the latter case, the primary profile corresponds to current personal statistics; other profiles are retained separately and included in the next backup. Their results are not mixed into the current calendar or statistics; profile switching is not available in the UI yet. `user_id` is only a file field, not an external account. Old JSON did not contain owners, so original user separation cannot be recovered from it.

The center directory is shared: the file's entire directory is imported even when selecting one profile. Other profiles' history and favorites are not imported in that mode. If different profiles were already explicitly merged into the personal history by earlier imports, the app cannot split them automatically: the keep-all-separately mode is blocked for that mapping. You can import a selected profile separately.

An existing UUID is skipped. If the same UUID contains different data, explicitly confirm «Сохранить локальную запись» (Keep local record): import will not overwrite a workout, plan, series, center or profile metadata. Identical real workouts with different UUIDs are both retained. Content matches are suggestions only: select «Пропустить запись из файла» (Skip file record) only after checking. This skip is not a permanent prohibition on later imports.

For older files without UUIDs, identity depends on exact file bytes and record position. An unchanged file can be imported again without duplicates; changing encoding, whitespace or rows creates a different source. Keep original files. JSON 1–3 did not preserve creation times or one-off-plan-to-series links: missing information is not guessed, and creation times are assigned during import.

Press «Применить импорт» (Apply import) after reviewing. Until then, the database is unchanged; «Отмена» (Cancel) closes the preview. Any file or write error rolls back the entire operation; existing data is never cleared. Reading and parsing run in the background, and repeated file operations are blocked. Preview and backup snapshot survive rotation, but process termination requires restarting the operation; an interrupted export file may be empty.

Unknown JSON versions/fields, unsupported sports, mixed sport details, invalid scale/grade, date, number, status, frequency or missing reference cause an error without partial writes. Explicitly historical unknown disciplines and original categories are retained without highest-grade comparisons. Data from an inconsistent old database is exported without silent removal, but validation may reject reimport; retain the original and do not manually alter your only copy.

Check workout counts, details and calendar after transfer. New backups cannot be imported into older app builds. A [format and limitations reference](backup-format.md) is included in the project.

## Known limitations

Historical-scale confirmation is not available yet. Filters cover a period and one center; multi-center comparisons and charts are not provided. Other scales, such as V-scale or UIAA, cannot yet be selected for new routes. Compatibility with your multi-year history and applicability of the selected scales to your gyms require anonymized examples and confirmation. No external connection is required to transfer files.

## Build and verification for developers

Install a JDK and Android SDK as described in README, then configure `JAVA_HOME` and the local SDK path. Library versions are pinned in `gradle/libs.versions.toml`; the Wrapper uses Gradle 9.8.0 with a checksum.

```powershell
.\gradlew.bat verifyOfflinePolicy testDebugUnitTest lintDebug assembleDebug
```

The first build may download tools and libraries. Once the cache is populated, repeat the command with `--offline`; the app itself needs no network. `verifyOfflinePolicy` rejects network permissions in merged debug/release manifests and enabled automatic backup. `connectedDebugAndroidTest` requires a running emulator or test device. The release APK is not yet signed with a distribution key; use the debug APK for installation.
