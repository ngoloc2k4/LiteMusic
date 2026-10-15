package com.litemusic.app.core.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object DebugLogServer {
    private const val TAG = "DebugLogServer"
    private const val DEFAULT_PORT = 8888
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    
    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var activePort: Int = DEFAULT_PORT
        private set

    fun getLocalUrl(): String {
        return "http://localhost:$activePort"
    }

    @Synchronized
    fun start(scope: CoroutineScope, port: Int = DEFAULT_PORT) {
        if (isRunning) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                var currentPort = port
                var bound = false
                while (!bound && currentPort < port + 10) {
                    try {
                        serverSocket = ServerSocket(currentPort, 50, InetAddress.getByName("0.0.0.0"))
                        activePort = currentPort
                        bound = true
                    } catch (e: Exception) {
                        currentPort++
                    }
                }

                if (!bound || serverSocket == null) {
                    Log.e(TAG, "Failed to bind DebugLogServer to any port starting at $port")
                    return@launch
                }

                isRunning = true
                Log.i(TAG, "DebugLogServer started on http://localhost:$activePort")

                while (isActive && !serverSocket!!.isClosed) {
                    val clientSocket = try {
                        serverSocket!!.accept()
                    } catch (e: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DebugLogServer error", e)
            } finally {
                isRunning = false
                stop()
            }
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "DebugLogServer stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = OutputStreamWriter(BufferedOutputStream(socket.getOutputStream()), Charsets.UTF_8)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            when {
                path == "/clear" -> {
                    clearLogcat()
                    sendResponse(output, 200, "text/plain", "OK: Logcat cleared")
                }
                path == "/raw" || path.startsWith("/raw?") -> {
                    val logs = captureLogcat()
                    sendResponse(output, 200, "text/plain; charset=utf-8", logs)
                }
                else -> {
                    val html = buildWebDashboardHtml(activePort)
                    sendResponse(output, 200, "text/html; charset=utf-8", html)
                }
            }
        } catch (e: Exception) {
            // Socket errors/client disconnects
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(writer: OutputStreamWriter, statusCode: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode OK\r\n")
        writer.write("Content-Type: $contentType\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.write(body)
        writer.flush()
    }

    fun captureLogcat(maxLines: Int = 1000): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = ArrayDeque<String>(maxLines)
            var line: String? = reader.readLine()
            while (line != null) {
                if (lines.size >= maxLines) {
                    lines.removeFirst()
                }
                lines.addLast(line)
                line = reader.readLine()
            }
            process.destroy()
            lines.joinToString("\n")
        } catch (e: Exception) {
            "Error capturing logcat: ${e.message}"
        }
    }

    fun clearLogcat() {
        try {
            val process = ProcessBuilder("logcat", "-c").start()
            process.waitFor()
            process.destroy()
        } catch (_: Exception) {}
    }

    fun copyLogcatToClipboard(context: Context) {
        try {
            val logs = captureLogcat(1200)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("LiteMusic_Logcat", logs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Đã sao chép Logcat vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi sao chép Logcat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildWebDashboardHtml(port: Int): String {
        return """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LiteMusic - Debug Console</title>
    <style>
        :root {
            --bg-primary: #0d1117;
            --bg-secondary: #161b22;
            --border: #30363d;
            --text-main: #c9d1d9;
            --text-muted: #8b949e;
            --accent: #58a6ff;
            --accent-green: #3fb950;
            --accent-red: #f85149;
            --accent-yellow: #d29922;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", monospace;
            background-color: var(--bg-primary);
            color: var(--text-main);
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        header {
            background-color: var(--bg-secondary);
            border-bottom: 1px solid var(--border);
            padding: 12px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 12px;
        }
        .title-group {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .badge {
            background: #238636;
            color: #ffffff;
            font-size: 11px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 12px;
        }
        .controls {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }
        input, select, button {
            background-color: #21262d;
            border: 1px solid var(--border);
            color: var(--text-main);
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 13px;
            outline: none;
        }
        input:focus, select:focus {
            border-color: var(--accent);
        }
        button {
            cursor: pointer;
            font-weight: 600;
            transition: background 0.2s;
        }
        button:hover { background-color: #30363d; }
        button.btn-danger { color: var(--accent-red); }
        button.btn-danger:hover { background-color: rgba(248, 81, 73, 0.15); }
        button.btn-primary { background: #238636; color: white; border: none; }
        button.btn-primary:hover { background: #2ea043; }
        
        main {
            flex: 1;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            padding: 10px 16px;
        }
        #log-container {
            flex: 1;
            background: #090d13;
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 12px;
            overflow-y: auto;
            font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
            font-size: 12px;
            line-height: 1.5;
            white-space: pre-wrap;
            word-break: break-all;
        }
        .log-line {
            padding: 1px 0;
            border-bottom: 1px solid rgba(255,255,255,0.03);
        }
        .log-V { color: #8b949e; }
        .log-D { color: #58a6ff; }
        .log-I { color: #3fb950; }
        .log-W { color: #d29922; }
        .log-E { color: #f85149; font-weight: bold; }
        footer {
            padding: 6px 16px;
            background: var(--bg-secondary);
            border-top: 1px solid var(--border);
            font-size: 12px;
            color: var(--text-muted);
            display: flex;
            justify-content: space-between;
        }
    </style>
</head>
<body>
    <header>
        <div class="title-group">
            <h2>🎧 LiteMusic Debug Hub</h2>
            <span class="badge">Port $port</span>
        </div>
        <div class="controls">
            <input type="text" id="filter-input" placeholder="🔍 Tìm kiếm log (Tag, Text)..." style="width: 220px;" oninput="applyFilter()">
            <select id="level-select" onchange="applyFilter()">
                <option value="ALL">Mọi cấp độ (All)</option>
                <option value="D">Debug & cao hơn</option>
                <option value="I">Info & cao hơn</option>
                <option value="W">Warn & Error</option>
                <option value="E">Chỉ Error</option>
            </select>
            <button id="toggle-refresh-btn" onclick="toggleAutoRefresh()">⏸️ Dừng Auto-Refresh</button>
            <button onclick="fetchLogs()">🔄 Tải lại</button>
            <button onclick="copyLogs()">📋 Sao chép</button>
            <button class="btn-danger" onclick="clearLogs()">🗑️ Xóa Logcat</button>
        </div>
    </header>

    <main>
        <div id="log-container">Đang kết nối tới Logcat Android...</div>
    </main>

    <footer>
        <span id="status-text">Đang kết nối...</span>
        <span>Local Endpoint: <a href="/raw" target="_blank" style="color: var(--accent);">/raw</a></span>
    </footer>

    <script>
        let rawLogLines = [];
        let autoRefresh = true;
        let refreshInterval = null;

        async function fetchLogs() {
            try {
                const res = await fetch('/raw');
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const text = await res.text();
                rawLogLines = text.split('\n');
                applyFilter();
                document.getElementById('status-text').innerText = 'Cập nhật lần cuối: ' + new Date().toLocaleTimeString() + ' (' + rawLogLines.length + ' dòng)';
            } catch (e) {
                document.getElementById('status-text').innerText = 'Lỗi kết nối: ' + e.message;
            }
        }

        function applyFilter() {
            const filter = document.getElementById('filter-input').value.toLowerCase();
            const level = document.getElementById('level-select').value;
            const container = document.getElementById('log-container');

            const filtered = rawLogLines.filter(line => {
                if (!line.trim()) return false;
                if (filter && !line.toLowerCase().includes(filter)) return false;
                if (level === 'ALL') return true;
                
                // Android logcat format check: 02-14 12:00:00.000 ... V/ D/ I/ W/ E
                if (level === 'E' && !line.includes(' E ') && !line.includes(' E/')) return false;
                if (level === 'W' && !line.includes(' W ') && !line.includes(' W/') && !line.includes(' E ') && !line.includes(' E/')) return false;
                if (level === 'I' && !line.includes(' I ') && !line.includes(' I/') && !line.includes(' W ') && !line.includes(' W/') && !line.includes(' E ') && !line.includes(' E/')) return false;
                if (level === 'D' && !line.includes(' D ') && !line.includes(' D/') && !line.includes(' I ') && !line.includes(' I/') && !line.includes(' W ') && !line.includes(' W/') && !line.includes(' E ') && !line.includes(' E/')) return false;
                return true;
            });

            container.innerHTML = filtered.map(line => {
                let cls = 'log-V';
                if (line.includes(' D/') || line.includes(' D ')) cls = 'log-D';
                else if (line.includes(' I/') || line.includes(' I ')) cls = 'log-I';
                else if (line.includes(' W/') || line.includes(' W ')) cls = 'log-W';
                else if (line.includes(' E/') || line.includes(' E ')) cls = 'log-E';
                const escaped = line.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
                return `<div class="log-line ` + cls + `">` + escaped + `</div>`;
            }).join('');

            container.scrollTop = container.scrollHeight;
        }

        function toggleAutoRefresh() {
            autoRefresh = !autoRefresh;
            const btn = document.getElementById('toggle-refresh-btn');
            if (autoRefresh) {
                btn.innerText = '⏸️ Dừng Auto-Refresh';
                startInterval();
            } else {
                btn.innerText = '▶️ Bật Auto-Refresh';
                clearInterval(refreshInterval);
            }
        }

        function startInterval() {
            clearInterval(refreshInterval);
            refreshInterval = setInterval(fetchLogs, 1500);
        }

        async function clearLogs() {
            if (!confirm('Bạn có chắc muốn xóa sạch bộ đệm Logcat?')) return;
            try {
                await fetch('/clear');
                fetchLogs();
            } catch (e) {
                alert('Lỗi khi xóa log: ' + e.message);
            }
        }

        function copyLogs() {
            const text = rawLogLines.join('\n');
            navigator.clipboard.writeText(text).then(() => {
                alert('Đã sao chép toàn bộ logcat vào clipboard!');
            });
        }

        fetchLogs();
        startInterval();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
