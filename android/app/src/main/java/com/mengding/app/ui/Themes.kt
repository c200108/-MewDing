package com.mengding.app.ui

import androidx.compose.ui.graphics.Color
import com.mengding.app.R

/**
 * 界面主题：由 美术/插画素材/ 里 5 张 AI 设计稿拆出来的素材组成，外加原来的"经典"线条风。
 * 每套主题换：主色（按钮、选中态、进度条）、底部标签和首页功能按钮的图标、Logo、字标、启动页。
 * 素材由 美术/插画素材/export_theme_assets.py 导出到 res/drawable-nodpi/theme_<代号>_<用途>.png。
 */
data class UiTheme(
    val key: String,
    val name: String,
    val desc: String,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    /** 深色模式下的主色 */
    val darkPrimary: Color,
    val logo: Int,
    val wordmark: Int? = null,
    val slogan: Int? = null,
    /** 底部标签和首页功能按钮用的彩色图标；null = 用线条图标 */
    val today: Int? = null,
    val pet: Int? = null,
    val notes: Int? = null,
    val games: Int? = null,
    val splash: List<Int> = emptyList(),
)

object Themes {
    val classic = UiTheme(
        "classic", "经典薄荷", "原版线条图标，薄荷绿主色",
        Color(0xFF2FB38C), Color(0xFF1E8C6C), Color(0xFFD6F7EA), Color(0xFF6FDDB8),
        logo = R.drawable.logo_cat,
    )

    val all = listOf(
        classic,
        UiTheme(
            "a", "蓝天铃铛猫", "奶牛猫抱着铃铛金币，天空蓝",
            Color(0xFF3F9DE0), Color(0xFF2A7BBE), Color(0xFFDDEFFC), Color(0xFF7DC0F2),
            logo = R.drawable.theme_a_logo, wordmark = R.drawable.theme_a_wordmark, slogan = R.drawable.theme_a_slogan,
            today = R.drawable.theme_a_today, pet = R.drawable.theme_a_pet,
            notes = R.drawable.theme_a_notes, games = R.drawable.theme_a_games,
        ),
        UiTheme(
            "b", "便签猫", "橘白猫举着小便签，圆润的糖果色图标",
            Color(0xFFEE8A3C), Color(0xFFC96A22), Color(0xFFFFE9D6), Color(0xFFF5AE72),
            logo = R.drawable.theme_b_logo, wordmark = R.drawable.theme_b_wordmark, slogan = R.drawable.theme_b_slogan,
            today = R.drawable.theme_b_today, pet = R.drawable.theme_b_pet,
            notes = R.drawable.theme_b_notes, games = R.drawable.theme_b_games,
        ),
        UiTheme(
            "c", "嫩芽铭牌", "头顶嫩芽的小猫捧着名牌，清新绿",
            Color(0xFF4FAE5C), Color(0xFF3A8A45), Color(0xFFE2F4E2), Color(0xFF86CF8E),
            logo = R.drawable.theme_c_logo, wordmark = R.drawable.theme_c_wordmark, slogan = R.drawable.theme_c_slogan,
            today = R.drawable.theme_c_today, pet = R.drawable.theme_c_pet,
            notes = R.drawable.theme_c_notes, games = R.drawable.theme_c_games,
        ),
        UiTheme(
            "d", "星星芽芽", "芽芽猫抱着星星，蜂蜜黄，带三张启动页插画",
            Color(0xFFE3962A), Color(0xFFB8741A), Color(0xFFFFF0D2), Color(0xFFF2BE6A),
            logo = R.drawable.theme_d_logo, wordmark = R.drawable.theme_d_wordmark, slogan = R.drawable.theme_d_slogan,
            today = R.drawable.theme_d_today, pet = R.drawable.theme_d_pet,
            notes = R.drawable.theme_d_notes, games = R.drawable.theme_d_games,
            splash = listOf(R.drawable.theme_d_splash_1, R.drawable.theme_d_splash_2, R.drawable.theme_d_splash_3),
        ),
        UiTheme(
            "e", "软萌 3D", "软乎乎的 3D 橘猫，薰衣草紫",
            Color(0xFF8270E8), Color(0xFF6352C8), Color(0xFFECE8FD), Color(0xFFAFA2F5),
            logo = R.drawable.theme_e_logo, wordmark = R.drawable.theme_e_wordmark, slogan = R.drawable.theme_e_slogan,
            today = R.drawable.theme_e_today, pet = R.drawable.theme_e_pet,
            notes = R.drawable.theme_e_notes, games = R.drawable.theme_e_games,
        ),
    )

    fun of(key: String): UiTheme = all.firstOrNull { it.key == key } ?: classic
}
