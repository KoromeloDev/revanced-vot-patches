# revanced-vot-patches (Voice Over Translation)

Voice Over Translation для **ReVanced Manager v2** — портированный userscript:
патч-бандл с патчем **Voice Over Translation** для YouTube
(`com.google.android.youtube` 20.40.45, 20.05.46, 21.13.164).

## Авторы и источники

- Портировал userscript **[ilyhalight/voice-over-translation](https://github.com/ilyhalight/voice-over-translation)** (MIT).
- Адаптировал под ReVanced: ReVanced Patcher v22 (API из `ReVanced/revanced-patcher`).

## Структура

```
settings.gradle.kts, gradle.properties
patches/
  ManifestPatch.kt              # VotProvider в AndroidManifest (идемпотентный)
  VoiceOverTranslationPatch.kt  # хуки: videoId, громкость плеера/трека, сброс при смене видео
extensions/vot/src/main/java/vot/extension/
  VotProvider.java              # запуск при старте приложения
  VotOverlay.java               # кнопка-индикатор загрузки («文A»), жизненный цикл плеера
  VotController.java            # управление переводом, кэш, синхронизация с плеером
  VotAudioPlayer.java           # MediaPlayer переведённой дорожки
  VotAudioSink.java             # перехват AudioTrack.setVolume/play — ducking оригинала
  VotApiClient.java             # protobuf+HMAC как в vot.js
  VotProtobuf.java, VotOAuth.java, VotVideoId.java, VotSettings.java
  VotSettingsDialog.java        # панель: настройки + «Перевести текущее видео»
patches-list.json               # описание бандла (API v4, опции)
dist/vot-patches-1.0.0.rvp      # готовый бандл (classes.dex + extensions/vot.rve)
```


## Настройки (ключи — те же, что у userscript)

Все опции и ключи — совместимы с `storageKeys` userscript
(`autoTranslate`, `useLivelyVoice`, `responseLanguage`, `sourceLanguage`,
`enabledSmartDucking`, `originalVolume`, `defaultVolume`, `syncVolume`, `proxyWorkerHost`, …).

Опции на телефоне:
- **Громкость перевода** — диапазон 0–100;
- **Автоматически уменьшать громкость видео** — включает ducking оригинала;
- **Громкость оригинала** — насколько приглушать дорожку видео (вкл. пока
  «Автоматически уменьшать…»);
- **Адаптивная громкость** — приглушать оригинал только пока звучит перевод;
- **Связать громкость перевода и видео** — громкость перевода подстраивается под
  системную;
- **Вход через Yandex ID** — OAuth (PKCE) через WebView, как в оригинале.

## Ограничения MVP

- Работает в диапазонах версий патча: `20.40.45`, `20.05.46`, `21.13.164`
  (хуки чувствительны к обфусцированным классам, проверены на 20.40.45).
- Живые голоса («Живые голоса») требуют OAuth-токена Яндекса.
- Кэш переводов (5 последних видео) хранится в памяти процесса и теряется
  при перезапуске YouTube.
- Переведённая аудиозапись позиционируется на позицию видео при выходе
  из паузы и после перемотки (по хуку `PlaybackState.setPlaybackState`).
- Громкость перевода ограничена 100% (Android не даёт превышать
  MediaPlayer volume).

## Сборка

```bash
cd revanced-vot-patches
# Создать ~/.gradle/gradle.properties:
#   gpr.user=<github-логин>
#   gpr.key=<github-token с read:packages>
gradle build :patches:buildAndroid   # → dist/vot-patches-1.0.0.rvp
```

Плагин `app.revanced.patches` v1.0.0-dev.8 и `ReVanced Patcher` v22.0.1
публикуются **только** в GitHub Packages — без токена бандл не соберётся.
Токен нужен только для сборки бандла; после получения `dist/vot-patches-1.0.0.rvp`
его можно отозвать — он больше не пригодится.

## Установка

1. `adb push dist/vot-patches-1.0.0.rvp /sdcard/Download/`
2. ReVanced Manager v2 → **Patches** → ✏️ → **+** → **Select from storage**
3. Указать `.rvp` → патчить YouTube → включить **Voice Over Translation**
4. На видео: правая кнопка («文A») — перевод текущего видео; на маленьком плеере её нет;
   долгое нажатие — настройки.
