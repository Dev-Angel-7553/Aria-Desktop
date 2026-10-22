package ru.muz.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.muz.auth.TokenInput
import ru.muz.desktop.AriaModel

val appIcon: ImageBitmap? by lazy {
    runCatching {
        AriaModel::class.java.getResourceAsStream("/aria.png")?.use {
            org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
        }
    }.getOrNull()
}

@Composable
fun LoginScreen(model: AriaModel) {
    var raw by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val token = TokenInput.extract(raw)
    val accent = Accent
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.background),
                radius = 1400f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                appIcon?.let { Image(it, null, modifier = Modifier.size(64.dp)) }
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Aria", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("Яндекс Музыка с текстами песен", color = SoftText)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Вход", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Нужен токен Яндекс Музыки: это ключ к вашему аккаунту. Ниже по шагам, как его получить.",
                color = SoftText,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Токен или адрес со страницы Яндекса") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (visible || raw.startsWith("http")) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!model.loginBusy) model.connect(raw) }),
                trailingIcon = {
                    if (raw.isEmpty()) {
                        RoundIcon(Icons.Filled.ContentPaste, "Вставить") { clipboard.getText()?.text?.let { raw = it.trim() } }
                    } else {
                        RoundIcon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Скрыть" else "Показать") { visible = !visible }
                    }
                },
            )
            if (raw.contains("access_token=") && token.isNotBlank()) {
                Text("Токен найден в адресе", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Pill(if (model.loginBusy) "Входим…" else "Войти", primary = true, modifier = Modifier.fillMaxWidth()) {
                if (!model.loginBusy) model.connect(raw)
            }
            if (model.loginMessage.isNotBlank()) {
                Text(model.loginMessage, color = SoftText, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
            ) {
                Text("Как получить токен", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Step(1, "Откройте страницу входа Яндекса в браузере и войдите в свой аккаунт.") {
                    Pill("Открыть страницу входа", Icons.Filled.OpenInNew, modifier = Modifier.padding(top = 8.dp)) { openUrl(TokenInput.OAUTH_URL) }
                }
                Step(2, "После входа браузер откроет адрес вида music.yandex.ru/#access_token=… Скопируйте его целиком из адресной строки.")
                Step(3, "Вставьте адрес в поле выше. Aria сама достанет из него токен.")
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).pointerHoverIcon(PointerIcon.Hand).clickable { other = !other },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Не получается? Другие способы", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(if (other) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Accent)
                }
                if (other) {
                    Text("Эти помощники показывают токен сразу после входа:", color = SoftText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    GuideLink("Расширение для Chrome") { openUrl(TokenInput.EXTENSION_CHROME) }
                    GuideLink("Расширение для Firefox") { openUrl(TokenInput.EXTENSION_FIREFOX) }
                    GuideLink("Приложение для Android") { openUrl(TokenInput.ANDROID_APP) }
                }
                Row(
                    Modifier.padding(top = 12.dp).pointerHoverIcon(PointerIcon.Hand).clickable { openUrl(TokenInput.GUIDE_URL) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MenuBook, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Text("Подробная инструкция", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lock, null, tint = SoftText, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(
                    "Токен хранится только на этом компьютере, в файле, доступном лишь вашему пользователю, и отправляется только в Яндекс.",
                    color = SoftText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text, fontSize = 14.sp)
            extra?.invoke()
        }
    }
}

@Composable
private fun GuideLink(title: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.OpenInNew, null, tint = SoftText, modifier = Modifier.size(18.dp))
    }
}
