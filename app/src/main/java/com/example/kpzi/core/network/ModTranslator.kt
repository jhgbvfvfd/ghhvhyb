package com.example.kpzi.core.network

/**
 * Non-destructive client-side Thai translation helper for mod descriptions.
 * Original cached/remote text is never overwritten.
 */
object ModTranslator {
    private val replacements = listOf(
        "Requires SMAPI" to "ต้องใช้ SMAPI",
        "Requires Content Patcher" to "ต้องใช้ Content Patcher",
        "Requirements" to "สิ่งที่ต้องติดตั้งเพิ่มเติม",
        "Installation" to "วิธีติดตั้ง",
        "Features" to "คุณสมบัติเด่น",
        "Compatibility" to "ความเข้ากันได้",
        "Stardew Valley 1.6" to "Stardew Valley เวอร์ชัน 1.6",
        "Content Pack" to "แพ็กเนื้อหา (Content Pack)",
        "Replaces" to "ปรับเปลี่ยน",
        "Adds" to "เพิ่ม",
        "Fixes" to "แก้ไขบั๊ก",
        "Configurable" to "ปรับแต่งค่าได้",
        "Multiplayer" to "รองรับเล่นหลายคน",
        "Android" to "แอนดรอยด์",
        "portrait" to "ภาพตัวละคร",
        "crops" to "พืชผล",
        "farm" to "ฟาร์ม",
        "buildings" to "สิ่งปลูกสร้าง",
        "textures" to "พื้นผิวภาพ",
        "seasons" to "ฤดูกาล",
        "weather" to "สภาพอากาศ",
        "villagers" to "ชาวบ้าน",
        "dialogue" to "บทสนทนา",
    )

    fun translatePreservingOriginal(original: String): String {
        var result = original
        for ((en, th) in replacements) {
            result = result.replace(en, th, ignoreCase = true)
        }
        return "[คำแปลเบื้องต้นโดยไม่แก้ไขต้นฉบับ]\n$result"
    }
}
