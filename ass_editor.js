#!/usr/bin/env node
// ass_editor.js
const fs = require('fs');
const path = require('path');

class ASSParser {
    constructor(filename) {
        this.filename = filename;
        this.header = [];
        this.events = [];
        this.rawLines = [];
    }

    parse() {
        const content = fs.readFileSync(this.filename, 'utf8');
        const lines = content.split(/\r?\n/);
        this.rawLines = lines;
        let inEvents = false;
        for (const line of lines) {
            if (line.startsWith('[Events]')) {
                inEvents = true;
                this.header.push(line);
                continue;
            }
            if (!inEvents) {
                this.header.push(line);
                continue;
            }
            if (line.startsWith('Format:')) {
                this.header.push(line);
                continue;
            }
            if (line.startsWith('Dialogue:') || line.startsWith('Comment:')) {
                const ev = this.parseEvent(line);
                if (ev) this.events.push(ev);
            } else {
                this.header.push(line);
            }
        }
        return this;
    }

    parseEvent(line) {
        const parts = line.split(',', 9);
        if (parts.length < 10) return null;
        const prefix = parts[0];
        const startStr = parts[1].trim();
        const endStr = parts[2].trim();
        const style = parts[3].trim();
        const text = parts.slice(9).join(',').trim(); // остаток после 9-й запятой
        const startMs = this.timeToMs(startStr);
        const endMs = this.timeToMs(endStr);
        return { startMs, endMs, style, text, prefix, raw: line };
    }

    timeToMs(ts) {
        if (!ts) return 0;
        const parts = ts.split(':');
        if (parts.length !== 3) return 0;
        const h = parseInt(parts[0]);
        const m = parseInt(parts[1]);
        const secPart = parts[2];
        let s = 0, ms = 0;
        if (secPart.includes('.')) {
            const [sec, millis] = secPart.split('.');
            s = parseInt(sec);
            ms = parseInt(millis.padEnd(3, '0').slice(0, 3));
        } else {
            s = parseInt(secPart);
        }
        return (h * 3600 + m * 60 + s) * 1000 + ms;
    }

    msToTime(ms) {
        let sign = '';
        if (ms < 0) { sign = '-'; ms = -ms; }
        const h = Math.floor(ms / 3600000);
        ms %= 3600000;
        const m = Math.floor(ms / 60000);
        ms %= 60000;
        const s = Math.floor(ms / 1000);
        ms %= 1000;
        return `${sign}${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}.${String(ms).padStart(3,'0')}`;
    }

    applyShift(deltaMs) {
        for (const ev of this.events) {
            ev.startMs = Math.max(0, ev.startMs + deltaMs);
            ev.endMs = Math.max(0, ev.endMs + deltaMs);
        }
    }

    applyReplace(oldStr, newStr) {
        for (const ev of this.events) {
            ev.text = ev.text.replace(new RegExp(oldStr, 'g'), newStr);
        }
    }

    applyStyle(newStyle) {
        for (const ev of this.events) {
            ev.style = newStyle;
        }
    }

    save(filename) {
        const lines = [];
        // Сохраняем заголовок
        for (const h of this.header) {
            lines.push(h);
        }
        // Сохраняем события
        for (const ev of this.events) {
            const start = this.msToTime(ev.startMs);
            const end = this.msToTime(ev.endMs);
            // Простая сборка, теряем некоторые поля
            const newLine = `${ev.prefix},${start},${end},${ev.style},0,0,0,,${ev.text}`;
            lines.push(newLine);
        }
        fs.writeFileSync(filename, lines.join('\n'), 'utf8');
        console.log(`Saved to ${filename}`);
    }

    listEvents(color = true) {
        const GREEN = color ? '\x1b[92m' : '';
        const YELLOW = color ? '\x1b[93m' : '';
        const RESET = color ? '\x1b[0m' : '';
        for (let i = 0; i < this.events.length; i++) {
            const ev = this.events[i];
            const start = this.msToTime(ev.startMs);
            const end = this.msToTime(ev.endMs);
            console.log(`${GREEN}[${i}]${RESET} ${start} --> ${end}  ${YELLOW}${ev.style}${RESET}: ${ev.text}`);
        }
    }

    exportSrt(filename) {
        let content = '';
        for (let i = 0; i < this.events.length; i++) {
            const ev = this.events[i];
            const start = this.msToTime(ev.startMs).replace('.', ',');
            const end = this.msToTime(ev.endMs).replace('.', ',');
            content += `${i+1}\n${start} --> ${end}\n${ev.text}\n\n`;
        }
        fs.writeFileSync(filename, content, 'utf8');
        console.log(`Exported SRT to ${filename}`);
    }
}

function main() {
    const args = process.argv.slice(2);
    const opts = {};
    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '--input': opts.input = args[++i]; break;
            case '--output': opts.output = args[++i]; break;
            case '--shift': opts.shift = parseInt(args[++i]); break;
            case '--replace': opts.replace = [args[++i], args[++i]]; break;
            case '--style': opts.style = args[++i]; break;
            case '--list': opts.list = true; break;
            case '--export-srt': opts.exportSrt = args[++i]; break;
        }
    }
    if (!opts.input) {
        console.error('Error: --input required');
        process.exit(1);
    }
    const ass = new ASSParser(opts.input);
    ass.parse();

    if (opts.shift !== undefined) ass.applyShift(opts.shift);
    if (opts.replace) ass.applyReplace(opts.replace[0], opts.replace[1]);
    if (opts.style) ass.applyStyle(opts.style);

    if (opts.list) ass.listEvents();
    if (opts.exportSrt) ass.exportSrt(opts.exportSrt);

    if (opts.shift !== undefined || opts.replace || opts.style || opts.output) {
        const out = opts.output || opts.input;
        ass.save(out);
    }
}

if (require.main === module) main();
