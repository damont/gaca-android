package dev.monts.gaca.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.R
import dev.monts.gaca.ui.common.BrandMark
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/** First run, or after "Disconnect from this server": pair this phone with a server. */
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val s by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { viewModel.onScanned(it.rawValue) }
            .addOnFailureListener { viewModel.scanFailed() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(R.drawable.ic_gaca_wings_level, "Gaca, wings level", 132.dp, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text("Gaca", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Connect to your server", style = MaterialTheme.typography.bodyMedium, color = TextMeta, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        if (s.mode == PairMode.SCAN) {
            Text(
                "On the computer that runs your GACA server, run",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text("gaca-api pair", style = MaterialTheme.typography.titleMedium, color = HeaderAccent, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text("then scan the QR code it shows.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        } else {
            OutlinedTextField(
                value = s.address,
                onValueChange = viewModel::setAddress,
                label = { Text("Server address") },
                placeholder = { Text("gaca.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = s.code,
                onValueChange = viewModel::setCode,
                label = { Text("Pairing code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            )
        }

        s.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }

        Spacer(Modifier.height(20.dp))
        val ready = s.mode == PairMode.SCAN || (s.address.isNotBlank() && s.code.isNotBlank())
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ready) HeaderAccent else HeaderAccent.copy(alpha = 0.4f))
                .clickable(enabled = ready && !s.busy) {
                    if (s.mode == PairMode.SCAN) scan() else viewModel.pair()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (s.busy) {
                CircularProgressIndicator(color = OnAccent, modifier = Modifier.height(18.dp))
            } else {
                Text(
                    if (s.mode == PairMode.SCAN) "Scan QR code" else "Pair",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnAccent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (s.mode == PairMode.SCAN) "Enter a code instead" else "Scan a QR code instead",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable {
                if (s.mode == PairMode.SCAN) viewModel.showTyping() else viewModel.showScanning()
            },
        )
    }
}
