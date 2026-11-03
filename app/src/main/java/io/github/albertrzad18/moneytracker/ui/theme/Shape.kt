package io.github.albertrzad18.moneytracker.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Pills for buttons, fields, segments, chips, the nav indicator and bars. */
val Pill = CircleShape

/** Cards 32 (Summary, Settings) or 28 (History, instrument card), menus 22, inner lists 24. */
val MoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val CardShape = RoundedCornerShape(32.dp)
val SmallCardShape = RoundedCornerShape(28.dp)
val InsetShape = RoundedCornerShape(24.dp)
val MenuShape = RoundedCornerShape(22.dp)
val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
