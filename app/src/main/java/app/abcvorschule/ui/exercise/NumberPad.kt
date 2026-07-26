package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var buffer by remember { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = buffer.ifEmpty { "?" },
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.testTag("number_buffer"),
        )
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "OK"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "⌫" -> buffer = buffer.dropLast(1)
                                "OK" -> buffer.toIntOrNull()?.let(onSubmit)
                                else -> if (buffer.length < 3) buffer += key
                            }
                        },
                        modifier = Modifier.testTag("pad_$key"),
                    ) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        Button(
            onClick = { buffer.toIntOrNull()?.let(onSubmit) },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("OK")
        }
    }
}
