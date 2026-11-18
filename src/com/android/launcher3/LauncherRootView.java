package com.android.launcher3;

import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.ViewDebug;
import android.view.WindowInsets;

import androidx.core.view.ViewCompat;

import com.android.launcher3.graphics.SysUiScrim;
import com.android.launcher3.statemanager.StatefulContainer;

import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.ActivityContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.hoko.blur.HokoBlur;
import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.util.FileAccessManager;
import app.lawnchair.util.FileAccessState;

public class LauncherRootView extends InsettableFrameLayout {

    private final Rect mTempRect = new Rect();

    private final StatefulContainer mStatefulContainer;

    @ViewDebug.ExportedProperty(category = "launcher")
    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

    private WindowStateListener mWindowStateListener;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisallowBackGesture;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mForceHideBackArrow;

    private final SysUiScrim mSysUiScrim;
    private final boolean mEnableTaskbarOnPhone;

    private final PreferenceManager pref;

    /** Incremented whenever a blur job is invalidated, so stale results are discarded. */
    private final AtomicInteger mBlurGeneration = new AtomicInteger();

    public LauncherRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStatefulContainer = ActivityContext.lookupContext(context);
        mSysUiScrim = new SysUiScrim(this);

        pref = PreferenceManager.getInstance(context);
        PreferenceManager2 prefs2 = PreferenceManager2.getInstance(context);
        
        mEnableTaskbarOnPhone = PreferenceCacheExtensionsKt.firstCached(prefs2.getEnableTaskbarOnPhone());

        FileAccessManager fileAccessManager = FileAccessManager.getInstance(context);
        FileAccessState wallpaperAccessState = fileAccessManager.getWallpaperAccessState().getValue();
        if (pref.getEnableWallpaperBlur().get() && wallpaperAccessState != FileAccessState.Denied.INSTANCE) {
            scheduleBlur(context);
        }
    }

    /**
     * Defers the expensive wallpaper blur until after the first draw so it never blocks the
     * cold-start main thread. The configuration is captured up front (cheap, cached reads);
     * the binder call, bitmap allocation and blur all happen on a background executor.
     */
    private void scheduleBlur(Context context) {
        var display = mStatefulContainer.getDeviceProfile();
        int width = display.getDeviceProperties().getWidthPx();
        int height = display.getDeviceProperties().getHeightPx();
        int sampleFactor = pref.getWallpaperBlurFactorThreshold().get();
        int radius = pref.getWallpaperBlur().get();
        int generation = mBlurGeneration.incrementAndGet();

        ViewCompat.postOnAnimation(this,
                () -> blurWallpaperAsync(context, generation, width, height, sampleFactor, radius));
    }

    private void blurWallpaperAsync(
            Context context, int generation, int width, int height, int sampleFactor, int radius) {
        if (mBlurGeneration.get() != generation) {
            return;
        }
        UI_HELPER_EXECUTOR.execute(() -> {
            if (mBlurGeneration.get() != generation) {
                return;
            }
            Drawable wallpaper = WallpaperManager.getInstance(context).getDrawable();
            if (wallpaper == null) {
                return;
            }

            // Reuse a single full-screen bitmap for both the wallpaper and the white scrim.
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap);
                wallpaper.setBounds(0, 0, width, height);
                wallpaper.draw(canvas);

                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setAlpha((int) (0.2 * 255));
                canvas.drawRect(0, 0, width, height, paint);

                Bitmap blurredBitmap = HokoBlur.with(context)
                        .forceCopy(true)
                        .scheme(HokoBlur.SCHEME_OPENGL)
                        .sampleFactor(sampleFactor)
                        .radius(radius)
                        .blur(bitmap);

                final Bitmap result = blurredBitmap;
                MAIN_EXECUTOR.execute(() -> {
                    if (mBlurGeneration.get() == generation && isAttachedToWindow()) {
                        setBackground(new BitmapDrawable(getContext().getResources(), result));
                    }
                });
            } catch (RuntimeException ignored) {
                // Keep the existing background if the wallpaper or blur cannot be processed.
            }
        });
    }

    private void handleSystemWindowInsets(Rect insets) {
        // Update device profile before notifying the children.
        mStatefulContainer.getDeviceProfile().updateInsets(insets);
        boolean resetState = !insets.equals(mInsets);
        setInsets(insets);

        if (resetState) {
            mStatefulContainer.getStateManager().reapplyState(true /* cancelCurrentAnimation */);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mStatefulContainer.onRootViewDispatchKeyEvent(event)
                || super.dispatchKeyEvent(event);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mStatefulContainer.handleConfigurationChanged(
                mStatefulContainer.asContext().getResources().getConfiguration());
        return updateInsets(insets);
    }

    private WindowInsets updateInsets(WindowInsets insets) {
        insets = WindowManagerProxy.INSTANCE.get(getContext())
                .normalizeWindowInsets(getContext(), insets, mTempRect);
        handleSystemWindowInsets(mTempRect);
        return insets;
    }

    @Override
    public void setInsets(Rect insets) {
        // If the insets haven't changed, this is a no-op. Avoid unnecessary layout caused by
        // modifying child layout params.
        if (!insets.equals(mInsets)) {
            super.setInsets(insets);
            mSysUiScrim.onInsetsChanged(insets);
        }
    }

    public void dispatchInsets() {
        if (isAttachedToWindow()) {
            updateInsets(getRootWindowInsets());
        } else {
            mStatefulContainer.getDeviceProfile().updateInsets(mInsets);
        }
        super.setInsets(mInsets);
    }

    public void setWindowStateListener(WindowStateListener listener) {
        mWindowStateListener = listener;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (mWindowStateListener != null) {
            mWindowStateListener.onWindowFocusChanged(hasWindowFocus);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mWindowStateListener != null) {
            mWindowStateListener.onWindowVisibilityChanged(visibility);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Invalidate any in-flight blur so it cannot install a stale background on a dead view.
        mBlurGeneration.incrementAndGet();
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mSysUiScrim.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(l, t, r, b);
        setDisallowBackGesture(mDisallowBackGesture);
        mSysUiScrim.setSize(r - l, b - t);
    }

    public void setForceHideBackArrow(boolean forceHideBackArrow) {
        this.mForceHideBackArrow = forceHideBackArrow;
        setDisallowBackGesture(mDisallowBackGesture);
    }

    public void setDisallowBackGesture(boolean disallowBackGesture) {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        mDisallowBackGesture = disallowBackGesture;
        if (Utilities.ATLEAST_Q) {
            setSystemGestureExclusionRects((mForceHideBackArrow || mDisallowBackGesture)
                    ? SYSTEM_GESTURE_EXCLUSION_RECT
                    : Collections.emptyList());
        }
    }

    public SysUiScrim getSysUiScrim() {
        return mSysUiScrim;
    }

    public interface WindowStateListener {

        void onWindowFocusChanged(boolean hasFocus);

        void onWindowVisibilityChanged(int visibility);
    }
}
