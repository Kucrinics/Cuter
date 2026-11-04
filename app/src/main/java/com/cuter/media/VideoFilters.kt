package com.cuter.media

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import com.cuter.data.FilterType

/** 4x5 colour matrix effect (RGBA). */
class ColorMatrixEffect(private val matrix: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

object VideoFilters {

    private val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private val VINTAGE = floatArrayOf(
        0.62f, 0.32f, 0.12f, 0f, 0.03f,
        0.22f, 0.58f, 0.14f, 0f, 0.02f,
        0.18f, 0.26f, 0.44f, 0f, 0.02f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun effectsFor(filter: FilterType): List<Effect> = when (filter) {
        FilterType.NONE -> emptyList()
        FilterType.MONO -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(1.08f))
        FilterType.SEPIA -> listOf(ColorMatrixEffect(SEPIA))
        FilterType.WARM -> listOf(RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.02f).setBlueScale(0.88f).build())
        FilterType.COOL -> listOf(RgbAdjustment.Builder().setRedScale(0.9f).setGreenScale(1.0f).setBlueScale(1.18f).build())
        FilterType.VINTAGE -> listOf(ColorMatrixEffect(VINTAGE))
        FilterType.CONTRAST -> listOf(Contrast(1.35f), Brightness(-0.02f))
        FilterType.FADE -> listOf(Contrast(0.82f), Brightness(0.09f), RgbAdjustment.Builder().setRedScale(1.02f).setBlueScale(1.04f).build())
        FilterType.INVERT -> listOf(RgbFilter.createInvertedFilter())
        FilterType.BLUR -> listOf(GaussianBlur(14f))
    }
}
