// ass_editor.cpp
#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <regex>
#include <sstream>
#include <cstring>
#include <iomanip>
#include <algorithm>

using namespace std;

struct Event {
    string prefix;
    int startMs;
    int endMs;
    string style;
    string text;
};

vector<string> split(const string& s, char delim) {
    vector<string> parts;
    stringstream ss(s);
    string item;
    while (getline(ss, item, delim)) {
        parts.push_back(item);
    }
    return parts;
}

int timeToMs(const string& ts) {
    if (ts.empty()) return 0;
    auto parts = split(ts, ':');
    if (parts.size() != 3) return 0;
    int h = stoi(parts[0]);
    int m = stoi(parts[1]);
    string secPart = parts[2];
    int s, ms;
    size_t dot = secPart.find('.');
    if (dot != string::npos) {
        s = stoi(secPart.substr(0, dot));
        string millis = secPart.substr(dot + 1);
        if (millis.length() > 3) millis = millis.substr(0, 3);
        ms = stoi(millis);
    } else {
        s = stoi(secPart);
        ms = 0;
    }
    return (h * 3600 + m * 60 + s) * 1000 + ms;
}

string msToTime(int ms) {
    string sign = "";
    if (ms < 0) { sign = "-"; ms = -ms; }
    int h = ms / 3600000;
    ms %= 3600000;
    int m = ms / 60000;
    ms %= 60000;
    int s = ms / 1000;
    ms %= 1000;
    ostringstream oss;
    oss << sign << h << ":" << setw(2) << setfill('0') << m << ":" << setw(2) << s << "." << setw(3) << ms;
    return oss.str();
}

class ASSEditor {
public:
    vector<string> header;
    vector<Event> events;

    void parse(const string& filename) {
        ifstream file(filename);
        if (!file) {
            cerr << "Cannot open file: " << filename << endl;
            return;
        }
        string line;
        bool inEvents = false;
        while (getline(file, line)) {
            if (line.find("[Events]") == 0) {
                inEvents = true;
                header.push_back(line);
                continue;
            }
            if (!inEvents) {
                header.push_back(line);
                continue;
            }
            if (line.find("Format:") == 0) {
                header.push_back(line);
                continue;
            }
            if (line.find("Dialogue:") == 0 || line.find("Comment:") == 0) {
                Event ev = parseEvent(line);
                events.push_back(ev);
            } else {
                header.push_back(line);
            }
        }
    }

    Event parseEvent(const string& line) {
        auto parts = split(line, ',');
        while (parts.size() < 10) parts.push_back("");
        Event ev;
        ev.prefix = parts[0];
        ev.startMs = timeToMs(parts[1]);
        ev.endMs = timeToMs(parts[2]);
        ev.style = parts[3];
        // text is parts[9] but may contain commas, so we join from 9 onwards
        ev.text = parts[9];
        for (size_t i = 10; i < parts.size(); ++i) ev.text += "," + parts[i];
        return ev;
    }

    void applyShift(int deltaMs) {
        for (auto& e : events) {
            e.startMs = max(0, e.startMs + deltaMs);
            e.endMs = max(0, e.endMs + deltaMs);
        }
    }

    void applyReplace(const string& oldStr, const string& newStr) {
        regex re(oldStr);
        for (auto& e : events) {
            e.text = regex_replace(e.text, re, newStr);
        }
    }

    void applyStyle(const string& newStyle) {
        for (auto& e : events) {
            e.style = newStyle;
        }
    }

    void save(const string& filename) {
        ofstream out(filename);
        for (const auto& h : header) out << h << endl;
        for (const auto& e : events) {
            string start = msToTime(e.startMs);
            string end = msToTime(e.endMs);
            out << e.prefix << "," << start << "," << end << "," << e.style << ",0,0,0,," << e.text << endl;
        }
        cout << "Saved to " << filename << endl;
    }

    void listEvents(bool color) {
        string g = color ? "\033[92m" : "";
        string y = color ? "\033[93m" : "";
        string r = color ? "\033[0m" : "";
        for (size_t i = 0; i < events.size(); ++i) {
            const auto& e = events[i];
            string start = msToTime(e.startMs);
            string end = msToTime(e.endMs);
            cout << g << "[" << i << "]" << r << " " << start << " --> " << end << "  " << y << e.style << r << ": " << e.text << endl;
        }
    }

    void exportSrt(const string& filename) {
        ofstream out(filename);
        for (size_t i = 0; i < events.size(); ++i) {
            const auto& e = events[i];
            string start = msToTime(e.startMs);
            replace(start.begin(), start.end(), '.', ',');
            string end = msToTime(e.endMs);
            replace(end.begin(), end.end(), '.', ',');
            out << i+1 << endl;
            out << start << " --> " << end << endl;
            out << e.text << endl << endl;
        }
        cout << "Exported SRT to " << filename << endl;
    }
};

int main(int argc, char* argv[]) {
    string input, output, replaceOld, replaceNew, style, exportSrt;
    int shift = 0;
    bool list = false;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "--input" && i+1 < argc) input = argv[++i];
        else if (arg == "--output" && i+1 < argc) output = argv[++i];
        else if (arg == "--shift" && i+1 < argc) shift = stoi(argv[++i]);
        else if (arg == "--replace" && i+1 < argc) replaceOld = argv[++i];
        else if (arg == "--replace-to" && i+1 < argc) replaceNew = argv[++i];
        else if (arg == "--style" && i+1 < argc) style = argv[++i];
        else if (arg == "--list") list = true;
        else if (arg == "--export-srt" && i+1 < argc) exportSrt = argv[++i];
    }

    if (input.empty()) {
        cerr << "Error: --input required" << endl;
        return 1;
    }

    ASSEditor editor;
    editor.parse(input);

    if (shift != 0) editor.applyShift(shift);
    if (!replaceOld.empty() && !replaceNew.empty()) editor.applyReplace(replaceOld, replaceNew);
    if (!style.empty()) editor.applyStyle(style);

    if (list) editor.listEvents(true);
    if (!exportSrt.empty()) editor.exportSrt(exportSrt);

    if (!output.empty() || shift != 0 || !replaceOld.empty() || !style.empty()) {
        string out = output.empty() ? input : output;
        editor.save(out);
    }

    return 0;
}
