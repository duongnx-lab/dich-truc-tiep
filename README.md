# Dịch Trực Tiếp — ứng dụng phiên dịch giọng nói thời gian thực (Android)

Ứng dụng Android (Kotlin + Jetpack Compose) nghe giọng nói, **tự phát hiện ngôn ngữ** và **dịch ngay lập tức** sang ngôn ngữ bạn chọn.

## Tính năng chính

### 1. Nghe & Dịch (phụ đề trực tiếp)
Dùng khi trong phòng có người nước ngoài phát biểu: đặt điện thoại hướng về người nói, bấm micro.
- Nghe **liên tục**, tự cắt câu theo khoảng lặng.
- **Tự nhận diện ngôn ngữ người nói** và dịch sang ngôn ngữ đích (mặc định Tiếng Việt).
- **Phụ đề tạm thời** hiện ngay khi người nói chưa dứt câu, rồi được thay bằng bản dịch hoàn chỉnh.
- Câu đã là ngôn ngữ đích (ví dụ người Việt chen vào) thì giữ nguyên, không dịch.
- **Chế độ phụ đề toàn màn hình** (chữ lớn, nền đen) để nhiều người cùng xem.
- Tùy chọn **đọc to bản dịch** (nên đeo tai nghe), chỉnh cỡ chữ, ẩn/hiện câu gốc.
- Sao chép / chia sẻ toàn bộ biên bản, tự lưu vào Lịch sử.

### 2. Hội thoại 2 người
Hai người hai thứ tiếng nói chuyện trực tiếp với nhau.
- Màn hình chia đôi; nửa trên **xoay ngược 180°** để người ngồi đối diện đọc được.
- **Chế độ tự động (micro giữa)**: nghe rảnh tay, tự biết ai đang nói (dựa trên ngôn ngữ), dịch sang ngôn ngữ của người kia và **đọc to**.
- **Chế độ bấm-để-nói (micro mỗi bên)**: chính xác nhất trong môi trường ồn.
- Tự **tạm ngắt micro khi đang đọc** bản dịch để không tự nghe lại chính mình.
- Mỗi nửa màn hình hiển thị bằng đúng ngôn ngữ của người đó.

### 3. Chức năng mở rộng
- **Dịch văn bản**: gõ / dán / đọc, tự nhận diện ngôn ngữ, dịch tự động khi gõ.
- **Bôi đen chữ ở bất kỳ ứng dụng nào → "Dịch với Dịch Trực Tiếp"**, hoặc Chia sẻ văn bản vào app.
- **Lịch sử** các phiên, xuất tệp `.txt`, chia sẻ.
- **Quản lý gói ngôn ngữ offline** (tải trước để dịch không cần mạng).
- 36 ngôn ngữ (Việt, Anh, Trung, Nhật, Hàn, Thái, Khmer, Lào, Pháp, Đức, Nga, …).

## Cách hoạt động

```
Micro ─► Nhận dạng giọng nói ─► Xác định ngôn ngữ ─► Hàng đợi dịch (giữ thứ tự) ─► Hiển thị + Đọc to ─► Lưu lịch sử
          (Android / Whisper)    (gợi ý của bộ nhận dạng   (ML Kit offline / AI)
                                  + bộ chữ viết + ML Kit
                                  Language ID)
```

Có 2 bộ máy nhận dạng, chọn trong **Cài đặt**:

| | Android (Google) – mặc định | Whisper (API đám mây) |
|---|---|---|
| Chi phí | Miễn phí | Groq: có gói miễn phí · OpenAI: trả phí |
| Tự nhận diện ngôn ngữ | Trong **tối đa 4 ngôn ngữ khai báo trước** | **Mọi ngôn ngữ** (~100) |
| Độ chính xác khi nói xa / ồn | Khá | Tốt |
| Phụ đề tạm thời | Rất nhanh | ~2 giây/lần |
| Offline | Có (Android 12+, nếu máy có gói) | Không |

Và 2 bộ máy dịch:
- **ML Kit (offline)**: miễn phí, chạy trên máy, ~30 MB mỗi ngôn ngữ (không có Khmer/Lào/Myanmar).
- **AI (API đám mây)**: hiểu ngữ cảnh các câu trước, tự sửa lỗi nhận dạng, dịch tự nhiên hơn.

Ứng dụng tự chuyển phương án: ngôn ngữ ML Kit không hỗ trợ → dùng AI (nếu có key); mất mạng khi dùng AI → dùng ML Kit (nếu đã có gói).

> **Khuyến nghị cho "người nước ngoài phát biểu trong phòng" mà bạn không biết trước ngôn ngữ:** dùng **Whisper + Groq** (miễn phí). Nếu biết trước (ví dụ luôn là tiếng Anh/Nhật), bộ nhận dạng Android là đủ và nhanh hơn.

## Build & cài đặt

### Cách 1 — Android Studio (khuyên dùng)
1. Cài [Android Studio](https://developer.android.com/studio) (bản Ladybug 2024.2 trở lên).
2. **File › Open** → chọn thư mục này (`Dịch thời gian thực`). Chờ Gradle Sync xong (lần đầu tải thư viện vài phút).
3. Cắm điện thoại (bật *Gỡ lỗi USB*) → bấm **Run ▶**.
4. Tạo file APK: **Build › Build App Bundle(s) / APK(s) › Build APK(s)** → file ở `app/build/outputs/apk/debug/`.

> Mẹo: nếu đường dẫn thư mục có dấu tiếng Việt mà Gradle báo lỗi lạ, chép dự án sang đường dẫn không dấu (ví dụ `C:\Projects\DichTrucTiep`).

### Cách 2 — Dòng lệnh
Cần JDK 17 và Android SDK (đặt `ANDROID_HOME` hoặc tạo `local.properties` với `sdk.dir=...`):
```bat
gradlew.bat assembleDebug
```

### Cách 3 — GitHub Actions (không cần cài gì)
Mã nguồn đã có trên repo riêng tư **github.com/duongnx-lab/dich-truc-tiep**. Mỗi lần đẩy code lên nhánh `main`, workflow `.github/workflows/build-apk.yml` (nằm trong repo) sẽ:
1. build APK debug + release,
2. chạy thử trên emulator Android 14 (mở từng màn hình, chụp ảnh, kiểm tra crash),
3. đưa APK + ảnh chụp vào nhánh `build-output`, đồng thời để ở tab **Actions › (lần chạy) › Artifacts**.

Thư mục `ci/` chứa kịch bản chạy thử trên emulator.

Yêu cầu máy: **Android 8.0 (API 26) trở lên**. Bộ nhận dạng Android cần ứng dụng **Google** (hoặc dịch vụ nhận dạng giọng nói khác) trên máy.

## Lấy API key miễn phí (Groq) cho Whisper + dịch AI
1. Vào https://console.groq.com/keys → đăng nhập → **Create API Key**.
2. Mở app › **Cài đặt** › *API đám mây* › chọn **Groq** › dán API key › **Kiểm tra kết nối**.
3. Chọn *Nhận dạng: Whisper* và/hoặc *Dịch: AI*.

Mặc định: Groq `whisper-large-v3-turbo` (nhận dạng) + `llama-3.3-70b-versatile` (dịch); OpenAI `whisper-1` + `gpt-4o-mini`. Tên mô hình có thể sửa trong Cài đặt nếu nhà cung cấp thay đổi. Chọn "Tùy chỉnh" để dùng bất kỳ máy chủ tương thích OpenAI nào (kể cả tự host).

## Mẹo dùng hiệu quả
- **Tải trước gói offline** (Cài đặt › Gói ngôn ngữ offline) cho các ngôn ngữ hay dùng → dịch tức thì.
- Chế độ Nghe & Dịch + bộ nhận dạng Android: chạm **"Người nói"** để khai báo đúng ngôn ngữ của diễn giả.
- Bật **Đọc to** trong phòng họp thì nên **đeo tai nghe**.
- Hội thoại ở nơi ồn: dùng nút **bấm-để-nói** của từng bên.
- Hội thoại: nếu người nghe thiếu giọng đọc, cài thêm giọng ở *Cài đặt › Giọng đọc của hệ thống*.

## Cấu trúc mã nguồn
```
app/src/main/java/vn/dichtructiep/
├── App.kt, MainActivity.kt          # khởi tạo, nhận văn bản chia sẻ
├── data/                            # Ngôn ngữ, Cài đặt (DataStore), Lịch sử (JSON)
├── speech/
│   ├── AndroidSpeechEngine.kt       # SpeechRecognizer nghe liên tục, đa ngôn ngữ
│   └── CloudSpeechEngine.kt         # Thu âm 16 kHz + VAD tự cắt câu + Whisper
├── translate/
│   ├── LanguageDetector.kt          # gộp 3 nguồn bằng chứng để xác định ngôn ngữ
│   ├── TranslationService.kt        # ML Kit offline / AI, tự chuyển phương án
│   └── ModelManager.kt              # tải / xóa gói ngôn ngữ offline
├── net/OpenAiClient.kt              # API tương thích OpenAI (Groq, OpenAI…)
├── tts/Speaker.kt                   # đọc bản dịch
├── live/LiveViewModel.kt            # bộ não của 2 chế độ dịch trực tiếp
└── ui/                              # Compose: home, live (Nghe & Dịch, Hội thoại), text, history, settings
```

## Hướng phát triển tiếp
- Dịch khi tắt màn hình / phụ đề nổi trên ứng dụng khác (Foreground Service + overlay).
- Nhận dạng Whisper ngay trên máy (whisper.cpp) để tự nhận diện mọi ngôn ngữ mà không cần mạng.
- Nhận dạng streaming độ trễ thấp (Deepgram, Google Cloud Speech v2, OpenAI Realtime).
- Phân biệt người nói theo giọng (speaker diarization) khi cả hai nói cùng ngôn ngữ.
- Dịch cuộc gọi / âm thanh phát từ máy (AudioPlaybackCapture, Android 10+).
