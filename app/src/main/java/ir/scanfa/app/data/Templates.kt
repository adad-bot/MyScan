package ir.scanfa.app.data

/** الگوهای آمادهٔ مدارک فارسی. */
object Templates {
    val GENERAL = DocTemplate(
        id = "general",
        title = "الگوی عمومی مدارک",
        note = "برای هر مجموعه‌ای از برگه‌ها؛ فقط ترتیب صفحات را کنترل می‌کند.",
        slots = emptyList()
    )

    val SIGNUP = DocTemplate(
        id = "signup",
        title = "الگوی مدارک ثبت‌نام",
        note = "چهار قلم لازم: روی و پشت مدرک هویتی، مدرک تحصیلی و عکس.",
        slots = listOf(
            Slot("id_front", "روی مدرک هویتی"),
            Slot("id_back", "پشت مدرک هویتی"),
            Slot("education", "مدرک تحصیلی"),
            Slot("photo", "عکس پرسنلی")
        )
    )

    val CASE = DocTemplate(
        id = "case",
        title = "الگوی پرونده حقوقی",
        note = "قرارداد، رسید پرداخت و نامهٔ اداری با شمارهٔ پیوست.",
        slots = listOf(
            Slot("contract", "قرارداد"),
            Slot("receipt", "رسید پرداخت"),
            Slot("letter", "نامهٔ اداری")
        )
    )

    val all: List<DocTemplate> = listOf(GENERAL, SIGNUP, CASE)

    fun byId(id: String?): DocTemplate? = all.firstOrNull { it.id == id }

    fun slotTitle(template: DocTemplate?, slotId: String?): String {
        if (template == null || slotId == null) return ""
        return template.slots.firstOrNull { it.id == slotId }?.title ?: ""
    }
}
