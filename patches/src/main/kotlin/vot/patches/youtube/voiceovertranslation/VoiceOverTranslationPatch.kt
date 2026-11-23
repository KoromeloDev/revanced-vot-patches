package vot.patches.youtube.voiceovertranslation

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val PLAYER_RESPONSE_MODEL_IMPL =
    "Lcom/google/android/libraries/youtube/innertube/model/player/PlayerResponseModelImpl;"

private const val MEDIA_SESSION_SET_STATE =
    "Landroid/media/session/MediaSession;->setPlaybackState(Landroid/media/session/PlaybackState;)V"

private const val AUDIO_TRACK_SET_VOLUME = "Landroid/media/AudioTrack;->setVolume(F)I"
private const val AUDIO_TRACK_PLAY = "Landroid/media/AudioTrack;->play()V"

/**
 * Voice Over Translation (VOT) — захватываю идентификатор текущего видео
 * из `PlayerResponseModelImpl` (подменяю строковые getter'ы) и вешаю хуки
 * управления плеером: громкость дорожки, паузу/позицию и сброс при смене видео.
 */
val voiceOverTranslationPatch = bytecodePatch(
    name = "Voice Over Translation",
    description = "Перевод видео на другие языки (API Yandex VOT): кнопка в плеере, настройки.",
) {
    compatibleWith(
        "com.google.android.youtube" to setOf("20.40.45", "20.05.46", "21.13.164"),
        "app.revanced.android.youtube" to setOf("20.40.45", "20.05.46", "21.13.164"),
    )
    dependsOn(manifestPatch)

    // Классы extension-проекта extensions/vot.
    extendWith("extensions/vot.rve")

    apply {
        val classDef = classDefs.firstOrNull { it.type == PLAYER_RESPONSE_MODEL_IMPL }
            ?: error("VOT: не найден $PLAYER_RESPONSE_MODEL_IMPL")

        val mutableClass = classDefs.getOrReplaceMutable(classDef)

        mutableClass.methods
            .filter { it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty() }
            .forEach { method ->
                val implementation = method.implementation ?: return@forEach

                val returnIndices = implementation.instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                    .map { it.index }

                // С конца, чтобы вставка не сдвигала ещё не обработанные индексы.
                returnIndices.asReversed().forEach { index ->
                    val register =
                        (implementation.instructions.elementAt(index) as OneRegisterInstruction).registerA
                    method.addInstruction(
                        index,
                        "invoke-static {v$register}, Lvot/extension/VotVideoId;->accept(Ljava/lang/String;)V",
                    )
                }
            }

        // Хук состояния плеера: пауза/воспроизведение/позиция для синхронизации.
        val playbackTargets = mutableListOf<Pair<String, String>>()
        for (classDef in classDefs) {
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue
                val found = implementation.instructions.any { instruction ->
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        MEDIA_SESSION_SET_STATE
                }
                if (found) playbackTargets += classDef.type to method.name
            }
        }

        for ((type, methodName) in playbackTargets) {
            val classDef = classDefs.firstOrNull { it.type == type } ?: continue
            val mutableClass = classDefs.getOrReplaceMutable(classDef)
            mutableClass.methods.filter { it.name == methodName }.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val indices = mutableListOf<Int>()
                implementation.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        MEDIA_SESSION_SET_STATE
                    ) {
                        indices += index
                    }
                }
                for (index in indices.asReversed()) {
                    val instruction =
                        implementation.instructions.elementAt(index) as? FiveRegisterInstruction
                            ?: continue
                    if (instruction.registerCount < 2) continue
                    val argument = instruction.registerD
                    method.addInstruction(
                        index,
                        "invoke-static {v$argument}, " +
                            "Lvot/extension/VotController;->onPlaybackState(Ljava/lang/Object;)V",
                    )
                }
            }
        }

        // Хук громкости дорожки видео: перехватываем AudioTrack.setVolume,
        // чтобы приглушать оригинал, не трогая системную громкость.
        val volumeTargets = mutableListOf<Pair<String, String>>()
        for (classDef in classDefs) {
            // Не трогаем собственные extension-классы (иначе рекурсия в VotAudioSink).
            if (classDef.type.startsWith("Lvot/extension/")) continue
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue
                val found = implementation.instructions.any { instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@any false
                    val reference = (instruction as? ReferenceInstruction)?.reference?.toString()
                    reference == AUDIO_TRACK_SET_VOLUME || reference == AUDIO_TRACK_PLAY
                }
                if (found) volumeTargets += classDef.type to method.name
            }
        }

        for ((type, methodName) in volumeTargets) {
            if (type.startsWith("Lorg/webrtc/") || type.startsWith("Lvot/extension/")) continue
            val classDef = classDefs.firstOrNull { it.type == type } ?: continue
            val mutableClass = classDefs.getOrReplaceMutable(classDef)
            mutableClass.methods.filter { it.name == methodName }.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val replacements = mutableListOf<Pair<Int, Boolean>>() // index, isPlay
                implementation.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@forEachIndexed
                    when ((instruction as? ReferenceInstruction)?.reference?.toString()) {
                        AUDIO_TRACK_SET_VOLUME -> replacements += index to false
                        AUDIO_TRACK_PLAY -> replacements += index to true
                    }
                }
                for ((index, isPlay) in replacements.asReversed()) {
                    val instruction =
                        implementation.instructions.elementAt(index) as? FiveRegisterInstruction
                            ?: continue
                    val track = instruction.registerC
                    if (isPlay) {
                        method.replaceInstruction(
                            index,
                            "invoke-static {v$track}, " +
                                "Lvot/extension/VotAudioSink;->play(Landroid/media/AudioTrack;)V",
                        )
                    } else {
                        if (instruction.registerCount < 2) continue
                        val volume = instruction.registerD
                        method.replaceInstruction(
                            index,
                            "invoke-static {v$track, v$volume}, " +
                                "Lvot/extension/VotAudioSink;->setVolume(Landroid/media/AudioTrack;F)I",
                        )
                    }
                }
            }
        }
    }
}