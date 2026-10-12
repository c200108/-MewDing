package com.mengding.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.R
import com.mengding.app.logic.Catalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动页：当前界面主题的主图标弹出来 + 字标 + 标语。
 * "星星芽芽"主题下面再排三张启动页插画；"经典"主题下面排六只像素宠物。
 * 1.8 秒后自动淡出，点一下可以跳过。
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val ui = LocalUiTheme.current
    val dark = isSystemInDarkTheme()
    val logoScale = remember { Animatable(0.5f) }
    val contentAlpha = remember { Animatable(0f) }
    val whole = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(250); contentAlpha.animateTo(1f, tween(450)) }
        delay(1800)
        whole.animateTo(0f, tween(280))
        onDone()
    }
    val top = if (dark) P.cream else ui.primarySoft
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = whole.value }
            .background(Brush.verticalGradient(listOf(top, P.cream)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(if (ui.key == "classic") R.drawable.classic_splash else ui.logo), "萌叮",
                Modifier.size(if (ui.key == "classic") 286.dp else 168.dp)
                    .graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value }
                    .clip(RoundedCornerShape(if (ui.key == "classic") 28.dp else 40.dp)),
                contentScale = ContentScale.Crop,
            )
            Gap(22.dp)
            Column(Modifier.graphicsLayer { alpha = contentAlpha.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (ui.wordmark != null) Image(painterResource(ui.wordmark), "萌叮", Modifier.width(190.dp), contentScale = ContentScale.FillWidth)
                else Text("萌叮", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = P.ink)
                Gap(8.dp)
                if (ui.slogan != null) Image(painterResource(ui.slogan), null, Modifier.width(230.dp), contentScale = ContentScale.FillWidth)
                else Text("小小提醒，大大快乐", fontSize = 16.sp, color = P.sub)
                Gap(36.dp)
                when {
                    ui.splash.isNotEmpty() -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ui.splash.forEach {
                            Image(painterResource(it), null, Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.FillWidth)
                        }
                    }
                    // 经典薄荷主题使用上方的萌猫问候插画，不再重复显示默认桌面图标宠物。
                }
            }
        }
    }
}
