// ass_editor.go
package main

import (
	"bufio"
	"flag"
	"fmt"
	"os"
	"regexp"
	"strconv"
	"strings"
)

type Event struct {
	Prefix   string
	StartMs  int
	EndMs    int
	Style    string
	Text     string
	Raw      string
}

type ASSParser struct {
	filename string
	header   []string
	events   []*Event
}

func NewASSParser(filename string) *ASSParser {
	return &ASSParser{filename: filename}
}

func (p *ASSParser) Parse() error {
	file, err := os.Open(p.filename)
	if err != nil {
		return err
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	inEvents := false
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "[Events]") {
			inEvents = true
			p.header = append(p.header, line)
			continue
		}
		if !inEvents {
			p.header = append(p.header, line)
			continue
		}
		if strings.HasPrefix(line, "Format:") {
			p.header = append(p.header, line)
			continue
		}
		if strings.HasPrefix(line, "Dialogue:") || strings.HasPrefix(line, "Comment:") {
			ev, err := parseEvent(line)
			if err == nil {
				p.events = append(p.events, ev)
			}
		} else {
			p.header = append(p.header, line)
		}
	}
	return scanner.Err()
}

func parseEvent(line string) (*Event, error) {
	parts := strings.SplitN(line, ",", 10)
	if len(parts) < 10 {
		return nil, fmt.Errorf("invalid event line")
	}
	prefix := parts[0]
	startStr := strings.TrimSpace(parts[1])
	endStr := strings.TrimSpace(parts[2])
	style := strings.TrimSpace(parts[3])
	text := strings.TrimSpace(parts[9])
	startMs, err := timeToMs(startStr)
	if err != nil {
		return nil, err
	}
	endMs, err := timeToMs(endStr)
	if err != nil {
		return nil, err
	}
	return &Event{
		Prefix:  prefix,
		StartMs: startMs,
		EndMs:   endMs,
		Style:   style,
		Text:    text,
		Raw:     line,
	}, nil
}

func timeToMs(ts string) (int, error) {
	if ts == "" {
		return 0, nil
	}
	parts := strings.Split(ts, ":")
	if len(parts) != 3 {
		return 0, fmt.Errorf("invalid time format")
	}
	h, _ := strconv.Atoi(parts[0])
	m, _ := strconv.Atoi(parts[1])
	secPart := parts[2]
	var s, ms int
	if strings.Contains(secPart, ".") {
		sp := strings.SplitN(secPart, ".", 2)
		s, _ = strconv.Atoi(sp[0])
		millis := sp[1]
		if len(millis) > 3 {
			millis = millis[:3]
		}
		ms, _ = strconv.Atoi(millis)
	} else {
		s, _ = strconv.Atoi(secPart)
	}
	return (h*3600 + m*60 + s) * 1000 + ms, nil
}

func msToTime(ms int) string {
	sign := ""
	if ms < 0 {
		sign = "-"
		ms = -ms
	}
	h := ms / 3600000
	ms %= 3600000
	m := ms / 60000
	ms %= 60000
	s := ms / 1000
	ms %= 1000
	return fmt.Sprintf("%s%d:%02d:%02d.%03d", sign, h, m, s, ms)
}

func (p *ASSParser) ApplyShift(deltaMs int) {
	for _, ev := range p.events {
		if ev.StartMs+deltaMs < 0 {
			ev.StartMs = 0
		} else {
			ev.StartMs += deltaMs
		}
		if ev.EndMs+deltaMs < 0 {
			ev.EndMs = 0
		} else {
			ev.EndMs += deltaMs
		}
	}
}

func (p *ASSParser) ApplyReplace(old, new string) {
	re := regexp.MustCompile(old)
	for _, ev := range p.events {
		ev.Text = re.ReplaceAllString(ev.Text, new)
	}
}

func (p *ASSParser) ApplyStyle(newStyle string) {
	for _, ev := range p.events {
		ev.Style = newStyle
	}
}

func (p *ASSParser) Save(filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer file.Close()
	for _, h := range p.header {
		fmt.Fprintln(file, h)
	}
	for _, ev := range p.events {
		start := msToTime(ev.StartMs)
		end := msToTime(ev.EndMs)
		line := fmt.Sprintf("%s,%s,%s,%s,0,0,0,,%s", ev.Prefix, start, end, ev.Style, ev.Text)
		fmt.Fprintln(file, line)
	}
	return nil
}

func (p *ASSParser) ListEvents(color bool) {
	green, yellow, reset := "", "", ""
	if color {
		green = "\033[92m"
		yellow = "\033[93m"
		reset = "\033[0m"
	}
	for i, ev := range p.events {
		start := msToTime(ev.StartMs)
		end := msToTime(ev.EndMs)
		fmt.Printf("%s[%d]%s %s --> %s  %s%s%s: %s\n",
			green, i, reset, start, end, yellow, ev.Style, reset, ev.Text)
	}
}

func (p *ASSParser) ExportSrt(filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer file.Close()
	for i, ev := range p.events {
		start := strings.Replace(msToTime(ev.StartMs), ".", ",", -1)
		end := strings.Replace(msToTime(ev.EndMs), ".", ",", -1)
		fmt.Fprintf(file, "%d\n%s --> %s\n%s\n\n", i+1, start, end, ev.Text)
	}
	return nil
}

func main() {
	var (
		input     string
		output    string
		shift     int
		replace   string
		replaceTo string
		style     string
		list      bool
		exportSrt string
	)
	flag.StringVar(&input, "input", "", "Input ASS file")
	flag.StringVar(&output, "output", "", "Output file")
	flag.IntVar(&shift, "shift", 0, "Shift time in milliseconds")
	flag.StringVar(&replace, "replace", "", "Old text to replace (regex)")
	flag.StringVar(&replaceTo, "replace-to", "", "New text")
	flag.StringVar(&style, "style", "", "Change style name")
	flag.BoolVar(&list, "list", false, "List events")
	flag.StringVar(&exportSrt, "export-srt", "", "Export to SRT")
	flag.Parse()

	if input == "" {
		fmt.Fprintln(os.Stderr, "Error: --input required")
		os.Exit(1)
	}
	p := NewASSParser(input)
	if err := p.Parse(); err != nil {
		fmt.Fprintf(os.Stderr, "Parse error: %v\n", err)
		os.Exit(1)
	}

	if shift != 0 {
		p.ApplyShift(shift)
	}
	if replace != "" && replaceTo != "" {
		p.ApplyReplace(replace, replaceTo)
	}
	if style != "" {
		p.ApplyStyle(style)
	}
	if list {
		p.ListEvents(true)
	}
	if exportSrt != "" {
		if err := p.ExportSrt(exportSrt); err != nil {
			fmt.Fprintf(os.Stderr, "Export error: %v\n", err)
		}
	}
	if output != "" || shift != 0 || replace != "" || style != "" {
		out := output
		if out == "" {
			out = input
		}
		if err := p.Save(out); err != nil {
			fmt.Fprintf(os.Stderr, "Save error: %v\n", err)
		} else {
			fmt.Printf("Saved to %s\n", out)
		}
	}
}
