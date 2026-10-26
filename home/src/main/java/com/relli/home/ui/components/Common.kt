package com.relli.home.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.relli.home.HomeApp
import com.relli.home.ui.theme.Relli

fun toast(msg: String) = Toast.makeText(HomeApp.instance, msg, Toast.LENGTH_SHORT).show()

@Composable
fun Img(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), placeholder: String? = null, scale: ContentScale = ContentScale.Crop) {
    Box(modifier.clip(shape).background(Relli.raise), contentAlignment = Alignment.Center) {
        val fallback = @Composable {
            if (placeholder != null) Text(placeholder, style = MaterialTheme.typography.labelLarge, color = Relli.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            else Icon(Icons.Rounded.Movie, null, tint = Relli.faint)
        }
        if (url != null) SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize(), error = { fallback() })
        else fallback()
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0.01f) return
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Relli.gold))
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector?, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(CircleShape)
            .then(if (primary) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) Relli.onGold else Relli.cream, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) Relli.onGold else Relli.cream, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Relli.onGold else Relli.muted, maxLines = 1) }
}
