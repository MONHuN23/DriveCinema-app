package com.drivecinema.app.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme

/**
 * Milestone P: a one-line text field in DriveCinema's colours (white when focused, so the TV remote's place is clear).
 * On the TV, OK opens the system keyboard; Up and Down leave the field. [onImeAction] runs on the keyboard's
 * [imeAction] key (Search, Done).
 */
@Composable
fun DcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    textStyle: TextStyle = DcTheme.type.body.copy(color = DcColors.Text1),
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = imeAction
        ),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        supportingText = supporting?.let { { Text(it, style = DcTheme.type.caption) } },
        shape = RoundedCornerShape(DcTokens.Radius.CONTROL.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DcColors.Text1,
            unfocusedTextColor = DcColors.Text1,
            focusedBorderColor = DcColors.Focus,
            unfocusedBorderColor = DcColors.Outline,
            focusedLabelColor = DcColors.Text1,
            unfocusedLabelColor = DcColors.Text2,
            cursorColor = DcColors.Text1,
            focusedSupportingTextColor = DcColors.Text2,
            unfocusedSupportingTextColor = DcColors.Text3,
            focusedContainerColor = DcColors.Canvas,
            unfocusedContainerColor = DcColors.Canvas
        ),
        modifier = modifier.fillMaxWidth()
    )
}
