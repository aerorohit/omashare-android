package org.omashare.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = offer.fromAlias.ifEmpty { "Someone" },
                    color = MochaText,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                offer.files.forEach { file ->
                    Text(
                        text = "${file.name}  ${FileStager.humanBytes(file.size)}",
                        color = MochaMuted,
                        fontSize = 14.sp,
                    )
                }
                if (offer.files.isEmpty()) {
                    Text("a file", color = MochaMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDecline) {
                        Text("Decline", color = MochaRed)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MochaAccent,
                            contentColor = MochaBackground,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}
