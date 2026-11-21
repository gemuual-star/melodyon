# PROGRESS — Melodyon

## Текущее состояние
- База: Metrolist v14.0.1 (commit c7b6590), ветка `main` в `gemuual-star/melodyon` (приватный).
- Публичный репо релизов: `gemuual-star/melodyon-releases` (пустой).
- GitHub Actions в приватном репо ВЫКЛЮЧЕНЫ (чтобы upstream-workflow не жгли минуты). Включить после замены workflow на свои.

## Сделано
- [x] Созданы репозитории, первый коммит: docs/RULES.md, docs/PROGRESS.md, SPEC.md, .gitignore.

## Не проверено
- Ничего из кода ещё не собиралось.

## Следующие шаги
1. Изучить структуру (модули app/desktopApp/shared/innertube...), версии JDK/SDK/Gradle; поставить окружение в песочнице.
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
git clone https://github.com/gemuual-star/melodyon.git /home/user/work/melodyon
# далее — версии JDK/SDK будут дописаны после настройки
```
