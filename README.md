# Alcohol

Android-приложение для домашней варки пива и сидра: рецепты, себестоимость, дневник партий и напоминания о переливе, розливе и других этапах.

## Стек

- Kotlin, Jetpack Compose (Material 3)
- Room (SQLite) для хранения
- Запланировано: WorkManager для напоминаний

## Структура

- `app/src/main/java/com/example/alcohol/MainActivity.kt`: две вкладки «Пиво» и «Сидр».
- `app/src/main/java/com/example/alcohol/data/`: сущности Room (ингредиенты, рецепты, партии, этапы) и база данных.

## Сборка

Открыть папку в Android Studio и запустить конфигурацию `app`. Gradle wrapper в репозиторий пока не добавлен, Android Studio создаст его при первой синхронизации.
