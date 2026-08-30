// ASSEditor.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ASSEditor {
    static class Event {
        String prefix;
        int startMs, endMs;
        String style, text;
        String raw;
    }

    private List<String> header = new ArrayList<>();
    private List<Event> events = new ArrayList<>();

    public void parse(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename));
        boolean inEvents = false;
        for (String line : lines) {
            if (line.startsWith("[Events]")) {
                inEvents = true;
                header.add(line);
                continue;
            }
            if (!inEvents) {
                header.add(line);
                continue;
            }
            if (line.startsWith("Format:")) {
                header.add(line);
                continue;
            }
            if (line.startsWith("Dialogue:") || line.startsWith("Comment:")) {
                try {
                    events.add(parseEvent(line));
                } catch (Exception e) {
                    System.err.println("Skipping invalid line: " + line);
                }
            } else {
                header.add(line);
            }
        }
    }

    private Event parseEvent(String line) {
        String[] parts = line.split(",", 10);
        if (parts.length < 10) throw new IllegalArgumentException("Invalid event");
        Event e = new Event();
        e.prefix = parts[0];
        e.startMs = timeToMs(parts[1].trim());
        e.endMs = timeToMs(parts[2].trim());
        e.style = parts[3].trim();
        e.text = parts[9].trim();
        e.raw = line;
        return e;
    }

    private int timeToMs(String ts) {
        if (ts.isEmpty()) return 0;
        String[] parts = ts.split(":");
        if (parts.length != 3) return 0;
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        String secPart = parts[2];
        int s, ms;
        if (secPart.contains(".")) {
            String[] sp = secPart.split("\\.");
            s = Integer.parseInt(sp[0]);
            String millis = sp[1];
            if (millis.length() > 3) millis = millis.substring(0, 3);
            ms = Integer.parseInt(millis);
        } else {
            s = Integer.parseInt(secPart);
            ms = 0;
        }
        return (h * 3600 + m * 60 + s) * 1000 + ms;
    }

    private String msToTime(int ms) {
        String sign = "";
        if (ms < 0) { sign = "-"; ms = -ms; }
        int h = ms / 3600000;
        ms %= 3600000;
        int m = ms / 60000;
        ms %= 60000;
        int s = ms / 1000;
        ms %= 1000;
        return String.format("%s%d:%02d:%02d.%03d", sign, h, m, s, ms);
    }

    public void applyShift(int deltaMs) {
        for (Event e : events) {
            e.startMs = Math.max(0, e.startMs + deltaMs);
            e.endMs = Math.max(0, e.endMs + deltaMs);
        }
    }

    public void applyReplace(String old, String newStr) {
        Pattern p = Pattern.compile(old);
        for (Event e : events) {
            e.text = p.matcher(e.text).replaceAll(newStr);
        }
    }

    public void applyStyle(String newStyle) {
        for (Event e : events) {
            e.style = newStyle;
        }
    }

    public void save(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            for (String h : header) pw.println(h);
            for (Event e : events) {
                String start = msToTime(e.startMs);
                String end = msToTime(e.endMs);
                pw.printf("%s,%s,%s,%s,0,0,0,,%s%n", e.prefix, start, end, e.style, e.text);
            }
        }
        System.out.println("Saved to " + filename);
    }

    public void listEvents(boolean color) {
        String green = color ? "\u001B[92m" : "";
        String yellow = color ? "\u001B[93m" : "";
        String reset = color ? "\u001B[0m" : "";
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            String start = msToTime(e.startMs);
            String end = msToTime(e.endMs);
            System.out.printf("%s[%d]%s %s --> %s  %s%s%s: %s%n",
                green, i, reset, start, end, yellow, e.style, reset, e.text);
        }
    }

    public void exportSrt(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            for (int i = 0; i < events.size(); i++) {
                Event e = events.get(i);
                String start = msToTime(e.startMs).replace('.', ',');
                String end = msToTime(e.endMs).replace('.', ',');
                pw.printf("%d%n%s --> %s%n%s%n%n", i+1, start, end, e.text);
            }
        }
        System.out.println("Exported SRT to " + filename);
    }

    public static void main(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i+1].startsWith("--")) {
                    params.put(key, args[++i]);
                } else {
                    params.put(key, "");
                }
            }
        }
        String input = params.get("input");
        if (input == null) {
            System.err.println("Error: --input required");
            System.exit(1);
        }
        ASSEditor editor = new ASSEditor();
        try {
            editor.parse(input);
        } catch (IOException e) {
            System.err.println("Parse error: " + e.getMessage());
            System.exit(1);
        }
        if (params.containsKey("shift")) {
            editor.applyShift(Integer.parseInt(params.get("shift")));
        }
        if (params.containsKey("replace") && params.containsKey("replace-to")) {
            editor.applyReplace(params.get("replace"), params.get("replace-to"));
        }
        if (params.containsKey("style")) {
            editor.applyStyle(params.get("style"));
        }
        if (params.containsKey("list")) {
            editor.listEvents(true);
        }
        if (params.containsKey("export-srt")) {
            try {
                editor.exportSrt(params.get("export-srt"));
            } catch (IOException e) {
                System.err.println("Export error: " + e.getMessage());
            }
        }
        if (params.containsKey("output") || params.containsKey("shift") || params.containsKey("replace") || params.containsKey("style")) {
            String output = params.getOrDefault("output", input);
            try {
                editor.save(output);
            } catch (IOException e) {
                System.err.println("Save error: " + e.getMessage());
            }
        }
    }
}
