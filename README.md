# FreeLLMAPI (ชื่อโปรเจกต์ชั่วคราว)

FreeLLMAPI เป็นแอป Android แบบ AI API Gateway ทำงานบนเครื่องของคุณเอง แอปรวม API ของหลาย provider ไว้หลัง endpoint เดียวที่ใช้รูปแบบเดียวกับ OpenAI

ระหว่างทาง แอปจะเลือกเส้นทางโมเดล สลับ key และ fallback ให้อัตโนมัติ พร้อมเก็บสถิติการใช้งานและ log ไว้ด้วย

แนวคิดมาจาก [FreeLLMAPI ฝั่ง Node.js](https://github.com/tashfeenahmed/freellmapi) แต่โค้ดชุดนี้เขียนใหม่ทั้งหมดเป็นแอป Android แบบ native

## ฟีเจอร์

| ฟีเจอร์ | รายละเอียด |
|---|---|
| Dashboard | สถานะเซิร์ฟเวอร์, จำนวน request, success rate, token และ latency ของวันนี้, กราฟ 7 วัน, สุขภาพของแต่ละ provider |
| Providers | เพิ่ม ลบ เปิด/ปิด และเรียงลำดับความสำคัญ provider ได้ แก้ Base URL ได้ และปุ่ม Test & sync ใช้ดึงรายชื่อโมเดลจริง |
| API Keys | เพิ่มได้หลาย key ต่อ provider, แสดงแบบปิดบางส่วน, ดูสถานะ Active / Cooling / Error |
| Models | ค้นหาโมเดล กรองตาม provider, Free หรือ Enabled เปิด/ปิดรายโมเดล และแตะเพื่อคัดลอก id |
| Playground | แชตทดสอบผ่าน router ตัวเดียวกับ API ปรับ stream, temperature, max tokens และ system prompt ได้ ใต้คำตอบแสดงเส้นทาง fallback |
| Unified API | `POST /v1/chat/completions` (รองรับทั้ง stream และ non-stream) กับ `GET /v1/models` |
| Model Routing | ใช้ได้ทั้ง `auto`, alias ที่ตั้งเอง, model id ตรงตัว หรือ `<provider>/<model>` มีกลยุทธ์ Priority, Round-robin และ Fastest |
| Automatic Fallback | 429 ให้ key นั้นพักตาม Retry-After แล้วลอง key ถัดไป, 401/403 พัก key 10 นาที, 5xx/timeout ข้ามไป provider ถัดไป |
| Usage Tracking | นับ request และ token ต่อ provider ต่อโมเดล ดูได้ทั้งวันนี้, 7 วัน และ 30 วัน |
| Request Logs | status, latency, token และเส้นทาง fallback (ไม่เก็บ prompt และไม่เก็บ key) |
| Dark Mode | มีโหมด Dark, Light และตามระบบ (ค่าเริ่มต้นคือ Dark) |
| Local API Server | ตั้ง port ได้ เลือกเปิดให้ LAN ใช้ได้ มี gateway key และทำงานเป็น foreground service |

UI เขียนด้วย Jetpack Compose และ Material 3 ทั้งหมด ไม่มี WebView บนมือถือใช้ Navigation Bar ด้านล่าง ส่วนจอกว้างตั้งแต่ 600dp ขึ้นไป เช่นแท็บเล็ต จะเปลี่ยนเป็น Navigation Rail

## Provider ที่รองรับ (ตรวจกับเอกสาร API เมื่อ พ.ย. 2026)

| Provider | Base URL เริ่มต้น | โปรโตคอล | หมายเหตุจากเอกสาร |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | Chat Completions | ส่ง `max_completion_tokens` แทน `max_tokens` |
| Anthropic | `https://api.anthropic.com` | Messages `/v1/messages` | ส่ง `anthropic-version: 2026-10-21` และไม่ส่ง temperature/top_p เพราะเวอร์ชันนี้เอาออกแล้ว |
| Google Gemini | `https://generativelanguage.googleapis.com` | `generateContent` / `streamGenerateContent?alt=sse` | ใช้ header `x-goog-api-key` และกรอง thought parts ออก |
| DeepSeek | `https://api.deepseek.com` | OpenAI-compatible | โมเดล `deepseek-flash`, `deepseek-v4-pro` |
| Groq | `https://api.groq.com/openai/v1` | OpenAI-compatible | ไม่ส่ง `logprobs`, `logit_bias`, `n` และอ่าน usage จาก `x_groq` |
| OpenRouter | `https://openrouter.ai/api/v1` | OpenAI-compatible | ส่ง header `X-OpenRouter-Title`, ข้าม SSE comment และตอน sync จะเปิดใช้เฉพาะโมเดลฟรี |
| Qwen | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | OpenAI-compatible | key ผูกกับ region ถ้าอยู่ region อื่นให้แก้ Base URL |
| Custom | `http://127.0.0.1:11434/v1` | OpenAI-compatible | สำหรับ Ollama, LM Studio, vLLM ใช้ได้โดยไม่ต้องมี key |

## Build APK

ต้องมี JDK 17 ขึ้นไป และ Android SDK platform 35 (Android Studio ติดตั้งให้ครบ) Gradle wrapper จะดาวน์โหลด Gradle 8.11.1 ให้เอง

```bash
./gradlew assembleDebug
```

ไฟล์ที่ได้อยู่ที่ `app/build/outputs/apk/debug/app-debug.apk` รองรับ Android 10 (API 29) ขึ้นไป

ติดตั้งลงมือถือที่เปิด USB debugging:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ทดสอบ:

```bash
./gradlew testDebugUnitTest          # unit tests: router, fallback, adapters, SSE, local server
./gradlew connectedDebugAndroidTest  # บนเครื่องหรือ emulator: Room + Keystore + server + UI
```

`.github/workflows/android.yml` จะ build APK, รัน unit test แล้วติดตั้งและทดสอบบน emulator Android 10 กับ Android 15 จากนั้นแนบ APK ไว้ใน GitHub Release

## ใช้งาน

1. ไปที่แท็บ Providers กด Add provider เลือก provider แล้วใส่ API key
2. ในหน้าของ provider กด Test & sync models เพื่อดึงรายชื่อโมเดลจริงมา
3. ไปที่ More → Local API server กด Start แล้วคัดลอก gateway key
4. เรียกจากแอปไหนก็ได้ที่ใช้ OpenAI SDK

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Authorization: Bearer <gateway key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"auto","messages":[{"role":"user","content":"สวัสดี"}]}'
```

ค่า `model` ใช้ได้ดังนี้:
- `auto`: ใช้โมเดลหลักของแต่ละ provider ตามลำดับความสำคัญ
- alias ที่สร้างไว้ใน Routing เช่น `fast`
- model id ตรงตัว เช่น `llama-3.3-70b-versatile` จะลองทุก provider ที่มีโมเดลนี้
- `groq/openai/gpt-oss-120b` ระบุ provider ชัดเจน

ถ้าส่งชื่อโมเดลที่ไม่มีอยู่ จะได้ 404 แอปจะไม่แอบสลับไปใช้โมเดลอื่น

response จะมี header `X-FreeLLMAPI-Provider`, `X-FreeLLMAPI-Model` และ `X-FreeLLMAPI-Attempts` บอกว่า request ถูกส่งไปที่ไหน

## ความปลอดภัย

- **การเก็บ key**: API key ทุกตัวและ gateway key เข้ารหัสด้วย AES-256-GCM โดยใช้ key ใน Android Keystore ที่ export ออกมาไม่ได้ ในฐานข้อมูลจึงมีแค่ ciphertext กับ key แบบปิดบางส่วน
- **การใช้ key**: แอปถอดรหัส key ในหน่วยความจำเฉพาะตอนที่ request นั้นต้องใช้
- **Log**: ข้อความ error ทุกอันถูกลบส่วนที่ดูเหมือน key ออกก่อนบันทึกหรือแสดงผล และ log ไม่เก็บ prompt หรือคำตอบ
- **การเข้าถึงเซิร์ฟเวอร์**: ค่าเริ่มต้นเซิร์ฟเวอร์ฟังแค่ `127.0.0.1` ถ้าเปิดโหมด LAN จะรับเฉพาะ IP ในเครือข่ายส่วนตัว (10/8, 172.16/12, 192.168/16, link-local, IPv6 ULA) ส่วน IP สาธารณะถูกปฏิเสธเสมอ
- **การยืนยันตัวตน**: ทุก route ภายใต้ `/v1` ต้องมี gateway key ซึ่งตรวจแบบ constant-time ถ้าใส่ key ผิด 10 ครั้งใน 1 นาที IP นั้นจะถูกล็อก 1 นาที
- **CORS**: ไม่ส่ง CORS header หน้าเว็บในเบราว์เซอร์จึงเรียก API นี้ไม่ได้
- **Backup**: ปิด backup และ device transfer ไว้ กันข้อมูลที่เข้ารหัสหลุดออกจากเครื่อง
- **Cleartext**: บังคับ HTTPS ทุก host ยกเว้น localhost, 127.0.0.1 และ 10.0.2.2 สำหรับเซิร์ฟเวอร์ในเครื่อง

## โครงสร้างโค้ด

```
app/src/main/java/com/avatarsdev/freellmapi/
├── core/            Kotlin ล้วน ทดสอบบน JVM ได้
│   ├── provider/    ProviderType + adapters (OpenAI-compatible, Anthropic, Gemini)
│   ├── gateway/     ModelRouter, GatewayEngine (fallback + key rotation)
│   ├── server/      MiniHttpServer, GatewayApi (auth, LAN guard), OpenAI protocol
│   ├── net/         OkHttp wrapper, SSE parser
│   └── security/    Redactor
├── data/            Room (providers, keys, models, routes, logs, usage), repositories, settings
├── security/        KeystoreCipher (AES-GCM + Android Keystore)
├── service/         ServerController, GatewayService (foreground service)
└── ui/              Compose: theme, components, ทุกหน้าจอ
```
