package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom geometric brand icons so Google Drive (3-ribbon triangle) and GitHub (Octocat mark)
 * are unmistakably distinct at a glance across Home, Add Pair, and Detail screens.
 */
object BrandIcons {

    val GoogleDrive: ImageVector by lazy {
        ImageVector.Builder(
            name = "GoogleDriveBrand",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Left slanted ribbon
            path(
                fill = SolidColor(Color.Black),
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(8.35f, 3.2f)
                lineTo(1.85f, 14.45f)
                lineTo(5.15f, 20.15f)
                lineTo(11.65f, 8.9f)
                close()
            }
            // Right slanted ribbon
            path(
                fill = SolidColor(Color.Black),
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15.65f, 3.2f)
                lineTo(9.15f, 3.2f)
                lineTo(15.65f, 14.45f)
                lineTo(22.15f, 14.45f)
                close()
            }
            // Bottom horizontal ribbon
            path(
                fill = SolidColor(Color.Black),
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(8.85f, 15.2f)
                lineTo(5.85f, 20.4f)
                lineTo(18.85f, 20.4f)
                lineTo(21.85f, 15.2f)
                close()
            }
        }.build()
    }

    val GitHub: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitHubBrand",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Classic Octocat silhouette mark
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(12f, 2f)
                curveTo(6.477f, 2f, 2f, 6.484f, 2f, 12.017f)
                curveTo(2f, 16.443f, 4.865f, 20.197f, 8.839f, 21.522f)
                curveTo(9.339f, 21.612f, 9.521f, 21.305f, 9.521f, 21.041f)
                curveTo(9.521f, 20.804f, 9.512f, 20.174f, 9.508f, 19.339f)
                curveTo(6.726f, 19.944f, 6.139f, 17.996f, 6.139f, 17.996f)
                curveTo(5.684f, 16.839f, 5.029f, 16.531f, 5.029f, 16.531f)
                curveTo(4.121f, 15.91f, 5.098f, 15.923f, 5.098f, 15.923f)
                curveTo(6.101f, 15.993f, 6.629f, 16.955f, 6.629f, 16.955f)
                curveTo(7.521f, 18.486f, 8.97f, 18.044f, 9.541f, 17.788f)
                curveTo(9.631f, 17.141f, 9.889f, 16.699f, 10.175f, 16.449f)
                curveTo(7.954f, 16.197f, 5.62f, 15.338f, 5.62f, 11.497f)
                curveTo(5.62f, 10.402f, 6.01f, 9.508f, 6.649f, 8.806f)
                curveTo(6.546f, 8.554f, 6.203f, 7.536f, 6.747f, 6.158f)
                curveTo(6.747f, 6.158f, 7.587f, 5.888f, 9.497f, 7.184f)
                curveTo(10.295f, 6.962f, 11.15f, 6.851f, 12f, 6.847f)
                curveTo(12.85f, 6.851f, 13.705f, 6.962f, 14.505f, 7.184f)
                curveTo(16.413f, 5.888f, 17.251f, 6.158f, 17.251f, 6.158f)
                curveTo(17.797f, 7.536f, 17.454f, 8.554f, 17.351f, 8.806f)
                curveTo(17.991f, 9.508f, 18.38f, 10.402f, 18.38f, 11.497f)
                curveTo(18.38f, 15.348f, 16.041f, 16.194f, 13.813f, 16.441f)
                curveTo(14.172f, 16.75f, 14.492f, 17.361f, 14.492f, 18.295f)
                curveTo(14.492f, 19.633f, 14.48f, 20.713f, 14.48f, 21.041f)
                curveTo(14.48f, 21.307f, 14.66f, 21.618f, 15.168f, 21.52f)
                curveTo(19.138f, 20.193f, 22f, 16.441f, 22f, 12.017f)
                curveTo(22f, 6.484f, 17.522f, 2f, 12f, 2f)
                close()
            }
        }.build()
    }
}
