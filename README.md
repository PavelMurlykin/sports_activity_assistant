# sports_activity_assistant

Android-прототип на Kotlin/Jetpack Compose для переноса логики из `sports_activity_bot`.

Что уже заложено:
- пакет приложения `com.pamurlykin.sportsactivityassistant`;
- локальное хранение через `Room`;
- базовая схема данных, повторяющая ключевые PostgreSQL-сущности бота;
- отдельные локальные таблицы для планирования и повторяющихся тренировок;
- экран `Расписание` с помесячным календарем;
- экран `Статистика` и детализация по конкретному виду спорта;
- сид-данные для быстрого локального запуска без сервера.

Основные сущности, перенесенные из бота:
- `users`
- `sports`
- `sports_complexes`
- `sports_complex_sports`
- `user_favorite_complexes`
- `trainings`
- `football_trainings`
- `climbing_trainings`
- `climbing_routes`

Дополнительно для мобильного сценария:
- `planned_trainings`
- `recurrence_rules`

