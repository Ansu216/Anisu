package com.ansu.anime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the screen this is called from is the one in front, not covered by another page and not mid-transition
 * to one. A navigation back-stack entry drops below RESUMED the moment another page is opened over it, so a
 * home banner that slides on by itself can ask this first and stay still while a details page is opening.
 */
@Composable
fun rememberIsResumed(): State<Boolean> {
    val owner = LocalLifecycleOwner.current
    val resumed = remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
