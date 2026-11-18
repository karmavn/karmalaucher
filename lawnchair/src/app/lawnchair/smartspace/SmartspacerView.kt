package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.viewpager.widget.ViewPager
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.util.firstCached
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView
import com.kieronquinn.app.smartspacer.sdk.client.R as SmartspacerR
import com.kieronquinn.app.smartspacer.sdk.client.views.BcSmartspaceView
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.Popup
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.PopupFactory
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceConfig
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.UiSurface
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SmartspacerView(context: Context, attrs: AttributeSet?) : BcSmartspaceView(context, attrs) {
    private lateinit var viewPager: ViewPager
    private val prefs2 = PreferenceManager2.getInstance(context)
    private var targetCount = 5

    init {
        // Read the cached value synchronously so `config` below is built with the user's count.
        targetCount = prefs2.smartspacerMaxCount.firstCached(prefs2)
        // Collect only while attached: a plain CoroutineScope here leaked a subscriber (and the
        // old view/context) on every activity recreate.
        repeatOnAttached {
            prefs2.smartspacerMaxCount.get()
                .onEach { targetCount = it }
                .launchIn(this)
        }

        popupFactory = object : PopupFactory {
            override fun createPopup(
                context: Context,
                anchorView: View,
                target: SmartspaceTarget,
                backgroundColor: Int,
                textColour: Int,
                launchIntent: (Intent?) -> Unit,
                dismissAction: ((SmartspaceTarget) -> Unit)?,
                aboutIntent: Intent?,
                feedbackIntent: Intent?,
                settingsIntent: Intent?,
            ): Popup {
                val launcher = context.launcher
                val pos = Rect()
                launcher.dragLayer.getDescendantRectRelativeToSelf(anchorView, pos)
                val options = listOfNotNull(
                    getAboutOption(launchIntent, aboutIntent),
                    getCustomizeOption(launchIntent, settingsIntent),
                    getFeedbackOption(launchIntent, feedbackIntent),
                    getDismissOption(target, dismissAction),
                ).ifEmpty { listOf(getCustomizeOptionFallback()) }
                val popup = OptionsPopupView
                    .show<LawnchairLauncher>(launcher, RectF(pos), options, true)
                return object : Popup {
                    override fun dismiss() {
                        popup.close(true)
                    }
                }
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        viewPager = findViewById<ViewPager>(SmartspacerR.id.smartspace_card_pager)!!
        viewPager.setLayoutParams(LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.UNSPECIFIED_GRAVITY))
    }

    override val config = SmartspaceConfig(
        targetCount,
        UiSurface.HOMESCREEN,
        context.packageName,
    )

    private fun getDismissOption(
        target: SmartspaceTarget,
        dismissAction: ((SmartspaceTarget) -> Unit)?,
    ): OptionsPopupView.OptionItem? {
        if (dismissAction == null) return null
        return OptionsPopupView.OptionItem(
            context,
            SmartspacerR.string.smartspace_long_press_popup_dismiss,
            SmartspacerR.drawable.ic_smartspace_long_press_dismiss,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            dismissAction.invoke(target)
            true
        }
    }

    private fun getAboutOption(
        launchIntent: (Intent?) -> Unit,
        aboutIntent: Intent?,
    ): OptionsPopupView.OptionItem? {
        if (aboutIntent == null) return null
        return OptionsPopupView.OptionItem(
            context,
            SmartspacerR.string.smartspace_long_press_popup_about,
            SmartspacerR.drawable.ic_smartspace_long_press_about,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            launchIntent(aboutIntent)
            true
        }
    }

    private fun getFeedbackOption(
        launchIntent: (Intent?) -> Unit,
        feedbackIntent: Intent?,
    ): OptionsPopupView.OptionItem? {
        if (feedbackIntent == null) return null
        return OptionsPopupView.OptionItem(
            context,
            SmartspacerR.string.smartspace_long_press_popup_feedback,
            SmartspacerR.drawable.ic_smartspace_long_press_feedback,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            launchIntent(feedbackIntent)
            true
        }
    }

    private fun getCustomizeOption(
        launchIntent: (Intent?) -> Unit,
        settingsIntent: Intent?,
    ): OptionsPopupView.OptionItem? {
        if (settingsIntent == null) return null
        return OptionsPopupView.OptionItem(
            context,
            R.string.action_customize,
            R.drawable.ic_setting,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            launchIntent(settingsIntent)
            true
        }
    }

    private fun getCustomizeOptionFallback() = OptionsPopupView.OptionItem(
        context,
        R.string.action_customize,
        R.drawable.ic_setting,
        StatsLogManager.LauncherEvent.IGNORE,
    ) {
        context.startActivity(PreferenceActivity.createIntent(context, Smartspace))
        true
    }
}
