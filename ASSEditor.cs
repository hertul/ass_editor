// ASSEditor.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;

namespace ASSEditor
{
    class Program
    {
        static void Main(string[] args)
        {
            var opts = new Dictionary<string, string>();
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i].StartsWith("--"))
                {
                    string key = args[i].Substring(2);
                    if (i + 1 < args.Length && !args[i + 1].StartsWith("--"))
                        opts[key] = args[++i];
                    else
                        opts[key] = "";
                }
            }
            if (!opts.ContainsKey("input"))
            {
                Console.Error.WriteLine("Error: --input required");
                return;
            }
            var editor = new ASSEditor();
            editor.Parse(opts["input"]);

            if (opts.ContainsKey("shift"))
                editor.ApplyShift(int.Parse(opts["shift"]));
            if (opts.ContainsKey("replace") && opts.ContainsKey("replace-to"))
                editor.ApplyReplace(opts["replace"], opts["replace-to"]);
            if (opts.ContainsKey("style"))
                editor.ApplyStyle(opts["style"]);
            if (opts.ContainsKey("list"))
                editor.ListEvents(true);
            if (opts.ContainsKey("export-srt"))
                editor.ExportSrt(opts["export-srt"]);
            if (opts.ContainsKey("output") || opts.ContainsKey("shift") || opts.ContainsKey("replace") || opts.ContainsKey("style"))
            {
                string output = opts.GetValueOrDefault("output", opts["input"]);
                editor.Save(output);
            }
        }
    }

    class ASSEditor
    {
        private List<string> header = new List<string>();
        private List<Event> events = new List<Event>();

        class Event
        {
            public string Prefix { get; set; }
            public int StartMs { get; set; }
            public int EndMs { get; set; }
            public string Style { get; set; }
            public string Text { get; set; }
        }

        public void Parse(string filename)
        {
            var lines = File.ReadAllLines(filename);
            bool inEvents = false;
            foreach (var line in lines)
            {
                if (line.StartsWith("[Events]"))
                {
                    inEvents = true;
                    header.Add(line);
                    continue;
                }
                if (!inEvents)
                {
                    header.Add(line);
                    continue;
                }
                if (line.StartsWith("Format:"))
                {
                    header.Add(line);
                    continue;
                }
                if (line.StartsWith("Dialogue:") || line.StartsWith("Comment:"))
                {
                    events.Add(ParseEvent(line));
                }
                else
                {
                    header.Add(line);
                }
            }
        }

        private Event ParseEvent(string line)
        {
            var parts = line.Split(new[] { ',' }, 10);
            if (parts.Length < 10) throw new Exception("Invalid event");
            return new Event
            {
                Prefix = parts[0],
                StartMs = TimeToMs(parts[1].Trim()),
                EndMs = TimeToMs(parts[2].Trim()),
                Style = parts[3].Trim(),
                Text = parts[9].Trim()
            };
        }

        private int TimeToMs(string ts)
        {
            if (string.IsNullOrEmpty(ts)) return 0;
            var parts = ts.Split(':');
            if (parts.Length != 3) return 0;
            int h = int.Parse(parts[0]);
            int m = int.Parse(parts[1]);
            var secPart = parts[2];
            int s, ms;
            if (secPart.Contains('.'))
            {
                var sp = secPart.Split('.');
                s = int.Parse(sp[0]);
                string millis = sp[1];
                if (millis.Length > 3) millis = millis.Substring(0, 3);
                ms = int.Parse(millis);
            }
            else
            {
                s = int.Parse(secPart);
                ms = 0;
            }
            return (h * 3600 + m * 60 + s) * 1000 + ms;
        }

        private string MsToTime(int ms)
        {
            string sign = "";
            if (ms < 0) { sign = "-"; ms = -ms; }
            int h = ms / 3600000;
            ms %= 3600000;
            int m = ms / 60000;
            ms %= 60000;
            int s = ms / 1000;
            ms %= 1000;
            return $"{sign}{h}:{m:D2}:{s:D2}.{ms:D3}";
        }

        public void ApplyShift(int deltaMs)
        {
            foreach (var e in events)
            {
                e.StartMs = Math.Max(0, e.StartMs + deltaMs);
                e.EndMs = Math.Max(0, e.EndMs + deltaMs);
            }
        }

        public void ApplyReplace(string old, string newStr)
        {
            foreach (var e in events)
                e.Text = Regex.Replace(e.Text, old, newStr);
        }

        public void ApplyStyle(string newStyle)
        {
            foreach (var e in events) e.Style = newStyle;
        }

        public void Save(string filename)
        {
            using var sw = new StreamWriter(filename);
            foreach (var h in header) sw.WriteLine(h);
            foreach (var e in events)
            {
                string start = MsToTime(e.StartMs);
                string end = MsToTime(e.EndMs);
                sw.WriteLine($"{e.Prefix},{start},{end},{e.Style},0,0,0,,{e.Text}");
            }
            Console.WriteLine($"Saved to {filename}");
        }

        public void ListEvents(bool color)
        {
            string g = color ? "\u001B[92m" : "";
            string y = color ? "\u001B[93m" : "";
            string r = color ? "\u001B[0m" : "";
            for (int i = 0; i < events.Count; i++)
            {
                var e = events[i];
                string start = MsToTime(e.StartMs);
                string end = MsToTime(e.EndMs);
                Console.WriteLine($"{g}[{i}]{r} {start} --> {end}  {y}{e.Style}{r}: {e.Text}");
            }
        }

        public void ExportSrt(string filename)
        {
            using var sw = new StreamWriter(filename);
            for (int i = 0; i < events.Count; i++)
            {
                var e = events[i];
                string start = MsToTime(e.StartMs).Replace('.', ',');
                string end = MsToTime(e.EndMs).Replace('.', ',');
                sw.WriteLine($"{i+1}");
                sw.WriteLine($"{start} --> {end}");
                sw.WriteLine(e.Text);
                sw.WriteLine();
            }
            Console.WriteLine($"Exported SRT to {filename}");
        }
    }
}
