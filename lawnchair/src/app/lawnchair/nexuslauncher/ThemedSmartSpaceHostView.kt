package app.lawnchair.nexuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.descendants
import app.lawnchair.font.FontManager
import com.android.launcher3.R
import com.android.launcher3.icons.ShadowGenerator
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.Themes
import com.android.launcher3.views.DoubleShadowBubbleTextView

class ThemedSmartSpaceHostView(context: Context) : SmartSpaceHostView(context) {

    private val isWorkspaceDarkText = Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
    private val workspaceTextColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private val shadowGenerator = ShadowGenerator(ResourceUtils.pxFromDp(48f, resources.displayMetrics))

    // RemoteViews re-inflate on every update; remember the last (source, shadow) pair so an
    // unchanged weather icon is not run through the shadow generator again.
    private var lastWeatherSource: Bitmap? = null
    private var lastWeatherShadow: Bitmap? = null

    private val templateTextView = LayoutInflater.from(context)
        .inflate(R.layout.smartspace_text_template, this, false) as DoubleShadowBubbleTextView

    override fun getErrorView(): View {
        return super.getErrorView().also { overrideStyles(it as ViewGroup) }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        overrideStyles(this)
    }

    private fun overrideStyles(parent: ViewGroup) {
        val images = mutableListOf<ImageView>()
        for (child in parent.descendants) {
            when (child) {
                is TextView -> overrideTextView(child)
                is ImageView -> images.add(child)
            }
        }

        if (images.isNotEmpty()) {
            overrideWeatherIcon(images.last())
        }
        if (images.size > 1) {
            overrideCardIcon(images.first())
        }
    }

    private fun overrideTextView(tv: TextView) {
        if (isWorkspaceDarkText) {
            tv.paint.clearShadowLayer()
        } else {
            val shadowInfo = templateTextView.shadowInfo
            tv.paint.setShadowLayer(
                shadowInfo.ambientShadowBlur,
                shadowInfo.keyShadowOffsetX,
                shadowInfo.keyShadowOffsetY,
                shadowInfo.ambientShadowColor,
            )
        }
        tv.setTextColor(workspaceTextColor)
        tv.letterSpacing = -0.02F
        FontManager.INSTANCE.get(context).setCustomFont(tv, R.id.font_heading)
    }

    override fun onDetachedFromWindow() {
        lastWeatherSource = null
        lastWeatherShadow = null
        super.onDetachedFromWindow()
    }

    private fun overrideWeatherIcon(iv: ImageView) {
        if (isWorkspaceDarkText) return
        val source = (iv.drawable as? BitmapDrawable)?.bitmap ?: return
        if (source.isRecycled) return
        // The ImageView may already be showing our shadow bitmap from a previous refresh.
        if (source === lastWeatherShadow) return
        if (source === lastWeatherSource) {
            lastWeatherShadow?.let {
                iv.setImageBitmap(it)
                return
            }
        }
        val shadow = addShadowToBitmap(source)
        lastWeatherSource = source
        lastWeatherShadow = shadow
        iv.setImageBitmap(shadow)
    }

    private fun overrideCardIcon(iv: ImageView) {
        if (isWorkspaceDarkText) {
            iv.setColorFilter(workspaceTextColor)
        }
    }

    private fun addShadowToBitmap(bitmap: Bitmap): Bitmap {
        return if (!bitmap.isRecycled) {
            val newBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(newBitmap)
            shadowGenerator.drawShadow(bitmap, canvas)
            newBitmap
        } else {
            bitmap
        }
    }
}
