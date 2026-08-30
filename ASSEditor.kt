// ASSEditor.kt
import java.io.File
import java.util.*
import kotlin.text.Regex

class ASSEditor {
    private val header = mutableListOf<String>()
    private val events = mutableListOf<Event>()

    data class Event(val prefix: String, var startMs: Int, var endMs: Int, var style: String, var text: String)

    fun parse(filename: String) {
        val lines = File(filename).readLines()
        var inEvents = false
        for (line in lines) {
            when {
                line.startsWith("[Events]") -> {
                    inEvents = true
                    header.add(line)
                }
                !inEvents -> header.add(line)
                line.startsWith("Format:") -> header.add(line)
                line.startsWith("Dialogue:") || line.startsWith("Comment:") -> {
                    events.add(parseEvent(line))
                }
                else -> header.add(line)
            }
        }
    }

    private fun parseEvent(line: String): Event {
        val parts = line.split(",", limit = 10)
        if (parts.size < 10) throw IllegalArgumentException("Invalid event")
        return Event(
            prefix = parts[0],
            startMs = timeToMs(parts[1].trim()),
            endMs = timeToMs(parts[2].trim()),
            style = parts[3].trim(),
            text = parts[9].trim()
        )
    }

    private fun timeToMs(ts: String): Int {
        if (ts.isEmpty()) return 0
        val parts = ts.split(":")
        if (parts.size != 3) return 0
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val secPart = parts[2]
        val (s, ms) = if (secPart.contains(".")) {
            val sp = secPart.split(".")
            val s = sp[0].toInt()
            val millis = sp[1].padEnd(3, '0').take(3)
            s to millis.toInt()
        } else {
            secPart.toInt() to 0
        }
        return (h * 3600 + m * 60 + s) * 1000 + ms
    }

    private fun msToTime(ms: Int): String {
        var msCopy = ms
        var sign = ""
        if (msCopy < 0) { sign = "-"; msCopy = -msCopy }
        val h = msCopy / 3600000
        msCopy %= 3600000
        val m = msCopy / 60000
        msCopy %= 60000
        val s = msCopy / 1000
        msCopy %= 1000
        return "$sign$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${msCopy.toString().padStart(3, '0')}"
    }

    fun applyShift(deltaMs: Int) {
        for (e in events) {
            e.startMs = (e.startMs + deltaMs).coerceAtLeast(0)
            e.endMs = (e.endMs + deltaMs).coerceAtLeast(0)
        }
    }

    fun applyReplace(old: String, new: String) {
        val regex = Regex(old)
        for (e in events) {
            e.text = regex.replace(e.text, new)
        }
    }

    fun applyStyle(newStyle: String) {
        for (e in events) {
            e.style = newStyle
        }
    }

    fun save(filename: String) {
        val out = File(filename).printWriter()
        header.forEach { out.println(it) }
        events.forEach { e ->
            val start = msToTime(e.startMs)
            val end = msToTime(e.endMs)
            out.println("${e.prefix},$start,$end,${e.style},0,0,0,,${e.text}")
        }
        out.close()
        println("Saved to $filename")
    }

    fun listEvents(color: Boolean) {
        val g = if (color) "\u001B[92m" else ""
        val y = if (color) "\u001B[93m" else ""
        val r = if (color) "\u001B[0m" else ""
        events.forEachIndexed { i, e ->
            val start = msToTime(e.startMs)
            val end = msToTime(e.endMs)
            println("$g[$i]$r $start --> $end  $y${e.style}$r: ${e.text}")
        }
    }

    fun exportSrt(filename: String) {
        val out = File(filename).printWriter()
        events.forEachIndexed { i, e ->
            var start = msToTime(e.startMs).replace('.', ',')
            var end = msToTime(e.endMs).replace('.', ',')
            out.println(i + 1)
            out.println("$start --> $end")
            out.println(e.text)
            out.println()
        }
        out.close()
        println("Exported SRT to $filename")
    }
}

fun main(args: Array<String>) {
    val params = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        if (args[i].startsWith("--")) {
            val key = args[i].substring(2)
            if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                params[key] = args[++i]
            } else {
                params[key] = ""
            }
        }
        i++
    }
    val input = params["input"] ?: run {
        System.err.println("Error: --input required")
        return
    }
    val editor = ASSEditor()
    editor.parse(input)

    params["shift"]?.let { editor.applyShift(it.toInt()) }
    if (params.containsKey("replace") && params.containsKey("replace-to")) {
        editor.applyReplace(params["replace"]!!, params["replace-to"]!!)
    }
    params["style"]?.let { editor.applyStyle(it) }
    if (params.containsKey("list")) {
        editor.listEvents(true)
    }
    params["export-srt"]?.let { editor.exportSrt(it) }
    if (params.containsKey("output") || params.containsKey("shift") || params.containsKey("replace") || params.containsKey("style")) {
        val output = params["output"] ?: input
        editor.save(output)
    }
}
