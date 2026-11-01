package com.landezhaole.liquidglass

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.androidx_drag(onDrag: (Offset) -> Unit) = pointerInput(Unit) {
    detectDragGestures { change, amount -> change.consume(); onDrag(amount) }
}
