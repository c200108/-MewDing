package com.mengding.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘线性图标（2 像素描边、圆头），不用 emoji、不用图标库。
 * 颜色由 Icon 的 tint 决定。
 */
object Ic {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    val Home = icon("home") {
        moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
        moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
        moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f)
    }
    val Room = icon("room") {
        moveTo(3f, 12f); lineTo(12f, 4f); lineTo(21f, 12f)
        moveTo(5f, 10.5f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 10.5f)
        // 屋里一颗心
        moveTo(12f, 17.5f); curveTo(9f, 15.5f, 8.5f, 12.5f, 10.5f, 12f); curveTo(11.3f, 11.8f, 12f, 12.6f, 12f, 13.2f)
        curveTo(12f, 12.6f, 12.7f, 11.8f, 13.5f, 12f); curveTo(15.5f, 12.5f, 15f, 15.5f, 12f, 17.5f)
    }
    val Calendar = icon("calendar") {
        moveTo(5f, 6f); lineTo(19f, 6f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        moveTo(5f, 10f); lineTo(19f, 10f)
        moveTo(9f, 3.5f); lineTo(9f, 7.5f); moveTo(15f, 3.5f); lineTo(15f, 7.5f)
        moveTo(12f, 17.5f); curveTo(10f, 16f, 10f, 13f, 12f, 12.5f); curveTo(14f, 13f, 14f, 16f, 12f, 17.5f)
    }
    val Game = icon("game") {
        moveTo(7f, 8f); lineTo(17f, 8f); curveTo(20f, 8f, 21.5f, 11f, 21f, 15f)
        curveTo(20.6f, 18f, 18f, 18.5f, 16.5f, 16.5f); lineTo(15f, 15f); lineTo(9f, 15f); lineTo(7.5f, 16.5f)
        curveTo(6f, 18.5f, 3.4f, 18f, 3f, 15f); curveTo(2.5f, 11f, 4f, 8f, 7f, 8f)
        moveTo(7.5f, 10f); lineTo(7.5f, 13f); moveTo(6f, 11.5f); lineTo(9f, 11.5f)
        moveTo(15.5f, 11f); lineTo(15.6f, 11f); moveTo(17.5f, 12.5f); lineTo(17.6f, 12.5f)
    }
    val Me = icon("me") {
        moveTo(12f, 12f); curveTo(9.8f, 12f, 8.5f, 10.4f, 8.5f, 8.2f); curveTo(8.5f, 6f, 10f, 4.5f, 12f, 4.5f)
        curveTo(14f, 4.5f, 15.5f, 6f, 15.5f, 8.2f); curveTo(15.5f, 10.4f, 14.2f, 12f, 12f, 12f)
        moveTo(5f, 20f); curveTo(5.5f, 16f, 8.5f, 14.5f, 12f, 14.5f); curveTo(15.5f, 14.5f, 18.5f, 16f, 19f, 20f)
    }
    val Drop = icon("drop") {
        moveTo(12f, 3.5f); curveTo(9f, 8f, 6f, 11f, 6f, 14.5f); curveTo(6f, 18f, 8.7f, 20.5f, 12f, 20.5f)
        curveTo(15.3f, 20.5f, 18f, 18f, 18f, 14.5f); curveTo(18f, 11f, 15f, 8f, 12f, 3.5f)
        moveTo(9f, 15f); curveTo(9f, 16.5f, 10f, 17.5f, 11.2f, 17.7f)
    }
    val Bowl = icon("bowl") {
        moveTo(3.5f, 12f); lineTo(20.5f, 12f); curveTo(20.5f, 17f, 16.5f, 19.5f, 12f, 19.5f)
        curveTo(7.5f, 19.5f, 3.5f, 17f, 3.5f, 12f)
        moveTo(9f, 9f); curveTo(9f, 7.5f, 10f, 7f, 10f, 5.5f); moveTo(14f, 9f); curveTo(14f, 7.5f, 15f, 7f, 15f, 5.5f)
    }
    val Moon = icon("moon") {
        moveTo(19f, 14.5f); curveTo(14f, 16f, 8.5f, 11.5f, 10.5f, 5f); curveTo(6.5f, 6f, 4.5f, 9.5f, 4.5f, 12.5f)
        curveTo(4.5f, 16.8f, 8f, 20f, 12f, 20f); curveTo(15.2f, 20f, 18f, 17.8f, 19f, 14.5f)
    }
    val Bell = icon("bell") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(10f, 20.5f); lineTo(14f, 20.5f); moveTo(12f, 3f); lineTo(12f, 5f)
    }
    val Minus = icon("minus") { moveTo(5f, 12f); lineTo(19f, 12f) }
    val Plus = icon("plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val Next = icon("next") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Close = icon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Coin = icon("coin") {
        // 萌点：一枚印着小爪印的硬币
        moveTo(12f, 3.5f); curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f); curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
        curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f); curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
        moveTo(12f, 16f); curveTo(10f, 16f, 9.5f, 14.5f, 10.5f, 13.5f); curveTo(11.3f, 12.7f, 12.7f, 12.7f, 13.5f, 13.5f)
        curveTo(14.5f, 14.5f, 14f, 16f, 12f, 16f)
        moveTo(9.5f, 11f); lineTo(9.6f, 11f); moveTo(12f, 9.5f); lineTo(12.1f, 9.5f); moveTo(14.5f, 11f); lineTo(14.6f, 11f)
    }
    val Heart = icon("heart") {
        moveTo(12f, 19.5f); curveTo(6f, 15.5f, 3.5f, 12f, 4.2f, 8.8f); curveTo(4.8f, 6f, 8.5f, 4.8f, 12f, 8.2f)
        curveTo(15.5f, 4.8f, 19.2f, 6f, 19.8f, 8.8f); curveTo(20.5f, 12f, 18f, 15.5f, 12f, 19.5f)
    }
    val Gear = icon("gear") {
        moveTo(12f, 15f); curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f); curveTo(9f, 10.3f, 10.3f, 9f, 12f, 9f)
        curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f); curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f)
        moveTo(12f, 3f); lineTo(12f, 5.5f); moveTo(12f, 18.5f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(5.5f, 12f); moveTo(18.5f, 12f); lineTo(21f, 12f)
        moveTo(5.6f, 5.6f); lineTo(7.4f, 7.4f); moveTo(16.6f, 16.6f); lineTo(18.4f, 18.4f)
        moveTo(5.6f, 18.4f); lineTo(7.4f, 16.6f); moveTo(16.6f, 7.4f); lineTo(18.4f, 5.6f)
    }
    val Lock = icon("lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8.5f, 11f); lineTo(8.5f, 8f); curveTo(8.5f, 5.8f, 10f, 4.5f, 12f, 4.5f); curveTo(14f, 4.5f, 15.5f, 5.8f, 15.5f, 8f)
        lineTo(15.5f, 11f); moveTo(12f, 14.5f); lineTo(12f, 16.5f)
    }
    val Chart = icon("chart") {
        moveTo(4f, 20f); lineTo(20f, 20f)
        moveTo(7f, 17f); lineTo(7f, 12f); moveTo(12f, 17f); lineTo(12f, 7f); moveTo(17f, 17f); lineTo(17f, 10f)
    }
    val Shield = icon("shield") {
        moveTo(12f, 3.5f); lineTo(19f, 6.5f); lineTo(19f, 11.5f); curveTo(19f, 16f, 15.8f, 19.3f, 12f, 20.5f)
        curveTo(8.2f, 19.3f, 5f, 16f, 5f, 11.5f); lineTo(5f, 6.5f); close()
        moveTo(9f, 12f); lineTo(11.2f, 14.2f); lineTo(15f, 10f)
    }
    val Edit = icon("edit") {
        moveTo(5f, 19f); lineTo(5.5f, 15.5f); lineTo(15.5f, 5.5f); lineTo(18.5f, 8.5f); lineTo(8.5f, 18.5f); close()
        moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
    }
    val Vibrate = icon("vibrate") {
        moveTo(8.5f, 4.5f); lineTo(15.5f, 4.5f); lineTo(15.5f, 19.5f); lineTo(8.5f, 19.5f); close()
        moveTo(11f, 16.5f); lineTo(13f, 16.5f)
        moveTo(4.5f, 9f); lineTo(4.5f, 15f); moveTo(2.5f, 10.5f); lineTo(2.5f, 13.5f)
        moveTo(19.5f, 9f); lineTo(19.5f, 15f); moveTo(21.5f, 10.5f); lineTo(21.5f, 13.5f)
    }
    val Sound = icon("sound") {
        moveTo(4f, 9.5f); lineTo(8f, 9.5f); lineTo(12.5f, 5.5f); lineTo(12.5f, 18.5f); lineTo(8f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15.5f, 9f); curveTo(17f, 10.5f, 17f, 13.5f, 15.5f, 15f)
        moveTo(18f, 6.5f); curveTo(21f, 9.5f, 21f, 14.5f, 18f, 17.5f)
    }
    val Mute = icon("mute") {
        moveTo(4f, 9.5f); lineTo(8f, 9.5f); lineTo(12.5f, 5.5f); lineTo(12.5f, 18.5f); lineTo(8f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(16f, 9.5f); lineTo(21f, 14.5f); moveTo(21f, 9.5f); lineTo(16f, 14.5f)
    }
    val Bag = icon("bag") {
        moveTo(5f, 8f); lineTo(19f, 8f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(9f, 10.5f); lineTo(9f, 6.5f); curveTo(9f, 3.5f, 15f, 3.5f, 15f, 6.5f); lineTo(15f, 10.5f)
    }
    val Gift = icon("gift") {
        moveTo(4.5f, 10f); lineTo(19.5f, 10f); lineTo(19.5f, 13f); lineTo(4.5f, 13f); close()
        moveTo(6f, 13f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 13f); moveTo(12f, 10f); lineTo(12f, 20f)
        moveTo(12f, 10f); curveTo(10f, 9f, 7.5f, 7.5f, 9f, 5.5f); curveTo(10.5f, 4f, 12f, 7f, 12f, 10f)
        curveTo(12f, 7f, 13.5f, 4f, 15f, 5.5f); curveTo(16.5f, 7.5f, 14f, 9f, 12f, 10f)
    }
    val Cloud = icon("cloud") {
        moveTo(7f, 18f); curveTo(4.5f, 18f, 3f, 16.3f, 3f, 14.3f); curveTo(3f, 12.3f, 4.6f, 10.8f, 6.5f, 10.8f)
        curveTo(7f, 7.8f, 9.3f, 6f, 12f, 6f); curveTo(14.8f, 6f, 17f, 8f, 17.3f, 10.6f)
        curveTo(19.4f, 10.8f, 21f, 12.4f, 21f, 14.4f); curveTo(21f, 16.4f, 19.4f, 18f, 17.3f, 18f); close()
    }
    val Paint = icon("palette") {
        moveTo(12f, 20.5f); curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f); curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
        curveTo(16.7f, 3.5f, 20.5f, 6.8f, 20.5f, 11f); curveTo(20.5f, 13.5f, 18.7f, 14.5f, 16.8f, 14.5f)
        lineTo(15f, 14.5f); curveTo(13.8f, 14.5f, 13.2f, 15.8f, 14f, 16.8f); curveTo(14.8f, 17.8f, 14f, 20.5f, 12f, 20.5f)
        moveTo(8f, 11f); lineTo(8.1f, 11f); moveTo(11f, 7.5f); lineTo(11.1f, 7.5f); moveTo(15.5f, 8.5f); lineTo(15.6f, 8.5f)
    }
    val Box = icon("box") {
        moveTo(4f, 8f); lineTo(12f, 4f); lineTo(20f, 8f); lineTo(20f, 16f); lineTo(12f, 20f); lineTo(4f, 16f); close()
        moveTo(4f, 8f); lineTo(12f, 12f); lineTo(20f, 8f); moveTo(12f, 12f); lineTo(12f, 20f)
    }

    val Note = icon("note") {
        moveTo(6f, 3.5f); lineTo(15f, 3.5f); lineTo(19f, 7.5f); lineTo(19f, 20.5f); lineTo(6f, 20.5f); close()
        moveTo(15f, 3.5f); lineTo(15f, 7.5f); lineTo(19f, 7.5f)
        moveTo(9f, 11.5f); lineTo(16f, 11.5f); moveTo(9f, 15f); lineTo(16f, 15f); moveTo(9f, 18f); lineTo(13f, 18f)
    }
    val Mic = icon("mic") {
        moveTo(12f, 14.5f); curveTo(10.3f, 14.5f, 9f, 13.2f, 9f, 11.5f); lineTo(9f, 6.5f); curveTo(9f, 4.8f, 10.3f, 3.5f, 12f, 3.5f)
        curveTo(13.7f, 3.5f, 15f, 4.8f, 15f, 6.5f); lineTo(15f, 11.5f); curveTo(15f, 13.2f, 13.7f, 14.5f, 12f, 14.5f)
        moveTo(6f, 11f); curveTo(6f, 14.5f, 8.7f, 17.5f, 12f, 17.5f); curveTo(15.3f, 17.5f, 18f, 14.5f, 18f, 11f)
        moveTo(12f, 17.5f); lineTo(12f, 20.5f)
    }
    val Music = icon("music") {
        moveTo(9f, 17.5f); lineTo(9f, 5.5f); lineTo(19f, 3.5f); lineTo(19f, 15.5f)
        moveTo(9f, 17.5f); curveTo(9f, 19f, 7.9f, 20f, 6.5f, 20f); curveTo(5.1f, 20f, 4f, 19f, 4f, 17.5f)
        curveTo(4f, 16f, 5.1f, 15f, 6.5f, 15f); curveTo(7.9f, 15f, 9f, 16f, 9f, 17.5f)
        moveTo(19f, 15.5f); curveTo(19f, 17f, 17.9f, 18f, 16.5f, 18f); curveTo(15.1f, 18f, 14f, 17f, 14f, 15.5f)
        curveTo(14f, 14f, 15.1f, 13f, 16.5f, 13f); curveTo(17.9f, 13f, 19f, 14f, 19f, 15.5f)
    }
    val Image = icon("image") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        moveTo(4f, 16f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(20f, 17f)
        moveTo(15.5f, 8.5f); lineTo(15.6f, 8.5f)
    }
    val Table = icon("table") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        moveTo(4f, 9.7f); lineTo(20f, 9.7f); moveTo(4f, 14.3f); lineTo(20f, 14.3f); moveTo(10f, 5f); lineTo(10f, 19f)
    }
    val Bullets = icon("bullets") {
        moveTo(5f, 7f); lineTo(5.1f, 7f); moveTo(5f, 12f); lineTo(5.1f, 12f); moveTo(5f, 17f); lineTo(5.1f, 17f)
        moveTo(9f, 7f); lineTo(20f, 7f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 17f); lineTo(20f, 17f)
    }
    val TextFmt = icon("textfmt") {
        moveTo(4f, 19f); lineTo(9f, 5f); lineTo(14f, 19f); moveTo(6f, 14f); lineTo(12f, 14f)
        moveTo(15.5f, 13f); curveTo(16f, 11.8f, 17f, 11.5f, 18f, 11.5f); curveTo(19.3f, 11.5f, 20f, 12.3f, 20f, 13.5f)
        lineTo(20f, 19f); moveTo(20f, 15.5f); curveTo(17f, 15.3f, 15.5f, 16f, 15.5f, 17.3f); curveTo(15.5f, 18.4f, 16.4f, 19f, 17.4f, 19f)
        curveTo(18.8f, 19f, 20f, 18f, 20f, 16.5f)
    }
    val Pin = icon("pin") {
        moveTo(9f, 4f); lineTo(15f, 4f); moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(14f, 10f); lineTo(14f, 4f)
        moveTo(12f, 14f); lineTo(12f, 20.5f)
    }
    val Trash = icon("trash") {
        moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f); moveTo(9.5f, 6.5f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 6.5f)
        moveTo(6.5f, 6.5f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 6.5f)
        moveTo(10f, 10.5f); lineTo(10f, 16f); moveTo(14f, 10.5f); lineTo(14f, 16f)
    }
    val Search = icon("search") {
        moveTo(10.5f, 17f); curveTo(6.9f, 17f, 4f, 14.1f, 4f, 10.5f); curveTo(4f, 6.9f, 6.9f, 4f, 10.5f, 4f)
        curveTo(14.1f, 4f, 17f, 6.9f, 17f, 10.5f); curveTo(17f, 14.1f, 14.1f, 17f, 10.5f, 17f)
        moveTo(15.2f, 15.2f); lineTo(20f, 20f)
    }
    val Play = icon("play") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    val Pause = icon("pause") { moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f) }
    val Stop = icon("stop") { moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close() }
    val AlignL = icon("alignl") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 10f); lineTo(14f, 10f); moveTo(4f, 14f); lineTo(20f, 14f); moveTo(4f, 18f); lineTo(14f, 18f) }
    val AlignC = icon("alignc") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(7f, 10f); lineTo(17f, 10f); moveTo(4f, 14f); lineTo(20f, 14f); moveTo(7f, 18f); lineTo(17f, 18f) }
    val AlignR = icon("alignr") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(10f, 10f); lineTo(20f, 10f); moveTo(4f, 14f); lineTo(20f, 14f); moveTo(10f, 18f); lineTo(20f, 18f) }
    val Quote = icon("quote") {
        moveTo(5f, 5f); lineTo(5f, 19f)
        moveTo(9f, 8f); lineTo(19f, 8f); moveTo(9f, 12f); lineTo(19f, 12f); moveTo(9f, 16f); lineTo(15f, 16f)
    }
    val Speech = icon("speech") {
        moveTo(5f, 5f); lineTo(19f, 5f); curveTo(20f, 5f, 20.5f, 5.5f, 20.5f, 6.5f); lineTo(20.5f, 14f)
        curveTo(20.5f, 15f, 20f, 15.5f, 19f, 15.5f); lineTo(11f, 15.5f); lineTo(7f, 19.5f); lineTo(7f, 15.5f); lineTo(5f, 15.5f)
        curveTo(4f, 15.5f, 3.5f, 15f, 3.5f, 14f); lineTo(3.5f, 6.5f); curveTo(3.5f, 5.5f, 4f, 5f, 5f, 5f)
        moveTo(8f, 10.3f); lineTo(8.1f, 10.3f); moveTo(12f, 10.3f); lineTo(12.1f, 10.3f); moveTo(16f, 10.3f); lineTo(16.1f, 10.3f)
    }
}
