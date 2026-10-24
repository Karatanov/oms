# Пользовательское подключение Google Drive к OMS

## Созданная конфигурация Google Cloud

- Проект: OMS Procurement (`oval-blend-512511-m7`, номер `577716690911`).
- Google Drive API включён.
- OAuth приложение: OMS, External, Testing.
- В тестовой аудитории отображается `Karatanov@gmail.com`.
- Веб-клиент: OMS Web.
- Client ID: `577716690911-q48mveqt36d5894659eli86chonkb6vb.apps.googleusercontent.com`.
- Origin: `https://ua-oms.com`.
- Redirect URI: `https://ua-oms.com/api/v1/auth/google/callback`.
- Client Secret не хранится в репозитории. Новый JSON проверен и установлен на VPS: `/docker/secrets/oms-google-oauth-client.json`, root:root, 0600. Compose overlay скопирован на VPS и проходит `docker compose config --quiet`; приложение ещё не развёрнуто с OAuth.

## Настройка сервера

Сохраните JSON веб-клиента Google вне репозитория и образа контейнера.
Он содержит объект `web` с `client_id` и `client_secret`; это не ключ сервисного аккаунта.
Задайте `OMS_GOOGLE_OAUTH_CLIENT_FILE` как абсолютный путь к этому файлу внутри сервера.
Альтернатива: секретные переменные `OMS_GOOGLE_CLIENT_ID` и `OMS_GOOGLE_CLIENT_SECRET`.
`OMS_GOOGLE_REDIRECT_URI` должен точно совпадать с адресом выше.

Для VPS скопируйте `deploy/vps/docker-compose.google-drive.yml` рядом с основным
Compose-файлом. В серверном `.env` задайте:

```dotenv
COMPOSE_FILE=docker-compose.yml:docker-compose.google-drive.yml
GOOGLE_OAUTH_CLIENT_FILE=/docker/secrets/oms-google-oauth-client.json
```

Файл должен быть доступен пользователю контейнера, с ограниченным доступом на хосте.
`docker compose up -d` и существующий `deploy.sh` прочитают `COMPOSE_FILE` из `.env`.
Overlay отключает фоновое обновление: пользовательский Google-токен выдаётся на импорт.
Развёртывание OMS выполняется на VPS ua-oms.com, не на Render.

## Поведение

Администратор нажимает «Оновити з Google Drive», выбирает Google-аккаунт и
подтверждает разрешение. Сервер принимает callback, после чего основной экран
запускает существующее задание импорта с прогрессом и отменой. Ручная загрузка XLSX сохранена.

Текущая реализация прямой загрузки использует `drive.readonly`. Google показывает
доступ на чтение всех файлов пользователя; код скачивает только фиксированный
файл `1tN1kbjw94g3zHKLRNDjgryuBXbYWuk2j`. Владелец выбрал прямое обновление
с резервной загрузкой XLSX; scope сохранён в Google Cloud.
Для ограничения доступа одним файлом нужна отдельная интеграция Google Picker с `drive.file`.
Для внешнего публичного приложения restricted scope может требовать проверки Google.
Сейчас запуск доступен только аккаунтам из тестовой аудитории.

OAuth использует одноразовый state, привязку к пользователю/браузеру и PKCE.
Access token остаётся на сервере, не передаётся JavaScript, не сохраняется в БД.
Одноразовый билет импорта действует до 10 минут. Неиспользованные данные очищаются
ежеминутно, токен задания освобождается при завершении. Refresh token не запрашивается.
При перезапуске сервера требуется повторное подключение. Эта схема рассчитана на один
экземпляр сервера; для нескольких экземпляров потребуется общий защищённый storage.

## Проверка перед эксплуатацией

1. Настроить секрет на сервере (scope в Cloud уже сохранён).
2. Собрать сервер и frontend, выполнить тесты.
3. Проверить разрешение Google, отказ/закрытие окна, повтор callback и успешный импорт.
4. Проверить отсутствие OAuth code/token в reverse-proxy access logs: query callback
   нельзя журналировать. Ktor исключает callback из CallLogging.
5. После тестового импорта сравнить записи реестра с исходной книгой.

Исходная серверная загрузка по публичной ссылке/сервисному аккаунту оставлена для
явно настроенных фоновых задач; пользовательская кнопка использует отдельный OAuth API.
