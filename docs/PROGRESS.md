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
