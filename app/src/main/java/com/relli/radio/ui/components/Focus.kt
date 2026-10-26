package com.relli.radio.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

/**
 * What a selected item looks like. On a TV remote (or a keyboard) the focused item gets a
 * gold outline and a soft glow, so you always know where you are. Touches still get the ripple.
 */
object FocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 42
}

private class FocusNode(private val source: InteractionSource) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init {
        delegate(ripple(color = Relli.cream).create(source))
    }

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<Interaction>()
            source.interactions.collect { i ->
                when (i) {
                    is FocusInteraction.Focus -> focuses += i
                    is FocusInteraction.Unfocus -> focuses -= i.focus
                    is PressInteraction.Press -> Unit
                }
                val now = focuses.isNotEmpty()
                if (now != focused) { focused = now; invalidateDraw() }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            drawRect(Relli.gold.copy(alpha = 0.18f))
            val w = 3.dp.toPx()
            drawRect(Relli.gold, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
        }
    }
}

/** Puts [FocusIndication] in place for everything inside. */
@Composable
fun FocusRings(content: @Composable () -> Unit) {
    CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides FocusIndication, content = content)
}

/** Round icon button that shows the same focus highlight as everything else. */
@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .clickable(interactionSource = source, indication = FocusIndication, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
