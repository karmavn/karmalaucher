package app.lawnchair.gestures.config

import android.app.Activity
import android.content.Context
import app.lawnchair.LawnchairApp

val gestureHandlerOptions = listOf(
    GestureHandlerOption.NoOp,
    GestureHandlerOption.Sleep,
    GestureHandlerOption.Recents,
    GestureHandlerOption.OpenNotifications,
    GestureHandlerOption.OpenQuickSettings,
    GestureHandlerOption.OpenAppDrawer,
    GestureHandlerOption.OpenAppSearch,
    GestureHandlerOption.OpenSearch,
    GestureHandlerOption.OpenApp,
    GestureHandlerOption.OpenAssistant,
)

private val optionsDisabledInDeckLayout = setOf(
    GestureHandlerOption.OpenAppDrawer,
    GestureHandlerOption.OpenAppSearch,
)

fun filterGestureHandlerOptions(deckLayoutEnabled: Boolean): List<GestureHandlerOption> {
    return gestureHandlerOptions.filterNot { option ->
        (deckLayoutEnabled && option in optionsDisabledInDeckLayout) ||
            (!LawnchairApp.isRecentsEnabled && option == GestureHandlerOption.Recents)
    }
}

suspend fun GestureHandlerOption.buildConfigFrom(context: Context): GestureHandlerConfig? {
    val activity = context as? Activity ?: return null
    return buildConfig(activity)
}
