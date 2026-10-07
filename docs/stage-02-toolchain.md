# Этап 2: стабильные зависимости и воспроизводимые проверки

Дата проверки источников: 7 октября 2026 года. Исходный коммит этапа: `c72ab5e`.

## Выбранные версии

| Компонент | Было | Выбрано | Первичный источник |
| --- | --- | --- | --- |
| Android Gradle Plugin | 9.3.1 | 9.4.1 | [Google Maven](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml), [совместимость AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Gradle | 9.7.0 | 9.8.0 | [Релизы Gradle](https://gradle.org/releases/) |
| Kotlin / Compose compiler / Serialization plugin | 2.4.10 | 2.4.20 | [Релизы Kotlin](https://kotlinlang.org/docs/releases.html) |
| KSP | 2.3.11 | 2.3.12 | [Релиз KSP](https://github.com/google/ksp/releases/tag/2.3.12) |
| Compose BOM | 2026.06.01 | 2026.09.00 | [Google Maven](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml) |
| Core KTX | 1.19.0 | 1.19.1 | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Navigation Compose | 2.9.8 | 2.10.2 | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Room runtime / KTX / compiler / testing | 2.8.4 | 2.8.5 | [Room](https://developer.android.com/jetpack/androidx/releases/room) |

Activity `1.13.0`, Lifecycle `2.11.0`, [Coroutines `1.11.0`](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0), [Serialization runtime `1.11.0`](https://github.com/Kotlin/kotlinx.serialization/releases), [Material Components `1.14.0`](https://github.com/material-components/material-components-android/releases/tag/1.14.0), Android Test Runner `1.7.0`, Espresso `3.7.0` и JUnit `4.13.2` оставлены в своих стабильных линейках. Кандидат Serialization `1.12.0-RC` не используется. Room 3 имеет другие координаты и отдельный переход; вместо архитектурной замены используется актуальный патч существующей Room 2.

AGP 9.4 требует Gradle не ниже 9.6 и JDK не ниже 17. [Документация Kotlin 2.4.20](https://kotlinlang.org/docs/whatsnew2420.html) указывает полностью проверенный диапазон до Gradle 9.7.0 и разрешает более новые версии с возможными предупреждениями. Поэтому связка с 9.8.0 проверяется локально всей сборкой и тестами, а не объявляется совместимой только на основании номеров.

Compile SDK `37.1`, target SDK `37` и min SDK `26` сохранены. Для локальной проверки используется установленный Android Studio JBR `25.0.3`; Java source/target остаются `17`. Встроенная поддержка Kotlin в AGP сохранена; дополнительный `org.jetbrains.kotlin.android` не подключён.

## Воспроизводимость и офлайн-политика

- Все версии вынесены в `gradle/libs.versions.toml`; нет динамических версий.
- Gradle Wrapper закреплён на 9.8.0 с [официальной SHA-256](https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256): `bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
- Задача `verifyOfflinePolicy` проверяет объединённые манифесты debug и release через Variant API AGP. Проверяются сетевые разрешения и отключение автоматического бэкапа; это учитывает разрешения транзитивных библиотек, а не только исходный манифест.
- Добавлен GitHub Actions workflow: проверки манифестов, модульные тесты, Lint и сборки debug/release. Действия закреплены полными SHA, токен имеет только `contents: read`, учётные данные Git не сохраняются. Инструментальные тесты выполняются локально отдельно.
- CI и загрузка зависимостей относятся к среде разработки. APK по-прежнему не имеет сетевых разрешений и не нуждается в интернете.

## Проверки этапа

Сначала обновлены только инструменты и плагины: `testDebugUnitTest assembleDebug` прошли. Затем обновляются библиотеки и проверяется полный набор:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat verifyOfflinePolicy testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest --warning-mode all
```

Полный прогон прошёл: 10 модульных тестов, 7 Android-тестов, 0 ошибок и пропусков. Debug APK и неподписанный release APK собраны; debug Lint и release vital Lint прошли. Итоговый debug/release манифест проверен задачей `verifyOfflinePolicy`.

Тот же набор команд с `--offline` прошёл повторно. Android-тесты выполнены на временном read-only эмуляторе API 37.1 с режимом полёта и отключёнными Wi-Fi/мобильными данными. Обновлённый debug APK установлен отдельно после тестов и запущен: `Status: ok`, холодный запуск около 1,8 с.

Отдельный отрицательный тест подменяет вход только задачи проверки собственным XML из `.local-testing`, а не манифестом приложения. Разрешение `android.permission.INTERNET` правильно отклонено с сообщением `Offline app must not request android.permission.INTERNET`. Следующая обычная офлайн-проверка без подмены проходит.

Проверенный debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `DDB1ECE028F21703048451CFC88F12D95E3AC487D7EBACAD416BC612E7510DD7`.

Предупреждение `Configuration.setVisible(boolean)` возникает в инструментальной цепочке, не в коде приложения; новая Gradle отмечает удаление API в версии 11. Не включается режим игнорирования ошибок и не изменяются проверки APK. При обновлении плагинов предупреждение необходимо перепроверять.

JBR 25 также предупреждает об использовании `sun.misc.Unsafe` транзитивной Protobuf-библиотекой тестового раннера. Это предупреждение инструмента на компьютере, не сетевой код или ошибка установленного приложения; тесты проходят. Gradle упаковывает `libandroidx.graphics.path.so` без удаления отладочных символов — сборки успешны, отдельно установка NDK ради этого не выполнялась.

Физическое устройство и API 26 пока не проверены. Добавление workflow не означает, что его удалённый запуск уже подтверждён: результат CI следует проверять отдельно после пуша.
