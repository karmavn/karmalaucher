package app.lawnchair.smartspace

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.util.lookupLifecycleOwner
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BcSmartspaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var previewMode: Boolean = false,
) : FrameLayout(context, attrs) {

    private val provider = SmartspaceProvider.INSTANCE.get(context)

    private lateinit var viewPager: ViewPager
    private lateinit var indicator: PageIndicator
    private val adapter = CardPagerAdapter(context)
    private var scrollState = ViewPager.SCROLL_STATE_IDLE
    private var pendingTargets: List<SmartspaceTarget>? = null
    private var lastSortedTargets: List<SmartspaceTarget> = emptyList()
    private var runningAnimation: Animator? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        viewPager = findViewById<ViewPager>(R.id.smartspace_card_pager)!!
        viewPager.isSaveEnabled = false
        indicator = findViewById<PageIndicator>(R.id.smartspace_page_indicator)!!

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                indicator.setPageOffset(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
            }

            override fun onPageScrollStateChanged(state: Int) {
                scrollState = state
                if (state == 0) {
                    pendingTargets?.let {
                        pendingTargets = null
                        onSmartspaceTargetsUpdate(it)
                    }
                }
            }
        })

        val targets = if (previewMode) provider.previewTargets else provider.targets
        repeatOnAttached {
            val lifecycleOwner = context.lookupLifecycleOwner()
            viewPager.adapter = adapter
            if (lifecycleOwner != null) {
                var collectJob: Job? = null
                val startCollecting = {
                    if (collectJob == null) {
                        collectJob = targets
                            .onEach(::onSmartspaceTargetsUpdate)
                            .launchIn(this)
                    }
                }
                val stopCollecting = {
                    collectJob?.cancel()
                    collectJob = null
                }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> startCollecting()
                        Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> stopCollecting()
                        else -> Unit
                    }
                }
                val lifecycle = lifecycleOwner.lifecycle
                lifecycle.addObserver(observer)
                try {
                    awaitCancellation()
                } finally {
                    lifecycle.removeObserver(observer)
                    stopCollecting()
                }
            } else {
                targets
                    .onEach(::onSmartspaceTargetsUpdate)
                    .launchIn(this)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val smartspaceHeight =
            context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
        if (height <= 0 || height >= smartspaceHeight) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            scaleX = 1f
            scaleY = 1f
            return
        }

        val scale = height.toFloat() / smartspaceHeight.toFloat()
        val width = (MeasureSpec.getSize(widthMeasureSpec).toFloat() / scale).roundToInt()
        super.onMeasure(
            makeMeasureSpec(width, EXACTLY),
            makeMeasureSpec(smartspaceHeight, EXACTLY),
        )
        scaleX = scale
        scaleY = scale
        pivotX = 0f
        pivotY = smartspaceHeight.toFloat() / 2f
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        viewPager.setOnLongClickListener(l)
    }

    private fun onSmartspaceTargetsUpdate(targets: List<SmartspaceTarget>) {
        if (adapter.count > 1 && scrollState != ViewPager.SCROLL_STATE_IDLE) {
            pendingTargets = targets
            return
        }

        val sortedTargets = targets.sortedByDescending { it.score }.toMutableList()
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val currentItem = viewPager.currentItem
        val index = if (isRtl) adapter.count - currentItem else currentItem
        if (isRtl) {
            sortedTargets.reverse()
        }

        if (sortedTargets == lastSortedTargets) {
            return
        }
        lastSortedTargets = sortedTargets

        val oldCard = adapter.getCardAtPosition(currentItem)
        adapter.setTargets(sortedTargets)
        val count = adapter.count
        if (isRtl && count > 0) {
            viewPager.setCurrentItem((count - index).coerceIn(0 until count), false)
        }
        indicator.setNumPages(targets.size)
        if (isShown && isLaidOut) {
            oldCard?.let { animateSmartspaceUpdate(it) }
        }
    }

    private fun animateSmartspaceUpdate(oldCard: BcSmartspaceCard) {
        if (runningAnimation != null || oldCard.parent != null) return

        val animParent = viewPager.parent as ViewGroup
        oldCard.measure(makeMeasureSpec(viewPager.width, EXACTLY), makeMeasureSpec(viewPager.height, EXACTLY))
        oldCard.layout(viewPager.left, viewPager.top, viewPager.right, viewPager.bottom)
        val shift = resources.getDimension(R.dimen.enhanced_smartspace_dismiss_margin)
        val animator = AnimatorSet()
        animator.play(
            ObjectAnimator.ofFloat(
                oldCard,
                View.TRANSLATION_Y,
                0f,
                (-height).toFloat() - shift,
            ),
        )
        animator.play(ObjectAnimator.ofFloat(oldCard, View.ALPHA, 1f, 0f))
        animator.play(
            ObjectAnimator.ofFloat(
                viewPager,
                View.TRANSLATION_Y,
                height.toFloat() + shift,
                0f,
            ),
        )
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animParent.overlay.add(oldCard)
            }

            override fun onAnimationEnd(animation: Animator) {
                animParent.overlay.remove(oldCard)
                runningAnimation = null
            }
        })
        runningAnimation = animator
        animator.start()
    }
}
