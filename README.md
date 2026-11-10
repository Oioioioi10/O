# TikTok Upgrade AI (Android)

แอป Android สำหรับ **กำหนดสัดส่วนคอนเทนต์ที่อยากดูด้วย AI** และ **วิเคราะห์ประวัติการกด Like** จากไฟล์ข้อมูล TikTok ของคุณเอง

> แอปนี้ไม่ได้เป็นของหรือร่วมกับ TikTok และ **ไม่ได้ควบคุมอัลกอริทึม For You ของ TikTok โดยตรง** (TikTok ไม่มี API สำหรับเรื่องนี้) — ดูรายละเอียดใน [docs/PHASE1_RESEARCH.md](docs/PHASE1_RESEARCH.md)

## ติดตั้ง APK
1. ดาวน์โหลด `app-debug.apk` จากหน้า **Releases** (แท็ก `tiktok-upgrade-ai-build-N`) หรือจาก branch `ci-results-tiktok` › `build/app-debug.apk`
2. เปิดไฟล์บนมือถือ › อนุญาต "ติดตั้งแอปที่ไม่รู้จัก" ให้แอปที่ใช้เปิดไฟล์ (เช่น Files/Chrome)
3. กดติดตั้ง — รองรับ Android 8.0 (API 26) ขึ้นไป

APK เป็น debug build (เซ็นด้วย debug key) ติดตั้งใช้งานได้ปกติ แต่ไม่ได้มีไว้ลง Play Store

## วิธีใช้
### 1) นำเข้าประวัติ Like
1. ในแอป TikTok: โปรไฟล์ › ☰ › Settings and privacy › Account › **Download your data**
2. เลือกรูปแบบ **JSON** แล้ว Request data → รอจนพร้อม → ดาวน์โหลดไฟล์ ZIP
3. ใน TikTok Upgrade AI: **ตั้งค่า › เลือกไฟล์จากเครื่อง** › เลือก ZIP (หรือ JSON/TXT ที่แตกแล้ว)
4. แอปอ่านเฉพาะ *Like List* (วันที่ + ลิงก์คลิป), ตัดรายการซ้ำอัตโนมัติ นำเข้าซ้ำได้

ยังไม่มีไฟล์? กด **"ลองด้วยข้อมูลตัวอย่าง"** — ข้อมูลตัวอย่างเก็บแยก มีแถบสีเหลือง "ข้อมูลตัวอย่าง" ทุกหน้า และไม่ปนกับข้อมูลจริง

### 2) Like Analytics / History
- เลือกช่วงวันที่ (เช่น 5–6 พฤศจิกายน) หรือปุ่มลัด วันนี้ / 7 วัน / 30 วัน / ทั้งหมด
- ดู Likes รวม, เฉลี่ยต่อวัน, วันที่กดมากสุด, ชั่วโมงที่กดบ่อย, กราฟรายวันและรายชั่วโมง (แตะแท่งเพื่อดูค่า)
- History: รายการคลิปพร้อมวันและเวลา จัดกลุ่มตามวัน ค้นหาได้ (Video ID, ลิงก์, เวลาเช่น `21:`) สลับ ใหม่→เก่า / เก่า→ใหม่
- **Export CSV** ตามช่วงวันที่และคำค้นที่เลือก (UTF-8 + BOM เปิดใน Excel ได้)

### 3) AI Feed Controller
พิมพ์ เช่น
```
แสดงคลิป Roblox 50%
AI Tools 30%
Programming 20%
ไม่ต้องการคลิปเต้น
ไม่ต้องการคลิปตลก
```
AI จะแปลงเป็น
- **สัดส่วนหมวด** (ปรับให้รวม 100% อัตโนมัติ, หมวดที่ไม่ระบุ % แบ่งส่วนที่เหลือ, หัวข้อที่ไม่รู้จักกลายเป็นหมวดกำหนดเอง)
- **หมวดที่ไม่แสดง** + Keyword ของแต่ละหมวด
- **เงื่อนไขการจัดอันดับ**: ตัดคลิปต้องห้ามก่อน → ให้คะแนนตามน้ำหนัก → โบนัสหลาย keyword → สลับหมวดแบบ Smooth Weighted Round-Robin
- **แผนเซสชัน 20 คลิป** (เช่น Roblox ×10, AI Tools ×6, Programming ×4) แตะแต่ละช่องเพื่อเปิดผลค้นหาใน TikTok
- **ตรวจคลิป/คิวจัดอันดับ**: แชร์คลิปจาก TikTok มาที่แอป (แชร์ › TikTok Upgrade AI) หรือวางแคปชัน/แฮชแท็ก แอปให้คะแนนและเรียงลำดับตามกติกา
- บันทึกเป็นโปรไฟล์ฟีดเพื่อสลับใช้ภายหลัง

ตัววิเคราะห์ค่าเริ่มต้นทำงาน **ในเครื่อง (ออฟไลน์)** รองรับไทย/อังกฤษ ถ้าต้องการให้ LLM ช่วยแปลงคำสั่งที่ซับซ้อน ใส่ Base URL / API Key / Model แบบ OpenAI-compatible ใน Settings (ถ้าเรียกไม่สำเร็จจะกลับไปใช้ตัวในเครื่องอัตโนมัติ)

## ความเป็นส่วนตัว
- ไม่ล็อกอิน TikTok และไม่ดึงข้อมูลบัญชี — ใช้เฉพาะไฟล์ที่ผู้ใช้เลือกเอง
- ข้อมูลอยู่ใน Room database บนเครื่อง (ปิด backup)
- สิทธิ์ INTERNET ใช้เฉพาะ AI ภายนอกที่ผู้ใช้ตั้งค่าเอง

## โครงสร้างโปรเจกต์
```
app/src/main/java/com/avatarsdev/tiktokupgrade/
├── TikTokUpgradeApp.kt, AppContainer.kt      # Application + DI แบบง่าย
├── data/
│   ├── db/                 # Room: likes, feed_profiles, inbox_clips
│   ├── importer/           # TikTokExportParser (ZIP / JSON หลายเวอร์ชัน / TXT)
│   ├── sample/             # ข้อมูลตัวอย่าง (source = SAMPLE, host example.com)
│   ├── LikeRepository.kt, SettingsRepository.kt
├── domain/
│   ├── analytics/          # LikeAnalytics (กรองช่วง, รายวัน, รายชั่วโมง, สรุป), CsvExporter
│   └── feed/               # Categories, PromptParser, Weights, FeedPlanner, ClipScorer, LlmPromptClient
└── ui/                     # Jetpack Compose (MVVM)
    ├── AppRoot.kt, MainActivity.kt (รับ Share intent)
    ├── dashboard/  feed/  likes/ (Analytics + History)  settings/
    ├── components/ (การ์ด, กราฟ Canvas, แถบน้ำหนัก)  theme/ (Dark premium)
app/src/test/               # Unit tests (JVM)
app/src/androidTest/        # UI tests บนอุปกรณ์จริง/Emulator + screenshots
ci/device-test.sh           # ติดตั้ง, เปิดแอป, ทดสอบ Share intent, ตรวจ crash, รัน UI tests
.github/workflows/android.yml
```
Stack: Kotlin 2.1 · Jetpack Compose (Material 3) · Room 2.6 · MVVM (ViewModel + StateFlow) · kotlinx.serialization · OkHttp · minSdk 26 / targetSdk 35

## Build เอง
```bash
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit tests
./gradlew connectedDebugAndroidTest  # UI tests (ต้องมีอุปกรณ์/emulator)
```
ต้องใช้ JDK 17 และ Android SDK 35 (หรือเปิดด้วย Android Studio)

CI (GitHub Actions) build ทุกครั้งที่ push ไปที่ branch `tiktok-upgrade-ai` ทดสอบบน emulator Android 10 และ 15 แล้วเผยแพร่ APK เป็น Release — ผลการทดสอบล่าสุดดู [docs/TEST_REPORT.md](docs/TEST_REPORT.md)
