package app.lawnchair.smartspace

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.text.TextUtils
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.layoutDirection
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.model.hasIntent
import com.android.launcher3.R
import java.util.Locale
import java.util.UUID

class BcSmartspaceCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var baseActionIconSubtitleView: DoubleShadowTextView? = null
    private val shadowIconCache = LruCache<String, DoubleShadowIconDrawable>(ICON_CACHE_SIZE)
    private var dateView: IcuDateTextView? = null
    private var dndImageView: ImageView? = null
    private var extrasGroup: ViewGroup? = null
    private var iconDrawable: DoubleShadowIconDrawable? = null
    private var iconTintColor = 0
    private var nextAlarmImageView: ImageView? = null
    private var nextAlarmTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private lateinit var target: SmartspaceTarget
    private var titleTextView: TextView? = null
    private var topPadding = 0
    private var usePageIndicatorUi = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        dateView = findViewById(R.id.date)
        titleTextView = findViewById(R.id.title_text)
        subtitleTextView = findViewById(R.id.subtitle_text)
        baseActionIconSubtitleView = findViewById(R.id.base_action_icon_subtitle)
        extrasGroup = findViewById(R.id.smartspace_extras_group)
        topPadding = paddingTop
        extrasGroup?.let {
            dndImageView = it.findViewById(R.id.dnd_icon)
            nextAlarmImageView = it.findViewById(R.id.alarm_icon)
            nextAlarmTextView = it.findViewById(R.id.alarm_text)
        }
    }

    fun setSmartspaceTarget(target: SmartspaceTarget, multipleCards: Boolean) {
        this.target = target
        val headerAction = target.headerAction
        val baseAction = target.baseAction
        usePageIndicatorUi = multipleCards

        if (headerAction != null) {
            iconDrawable = getOrCreateIconDrawable(HEADER_ICON_SLOT, headerAction, headerIconTint())

            var title: CharSequence? = headerAction.title
            var subtitle = headerAction.subtitle
            val hasTitle = target.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER ||
                !title.isNullOrEmpty()
            val hasSubtitle = !subtitle.isNullOrEmpty()
            if (!hasTitle) {
                title = subtitle
            }
            val contentDescription = headerAction.contentDescription
            setTitle(title, contentDescription, hasTitle != hasSubtitle)
            if (!hasTitle || !hasSubtitle) {
                subtitle = null
            }
            setSubtitle(subtitle, headerAction.contentDescription)
            updateIconTint()
        }

        if (baseAction != null && baseActionIconSubtitleView != null) {
            val icon = getOrCreateIconDrawable(BASE_ICON_SLOT, baseAction, NO_TINT)
            val iconView = baseActionIconSubtitleView!!
            if (icon != null) {
                icon.setTintList(null)
                iconView.text = baseAction.subtitle
                iconView.setCompoundDrawablesRelative(icon, null, null, null)
                iconView.isVisible = true
                BcSmartSpaceUtil.setOnClickListener(iconView, baseAction, null, "BcSmartspaceCard")
                setFormattedContentDescription(iconView, baseAction.subtitle, baseAction.contentDescription)
            } else {
                iconView.isInvisible = true
                iconView.setOnClickListener(null)
                iconView.contentDescription = null
            }
        }

        dateView?.let {
            val calendarAction = SmartspaceAction(
                id = headerAction?.id ?: baseAction?.id ?: UUID.randomUUID().toString(),
                title = "unusedTitle",
                intent = BcSmartSpaceUtil.getOpenCalendarIntent(),
            )
            BcSmartSpaceUtil.setOnClickListener(it, calendarAction, null, "BcSmartspaceCard")
        }

        when {
            headerAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }

            baseAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, baseAction, null, "BcSmartspaceCard")
            }

            else -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }
        }
    }

    fun setPrimaryTextColor(textColor: Int) {
        titleTextView?.setTextColor(textColor)
        dateView?.setTextColor(textColor)
        subtitleTextView?.setTextColor(textColor)
        baseActionIconSubtitleView?.setTextColor(textColor)
        iconTintColor = textColor
        updateIconTint()
    }

    fun setTitle(title: CharSequence?, contentDescription: CharSequence?, hasIcon: Boolean) {
        val titleView = titleTextView ?: return
        val isRTL = Locale.getDefault().layoutDirection == View.LAYOUT_DIRECTION_RTL
        titleView.textAlignment = if (isRTL) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
        titleView.text = title
        titleView.setCompoundDrawablesRelative(
            if (hasIcon) iconDrawable else null,
            null,
            null,
            null,
        )
        titleView.ellipsize = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_CALENDAR &&
            Locale.ENGLISH.language == context.resources.configuration.locale.language
        ) {
            TextUtils.TruncateAt.MIDDLE
        } else {
            TextUtils.TruncateAt.END
        }
        if (hasIcon) {
            setFormattedContentDescription(titleView, title, contentDescription)
        }
    }

    private fun setSubtitle(subtitle: CharSequence?, charSequence2: CharSequence?) {
        val subtitleView = subtitleTextView ?: return
        subtitleView.text = subtitle
        subtitleTextView!!.setCompoundDrawablesRelative(
            if (subtitle.isNullOrEmpty()) null else iconDrawable,
            null,
            null,
            null,
        )
        subtitleTextView!!.maxLines = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_TIPS && !usePageIndicatorUi) 2 else 1
        setFormattedContentDescription(subtitleTextView!!, subtitle, charSequence2)
    }

    private fun setFormattedContentDescription(
        textView: TextView,
        title: CharSequence?,
        contentDescription: CharSequence?,
    ) {
        textView.contentDescription = when {
            title.isNullOrEmpty() -> contentDescription

            !contentDescription.isNullOrEmpty() -> context.getString(
                R.string.generic_smartspace_concatenated_desc,
                contentDescription,
                title,
            )

            else -> title
        }
    }

    override fun onDetachedFromWindow() {
        shadowIconCache.evictAll()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Dimensions, theme colors and tints may have changed: drop every cached shadow.
        shadowIconCache.evictAll()
    }

    /**
     * Returns a [DoubleShadowIconDrawable] for [action], reusing one that was built for the
     * same source/tint/size. [DoubleShadowIconDrawable] rasterizes the icon and builds two
     * blurred shadows in its constructor, so avoiding that work on every refresh keeps the
     * per-refresh allocation count (and GC pressure) down.
     *
     * The key deliberately contains a token derived from the source [Icon]: resource-backed
     * icons use their resource id, which is stable even when the provider creates a fresh
     * [Icon] on each emission. Mutable icons (bitmaps, URIs, byte arrays) fall back to object
     * identity, so a changed image always misses the cache instead of showing a stale icon.
     */
    private fun getOrCreateIconDrawable(
        slot: String,
        action: SmartspaceAction,
        tint: Int,
    ): DoubleShadowIconDrawable? {
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_icon_size)
        val key = "$slot|${action.id}|${iconToken(action.icon)}|$tint|$iconSize"
        shadowIconCache.get(key)?.let { return it }
        val source = BcSmartSpaceUtil.getIconDrawable(action.icon, context) ?: return null
        return DoubleShadowIconDrawable(source, context).also { shadowIconCache.put(key, it) }
    }

    private fun iconToken(icon: Icon?): String {
        if (icon == null) return "none"
        return if (icon.mode == Icon.TYPE_RESOURCE) {
            val resId = try {
                icon.resId
            } catch (_: IllegalStateException) {
                -1
            }
            "res:$resId"
        } else {
            "id:${System.identityHashCode(icon)}"
        }
    }

    private fun headerIconTint(): Int = when (target.featureType) {
        // Weather icons are never tinted (see updateIconTint), so use a stable sentinel
        // instead of the text color to avoid needless cache entries on a theme change.
        SmartspaceTarget.FeatureType.FEATURE_WEATHER -> NO_TINT
        else -> iconTintColor
    }

    private fun updateIconTint() {
        val icon = iconDrawable ?: return
        when (target.featureType) {
            SmartspaceTarget.FeatureType.FEATURE_WEATHER -> icon.setTintList(null)
            else -> icon.setTint(iconTintColor)
        }
    }

    companion object {
        private const val ICON_CACHE_SIZE = 16
        private const val NO_TINT = Int.MIN_VALUE
        private const val HEADER_ICON_SLOT = "h"
        private const val BASE_ICON_SLOT = "b"
    }
}
