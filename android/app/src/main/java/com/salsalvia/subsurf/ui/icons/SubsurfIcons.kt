package com.salsalvia.subsurf.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * A tiny, hand-built icon set (2dp optical stroke, 24dp grid, rounded caps) that matches the
 * product's "field instrument" look. Drawing these locally keeps the APK small — the official
 * `material-icons-extended` artifact alone is several megabytes of unused glyphs.
 */
object SubsurfIcons {

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun fillIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }.build()

    /** Survey region: a closed sketch polygon with vertex dots. */
    val Region: ImageVector by lazy {
        ImageVector.Builder(
            name = "Region",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    moveTo(4.6f, 8.6f)
                    lineTo(12f, 3.6f)
                    lineTo(19.8f, 8.1f)
                    lineTo(18.1f, 18.4f)
                    lineTo(6.3f, 19.4f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(12.0f, 2.9f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, 2.6f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, -2.6f)
                    close()
                moveTo(4.6f, 7.3f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, 2.6f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, -2.6f)
                    close()
                moveTo(19.8f, 6.8f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, 2.6f)
                    arcToRelative(1.3f, 1.3f, 0f, true, true, 0f, -2.6f)
                    close()
            })
        }.build()
    }

    /** Layered map sheets. */
    val Layers: ImageVector by lazy {
        lineIcon("Layers") {
            moveTo(12f, 3.5f)
            lineTo(21f, 8.5f)
            lineTo(12f, 13.5f)
            lineTo(3f, 8.5f)
            close()
            moveTo(3f, 13f)
            lineTo(12f, 18f)
            lineTo(21f, 13f)
        }
    }

    /** Terrain / hillshade. */
    val Terrain: ImageVector by lazy {
        lineIcon("Terrain") {
            moveTo(2.5f, 19f)
            lineTo(9f, 6.5f)
            lineTo(13f, 13.5f)
            lineTo(16f, 9f)
            lineTo(21.5f, 19f)
            close()
        }
    }

    /** Analysis / bar chart. */
    val Analytics: ImageVector by lazy {
        lineIcon("Analytics") {
            moveTo(5f, 19.5f)
            lineTo(5f, 13f)
            moveTo(12f, 19.5f)
            lineTo(12f, 5.5f)
            moveTo(19f, 19.5f)
            lineTo(19f, 9.5f)
        }
    }

    /** Appearance / half-filled circle. */
    val Theme: ImageVector by lazy {
        ImageVector.Builder(
            name = "Theme",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                pathBuilder = {
                    moveTo(12f, 3f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(12f, 3.2f)
                arcToRelative(8.8f, 8.8f, 0f, true, true, 0f, 17.6f)
                arcToRelative(8.8f, 8.8f, 0f, true, true, 0f, -17.6f)
                close()
            })
        }.build()
    }

    /** Connectivity / signal arcs. */
    val Signal: ImageVector by lazy {
        ImageVector.Builder(
            name = "Signal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                pathBuilder = {
                    moveTo(3f, 8.5f)
                    arcTo(13f, 13f, 0f, false, true, 21f, 8.5f)
                    moveTo(6.2f, 12.4f)
                    arcTo(8.2f, 8.2f, 0f, false, true, 17.8f, 12.4f)
                    moveTo(9.4f, 16.2f)
                    arcTo(4.2f, 4.2f, 0f, false, true, 14.6f, 16.2f)
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(12.0f, 17.4f)
                    arcToRelative(2.1f, 2.1f, 0f, true, true, 0f, 4.2f)
                    arcToRelative(2.1f, 2.1f, 0f, true, true, 0f, -4.2f)
                    close()
            })
        }.build()
    }

    /** North arrow / orientation. */
    val Compass: ImageVector by lazy {
        ImageVector.Builder(
            name = "Compass",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                pathBuilder = {
                    moveTo(12f, 3f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(16.2f, 7.8f)
                lineTo(13.6f, 13.6f)
                lineTo(7.8f, 16.2f)
                lineTo(10.4f, 10.4f)
                close()
            })
        }.build()
    }

    /** Fit-to-region crosshair. */
    val Fit: ImageVector by lazy {
        lineIcon("Fit") {
            moveTo(3.5f, 8.5f)
            lineTo(3.5f, 3.5f)
            lineTo(8.5f, 3.5f)
            moveTo(15.5f, 3.5f)
            lineTo(20.5f, 3.5f)
            lineTo(20.5f, 8.5f)
            moveTo(20.5f, 15.5f)
            lineTo(20.5f, 20.5f)
            lineTo(15.5f, 20.5f)
            moveTo(8.5f, 20.5f)
            lineTo(3.5f, 20.5f)
            lineTo(3.5f, 15.5f)
        }
    }

    /** Ruler / measurement. */
    val Ruler: ImageVector by lazy {
        lineIcon("Ruler") {
            moveTo(3f, 8.5f)
            lineTo(21f, 8.5f)
            lineTo(21f, 15.5f)
            lineTo(3f, 15.5f)
            close()
            moveTo(7f, 8.5f)
            lineTo(7f, 12f)
            moveTo(11f, 8.5f)
            lineTo(11f, 12f)
            moveTo(15f, 8.5f)
            lineTo(15f, 12f)
        }
    }

    /** Provenance / verified source shield. */
    val Provenance: ImageVector by lazy {
        lineIcon("Provenance") {
            moveTo(12f, 3f)
            lineTo(20f, 6f)
            lineTo(20f, 12f)
            curveTo(20f, 16.6f, 16.4f, 20.2f, 12f, 21.2f)
            curveTo(7.6f, 20.2f, 4f, 16.6f, 4f, 12f)
            lineTo(4f, 6f)
            close()
            moveTo(8.6f, 11.8f)
            lineTo(11.2f, 14.4f)
            lineTo(15.6f, 9.2f)
        }
    }

    /** Caution / caveats. */
    val Caveat: ImageVector by lazy {
        lineIcon("Caveat") {
            moveTo(12f, 3.6f)
            lineTo(21.2f, 19.8f)
            lineTo(2.8f, 19.8f)
            close()
            moveTo(12f, 9.2f)
            lineTo(12f, 14f)
            moveTo(12f, 16.6f)
            lineTo(12f, 16.8f)
        }
    }

    /** Sparkle accent used on headline badges. */
    val Sparkle: ImageVector by lazy {
        fillIcon("Sparkle") {
            moveTo(12f, 2.6f)
            lineTo(13.8f, 10.2f)
            lineTo(21.4f, 12f)
            lineTo(13.8f, 13.8f)
            lineTo(12f, 21.4f)
            lineTo(10.2f, 13.8f)
            lineTo(2.6f, 12f)
            lineTo(10.2f, 10.2f)
            close()
        }
    }

    /** Globe / region on earth. */
    val Globe: ImageVector by lazy {
        lineIcon("Globe") {
            moveTo(12f, 3f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                    arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                    close()
            moveTo(3.2f, 12f)
            lineTo(20.8f, 12f)
            moveTo(12f, 3.1f)
            curveTo(15.5f, 6.2f, 15.5f, 17.8f, 12f, 20.9f)
            curveTo(8.5f, 17.8f, 8.5f, 6.2f, 12f, 3.1f)
            close()
        }
    }

    /** Undo the last vertex. */
    val Undo: ImageVector by lazy {
        lineIcon("Undo") {
            moveTo(8.2f, 6.6f)
            lineTo(4.4f, 10.4f)
            lineTo(8.2f, 14.2f)
            moveTo(4.4f, 10.4f)
            lineTo(14.2f, 10.4f)
            curveTo(17.4f, 10.4f, 19.8f, 12.3f, 19.8f, 15.2f)
            curveTo(19.8f, 18.1f, 17.4f, 19.8f, 14.2f, 19.8f)
            lineTo(8.6f, 19.8f)
        }
    }

    /** Palette for the appearance section. */
    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    moveTo(12f, 3f)
                    curveTo(7.1f, 3f, 3f, 7.1f, 3f, 12f)
                    curveTo(3f, 16.9f, 7.1f, 21f, 12f, 21f)
                    curveTo(13.5f, 21f, 14.2f, 19.9f, 14.2f, 18.8f)
                    curveTo(14.2f, 17.6f, 15.3f, 16.6f, 16.5f, 16.6f)
                    lineTo(18.4f, 16.6f)
                    curveTo(20.5f, 16.6f, 21.9f, 15.1f, 21.4f, 13.2f)
                    curveTo(20.5f, 7.4f, 16.7f, 3f, 12f, 3f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(7.4f, 11.6f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, 3.2f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, -3.2f)
                    close()
                moveTo(10.2f, 7.4f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, 3.2f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, -3.2f)
                    close()
                moveTo(15.4f, 7.4f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, 3.2f)
                    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, -3.2f)
                    close()
            })
        }.build()
    }

    /** Satellite observation. */
    val Satellite: ImageVector by lazy {
        lineIcon("Satellite") {
            moveTo(14.4f, 9.6f)
            lineTo(9.6f, 14.4f)
            moveTo(9.2f, 5.6f)
            lineTo(11.4f, 3.4f)
            lineTo(17.8f, 9.8f)
            lineTo(15.6f, 12f)
            moveTo(12.4f, 8.8f)
            lineTo(19.6f, 16f)
            lineTo(16.4f, 19.2f)
            lineTo(9.2f, 12f)
            close()
            moveTo(5.4f, 15.4f)
            lineTo(3.4f, 17.4f)
            moveTo(18.6f, 6.4f)
            lineTo(20.6f, 4.4f)
        }
    }

    /** Sensor / pulse used for the score hero. */
    val Pulse: ImageVector by lazy {
        lineIcon("Pulse") {
            moveTo(2.8f, 12.4f)
            lineTo(7.4f, 12.4f)
            lineTo(9.6f, 6.6f)
            lineTo(12.6f, 17.8f)
            lineTo(15.2f, 11f)
            lineTo(16.8f, 12.4f)
            lineTo(21.2f, 12.4f)
        }
    }

    /** Cloud cover. */
    val Cloud: ImageVector by lazy {
        lineIcon("Cloud") {
            moveTo(7.2f, 18.4f)
            curveTo(4.6f, 18.4f, 2.8f, 16.6f, 2.8f, 14.3f)
            curveTo(2.8f, 11.9f, 4.8f, 10.2f, 7.1f, 10.2f)
            curveTo(7.8f, 7.5f, 10.1f, 5.6f, 12.9f, 5.6f)
            curveTo(16.3f, 5.6f, 19.1f, 8.3f, 19.2f, 11.6f)
            curveTo(20.6f, 12.1f, 21.4f, 13.3f, 21.4f, 14.9f)
            curveTo(21.4f, 16.8f, 19.8f, 18.4f, 17.8f, 18.4f)
            close()
        }
    }

    /** Closed padlock — "lock this area and start tracing it". */
    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                pathBuilder = {
                    moveTo(7.4f, 10.4f)
                    lineTo(7.4f, 7.6f)
                    arcToRelative(4.6f, 4.6f, 0f, false, true, 9.2f, 0f)
                    lineTo(16.6f, 10.4f)
                },
            )
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    moveTo(5f, 10.4f)
                    lineTo(19f, 10.4f)
                    lineTo(19f, 20.4f)
                    lineTo(5f, 20.4f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(12f, 13.6f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 3f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, -3f)
                close()
                moveTo(11.2f, 16.2f)
                lineTo(12.8f, 16.2f)
                lineTo(13.1f, 18.6f)
                lineTo(10.9f, 18.6f)
                close()
            })
        }.build()
    }

    /** Open padlock — "unlock and go back to free navigation". */
    val Unlock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Unlock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                pathBuilder = {
                    moveTo(7.4f, 10.4f)
                    lineTo(7.4f, 7.6f)
                    arcToRelative(4.6f, 4.6f, 0f, false, true, 9.2f, 0f)
                    lineTo(16.6f, 12.2f)
                },
            )
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    moveTo(5f, 10.4f)
                    lineTo(19f, 10.4f)
                    lineTo(19f, 20.4f)
                    lineTo(5f, 20.4f)
                    close()
                },
            )
            path(fill = SolidColor(Color.Black), pathBuilder = {
                moveTo(12f, 13.6f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 3f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, -3f)
                close()
                moveTo(11.2f, 16.2f)
                lineTo(12.8f, 16.2f)
                lineTo(13.1f, 18.6f)
                lineTo(10.9f, 18.6f)
                close()
            })
        }.build()
    }

    /** Plus / zoom in. */
    val Plus: ImageVector by lazy {
        lineIcon("Plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    /** Minus / zoom out. */
    val Minus: ImageVector by lazy {
        lineIcon("Minus") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    /** Chevron used in list rows. */
    val ChevronRight: ImageVector by lazy {
        lineIcon("ChevronRight") {
            moveTo(9.4f, 5.4f)
            lineTo(16f, 12f)
            lineTo(9.4f, 18.6f)
        }
    }
}
