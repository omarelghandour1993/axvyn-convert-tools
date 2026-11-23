package com.axvyn.app.features.wordtopdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.axvyn.app.ui.components.DisabledPrimaryButton
import com.axvyn.app.ui.components.DisabledSelectionButton
import com.axvyn.app.ui.components.PlaceholderNotice
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell

@Composable
fun WordToPdfScreen(onBack: () -> Unit) {
    ToolScreenShell(
        title = "Word to PDF",
        description = "Turn a Word document into a PDF on your device.",
        icon = Icons.Default.Description,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            DisabledSelectionButton(label = "Choose a Word document", icon = Icons.Default.UploadFile)
            Text(
                text = "Supported: .docx only · Maximum file size: 10 MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "OUTPUT") {
            Text(
                text = "PDF, written by the app to a location you choose.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "The renderer will support paragraphs, headings, tables, images and right-to-left text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PlaceholderNotice(
            message = "The DOCX parser and PDF renderer are not implemented yet. Nothing is converted on this screen.",
        )
        DisabledPrimaryButton(label = "Convert to PDF")
    }
}
