package io.embodit.foldguide

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.foldguide.ui.fold.FoldScreen
import io.embodit.foldguide.ui.theme.FoldGuideTheme

/**
 * App entry. One Mixed Stage (AndroidManifest: `pico.spatial.stage.style=1`, immersion 0/0/0) holds
 * the whole product: the demo sheet on the desk and the guide panel (design package `container`).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            FoldGuideTheme {
                FoldScreen()
            }
        }
    }
