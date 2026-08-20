package tech.illusion.spacecube.content

import android.media.AudioManager
import android.media.ToneGenerator
import tech.illusion.spacecube.game.GameEvent

class GameSoundEffects {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)

    fun play(events: List<GameEvent>) {
        events.forEach { event ->
            when (event) {
                GameEvent.PIECE_LOCKED -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                GameEvent.LINES_CLEARED -> toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                GameEvent.GAME_OVER -> toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 400)
            }
        }
    }

    fun release() {
        toneGenerator.release()
    }
}
