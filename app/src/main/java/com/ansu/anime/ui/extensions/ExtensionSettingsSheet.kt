@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.extensions

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.TwoStatePreference
import com.ansu.anime.extension.aniyomi.AniyomiSourceAdapter
import com.ansu.anime.ui.theme.AnsuColors

/** What building the extension's settings produced: the preferences, or why there are none. */
private class SettingsModel(val preferences: List<Preference>, val error: String?)

private fun buildSettings(context: Context, source: AniyomiSourceAdapter): SettingsModel {
    val key = source.settingsKey ?: return SettingsModel(emptyList(), null)
    return try {
        // The extension reads its own values back from the same file (`source_<id>`), so the manager must use it.
        val manager = PreferenceManager(context)
        manager.sharedPreferencesName = key
        manager.sharedPreferencesMode = Context.MODE_PRIVATE
        val screen = manager.createPreferenceScreen(context)
        source.setupSettings(screen)
        SettingsModel(flatten(screen), null)
    } catch (e: Throwable) {
        SettingsModel(emptyList(), "${e::class.java.simpleName}: ${e.message ?: "could not open this source's settings"}")
    }
}

private fun flatten(group: PreferenceGroup): List<Preference> = buildList {
    for (index in 0 until group.preferenceCount) {
        val preference = group.getPreference(index)
        if (!preference.isVisible) continue
        add(preference)
        if (preference is PreferenceGroup) addAll(flatten(preference))
    }
}

/**
 * The settings an extension declares (preferred server, domain, quality, API keys...), shown the way Aniyomi
 * shows them. The extension builds ordinary AndroidX preferences; each one is drawn here and a change goes
 * through the preference itself (its change listener first, then the value), so the extension sees exactly what
 * it would see in Aniyomi.
 */
@Composable
fun ExtensionSettingsSheet(source: AniyomiSourceAdapter, onDismiss: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val model = remember(source) { buildSettings(appContext, source) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Preference?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                "${source.name} settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                model.error != null -> Text(
                    model.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                model.preferences.isEmpty() -> Text(
                    "This source has no settings.",
                    color = AnsuColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                else -> key(tick) {
                    model.preferences.forEach { preference ->
                        PreferenceRow(preference, onEdit = { editing = it }, onChanged = { tick++ })
                    }
                }
            }
            Text(
                "Some sources only apply a changed setting after the app is restarted.",
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    editing?.let { preference ->
        PreferenceEditor(
            preference = preference,
            onDismiss = { editing = null },
            onChanged = {
                tick++
                editing = null
            },
        )
    }
}

@Composable
private fun PreferenceRow(preference: Preference, onEdit: (Preference) -> Unit, onChanged: () -> Unit) {
    val title = preference.title?.toString().orEmpty()
    val summary = preference.summary?.toString()?.takeIf { it.isNotBlank() }
    val enabled = preference.isEnabled

    if (preference is PreferenceGroup) {
        if (title.isNotBlank()) {
            HorizontalDivider(color = AnsuColors.StrokeGlass)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        return
    }

    val onClick: (() -> Unit)? = when {
        !enabled -> null
        preference is TwoStatePreference -> null
        preference is SeekBarPreference -> null
        preference is EditTextPreference ||
            preference is ListPreference ||
            preference is MultiSelectListPreference -> ({ onEdit(preference) })
        preference.onPreferenceClickListener != null -> ({
            preference.onPreferenceClickListener?.onPreferenceClick(preference)
            onChanged()
        })
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) AnsuColors.TextPrimary else AnsuColors.TextTertiary,
            )
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = AnsuColors.TextSecondary)
            }
            if (preference is SeekBarPreference) {
                var value by remember { mutableIntStateOf(preference.value) }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt() },
                    onValueChangeFinished = {
                        if (preference.callChangeListener(value)) preference.value = value else value = preference.value
                        onChanged()
                    },
                    valueRange = preference.min.toFloat()..preference.max.toFloat().coerceAtLeast(preference.min.toFloat() + 1f),
                    enabled = enabled,
                )
            }
        }
        if (preference is TwoStatePreference) {
            Switch(
                checked = preference.isChecked,
                enabled = enabled,
                onCheckedChange = { checked ->
                    if (preference.callChangeListener(checked)) preference.isChecked = checked
                    onChanged()
                },
            )
        }
    }
}

@Composable
private fun PreferenceEditor(preference: Preference, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val title = preference.title?.toString().orEmpty()
    when (preference) {
        is EditTextPreference -> {
            var text by remember { mutableStateOf(preference.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = {
                        if (preference.callChangeListener(text)) preference.text = text
                        onChanged()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        is ListPreference -> {
            val entries = preference.entries?.map { it.toString() }.orEmpty()
            val values = preference.entryValues?.map { it.toString() }.orEmpty()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEachIndexed { index, label ->
                            val value = values.getOrNull(index) ?: return@forEachIndexed
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (preference.callChangeListener(value)) preference.value = value
                                    onChanged()
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = preference.value == value, onClick = null)
                                Text(label, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        is MultiSelectListPreference -> {
            val entries = preference.entries?.map { it.toString() }.orEmpty()
            val values = preference.entryValues?.map { it.toString() }.orEmpty()
            var selected by remember { mutableStateOf(preference.values.toSet()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEachIndexed { index, label ->
                            val value = values.getOrNull(index) ?: return@forEachIndexed
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (value in selected) selected - value else selected + value
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = value in selected, onCheckedChange = null)
                                Text(label, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val result = selected.toMutableSet()
                        if (preference.callChangeListener(result)) preference.values = result
                        onChanged()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        else -> onDismiss()
    }
}
