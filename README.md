# LiteMusic 🎵

> Ứng dụng nghe nhạc Android siêu nhẹ, mượt mà trên Android 7.0+ (API 24+), hỗ trợ đa nguồn phát miễn phí (YouTube Music, SoundCloud, Spotify Metadata Bridge), phát nền và cân bằng âm lượng tự động (Hardware DSP Normalizer).

---

## ⚡ Điểm nổi bật & Tính năng chính

- **Siêu nhẹ & Tiết kiệm tài nguyên**: Kích thước APK ~10–12 MB sau R8 ProGuard, mức chiếm RAM cực thấp (< 50–70 MB khi phát nền).
- **Android 7.0+ (API 24+) Tương thích cao**: Chạy ổn định từ máy cấu hình thấp đến Android 15.
- **Phát nền chuẩn Google Media3**: Tự động hiển thị notification điều khiển, hỗ trợ phím tai nghe Bluetooth, tự tạm dừng khi rút tai nghe (`handleAudioBecomingNoisy`).
- **Cân bằng âm lượng (Volume Normalization / Loudness DSP)**: Tích hợp `android.media.audiofx.LoudnessEnhancer` phần cứng theo chuẩn -14 LUFS, không làm méo tiếng.
- **Đa nguồn phát miễn phí (Multi-Source Stream Engine)**:
  - **YouTube Music**: Stream trực tiếp Opus 160kbps (chỉ tải audio, không tốn dữ liệu video).
  - **SoundCloud**: Stream trực tiếp MP3/Opus.
  - **Spotify**: Đọc playlist/metadata và tự động map sang audio stream không giới hạn.
- **CI/CD Tự động qua GitHub Actions**: Toàn bộ quá trình build, tối ưu hóa ProGuard, ký số APK (Signing) và xuất file cài đặt đều diễn ra trên GitHub Actions với bộ nhớ đệm (Cache) tốc độ cao.

---

## 🔑 Cấu hình Khóa Ký Dev (Persistent Dev Keystore)

Để đảm bảo mọi bản build/commit mới nhất đều có **cùng một chữ ký (Signature)**, cho phép bạn cài đè/update trực tiếp trên điện thoại mà không bị lỗi *"App not installed as package conflicts with an existing package"*, dự án đã tạo sẵn dev keystore tại:

- **Đường dẫn**: `keystore/dev-release.jks`
- **Key Alias**: `litemusic`
- **Store / Key Password**: `litemusic123`
- **Thuật toán**: RSA 2048-bit, hiệu lực 10.000 ngày.

> Gradle tự động đọc keystore này để ký cả bản `debug` và `release` trên GitHub Actions.

---

## 🚀 Đẩy Code lên GitHub để Build APK Tự Động

Trong thư mục `/storage/emulated/0/code/LiteMusic`, chạy các lệnh sau:

```bash
git init
git add .
git commit -m "feat: initial LiteMusic core with Media3 and GitHub Actions CI"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
git push -u origin main
```

Ngay sau khi `push`:
1. GitHub Actions sẽ tự động kích hoạt workflow `.github/workflows/build.yml`.
2. Gradle Cache sẽ lưu trữ dependencies để các lần build sau chỉ mất ~1-2 phút.
3. Khi hoàn tất, file APK đã ký sẵn mang tên `LiteMusic-<hash>-release.apk` sẽ nằm trong mục **Actions -> Summary -> Artifacts** để bạn tải về cài đặt trực tiếp.
4. Nếu bạn gắn tag `git tag v1.0.0 && git push origin v1.0.0`, GitHub Actions sẽ tự động tạo một **GitHub Release** công khai đính kèm file APK.
