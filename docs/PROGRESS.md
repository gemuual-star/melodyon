# PROGRESS — Melodyon

## Текущее состояние
- База: Metrolist v14.0.1 (commit c7b6590), ветка `main` в `gemuual-star/melodyon` (приватный).
- Публичный репо релизов: `gemuual-star/melodyon-releases` (пустой).
- GitHub Actions в приватном репо ВЫКЛЮЧЕНЫ (чтобы upstream-workflow не жгли минуты). Включить после замены workflow на свои.

## Сделано
- [x] Созданы репозитории, первый коммит: docs/RULES.md, docs/PROGRESS.md, SPEC.md, .gitignore.
- [x] Удалены upstream workflows (.github/workflows).
- [~] WIP: механический ребрендинг `scripts/melodyon/rebrand.py` (текст + пути, пакеты com.metrolist.* -> com.melodyon.*, com.metrolist.music -> com.melodyon.app).
  Не трогаются внешние идентификаторы: пакет библиотеки `com.metrolist.innertubex` (jitpack), `com.github.MetrolistGroup.{innertubex,quackjs}`,
  пакет из подмодуля `com.metrolist.music.listentogether.proto`, подмодуль metroproto, реестр player_configs (raw.githubusercontent.com/MetrolistGroup/faraday).
  Ещё не сделано: чистка ссылок/контактов (melodyon.cc, github.com/Melodyon/..., t.me, weblate, donate), удаление упоминаний Metrolist в UI.
- [x] Debug-сборка Android после ребрендинга проходит (BUILD SUCCESSFUL, ~10 мин с нуля). Тесты ещё не прогонялись.

- [x] Апдейтер (Android, Desktop, shared LatestRelease) смотрит в `gemuual-star/melodyon-releases`. Версия 1.0.0, versionCode 1. (коммит 250fc19)
- [x] Убраны ссылки/контакты: community links в «О приложении», «Сообщить о проблеме» в меню desktop, ссылка на политику конфиденциальности в онбординге, `melodyon.cc` intent-filter в манифесте.
- [~] «Слушать вместе»: функция отключена навсегда (`Settings.listenTogetherEnabled` всегда false, вход в Интеграциях скрыт, deep links убраны). Код модуля ещё физически в репо — удалить следующим шагом.
- Реестр player_configs MetrolistGroup/faraday оставлен (решение пользователя).

## Не проверено
- Сборка после коммитов 250fc19 и 08445c8 не проверена (песочница сбросилась во время сборки).
- Тесты не прогонялись.
- ВНИМАНИЕ: песочница ~8 ГБ RAM; синхронная сборка Gradle убила песочницу. Запускать в фоне с ограничением памяти (см. ниже) и опрашивать лог.

## Следующие шаги
1. Прогнать тесты (`./gradlew :shared:jvmSharedTest` / `test`), зафиксировать базовое число.
2. Удалить/заменить upstream workflows (.github/workflows) на свои: release-windows.yml (ручной запуск) и android fallback.
3. Ребрендинг metrolist → melodyon, applicationId com.melodyon.app, убрать ссылки/контакты/упоминания.
4. Апдейтер → gemuual-star/melodyon-releases.
5. Модуль SoundCloud + таблетка в поиске + интеграция (плеер, очередь, загрузки, плейлисты, тексты).
6. Keystore, секрет в Actions, сборки, релиз.

## Восстановление окружения
```
# секреты: положить secrets.json в /home/user/.secrets/secrets.json (chmod 600)
# askpass (токен не пишется в URL remote):
#   /home/user/bin/askpass.sh: Username -> x-access-token, Password -> github_token из secrets.json
export GIT_ASKPASS=/home/user/bin/askpass.sh GIT_TERMINAL_PROMPT=0
git clone https://github.com/gemuual-star/melodyon.git /home/user/workspace/melodyon
git -C /home/user/workspace/melodyon submodule update --init --recursive
mkdir -p /home/user/tools && cd /home/user/tools
# JDK 21 и JDK 25 (toolchain проекта = 25, vendor см. jdkVendor в shared/build.gradle.kts)
curl -sSL -o jdk.tgz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" && mkdir jdk21 && tar xzf jdk.tgz -C jdk21 --strip-components=1
curl -sSL -o jdk.tgz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" && mkdir jdk25 && tar xzf jdk.tgz -C jdk25 --strip-components=1
# Android SDK
curl -sSL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip && mkdir -p android-sdk/cmdline-tools && unzip -q clt.zip -d android-sdk/cmdline-tools && mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
export JAVA_HOME=/home/user/tools/jdk21 ANDROID_HOME=/home/user/tools/android-sdk PATH=/home/user/tools/jdk21/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses; sdkmanager --install "platforms;android-37.0" "platforms;android-37.1" "build-tools;37.0.0" platform-tools "ndk;30.0.16248370" "cmake;4.1.2"
echo "sdk.dir=/home/user/tools/android-sdk" > /home/user/workspace/melodyon/local.properties
cd /home/user/workspace/melodyon && ./gradlew :androidApp:assembleDebug -Porg.gradle.java.installations.paths=/home/user/tools/jdk21,/home/user/tools/jdk25 --console=plain
# Gradle 9.8.2 (wrapper), Kotlin 2.4.21, compileSdk 37.1, minSdk 24, targetSdk 37
```

### Сборка с ограничением памяти (рабочий вариант)
```
cd /home/user/workspace/melodyon
nohup ./gradlew :androidApp:assembleDebug -Porg.gradle.java.installations.paths=/home/user/tools/jdk21,/home/user/tools/jdk25 \
  -Dorg.gradle.workers.max=2 "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m" -Pkotlin.compiler.execution.strategy=in-process \
  --console=plain > /tmp/build.log 2>&1 &
```
Git-доступ: credential helper `/home/user/.secrets/gh-cred.sh` читает токен из secrets.json (токен не в URL remote).

## SoundCloud (статус на коммит после ce26844)
- [x] API-слой без авторизации: `shared/.../soundcloud/SoundCloudApi.kt` (поиск, next_href, трек, поток, автообновление client_id).
- [x] `SoundCloudStreamResolver` оборачивает YouTube-резолвер в Koin (`SharedModule.kt`): id вида `sc:<trackId>` идут в SoundCloud. Через него же работают очередь и оба загрузчика (Android `SongDownloadManager`, Desktop `DownloadManager` вызывают `resolveRemote`).
- [x] Трек SoundCloud → `SongItem` (`SoundCloudSongMapping.kt`), значит локальные плейлисты/библиотека используют существующую БД.
- [x] Таблетка «SoundCloud» перед «Все» (`ChipShelf.leadingContent` в `SearchContent.kt`), запрос только по нажатию; подключена в MobileApp, Android SearchScreen, Desktop SearchResultsList. Подгрузка страниц через `loadMoreFilteredResults`.
- [x] Android debug-сборка прошла на коммите 2f3cbaa (до UI-изменений).
- [ ] Не проверено: компиляция после UI-изменений; реальные запросы к SoundCloud; воспроизведение; скачивание HLS-треков (Desktop качает URL как файл, m3u8 может не скачаться); тексты песен для `sc:` (провайдеры ищут по названию/артисту, YouTube-провайдер может получить `sc:` id); радио/автомикс после SC-трека может пытаться звать YouTube `next` с `sc:` id.
- [ ] Удалить код «Слушать вместе» физически.
- [ ] Ключ подписи → секрет GitHub Actions; workflow Windows (ручной запуск) + Android release; публикация в melodyon-releases.

### Тесты
`./gradlew :shared:desktopTest --tests '*SoundCloud*' --tests '*LatestRelease*'` (+ флаги памяти из рецепта выше). Таск называется desktopTest, не jvmTest.

## CI на GitHub Actions (репо публичный с этого момента)
- Сборки и тесты идут в `.github/workflows/ci.yml` на каждый push в main: `:shared:desktopTest` (он зависит от `:shared:testServerWithoutReflection`), затем `:androidApp:assembleDebug`; APK и результаты тестов — артефакты прогона.
- Песочница (4 ядра, 8 ГБ) для Gradle не годится: только правка кода, коммит, push, чтение логов через API.
- Коммит 11880756: компиляция shared прошла; 79 тестов прогнано в testServerWithoutReflection, 23 упали — все в `ListenTogetherManagerLifecycleTest` (функция удалена по решению пользователя). Тест удалён вместе с выключением функции; остальной код «Слушать вместе» удалить физически позже.

## Зелёный CI (коммит a206eb8, прогон 44930447032)
- `:shared:desktopTest`: 3808 тестов, 0 упало, 7 пропущено. `:androidApp:assembleDebug` прошёл, APK в артефакте `android-debug-apk`.
- Исправлено: эталон отпечатка DeviceConnect (метка переименована в melodyon-device-connect-v2), удалены тесты менеджера «Слушать вместе» (Lifecycle, Sync).
- Не проверено: работа SoundCloud вживую (запросы, воспроизведение, HLS-скачивание, тексты, радио после SC-трека), запуск на устройстве, Windows.
- Следующие шаги: 1) физически удалить код «Слушать вместе»; 2) ключ подписи → секреты Actions, release-workflow Android + Windows (ручной запуск) с публикацией в gemuual-star/melodyon-releases; 3) доработать SC: HLS-скачивание, тексты для sc:, радио/автомикс без YouTube next для sc:.

## Релиз (подпись + Windows)
- Ключ подписи Android: PKCS12, alias `melodyon`, создан в песочнице (`/home/user/.secrets/melodyon-release.p12`), хранится в секретах репо: KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. В git и на Drive его нет. Если песочница сброшена — ключ всё равно доступен CI через секреты.
- `.github/workflows/release.yml` (ручной запуск): подписанный APK (ubuntu) + Windows x64 (windows-latest, helper на .NET, `:desktopApp:assembleWindowsRelease`). Публикацию в `gemuual-star/melodyon-releases` делает агент из песочницы (скачивает артефакты, создаёт релиз через API) — токен в CI не кладётся.
- CI разделён на два параллельных job: тесты и debug APK.
- Release-прогон 44932470999 (коммит df0b3e1): оба job зелёные. Опубликовано как prerelease v1.0.0 в melodyon-releases: Melodyon-1.0.0-android.apk (подписан), Melodyon-1.0.0-windows-x64.zip (портативный, не установщик). На устройствах не запускалось. Апдейтер смотрит releases/latest, prerelease он не видит — это намеренно, пока сборка тестовая.
- CI на df0b3e1 зелёный.
- Следующее: SoundCloud (HLS-скачивание, тексты, радио), физическое удаление «Слушать вместе», установщик Windows (сейчас ZIP).

## SoundCloud: исправление воспроизведения/скачивания (коммиты b1e36cf..6d92633)
- Причина ошибки: SoundCloud отдаёт почти все треки только как HLS AAC (fMP4); мы выбирали abr_sq (api возвращает 404) и не передавали track_authorization; mime HLS был в регистре, который ExoPlayer не узнаёт.
- Исправлено: выбор aac_160k HLS, пропуск abr_* и DRM (cbc/ctr-encrypted-hls — такие треки скрываются из выдачи), track_authorization, mime application/x-mpegURL.
- Скачивание HLS: плейлист + init-сегмент (#EXT-X-MAP) + сегменты в один файл (Android AudioCacheManager, Desktop DownloadManager).
- Радио после SC-трека: SoundCloud /tracks/{id}/related. Тексты: провайдеры ищут по названию/артисту (videoId sc: не передаётся).
- CI на 6d92633: 3860 тестов, 0 упало, 7 пропущено; debug APK собран. Release build успешен; ассеты v1.0.0 (prerelease) в melodyon-releases заменены.
- Живая проверка из песочницы: поиск, client_id, получение HLS-плейлиста и сегментов работают. В приложении на устройстве НЕ проверено.
- Не проверено: воспроизведение HLS fMP4 на Desktop (ffmpeg открывает URL напрямую); скачанный файл fMP4 на Android в .m4a.

## Релиз v1.0.1 (коммит 7815dda)
- appVersion=1.0.1 (gradle.properties), versionCode=2 (androidApp/build.gradle.kts).
- CI 44938469854 и Release build 44938468645 на 7815dda зелёные. Подпись APK тем же сертификатом, что и 1.0.0 (SHA-256 4e038a3a…cf9e).
- Опубликован как обычный (не prerelease) релиз v1.0.1 в gemuual-star/melodyon-releases, отмечен latest.
- ВАЖНО, имена ассетов: Android-апдейтер (`AppUpdateManager.GitHubRelease.apk`) берёт APK только с именем `Melodyon-Android-<версия>.apk` (или из PREFERRED_APK_NAMES) и требует sha256-digest ассета. Windows-апдейтер ставит только `melodyon-installer-windows-x64-<версия>.exe` в зарегистрированную установку; портативный ZIP сам не обновляется.
- Не проверено: обновление 1.0.0 → 1.0.1 изнутри приложения на устройстве; работа SoundCloud в приложении.
- Публикация: скачать артефакты android-release и windows-x64 прогона Release build, создать draft-релиз через API, загрузить ассеты на uploads.github.com, снять draft.

## Исправление: скачанный трек SoundCloud показывал 0:30 и не перематывался
- Причина: AAC-поток SoundCloud — фрагментированный MP4 (init + сегменты по ~10 с, у каждого свой `sidx`). Скачивание просто склеивало сегменты, и плеер считал длиной трека один сегмент.
- Решение: `shared/.../soundcloud/FragmentedMp4Remuxer.kt` пересобирает сегменты в обычный M4A (`ftyp`, `mdat` на сегмент, в конце `moov` с полной таблицей сэмплов). Подключено в `SoundCloudApi.downloadHls` (общий код для Android и Desktop). MP3-сегменты по-прежнему просто склеиваются.
- Проверено в песочнице на реальном треке SoundCloud (22 сегмента): ffprobe показывает 216.19 с при длине трека 216.19 с, перемотка на 150 с декодируется, PCM совпадает побайтно со старой склейкой. Юнит-тест `FragmentedMp4RemuxerTest` (3 теста) прошёл локально на kotlinc 2.2.20.
- Не проверено: в приложении на устройстве. Уже скачанные треки остаются битыми — их нужно удалить и скачать заново.
- Локальная проверка без Gradle: kotlinc в `/home/user/kt/kotlinc` (скачать `kotlin-compiler-2.2.20.zip` с релизов JetBrains/kotlin), тестовые сегменты в `/home/user/sc`.

## Релиз v1.0.2 (коммит f0281be)
- appVersion=1.0.2, versionCode=3. CI 44941107232: 3863 теста, 0 упало, 7 пропущено. Release build 44941106732 зелёный (Android + Windows x64).
- Опубликован как latest в gemuual-star/melodyon-releases: `Melodyon-Android-1.0.2.apk`, `Melodyon-1.0.2-windows-x64.zip`.
- Подпись APK: сертификат совпадает с 1.0.0/1.0.1 (SHA-256 4e038a3a…cf9e).
- Не проверено: обновление изнутри приложения, воспроизведение скачанного трека SoundCloud на устройстве.

## После 1.0.2: источники текстов, установщик Windows, версия 1.0.3 (в работе)
- Тексты: добавлены источники NETEASE (music.163.com), QQMUSIC (u.y.qq.com + c.y.qq.com), LYRICSOVH (api.lyrics.ovh, без тайминга) — `shared/.../api/lyrics/{NetEase,QQMusic,LyricsOvh}LyricsProvider.kt`, общий подбор записи `LyricsCandidateMatch.kt` (совпадение названия + длительность ±8 с, без длительности — совпадение артиста). Добавлены в `LyricsProvider.userConfigurable` (в конец, перед YouTube-фолбэками), в настройки и диалог выбора источника. Тест `ExtraLyricsProvidersTest`. Коммит 1779c6c, CI на момент записи ещё шёл.
- Живая проверка API из песочницы: NetEase (поиск + LRC), QQ Music (поиск + LRC), lyrics.ovh отвечают 200. В приложении не проверено.
- Установщик Windows: исходники MetrolistManager (GPL-3.0, коммит 18d2846) скопированы в `windows-installer/` и переименованы (модуль `github.com/gemuual-star/melodyon/windows-installer`, appId `com.melodyon.desktop`, `Melodyon.exe`). Job `windows-installer` в `release.yml`: go test/vet, проверка ZIP, сборка `melodyon-installer-windows-x64-<версия>.exe`, e2e-тест установки. Установщик скачивает `Melodyon-Desktop-windows.zip` из релиза `v<версия>` в gemuual-star/melodyon-releases — ассет в релизе должен называться именно так.
- appVersion=1.0.3, versionCode=4. Релиз 1.0.3 НЕ опубликован: сначала должны пройти CI и Release build.
- Не сделано: физическое удаление кода «Слушать вместе» (77 файлов вне каталогов listentogether ссылаются на него); UX/UI (ждёт ответа пользователя, что именно менять).
- CI 45018000226 на 1779c6c: debug APK собран; тесты 3869, 1 упал (`LegacySettingsMigrationTest.storedLyricsProvidersDropRetiredSourcesAndKeepTheRemainingOrder` — в ожидаемом порядке не было трёх новых источников, которые дописываются в конец). Тест обновлён следующим коммитом; результат этого коммита смотреть в Actions.
- Release build 45018327295 на 0d4d095 запущен вручную (первый прогон с job `windows-installer`), результат на момент записи неизвестен.

## Состояние на момент остановки (ждём ответа пользователя по UX/UI)
- Release build 45018327295 на 0d4d095: in_progress/None; Android release APK=completed/success, Windows x64=in_progress/None
- CI 45018706877 на df83103: in_progress/None; Android debug APK=in_progress/None, Shared tests=in_progress/None
- Настройка цвета текста песни уже есть: `Settings.lyricsNeutralColor` (Настройки → Тексты → Отображение → «Цвет текста песни»: «Следовать теме» / «Нейтральная» = белый или чёрный по яркости фона, см. `resolveLyricsTextColor` в `LyricsView.kt`). Новый код не писался.
- Следующие шаги: 1) дождаться CI на df83103 и Release build 45018327295 (первый прогон job `windows-installer`), чинить по логам; 2) опубликовать v1.0.3 в melodyon-releases: `Melodyon-Android-1.0.3.apk`, `Melodyon-Desktop-windows.zip` (имя обязательно такое — его качает установщик), `melodyon-installer-windows-x64-1.0.3.exe`; 3) UX/UI по ответам пользователя; 4) физическое удаление кода «Слушать вместе».

## Прокси (коммит ee10511)
- [x] Настройки → Конфиденциальность → «Прокси»: HTTP/HTTPS или SOCKS5, хост, порт, логин/пароль (необязательно), «Проверить подключение» (внешний IP через api.ipify.org), «Отключать, если недоступен» (5 неудач подряд → выкл + уведомление в футере группы).
- [x] Маршрутизация: `MelodyonProxy` (jvmSharedMain) — глобальный ProxySelector + java.net.Authenticator (ExoPlayer/HttpURLConnection), proxySelector/proxyAuthenticator в sharedOkHttpClient (API, SoundCloud, обложки Coil на Android, скачивания), `http_proxy` для FFmpeg на desktop. Локальные адреса идут напрямую.
- [x] Тесты: ProxySettingsTest (commonTest), MelodyonProxyLiveTest (desktopTest; работает только с секретом репо MELODYON_TEST_PROXY=host:port:user:pass). CI ee10511: 3875 тестов, 0 упало.
- [ ] Не проверено: прокси в самом приложении; SOCKS5 с логином на Android; вывод live-теста в логе CI не виден (stdout тестов не печатается).
- Релиз 1.0.3: запущен workflow «Release build»; после него вручную загрузить APK/ZIP/установщик в melodyon-releases (тег v1.0.3, latest).

## Релиз 1.0.4 (прокси-фикс)
- Пользователь: «ошибка подключения» к прокси в 1.0.3. На JVM с OkHttp 5.5.1 HTTP-прокси disp.oxylabs.io:8001 работает (IP 48.47.189.242); SOCKS5 падал «authentication failed», потому что Authenticator принимал только requestorType=PROXY.
- Исправлено (ad3a08c, 5331de5): разбор host:port и user:pass@host:port в поле хоста, trim логина/пароля, SOCKS5-авторизация, понятные ошибки, versionCode 5.
- CI: 3880 тестов, 0 упало, 7 пропущено. Release build успешен. Опубликован v1.0.4 (latest) в melodyon-releases.
- Не проверено: прокси в приложении на устройствах; точная причина ошибки у пользователя не воспроизведена.
- Публикация релиза: artifacts из Release build → API melodyon-releases (имена Melodyon-Android-X.apk, Melodyon-X-windows-x64-setup.exe, Melodyon-X-windows-x64.zip). Перед релизом поднимать appVersion в gradle.properties и versionCode в androidApp/build.gradle.kts.

## Релиз 1.0.5 (прокси через собственный туннель + отдельная страница)
- Пользователь (телефон): SOCKS5 «authentication failed», HTTP «unexpected end of stream». Прокси oxylabs: на CONNECT без авторизации отвечает 407; SOCKS5 user/pass проверен сырым сокетом — работает.
- Решение: `ProxyTunnel.kt` (jvmSharedMain) — свой SocketFactory: SOCKS5 RFC1928/1929 с логином и HTTP CONNECT с предварительным Proxy-Authorization; DNS цели на стороне прокси (ProxyAwareDns). sharedOkHttpClient: proxy=NO_PROXY + socketFactory/dns. ExoPlayer на Android переведён на OkHttpDataSource (media3-datasource-okhttp) с sharedOkHttpClient.
- Настройки: отдельная страница SettingsPageId.PROXY (корневая группа «Конфиденциальность и безопасность»), разделы Подключение/Сервер/Авторизация/Проверка/Надёжность, карточка результата проверки.
- Тесты: ProxyTunnelTest (фейковые SOCKS5/HTTP-серверы). CI cfbc8ff: 3885 тестов, 0 упало, 7 пропущено; debug APK собран.
- Не проверено: на телефоне; live-тест с реальным прокси в логах CI не виден.

## Аудит телеметрии (план s1)
Найдено и сделано:
- Sentry Metrolist (DSN o4511744603193344.ingest.de.sentry.io) был зашит в Android release, Desktop и iOS. Отправка была по согласию (по умолчанию выкл.). Удалено: DSN стёрт, `isCrashReportingAvailable()` всегда false (скрывает согласие в онбординге и настройках), Android release использует no-op `SentryManager`/`PlaybackErrorReporter`, убраны Sentry Gradle-плагин, `sentry-android-ndk` и meta-data в release-манифесте. Desktop ещё содержит библиотеку io.sentry, но без DSN и никогда не инициализируется.
- Lyprio (тексты, сервер Metrolist): ключи берутся из секретов CI LYPRIO_*; у нас их нет, провайдер не создаётся. Оставлен выключенным.
- Остались сторонние сервисы, не Metrolist: загрузка лога сбоя на paste.crdroid.net (только по кнопке пользователя), Spotify Canvas через spotify-canva.vercel.app, BetterLyrics/Unison (boidu.dev), реестр player_configs на GitHub Metrolist (решение пользователя оставить).
- В «О приложении» ещё есть список участников Metrolist со ссылками (AboutContent.kt) — убрать вместе с UI-шагом.
- Ссылки melodyon.cc остались только в коде «Слушать вместе» и разборе ссылок — уйдут в шаге s2.

## Удаление «Слушать вместе» (шаг s2 плана, ветка wip/remove-listen-together)
- Удалены: модули shared/android/desktop listentogether, ui/listentogether, listenTogetherProto, экраны, тесты.
- Проверки «гость комнаты» заменены на false и упрощены (скрипт подстановок), объявления isListenTogetherGuest() убраны.
- Идёт итеративная починка компиляции через CI на ветке (workflow_dispatch ci.yml ref=wip/remove-listen-together). После зелёного CI — fast-forward merge в main (без force push).
- Осталось: Android/desktop ссылки (MelodyonApp, MiniPlayer, PlayerState, AppContentHost, меню), Screen/NavigationId, SettingsPageId.LISTEN_TOGETHER, параметры isListenTogetherGuest в UI плеера, строки ресурсов listen_together_*, ключи Settings.

### Итог шага s2 (коммит 98c51158, влит в main fast-forward)
- Код «Слушать вместе» удалён из shared/Android/Windows; CI на ветке зелёный: 3836 тестов, 0 упало, 7 пропущено; debug APK собран.
- Ключи настроек listenTogether* в Settings.kt оставлены как мёртвые поля (для совместимости бэкапов/миграции), UI их не использует.
- Не проверено: компиляция desktopApp (Windows) — только через release.yml (запущен после мержа).
