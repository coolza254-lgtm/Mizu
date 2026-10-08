# Mizu 💧

แอปบันทึกการดื่มน้ำ (Android, Kotlin + Jetpack Compose) — offline-first, ไม่มี login / ads / analytics
ภาษา: ไทย (ค่าเริ่มต้น) · English · 日本語 (สลับในแอปได้ทันที)

## Build / test
```
./gradlew :core:test            # unit tests (reminder engine, weigh, CSV, version compare)
./gradlew :app:assembleDebug    # APK -> app/build/outputs/apk/debug/
```
ต้องใช้ JDK 17 และ Android SDK (CI ติดตั้งให้เอง) · `core/` เป็น Kotlin ล้วน ไม่มี Android import

## อัปเดตในแอป
ตั้งค่า → "อัปเดตแอป" → ตรวจสอบ → ดาวน์โหลด → ติดตั้ง
แอปอ่าน GitHub Release ล่าสุดของ `coolza254-lgtm/mizu` (repo ต้องเป็น **public**) แล้วโหลดไฟล์ `.apk` ใน release
(เป็นการเชื่อมต่ออินเทอร์เน็ตเพียงอย่างเดียวของแอป และทำเฉพาะตอนกดเอง)

### ปล่อยเวอร์ชันใหม่
1. แก้โค้ด → merge เข้า branch หลัก
2. Actions → **Release** → Run workflow → ใส่เลขเวอร์ชัน เช่น `1.0.1` (หรือ push tag `v1.0.1`)
3. รอ workflow เสร็จ แล้วเปิดแอปบนมือถือ กดตรวจสอบอัปเดต

ข้อควรรู้
- ครั้งแรกต้องติดตั้ง APK ด้วยมือ (จาก Releases หรือ artifact ของ CI) แล้วอนุญาต "ติดตั้งแอปที่ไม่รู้จัก" ให้ Mizu
- APK ทุกเวอร์ชันต้องเซ็นด้วยกุญแจเดียวกันจึงอัปเดตทับได้ ตอนนี้ใช้ `keystore/mizu.jks` ที่อยู่ใน repo (เหมาะกับแอปส่วนตัว)
  ถ้าต้องการกุญแจลับ ให้ตั้ง secrets `MIZU_KEYSTORE_PATH`, `MIZU_STORE_PASSWORD`, `MIZU_KEY_ALIAS`, `MIZU_KEY_PASSWORD` — แต่ต้องใช้กุญแจเดิมตลอด
- versionCode = เลข run ของ workflow (เพิ่มขึ้นเอง)

## ข้อกำหนดที่ตัดสินใจเอง
- การเตือนใช้ WorkManager (ไม่ใช้ exact alarm) จึงอาจเลื่อนได้เป็นนาทีเมื่อเครื่องอยู่ใน Doze
- เกณฑ์ "ทันเป้า": ≤350 ml/ชม. = ทัน, 350–500 = ตึง, >500 = ไม่ทัน (และส่งเตือน "ดื่มเพิ่มด่วน")
- ฟอนต์ใช้ Noto ของระบบ Android (Thai/JP/Latin) ไม่ได้ฝังไฟล์ฟอนต์
- แก้ไขบันทึกได้เฉพาะปริมาณ (ลบได้) · บันทึกลง Downloads ใช้ได้ Android 10+ (รุ่นเก่าใช้ปุ่มแชร์)
