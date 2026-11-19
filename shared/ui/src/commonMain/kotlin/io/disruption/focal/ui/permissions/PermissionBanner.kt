package io.disruption.focal.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.disruption.focal.core.permissions.Permission

/** A missing required permission, with how to fix it (story 9). */
@Composable
fun PermissionBanner(permission: Permission, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val copy = permission.copy()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(copy.missing, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onFix) { Text(copy.action) }
        }
    }
}

/** Run [action] each time the screen comes back to the foreground, e.g. from a settings screen. */
@Composable
fun OnResume(action: () -> Unit) {
    val current = rememberUpdatedState(action)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) current.value() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
