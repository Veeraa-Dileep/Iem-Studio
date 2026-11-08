package com.iemstudio.app.data.model

/** An EQ preset: built-in or user saved. */
data class EqPreset(
    val id: String,
    val name: String,
    val gains: List<Float>,
    val isCustom: Boolean = false,
)

object EqPresets {

    /** Factory presets. Gains are on the 0.5 dB grid, -12..+12. */
    val BUILT_INS = listOf(
        EqPreset("flat", "Flat", List(EQ_BAND_COUNT) { 0f }),
        EqPreset("bass", "Bass Boost", listOf(6f, 5f, 4f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("rock", "Rock", listOf(4f, 3f, 1.5f, 0.5f, 0f, 0.5f, 2f, 3f, 3.5f, 3f)),
        EqPreset("pop", "Pop", listOf(-1f, -0.5f, 1f, 3f, 4f, 3f, 1f, -0.5f, -1f, -1.5f)),
        EqPreset("vocal", "Vocal", listOf(-1.5f, -1f, 0f, 1.5f, 2.5f, 3f, 2.5f, 1f, 0f, -1f)),
        EqPreset("treble", "Treble Boost", listOf(0f, 0f, 0f, 0f, 0.5f, 1f, 2f, 3.5f, 5f, 6f)),
    )
}
