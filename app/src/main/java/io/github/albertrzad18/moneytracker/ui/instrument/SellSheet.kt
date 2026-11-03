package io.github.albertrzad18.moneytracker.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.PillButtonStyle
import io.github.albertrzad18.moneytracker.ui.components.PillDateField
import io.github.albertrzad18.moneytracker.ui.components.PillDecimalField
import io.github.albertrzad18.moneytracker.ui.components.ResultPill
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.ui.theme.SheetShape
import io.github.albertrzad18.moneytracker.ui.theme.amountColor
import io.github.albertrzad18.moneytracker.util.Formatters
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Bottom sheet for selling a purchase. The result updates live; a quantity is asked only when the
 * purchase has one. [onConfirm] gets the date, price, sold quantity (null = everything) and result %.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellSheet(
    lot: Lot,
    currentPrice: BigDecimal?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, BigDecimal, BigDecimal?, BigDecimal?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var priceText by rememberSaveable { mutableStateOf(currentPrice?.let { Formatters.editable(it) }.orEmpty()) }
    var quantityText by rememberSaveable { mutableStateOf(lot.quantity?.let { Formatters.editable(it) }.orEmpty()) }
    val price = Formatters.parseDecimal(priceText)?.takeIf { it.signum() > 0 }
    val owned = lot.quantity
    val quantity = Formatters.parseDecimal(quantityText)?.takeIf { it.signum() > 0 && (owned == null || it <= owned) }
    val quantityValid = owned == null || quantity != null
    val pct = price?.let { Calc.changePct(lot.buyPrice, it) }
    val gain = if (price != null && quantity != null) price.subtract(lot.buyPrice).multiply(quantity) else null

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim,
        // Insets are applied to the content below (navigation bar and keyboard), not twice.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(36.dp, 4.dp)
                    .clip(Pill)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(stringResource(R.string.sell_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.sell_lot_summary, Formatters.date(lot.buyDate), Formatters.money(lot.buyPrice, lot.currency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            PillDateField(
                date = date,
                onDateChange = { date = it },
                label = stringResource(R.string.field_sell_date),
                height = 50.dp,
                minDate = lot.buyDate,
            )
            PillDecimalField(
                value = priceText,
                onValueChange = { priceText = it },
                label = stringResource(R.string.field_sell_price, lot.currency),
                isError = priceText.isNotBlank() && price == null,
                height = 50.dp,
            )
            if (owned != null) {
                PillDecimalField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = stringResource(R.string.field_sell_quantity, Formatters.quantity(owned)),
                    isError = !quantityValid,
                    height = 50.dp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(Pill)
                    .background(MoneyTheme.colors.inset)
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sell_result), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                gain?.let {
                    Text(
                        Formatters.signedMoney(it, lot.currency),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                        color = amountColor(it),
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                ResultPill(pct)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    stringResource(R.string.action_cancel),
                    onClick = { close(onDismiss) },
                    style = PillButtonStyle.Outlined,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    stringResource(R.string.action_sell),
                    onClick = {
                        val sold = if (owned != null) quantity else null
                        close { onConfirm(date, price!!, sold, pct) }
                    },
                    enabled = price != null && quantityValid,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
