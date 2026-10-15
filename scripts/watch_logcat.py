#!/usr/bin/env python3
"""
Real-time Logcat Watcher & Vietnamese Issue Translator for LiteMusic.
Monitors logcat continuously via system logcat stream or local DebugLogServer,
filters LiteMusic / Media3 / ExoPlayer / Audio warnings and errors,
and converts each into an actionable Vietnamese diagnosis with fix guidance.
"""

import sys
import os
import time
import re
import subprocess
import urllib.request

DEBUG_SERVER_URL = "http://127.0.0.1:8888/raw"

IGNORE_PATTERNS = [
    r"Access denied finding property \"vendor\.display",
    r"type=1400 audit.*vendor_display_prop",
    r"Unable to match the desired swap behavior",
    r"sendCancelIfRunning: isInProgress=falsecallback",
    r"requestCursorAnchorInfo on inactive InputConnection",
    r"untrusted_app.*tests.*avc",
    r"com\.termux",
]

TARGET_TAG_KEYWORDS = [
    "litemusic", "exoplayer", "media3", "mediacodec", "audiotrack",
    "youtuberesolver", "soundcloudresolver", "playbackservice", "debuglogserver",
    "audiocachemanager", "audioeffectmanager", "windowonbackdispatcher", "openglrenderer"
]

def should_ignore(line: str) -> bool:
    for pattern in IGNORE_PATTERNS:
        if re.search(pattern, line):
            return True
    return False

def is_target_log(tag: str, msg: str, pid: str) -> bool:
    combined = (tag + " " + msg).lower()
    return any(kw in combined for kw in TARGET_TAG_KEYWORDS)

def translate_log_line(tag: str, level: str, msg: str) -> str:
    explanation = ""
    action = ""

    t_lower = tag.lower()
    m_lower = msg.lower()

    if "exoplayer" in t_lower or "media3" in t_lower or "mediacodec" in t_lower or "audiotrack" in t_lower:
        if "unrecognizedinputformatexception" in m_lower:
            explanation = "ExoPlayer không nhận diện được định dạng stream (thường là HLS .m3u8 hoặc DASH mpd)."
            action = "Cần khai báo media3-exoplayer-hls trong gradle và setMimeType(APPLICATION_M3U8) khi stream là HLS."
        elif "decoderconfigurationerror" in m_lower or "mediacodec" in m_lower:
            explanation = "Bộ giải mã phần cứng thiết bị không hỗ trợ profile codec video/audio này."
            action = "Fallback về software decoder hoặc chọn profile/resolution thấp hơn (720p/360p thay vì 1080p 60fps)."
        elif "behindlivewindowexception" in m_lower:
            explanation = "Stream trực tiếp bị trễ quá lâu so với live window."
            action = "Khởi động lại stream hoặc seek về vị trí live mới nhất."
        elif "ioexception" in m_lower or "source error" in m_lower or "response code: 403" in m_lower:
            explanation = "Lỗi kết nối hoặc URL stream hết hạn (403 Forbidden từ Google Video/SoundCloud)."
            action = "Cần resolve lại fresh stream URL và xoay vòng client ID / visitorData."
        else:
            explanation = f"Sự cố hệ thống phát nhạc ExoPlayer/Media3 ({tag})."
            action = "Kiểm tra định dạng stream và kết nối mạng."
    elif "youtuberesolver" in t_lower:
        if "visionos" in m_lower:
            explanation = "Lỗi trích xuất luồng video/audio YouTube từ client VisionOS."
            action = "Cần xoay visitorData hoặc chuyển sang client Android/TV."
        elif "failed to resolve" in m_lower:
            explanation = "YouTube chặn stream hoặc video bị hạn chế độ tuổi/quốc gia."
            action = "Xoay visitorData hoặc fallback sang định dạng adaptive Opus/MP4."
        else:
            explanation = f"Cảnh báo YouTube Resolver: {msg[:70]}"
            action = "Kiểm tra payload JSON từ YouTube API."
    elif "soundcloudresolver" in t_lower:
        if "401" in m_lower or "403" in m_lower or "search attempt error" in m_lower:
            explanation = "SoundCloud Client ID bị hết hạn hoặc rate-limit."
            action = "Bộ xoay vòng Client ID tự động sẽ kích hoạt key tiếp theo."
        elif "failed to resolve" in m_lower:
            explanation = "Không tìm thấy transcoding hợp lệ cho track SoundCloud này (có thể là track trả phí Go+)."
            action = "Kiểm tra stream fallback sang API track details."
        else:
            explanation = f"SoundCloud Resolver: {msg[:70]}"
            action = "Kiểm tra response từ SoundCloud API v2."
    elif "windowonbackdispatcher" in t_lower:
        if "onbackinvokedcallback is not enabled" in m_lower:
            explanation = "Chưa kích hoạt tính năng Predictive Back Gesture của Android 13+ trong Manifest."
            action = "Đã cấu hình android:enableOnBackInvokedCallback='true' trong Manifest, cần cài bản build mới."
        else:
            explanation = "Cảnh báo cử chỉ Back điều hướng hệ thống."
            action = "Không ảnh hưởng nghiêm trọng đến ứng dụng."
    elif "openglrenderer" in t_lower:
        explanation = "Cảnh báo GPU / Buffer render của Android Compose."
        action = "Đặc trưng tối ưu phần cứng của hệ điều hành, không phải lỗi app."
    else:
        explanation = f"Cảnh báo từ {tag}: {msg[:80]}"
        action = "Xem xét và kiểm tra vị trí mã nguồn phát sinh."

    icon = "🔴 [LỖI NGHIÊM TRỌNG]" if level in ("E", "F") else "🟡 [CẢNH BÁO]"
    return (
        f"{icon} [{tag}] {msg.strip()}\n"
        f"   🔍 Ý nghĩa: {explanation}\n"
        f"   🛠️ Hành động: {action}"
    )

def main():
    print("=" * 65)
    print("🎧 LITEMUSIC REAL-TIME LOGCAT WATCHER & AUTO-TRANSLATOR")
    print("   Giám sát và tự động dịch cảnh báo/lỗi sang tiếng Việt")
    print("=" * 65)
    sys.stdout.flush()

    log_regex = re.compile(r"^\d{2}-\d{2}\s+[\d:.]+\s+([VDIWEF])\/([^\(\:]+)(?:\((\s*\d+)\))?:\s*(.*)$")

    # Start live logcat streaming process
    cmd = ["logcat", "-v", "time"]
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, bufsize=1)
    except Exception as e:
        print(f"❌ Không thể khởi chạy logcat trực tiếp: {e}")
        return

    print("🟢 Đang lắng nghe luồng logcat trực tiếp từ hệ thống...")
    sys.stdout.flush()

    try:
        for raw_line in proc.stdout:
            line = raw_line.strip()
            if not line or should_ignore(line):
                continue

            match = log_regex.match(line)
            if not match:
                continue

            level, tag, pid, msg = match.groups()
            tag = tag.strip()
            level = level.strip()

            if level in ("W", "E", "F"):
                if is_target_log(tag, msg, pid or ""):
                    timestamp = time.strftime("%H:%M:%S")
                    print(f"\n⏰ [{timestamp}]")
                    print(translate_log_line(tag, level, msg))
                    print("-" * 50)
                    sys.stdout.flush()

    except KeyboardInterrupt:
        print("\n🛑 Dừng giám sát logcat.")
    finally:
        proc.terminate()

if __name__ == "__main__":
    main()
