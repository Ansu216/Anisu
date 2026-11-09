package com.ansu.anime.ui.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput

/**
 * Lets subtitles appear *earlier* than the video. The player hands a cue over only once the playback position
 * reaches it, so a negative subtitle offset is done by telling the text renderer the position is [leadMs] ahead of
 * the real one. A positive offset (later) is still done by holding cues back in `PlayerViewModel`.
 */
@OptIn(UnstableApi::class)
class SubtitleLeadRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    /** How many milliseconds ahead of the playback position the subtitles are drawn; 0 = in sync. */
    @Volatile
    var leadMs: Long = 0L

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        for (index in out.indices) {
            if (out[index].trackType == C.TRACK_TYPE_TEXT) out[index] = LeadRenderer(out[index])
        }
    }

    private inner class LeadRenderer(renderer: Renderer) : ForwardingRenderer(renderer) {
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            super.render(positionUs + leadMs * 1000L, elapsedRealtimeUs)
        }
    }
}
