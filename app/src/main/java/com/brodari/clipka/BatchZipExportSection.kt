package com.brodari.clipka

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun BatchZipExportSection(
    sourceUri: Uri?,
    clips: List<ClipSegment>,
    onStatusChanged: (String) -> Unit
) {
    val context = LocalContext.current

    var isExporting by remember { mutableStateOf(false) }
    var pendingZipFile by remember { mutableStateOf<File?>(null) }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destinationUri: Uri? ->
        val zipFile = pendingZipFile
        pendingZipFile = null

        try {
            if (destinationUri == null || zipFile == null) {
                onStatusChanged("ZIP save cancelled.")
                return@rememberLauncherForActivityResult
            }

            val output = context.contentResolver.openOutputStream(
                destinationUri,
                "w"
            ) ?: throw IllegalStateException("Could not open ZIP destination.")

            output.use { stream ->
                zipFile.inputStream().use { input ->
                    input.copyTo(stream)
                }
            }

            zipFile.delete()
            onStatusChanged("ZIP saved successfully.")
        } catch (error: Exception) {
            onStatusChanged(
                "ZIP save failed: ${error.message ?: "Unknown error"}"
            )
        } finally {
            zipFile?.delete()
            isExporting = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val uri = sourceUri

                if (uri == null) {
                    onStatusChanged("Please select a video first.")
                    return@Button
                }

                if (clips.isEmpty()) {
                    onStatusChanged("Please create at least one clip.")
                    return@Button
                }

                if (isExporting) {
                    return@Button
                }

                val invalidClip = clips.firstOrNull {
                    it.validate() !is ClipValidationResult.Valid
                }

                if (invalidClip != null) {
                    onStatusChanged(
                        "Invalid clip: ${invalidClip.name}"
                    )
                    return@Button
                }

                isExporting = true
                onStatusChanged("Preparing batch ZIP export...")

                try {
                    val exporter = BatchZipExporter(
                        context = context,
                        sourceUri = uri,
                        clips = clips.toList(),
                        onProgress = { message ->
                            onStatusChanged(message)
                        },
                        onComplete = { file ->
                            if (file == null) {
                                isExporting = false
                                onStatusChanged(
                                    "ZIP export failed. Check the export status."
                                )
                            } else {
                                pendingZipFile = file
                                saveZipLauncher.launch(
                                    "Clipka_Clips.zip"
                                )
                            }
                        }
                    )

                    exporter.start()
                } catch (error: Exception) {
                    isExporting = false
                    onStatusChanged(
                        "ZIP export failed: ${
                            error.message ?: "Unknown error"
                        }"
                    )
                }
            },
            enabled = sourceUri != null &&
                clips.isNotEmpty() &&
                !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isExporting) {
                    "Exporting clips..."
                } else {
                    "Export All Clips (ZIP)"
                }
            )
        }
    }
}
