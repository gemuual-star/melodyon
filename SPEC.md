# Spec: Melodyon — V1

Статус: одобрено владельцем.

## Objective
Melodyon — собственный форк Metrolist (последний релиз v14.0.1) под Android и Windows.
1. Ребрендинг: все упоминания Metrolist -> Melodyon (имя приложения, пакет, ресурсы, строки во всех локалях, иконки-названия, user agent). Конкретные ссылки и контакты (Telegram/Discord/донаты/сайт/GitHub авторов) удаляются из UI и about-экрана. Упоминание Metrolist в UI убирается полностью (решение владельца). Файл LICENSE (GPL-3.0) и копирайты в исходниках остаются.
2. Обновления: проверка и загрузка новых версий из публичного репозитория релизов `gemuual-star/melodyon-releases` (GitHub Releases API, без токена). Исходники живут в приватном `gemuual-star/melodyon`.
3. SoundCloud как альтернативный источник треков без авторизации:
   - таблетка «SoundCloud» первой в ряду фильтров результатов поиска (перед All/Songs/Videos...); выдача SC запрашивается только по нажатию;
   - воспроизведение (progressive MP3 / HLS через ExoPlayer), очередь, смешанная очередь YT+SC;
   - скачивание SC-треков в общую систему загрузок;
   - локальные плейлисты могут содержать SC-треки (не синхронизируются с YT Music);
   - тексты: поиск по название+исполнитель через уже встроенные провайдеры (LrcLib, KuGou и т.д.);
   - лайк/история/недавние — локально.

## Assumptions (одобрены)
1. applicationId: `com.melodyon.app`; версия стартует как 1.0.0 (versionCode 1). Ставится рядом с Metrolist, не поверх.
2. Windows-сборка — десктопная версия из исходников Metrolist (Compose Desktop), установщик MSI/EXE.
3. Сборки делаем обе каждый раз: Android APK собирается в песочнице (4 CPU / 8 ГБ RAM / 21 ГБ диска — хватит, но сборка долгая); Windows-установщик собирается только на Windows, поэтому — GitHub Actions (windows runner) в приватном репо, ручной запуск workflow на релиз, чтобы экономить минуты (у приватных репо Windows-минуты считаются x2).
4. Релиз = тег vX.Y.Z в приватном репо + релиз с APK и установщиком в публичном `melodyon-releases` (там только бинарники и ссылка на лицензию).
5. SoundCloud: client_id берётся динамически из публичных JS-скриптов soundcloud.com (как в yt-dlp/NewPipe), кэшируется, обновляется при 401/403. Неофициальный способ, может сломаться.
6. Только публичные стримируемые треки; превью-треки Go+ (30 сек) скрываются или помечаются.
7. Google Drive не используем, пока нет больших файлов вне git.

## Tech Stack
Как в upstream: Kotlin, Gradle (wrapper из репо), Jetpack Compose / Compose Multiplatform, Media3 ExoPlayer, Room, Ktor/OkHttp. Новые зависимости — только с согласия.

## Commands (уточню после клонирования, по факту репо)
- Android: `./gradlew :app:assembleRelease` (или нужный flavor)
- Тесты: `./gradlew test`
- Desktop: `./gradlew :desktop:packageReleaseMsi` (на Windows runner)
- Окружение песочницы: JDK 21, Android SDK cmdline-tools, platform/build-tools под compileSdk upstream; точные версии и команды — в docs/PROGRESS.md.

## Project Structure (новое)
- `docs/RULES.md`, `docs/PROGRESS.md`, `SPEC.md`
- модуль/пакет `soundcloud` (API-клиент: resolve client_id, search, stream resolve) — по образцу существующего `innertube`
- `.github/workflows/release-windows.yml`, скрипт публикации релиза в публичный репо
- `.gitignore`: keystore, *.jks, local.properties, secrets*, build/, .gradle/, кэши

## Testing Strategy
- Unit: SC-клиент на записанных JSON-ответах (парсинг поиска, resolve стрима, обновление client_id), маппинг SC-трек -> модель песни, парсинг GitHub Releases и сравнение версий.
- Тест на ребрендинг: grep-проверка, что в исходниках/ресурсах нет «metrolist» (кроме LICENSE и истории git).
- Сборка APK + запуск всех `./gradlew test` перед каждым «готово».
- Не проверяемо в песочнице: реальный запуск на устройстве и на Windows, UI. Это будет помечено как непроверенное.

## Boundaries
- Always: правила из docs/RULES.md, коммит+push после каждого куска, тесты перед «готово».
- Ask first: новые зависимости, смена applicationId после релиза, хранение ключа подписи вне песочницы.
- Never: секреты в git/Drive/логах/URL remote, force push, трогать другие репо аккаунта.

## Success Criteria
1. Приватный `gemuual-star/melodyon` с историей upstream на v14.0.1 + наши коммиты; первый наш коммит — docs/RULES.md.
2. `grep -ri metrolist` по коду и ресурсам пуст, кроме LICENSE.
3. Приложение проверяет обновления только в `gemuual-star/melodyon-releases`.
4. Таблетка SoundCloud первая; без нажатия запросов к SC нет; по нажатию — список треков, воспроизведение, очередь, скачивание, добавление в локальный плейлист, поиск текстов.
5. Собраны подписанный APK и Windows-установщик, оба лежат в релизе публичного репо.

## Out of Scope (Deferred)
- Авторизация SoundCloud, лайки/плейлисты аккаунта SC — нет авторизации.
- Синхронизация SC-плейлистов с YT Music — невозможно.
- Сборки под macOS/Linux — не просили.
- Плейлисты/альбомы/профили SC в выдаче — V2, в V1 только треки.

## Решения по открытым вопросам
1. Ключ подписи: генерируется в песочнице, дополнительно хранится как зашифрованный секрет GitHub Actions в приватном репо. Если песочница сбросится, APK собирается в Actions (Linux) с этим секретом.
2. applicationId `com.melodyon.app`, версия 1.0.0 (versionCode 1).
3. Упоминание Metrolist в UI удаляется полностью.
