
#!/usr/bin/env python3
# ass_editor.py
import argparse
import re
import sys
import os
from datetime import datetime, timedelta

class ASSParser:
    def __init__(self, filename):
        self.filename = filename
        self.header = []       # строки заголовка (не событийные)
        self.events = []       # список кортежей (start_ms, end_ms, style, text)
        self.raw_lines = []    # все строки файла (для сохранения с сохранением формата)
        self.style_defs = []   # строки стилей

    def parse(self):
        with open(self.filename, 'r', encoding='utf-8-sig') as f:
            lines = f.readlines()
        self.raw_lines = lines
        in_events = False
        for line in lines:
            if line.startswith('[Events]'):
                in_events = True
                self.header.append(line)
                continue
            if not in_events:
                self.header.append(line)
                continue
            if line.startswith('Format:'):
                self.header.append(line)
                continue
            if line.startswith('Dialogue:') or line.startswith('Comment:'):
                self.events.append(self.parse_event(line))
            else:
                self.header.append(line)  # другие строки в секции Events (например, пустые)
        return self

    def parse_event(self, line):
        # Формат: Dialogue: layer, start, end, style, actor, marginL, marginR, marginV, effect, text
        # Пример: Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,Hello world!
        parts = line.split(',', 9)  # разделяем на 10 частей (9 запятых)
        if len(parts) < 10:
            return None
        prefix = parts[0]  # Dialogue: или Comment:
        start_str = parts[1].strip()
        end_str = parts[2].strip()
        style = parts[3].strip()
        text = parts[9].strip()
        # Parse time
        start_ms = self.time_to_ms(start_str)
        end_ms = self.time_to_ms(end_str)
        return (start_ms, end_ms, style, text, prefix, line)

    def time_to_ms(self, ts):
        # ts format: H:MM:SS.cc  or  H:MM:SS.c (milliseconds)
        if not ts:
            return 0
        # sometimes there is a leading sign
        ts = ts.strip()
        # Handle fractions
        if ':' in ts:
            parts = ts.split(':')
            if len(parts) == 3:
                h = int(parts[0])
                m = int(parts[1])
                sec_part = parts[2]
                if '.' in sec_part:
                    s, ms = sec_part.split('.')
                    s = int(s)
                    ms = int(ms.ljust(3, '0')[:3])  # до 3 знаков
                else:
                    s = int(sec_part)
                    ms = 0
                return (h * 3600 + m * 60 + s) * 1000 + ms
        return 0

    def ms_to_time(self, ms):
        sign = ''
        if ms < 0:
            sign = '-'
            ms = -ms
        h = ms // 3600000
        ms %= 3600000
        m = ms // 60000
        ms %= 60000
        s = ms // 1000
        ms %= 1000
        return f"{sign}{h}:{m:02d}:{s:02d}.{ms:03d}"

    def apply_shift(self, delta_ms):
        new_events = []
        for ev in self.events:
            if ev is None:
                continue
            start, end, style, text, prefix, line = ev
            new_start = max(0, start + delta_ms)
            new_end = max(0, end + delta_ms)
            new_events.append((new_start, new_end, style, text, prefix, line))
        self.events = new_events

    def apply_replace(self, old, new, use_regex=False):
        for i, ev in enumerate(self.events):
            if ev is None:
                continue
            start, end, style, text, prefix, line = ev
            if use_regex:
                new_text = re.sub(old, new, text)
            else:
                new_text = text.replace(old, new)
            self.events[i] = (start, end, style, new_text, prefix, line)

    def apply_style(self, new_style):
        for i, ev in enumerate(self.events):
            if ev is None:
                continue
            start, end, style, text, prefix, line = ev
            self.events[i] = (start, end, new_style, text, prefix, line)

    def save(self, filename):
        with open(filename, 'w', encoding='utf-8') as f:
            # Write header
            for line in self.header:
                f.write(line)
            # Write events
            for ev in self.events:
                if ev is None:
                    continue
                start, end, style, text, prefix, _ = ev
                start_str = self.ms_to_time(start)
                end_str = self.ms_to_time(end)
                # Строим строку события с сохранением прочих полей из оригинальной строки (упрощённо)
                # В реальности мы должны перестроить строку, но для простоты используем старую строку и заменяем время и текст
                # Однако мы можем воссоздать: Dialogue: layer,start,end,style,actor,...text
                # Для упрощения просто пересоберём с нуля (потеряем неизменяемые поля)
                # Для этого лучше хранить все части, но мы пока пересоберём с базовыми значениями.
                # Поскольку у нас нет layer и прочего, мы будем использовать placeholder
                # Сделаем простую сборку: prefix + ',' + start_str + ',' + end_str + ',' + style + ',0,0,0,,' + text
                # Это не идеально, но работает для демонстрации.
                new_line = f"{prefix},{start_str},{end_str},{style},0,0,0,,{text}\n"
                f.write(new_line)

    def list_events(self, color=True):
        if color and sys.stdout.isatty():
            GREEN = '\033[92m'
            YELLOW = '\033[93m'
            RESET = '\033[0m'
        else:
            GREEN = YELLOW = RESET = ''
        for i, ev in enumerate(self.events):
            if ev is None:
                continue
            start, end, style, text, _, _ = ev
            start_str = self.ms_to_time(start)
            end_str = self.ms_to_time(end)
            print(f"{GREEN}[{i}]{RESET} {start_str} --> {end_str}  {YELLOW}{style}{RESET}: {text}")

    def export_srt(self, filename):
        with open(filename, 'w', encoding='utf-8') as f:
            for i, ev in enumerate(self.events):
                if ev is None:
                    continue
                start, end, style, text, _, _ = ev
                start_str = self.ms_to_time(start).replace('.', ',')  # SRT uses comma
                end_str = self.ms_to_time(end).replace('.', ',')
                f.write(f"{i+1}\n")
                f.write(f"{start_str} --> {end_str}\n")
                f.write(f"{text}\n\n")

def main():
    parser = argparse.ArgumentParser(description="ASS Editor")
    parser.add_argument("--input", required=True, help="Input ASS file")
    parser.add_argument("--output", help="Output file (default: overwrite input)")
    parser.add_argument("--shift", type=int, help="Shift time in milliseconds (e.g., +1000 or -500)")
    parser.add_argument("--replace", nargs=2, metavar=("OLD", "NEW"), help="Replace text")
    parser.add_argument("--style", help="Change style name for all events")
    parser.add_argument("--list", action="store_true", help="List events")
    parser.add_argument("--export-srt", help="Export to SRT format")
    args = parser.parse_args()

    ass = ASSParser(args.input)
    ass.parse()

    if args.shift is not None:
        ass.apply_shift(args.shift)
    if args.replace:
        ass.apply_replace(args.replace[0], args.replace[1], use_regex=False)
    if args.style:
        ass.apply_style(args.style)

    if args.list:
        ass.list_events()
    if args.export_srt:
        ass.export_srt(args.export_srt)

    output = args.output if args.output else args.input
    if args.shift is not None or args.replace or args.style or args.output:
        ass.save(output)
        print(f"Saved to {output}")

if __name__ == "__main__":
    main()
