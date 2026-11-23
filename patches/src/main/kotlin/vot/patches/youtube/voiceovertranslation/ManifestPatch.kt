package vot.patches.youtube.voiceovertranslation

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Добавил {@code VotProvider} в AndroidManifest плеера YouTube: провайдер
 * стартует при запуске приложения и загружает VOT. Идемпотентен —
 * при перепатче не добавляется повторно.
 */
val manifestPatch = resourcePatch(
    name = "Voice Over Translation (manifest)",
    description = "Регистрирует VotProvider в AndroidManifest.xml.",
    use = false,
) {
    compatibleWith(
        "com.google.android.youtube" to setOf("20.40.45", "20.05.46", "21.13.164"),
        "app.revanced.android.youtube" to setOf("20.40.45", "20.05.46", "21.13.164"),
    )

    apply {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as Element

            // Идемпотентность: не добавлять провайдер повторно при перепатче.
            val existing = manifest.getElementsByTagName("provider")
            for (i in 0 until existing.length) {
                val node = existing.item(i) as? Element ?: continue
                if (node.getAttribute("android:name") == "vot.extension.VotProvider") {
                    return@use
                }
            }

            val provider = manifest.createElement("provider").apply {
                setAttribute("android:name", "vot.extension.VotProvider")
                setAttribute("android:authorities", "vot.extension.provider")
                setAttribute("android:exported", "false")
            }
            application.appendChild(provider)
        }
    }
}
