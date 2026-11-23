package ir.scanfa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.scanfa.app.vm.ScannerViewModel

/** تغییر رمز ورود؛ در صورت وجود کد مادر، ابتدا کد مادر (یا ۵۶۳۵۷۰) لازم است. */
@Composable
fun PinChangeDialog(vm: ScannerViewModel, onClose: () -> Unit) {
    var master by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (vm.hasMaster() && !vm.masterMatches(master)) {
            error = "کد مادر نادرست است"
            return
        }
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            error = "رمز باید ۶ رقم باشد"
            return
        }
        if (pin != pin2) {
            error = "رمزها یکسان نیستند"
            return
        }
        vm.savePin(pin)
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("تعیین رمز ورود") },
        text = {
            Column {
                if (vm.hasMaster()) {
                    OutlinedTextField(
                        value = master,
                        onValueChange = { master = it },
                        label = { Text("کد مادر فعلی") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(6) },
                    label = { Text("رمز جدید (۶ رقم)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { pin2 = it.take(6) },
                    label = { Text("تکرار رمز") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onClose) { Text("انصراف") } }
    )
}

/** تعیین یا تغییر کد مادر؛ تغییر نیازمند کد فعلی (یا ۵۶۳۵۷۰) است. */
@Composable
fun MasterChangeDialog(vm: ScannerViewModel, onClose: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var code2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (vm.hasMaster() && !vm.masterMatches(current)) {
            error = "کد مادر فعلی نادرست است"
            return
        }
        if (code.length != 6 || !code.all { it.isDigit() }) {
            error = "کد مادر باید ۶ رقم باشد"
            return
        }
        if (code != code2) {
            error = "کدها یکسان نیستند"
            return
        }
        if (code == "563570") {
            error = "این کد برای بازیابی است؛ کد دیگری انتخاب کن"
            return
        }
        vm.saveMaster(code)
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (vm.hasMaster()) "تغییر کد مادر" else "تعیین کد مادر") },
        text = {
            Column {
                if (vm.hasMaster()) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("کد مادر فعلی") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(6) },
                    label = { Text("کد مادر جدید (۶ رقم)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code2,
                    onValueChange = { code2 = it.take(6) },
                    label = { Text("تکرار کد مادر") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onClose) { Text("انصراف") } }
    )
}
