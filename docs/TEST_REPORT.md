# รายงานผลการทดสอบ — TikTok Upgrade AI v1.0.0

**CI run:** GitHub Actions #7 (run 42779225702) · commit `7ad133a` · 10 พ.ย. 2026
**ผลรวม:** ✅ Build ผ่าน · ✅ Unit tests 34/34 · ✅ ติดตั้งและทดสอบบนอุปกรณ์ Android 10 และ Android 15 ผ่านทั้งคู่ · ไม่พบ crash/ANR

APK: `app-debug.apk` (17.9 MB, debug-signed) · versionName 1.0.0 · minSdk 26 · targetSdk 35
SHA-256: `a85eb13924508c26df83e64c8b6ee975b29247c4e83fdca42c21780dad84c80f`
ดาวน์โหลด: Release `tiktok-upgrade-ai-build-7`

## 1. Unit tests (JVM) — 34 ผ่าน / 0 ล้มเหลว
| Suite | Tests | ครอบคลุม |
|---|---|---|
| TikTokExportParserTest | 10 | โครงสร้าง Export แบบเก่า (`Activity › Like List`) และใหม่ (`Likes and Favorites`, key ตัวเล็ก), อ่านเฉพาะ Like List ไม่ปน Favorites/Watch History, ตัดรายการซ้ำ, UTC vs เวลาเครื่อง, BOM, TXT, ZIP (JSON และ TXT ข้างใน), ไฟล์เสีย/ไม่เกี่ยวข้อง, รูปแบบวันที่, ดึง Video ID |
| PromptParserTest | 11 | Prompt ตัวอย่างจากโจทย์ → Roblox 50 / AI Tools 30 / Programming 20 + ไม่แสดง เต้น/ตลก, คั่นด้วย `,` และไม่มีตัวคั่น, English negation, แบ่ง % ที่เหลือ, normalize ให้รวม 100, หมวดกำหนดเอง, exclude ชนะ include, word boundary, JSON round-trip |
| FeedPlannerAndScorerTest | 4 | แผน 20 ช่อง = 10/6/4 และไม่มีหมวดเดียวติดกัน 3 ครั้ง, ลิงก์ค้นหา encode ถูก, ให้คะแนน/บล็อกคลิป (ไทย+อังกฤษ), การจัดอันดับ (บล็อกอยู่ท้าย) |
| LikeAnalyticsTest | 5 | กรองช่วง 5–6 พ.ย. ตามเวลาท้องถิ่น, เรียงใหม่→เก่า/เก่า→ใหม่, ค้นหา, รายวัน/รายชั่วโมง/สรุป, CSV (escape + กัน formula injection), ข้อมูลตัวอย่างติดป้าย SAMPLE และ deterministic |
| LlmPromptClientTest | 4 | เรียก OpenAI-compatible endpoint (MockWebServer), header/body ถูก, แปลง JSON จากโมเดล + normalize, HTTP error, คำตอบไม่ใช่ JSON |

## 2. ทดสอบบนอุปกรณ์ (Emulator) — ผ่านทั้ง 2 เวอร์ชัน
| ขั้นตอน | Android 10 (API 29) | Android 15 (API 35) |
|---|---|---|
| ติดตั้ง APK (`adb install`) | ✅ Success | ✅ Success |
| เปิดแอป (cold start) และยังทำงานอยู่ | ✅ | ✅ |
| รับคลิปที่แชร์เข้ามา (ACTION_SEND) → เปิดหน้า AI Feed | ✅ | ✅ |
| ตรวจ logcat หา FATAL EXCEPTION / ANR | ✅ ไม่พบ | ✅ ไม่พบ |
| UI test: ข้อมูลตัวอย่าง → Dashboard → Analytics (30 วัน, กราฟ, Export) → History (ค้นหา, สลับเรียง) | ✅ | ✅ |
| UI test: พิมพ์ Prompt จากโจทย์ → ได้ 50/30/20, ไม่แสดง เต้น/ตลก, แผนเซสชัน, ตรวจคลิป (บล็อก "dance" / ตรงหมวด Roblox) | ✅ | ✅ |
| UI test: นำเข้า Export จริง (JSON), นำเข้าซ้ำ = 0 ใหม่/5 ซ้ำ, เลือก 5–6 พ.ย. → Likes รวม = 3 ตรงกับที่คำนวณ, ไม่มีแถบข้อมูลตัวอย่าง, History แสดง 3 คลิป | ✅ | ✅ |

Screenshots ของทุกขั้นอยู่ใน branch `ci-results-tiktok` (`device-api29/screens`, `device-api35/screens`)

## 3. Bug ที่พบและแก้ระหว่างทดสอบ
| # | อาการ | สาเหตุ | แก้ไข |
|---|---|---|---|
| 1 | UI test ล้มบน Android 15 (จอ 320×640) | ปุ่ม "ลองด้วยข้อมูลตัวอย่าง" อยู่ใต้แถบเมนูล่าง การแตะไปโดนแถบเมนู | Test เลื่อนปุ่มขึ้นมาก่อนแตะ |
| 2 | ชื่อเมนูล่างถูกตัด ("หน้าห…", "วิเครา…") บนจอแคบ | ข้อความยาวเกิน 5 ช่อง | ใช้ "หน้าแรก / AI Feed / สถิติ / ประวัติ / ตั้งค่า" |
| 3 | ป้ายวันที่ใต้กราฟ 30 วันซ้อนกัน | แสดงป้ายถี่เกินไป | แสดงไม่เกิน ~4 ป้าย |
| 4 | Placeholder ช่องค้นหาขึ้น 2 บรรทัด | ข้อความยาว | ย่อเป็นบรรทัดเดียว |

## 4. สิ่งที่ยังไม่ได้ทดสอบ / ข้อจำกัด
- ยังไม่ได้ทดสอบกับไฟล์ Export จริงของบัญชี TikTok (ใช้ไฟล์จำลองตามโครงสร้างที่เอกสาร/ตัวอย่างสาธารณะระบุ) — ถ้านำเข้าแล้วไม่เจอ Like ให้ส่งโครงสร้าง key ของไฟล์ (ไม่ต้องส่งข้อมูลจริง) มาเพื่อปรับ parser
- เวลาในไฟล์ Export สันนิษฐานว่าเป็น UTC (มีสวิตช์ใน Settings ให้เปลี่ยนเป็นเวลาเครื่อง)
- AI ภายนอก (LLM) ทดสอบกับ mock server เท่านั้น ยังไม่ได้ทดสอบกับผู้ให้บริการจริง
- การเปิดลิงก์ค้นหาใน TikTok ขึ้นกับว่าแอป TikTok ในเครื่องรับลิงก์ tiktok.com หรือไม่ (ถ้าไม่ จะเปิดในเบราว์เซอร์)
- ไม่ได้ทดสอบบน Android 8–9 (minSdk 26) บน emulator
