package com.focusforge.app.ui.timer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Native Android Clock-style 3-column scroll-wheel picker for Hours, Minutes, and Seconds.
 */
@Composable
fun TimerWheelPicker(
    totalSeconds: Int,
    onTotalSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalSeconds.coerceAtLeast(1)
    val hours = (safeTotal / 3600).coerceIn(0, 12)
    val minutes = ((safeTotal % 3600) / 60).coerceIn(0, 59)
    val seconds = (safeTotal % 60).coerceIn(0, 59)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle native-style center selection dividers
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(44.dp)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                label = "HRS",
                range = 0..12,
                selectedValue = hours,
                onValueSelected = { h ->
                    val next = (h * 3600 + minutes * 60 + seconds).coerceAtLeast(1)
                    onTotalSecondsChanged(next)
                }
            )
            Text(
                text = ":",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp)
            )
            WheelColumn(
                label = "MIN",
                range = 0..59,
                selectedValue = minutes,
                onValueSelected = { m ->
                    val next = (hours * 3600 + m * 60 + seconds).coerceAtLeast(1)
                    onTotalSecondsChanged(next)
                }
            )
            Text(
                text = ":",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp)
            )
            WheelColumn(
                label = "SEC",
                range = 0..59,
                selectedValue = seconds,
                onValueSelected = { s ->
                    val next = (hours * 3600 + minutes * 60 + s).coerceAtLeast(1)
                    onTotalSecondsChanged(next)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    label: String,
    range: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val itemHeight = 44.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedValue.coerceIn(range))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(selectedValue) {
        val targetIdx = (selectedValue - range.first).coerceIn(0, range.count() - 1)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIdx) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    val centeredIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .filter { (inProgress, _) -> !inProgress }
            .distinctUntilChanged()
            .collect { (_, idx) ->
                val newVal = (range.first + idx).coerceIn(range)
                if (newVal != selectedValue) {
                    onValueSelected(newVal)
                }
            }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .width(68.dp)
                .height(itemHeight * 3),
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(range.count()) { idx ->
                val value = range.first + idx
                val isCenter = idx == centeredIndex
                Box(
                    modifier = Modifier.height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(value),
                        fontSize = if (isCenter) 26.sp else 18.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCenter) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (isCenter) 1f else 0.4f),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}
