package com.mengding.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 原创奶油信笺气泡：薄荷描边、粉色封签和圆角尾巴，正文保持清晰。 */
@Composable
fun PetSpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.shadow(4.dp, RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF5), Color(0xFFFFF0F4))), RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFF71BCA8), RoundedCornerShape(18.dp)).padding(start = 13.dp, end = 13.dp, top = 15.dp, bottom = 9.dp)) {
            Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF493347), maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.TopEnd).offset(y = (-19).dp)
                .background(Color(0xFFFFCEDB), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("♥", fontSize = 10.sp, color = Color(0xFFAA5974))
            }
        }
        Canvas(Modifier.size(24.dp, 11.dp)) {
            val tail = Path().apply { moveTo(0f, 0f); quadraticBezierTo(size.width * .35f, size.height, size.width * .7f, size.height); lineTo(size.width, 0f); close() }
            drawPath(tail, Color(0xFF71BCA8))
            val inner = Path().apply { moveTo(2.dp.toPx(), 0f); quadraticBezierTo(size.width * .38f, size.height - 2.dp.toPx(), size.width * .68f, size.height - 2.dp.toPx()); lineTo(size.width - 2.dp.toPx(), 0f); close() }
            drawPath(inner, Color(0xFFFFF0F4))
        }
    }
}
