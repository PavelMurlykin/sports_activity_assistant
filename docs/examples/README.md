# Обезличенные примеры / Anonymous examples

Это синтетические файлы, не история пользователя. Они не входят в APK и не загружаются при первом запуске. Используйте отдельную тестовую установку; не импортируйте их в личную историю без намерения добавить эти записи.

- `training-backup-v6.json`: 3 результата (2 футбольных, 1 скалолазный), 4 строки трасс / 5 попыток, 1 центр примера, 1 профиль, 4 сохранённых плана, 1 еженедельная серия и 1 избранный центр. План 14.12.2025 связан с результатом; 21.12 отменён; 28.12 перенесён на 29.12; разовый скалолазный план — 11.01.2027. Виртуальные вхождения серии не являются отдельными строками файла.
- `football-history.csv`: 2 игры, те же футбольные значения, номер центра 42. Выберите/создайте центр в предпросмотре: это не локальный ID. Второй ряд оставляет необязательные показатели пустыми.

Контроль статистики после импорта JSON в пустую установку:

| Показатель / Metric | Значение / Value |
| --- | --- |
| Результаты / Completed workouts | 3 |
| Игры / Football games | 2 |
| Командный счёт / Team score | 3:1 |
| Личные голы / передачи · Personal goals / assists | 1 / 1 |
| Дистанция / средняя · Total / average distance | 7.35 км / km, 1 из / of 2 игр / games |
| Время / среднее · Total / average duration | 60 мин / min, 1 из / of 2 игр / games |
| Строки трасс / попытки · Route rows / attempts | 4 / 5 |
| Успех / Success | 2 / 5 (40%) |
| Максимум трудности / Highest successful lead grade | 6b+ |
| Повторный импорт / Reimport | 0 новых тренировок / new workouts |

CSV и JSON — альтернативные примеры. Импорт обоих может добавить похожие записи с разными идентификаторами: приложение не решает за пользователя, что они дубли. Сохраняйте исходные файлы, проверяйте совпадения и сопоставления.

These are synthetic files, not the user's history. They are not packaged in the APK or loaded at first launch. Use a separate test installation; do not import them into personal history unless you intend to add these records.

- `training-backup-v6.json`: 3 results (2 football, 1 climbing), 4 route rows / 5 attempts, 1 example center, 1 profile, 4 stored plans, 1 weekly series and 1 favorite. The December 14, 2025 plan links to a result; December 21 is canceled; December 28 is moved to December 29; a one-off climbing plan is January 11, 2027. Virtual series occurrences are not individual stored rows in the file.
- `football-history.csv`: 2 games with the same football values and center hint 42. Select/create a center in the preview; this is not a local ID. The second row leaves optional metrics empty.

The table gives expected statistics after JSON import into an empty installation. CSV and JSON are alternative examples: importing both may add similar records with different identities, not silently merge them. Keep original files and review matches/mappings. `DocumentedExamplesTest` reads these exact files, checks values/links and proves idempotent JSON reimport.
