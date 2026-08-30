// ass_editor.rs
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use regex::Regex;
use clap::{App, Arg};

struct Event {
    prefix: String,
    start_ms: i32,
    end_ms: i32,
    style: String,
    text: String,
    raw: String,
}

struct ASSParser {
    header: Vec<String>,
    events: Vec<Event>,
}

impl ASSParser {
    fn new() -> Self {
        ASSParser { header: Vec::new(), events: Vec::new() }
    }

    fn parse(&mut self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let content = fs::read_to_string(filename)?;
        let lines: Vec<&str> = content.lines().collect();
        let mut in_events = false;
        for line in lines {
            if line.starts_with("[Events]") {
                in_events = true;
                self.header.push(line.to_string());
                continue;
            }
            if !in_events {
                self.header.push(line.to_string());
                continue;
            }
            if line.starts_with("Format:") {
                self.header.push(line.to_string());
                continue;
            }
            if line.starts_with("Dialogue:") || line.starts_with("Comment:") {
                if let Ok(ev) = Self::parse_event(line) {
                    self.events.push(ev);
                }
            } else {
                self.header.push(line.to_string());
            }
        }
        Ok(())
    }

    fn parse_event(line: &str) -> Result<Event, Box<dyn std::error::Error>> {
        let parts: Vec<&str> = line.splitn(10, ',').collect();
        if parts.len() < 10 {
            return Err("Invalid event format".into());
        }
        let prefix = parts[0].to_string();
        let start_str = parts[1].trim();
        let end_str = parts[2].trim();
        let style = parts[3].trim().to_string();
        let text = parts[9].trim().to_string();
        let start_ms = Self::time_to_ms(start_str)?;
        let end_ms = Self::time_to_ms(end_str)?;
        Ok(Event { prefix, start_ms, end_ms, style, text, raw: line.to_string() })
    }

    fn time_to_ms(ts: &str) -> Result<i32, Box<dyn std::error::Error>> {
        if ts.is_empty() { return Ok(0); }
        let parts: Vec<&str> = ts.split(':').collect();
        if parts.len() != 3 { return Err("Invalid time".into()); }
        let h: i32 = parts[0].parse()?;
        let m: i32 = parts[1].parse()?;
        let sec_part = parts[2];
        let (s, ms) = if let Some((sec, millis)) = sec_part.split_once('.') {
            let s: i32 = sec.parse()?;
            let ms_str = format!("{:0<3}", &millis[..millis.len().min(3)]);
            let ms: i32 = ms_str.parse()?;
            (s, ms)
        } else {
            (sec_part.parse()?, 0)
        };
        Ok((h * 3600 + m * 60 + s) * 1000 + ms)
    }

    fn ms_to_time(ms: i32) -> String {
        let mut sign = "";
        let mut ms_abs = ms;
        if ms < 0 { sign = "-"; ms_abs = -ms; }
        let h = ms_abs / 3600000;
        let rem = ms_abs % 3600000;
        let m = rem / 60000;
        let rem = rem % 60000;
        let s = rem / 1000;
        let ms = rem % 1000;
        format!("{}{}:{:02}:{:02}.{:03}", sign, h, m, s, ms)
    }

    fn apply_shift(&mut self, delta_ms: i32) {
        for ev in &mut self.events {
            ev.start_ms = (ev.start_ms + delta_ms).max(0);
            ev.end_ms = (ev.end_ms + delta_ms).max(0);
        }
    }

    fn apply_replace(&mut self, old: &str, new: &str) {
        let re = Regex::new(old).unwrap();
        for ev in &mut self.events {
            ev.text = re.replace_all(&ev.text, new).to_string();
        }
    }

    fn apply_style(&mut self, new_style: &str) {
        for ev in &mut self.events {
            ev.style = new_style.to_string();
        }
    }

    fn save(&self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut out = String::new();
        for line in &self.header {
            out.push_str(line);
            out.push('\n');
        }
        for ev in &self.events {
            let start = Self::ms_to_time(ev.start_ms);
            let end = Self::ms_to_time(ev.end_ms);
            let line = format!("{},{},{},{},0,0,0,,{}\n", ev.prefix, start, end, ev.style, ev.text);
            out.push_str(&line);
        }
        fs::write(filename, out)?;
        println!("Saved to {}", filename);
        Ok(())
    }

    fn list_events(&self, color: bool) {
        let (green, yellow, reset) = if color {
            ("\x1b[92m", "\x1b[93m", "\x1b[0m")
        } else {
            ("", "", "")
        };
        for (i, ev) in self.events.iter().enumerate() {
            let start = Self::ms_to_time(ev.start_ms);
            let end = Self::ms_to_time(ev.end_ms);
            println!("{}[{}]{} {} --> {}  {}{}{}: {}", green, i, reset, start, end, yellow, ev.style, reset, ev.text);
        }
    }

    fn export_srt(&self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut out = String::new();
        for (i, ev) in self.events.iter().enumerate() {
            let start = Self::ms_to_time(ev.start_ms).replace('.', ',');
            let end = Self::ms_to_time(ev.end_ms).replace('.', ',');
            out.push_str(&format!("{}\n{} --> {}\n{}\n\n", i+1, start, end, ev.text));
        }
        fs::write(filename, out)?;
        println!("Exported SRT to {}", filename);
        Ok(())
    }
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("ASS Editor")
        .arg(Arg::with_name("input").long("input").takes_value(true).required(true))
        .arg(Arg::with_name("output").long("output").takes_value(true))
        .arg(Arg::with_name("shift").long("shift").takes_value(true))
        .arg(Arg::with_name("replace").long("replace").takes_value(true))
        .arg(Arg::with_name("replace-to").long("replace-to").takes_value(true))
        .arg(Arg::with_name("style").long("style").takes_value(true))
        .arg(Arg::with_name("list").long("list"))
        .arg(Arg::with_name("export-srt").long("export-srt").takes_value(true))
        .get_matches();

    let input = matches.value_of("input").unwrap();
    let mut parser = ASSParser::new();
    parser.parse(input)?;

    if let Some(shift_str) = matches.value_of("shift") {
        let delta: i32 = shift_str.parse()?;
        parser.apply_shift(delta);
    }

    if let (Some(old), Some(new)) = (matches.value_of("replace"), matches.value_of("replace-to")) {
        parser.apply_replace(old, new);
    }

    if let Some(style) = matches.value_of("style") {
        parser.apply_style(style);
    }

    if matches.is_present("list") {
        parser.list_events(true);
    }

    if let Some(srt) = matches.value_of("export-srt") {
        parser.export_srt(srt)?;
    }

    if let Some(output) = matches.value_of("output") {
        parser.save(output)?;
    } else if matches.is_present("shift") || matches.is_present("replace") || matches.is_present("style") {
        parser.save(input)?;
    }

    Ok(())
}
