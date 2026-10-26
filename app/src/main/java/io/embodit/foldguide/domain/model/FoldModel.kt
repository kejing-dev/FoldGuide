package io.embodit.foldguide.domain.model

/** Why spatial sensing cannot be used (from the HMD provider's support state). */
enum class BlockReason { NOT_IN_FULL_SPACE, WITHOUT_PERMISSION, DEVICE_NOT_SUPPORTED }

/** Language chosen in Settings: follow the system, Chinese, English. */
enum class LanguagePref { SYSTEM, ZH, EN }

/** Which side of the demo sheet the guide panel stands on (design `settings.hand`). */
enum class Hand { RIGHT, LEFT }

/** Persisted settings (design `dataModel.settings`). */
data class Settings(
    val language: LanguagePref = LanguagePref.SYSTEM,
    val hand: Hand = Hand.RIGHT,
    /** Look-to-advance (1 s on "Next"). Default on; debug builds start with it off (design `input.dwell`). */
    val dwell: Boolean = true,
    val welcomeSeen: Boolean = false,
)

/** Where the user left off: model id and zero-based step (design `dataModel.progress.current`). */
data class CurrentFold(val modelId: String, val step: Int)

/** Persisted progress: finished models and the unfinished one. */
data class Progress(
    val done: Set<String> = emptySet(),
    val current: CurrentFold? = null,
)
