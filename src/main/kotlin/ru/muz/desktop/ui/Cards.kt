package ru.muz.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    cover: String?,
    width: Dp = 172.dp,
    round: Boolean = false,
    onPlay: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHover()
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(12.dp)
    Column(
        Modifier.width(width + 24.dp).clip(RoundedCornerShape(16.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box {
            Cover(cover, width, shape = shape, modifier = Modifier.shadow(if (hovered) 12.dp else 4.dp, shape), px = 400)
            if (onPlay != null && hovered) {
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                    Box(
                        Modifier.size(46.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Accent)
                            .clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Играть", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = SoftText,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
