package org.omashare.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.omashare.app.service.ShareStore
import org.omashare.app.share.FileStager

@Composable
fun OfferSheet(
    offer: ShareStore.IncomingOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Dialog(onDismissRequest = onDecline) {
        Surface(
            color = MochaSurface,
            contentColor = MochaText,
            shape = OmashareCardShape,
            border = BorderStroke(1.dp, MochaOverlay),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Incoming")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = offer.fromAlias.ifEmpty { "Someone" },
                        color = MochaText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (offer.files.isEmpty()) {
                        Text(
                            text = "a file",
                            color = MochaMuted,
                            fontSize = 14.sp,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            offer.files.forEach { file ->
                                Text(
                                    text = "${file.name}  ${FileStager.humanBytes(file.size)}",
                                    color = MochaMuted,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietAction(
                        text = "Decline",
                        color = MochaRed,
                        onClick = onDecline,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onAccept,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MochaAccent,
                            contentColor = MochaBackground,
                        ),
                        shape = OmashareCardShape,
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}
