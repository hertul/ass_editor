# Редактор субтитров (ASS)

Многоязычная утилита для редактирования субтитров в формате ASS (Advanced SubStation Alpha).  
Позволяет читать, изменять временные метки, текст, стили и синхронизировать субтитры.

## Особенности
- Чтение и запись ASS-файлов (включая заголовки и события).
- Сдвиг времени всех субтитров на заданное количество миллисекунд (синхронизация).
- Поиск и замена текста в субтитрах (регулярные выражения).
- Отображение субтитров в цветном консольном выводе (с поддержкой ANSI).
- Поддержка стилей (чтение и изменение имени стиля для события).
- Экспорт в форматы SRT и TXT (упрощённо).
- Полностью офлайн, без внешних зависимостей (кроме стандартной библиотеки).

## Установка и запуск
Для каждого языка требуются соответствующие инструменты и зависимости (указаны ниже).

### Запуск на разных языках

1. **Python**  
   Запуск: `python ass_editor.py --input input.ass --shift +500 --replace "old" "new" --output output.ass`

2. **JavaScript (Node.js)**  
   Установка: `npm install commander` (или использовать встроенный `process.argv`).  
   Запуск: `node ass_editor.js --input input.ass --shift +500 --replace "old" "new" --output output.ass`

3. **Go**  
   Запуск: `go run ass_editor.go --input input.ass --shift +500 --replace "old" "new" --output output.ass`

4. **Rust**  
   Сборка: `cargo build --release`  
   Запуск: `cargo run -- --input input.ass --shift +500 --replace "old" "new" --output output.ass`

5. **Java**  
   Сборка: `javac ASSEditor.java`  
   Запуск: `java ASSEditor --input input.ass --shift +500 --replace "old" "new" --output output.ass`

6. **C# (.NET Core)**  
   Установка: `dotnet add package System.CommandLine` (опционально).  
   Запуск: `dotnet run -- --input input.ass --shift +500 --replace "old" "new" --output output.ass`

7. **C++ (Linux)**  
   Сборка: `g++ -std=c++11 -o ass_editor ass_editor.cpp`  
   Запуск: `./ass_editor --input input.ass --shift +500 --replace "old" "new" --output output.ass`

8. **Kotlin (JVM)**  
   Сборка: `kotlinc ASSEditor.kt -include-runtime -d ass_editor.jar`  
   Запуск: `java -jar ass_editor.jar --input input.ass --shift +500 --replace "old" "new" --output output.ass`

## Использование

Общие аргументы командной строки (везде, где поддерживается):

- `--input <файл>` – исходный ASS-файл (обязательно).
- `--output <файл>` – файл для сохранения (если не указан, изменения применяются к исходному).
- `--shift <±мс>` – сдвинуть время всех субтитров (положительное или отрицательное число в миллисекундах).
- `--replace <старый> <новый>` – заменить текст (можно использовать регулярные выражения).
- `--style <имя>` – изменить стиль всех событий на указанный.
- `--list` – показать все субтитры в консоли.
- `--export-srt <файл>` – экспортировать в формат SRT.
- `--help` – справка.

Пример (Python):
```bash
python ass_editor.py --input subs.ass --shift +1000 --replace "Hello" "Hi" --output fixed.ass
Структура репозитория
text
/
├── README.md
├── ass_editor.py
├── ass_editor.js
├── ass_editor.go
├── ass_editor.rs
├── ASSEditor.java
├── ASSEditor.cs
├── ass_editor.cpp
└── ASSEditor.kt
Лицензия
MIT
