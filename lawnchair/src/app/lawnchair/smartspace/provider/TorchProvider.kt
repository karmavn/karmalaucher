package app.lawnchair.smartspace.provider

import android.content.Context
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class TorchProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_torch,
        { smartspaceTorch },
    ) {
    private val cameraManager = context.getSystemService<CameraManager>()
    private val torchIcon by lazy { Icon.createWithResource(context, R.drawable.ic_flashlight_off) }

    @Volatile
    private var cachedFlashCameraIds: Set<String>? = null

    private fun getFlashCameraIds(): Set<String> {
        cachedFlashCameraIds?.let { return it }
        val manager = cameraManager ?: return emptySet()
        return try {
            manager.cameraIdList
                .filter { id ->
                    runCatching {
                        manager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }.getOrDefault(false)
                }
                .toSet()
                .also { cachedFlashCameraIds = it }
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Failed to query flash camera IDs", e)
            emptySet()
        }
    }

    override val internalTargets = torchFlow()
        .distinctUntilChanged()
        .map { enabled -> listOfNotNull(if (enabled) getSmartspaceTarget() else null) }

    private fun torchFlow(): Flow<Boolean> = callbackFlow {
        val manager = cameraManager
        val flashIds = getFlashCameraIds()
        if (manager == null || flashIds.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val enabledCameraIds = mutableSetOf<String>()

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id in flashIds) {
                    if (enabled) {
                        enabledCameraIds.add(id)
                    } else {
                        enabledCameraIds.remove(id)
                    }
                    trySend(enabledCameraIds.isNotEmpty())
                }
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id in flashIds) {
                    enabledCameraIds.remove(id)
                    trySend(enabledCameraIds.isNotEmpty())
                }
            }
        }

        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterTorchCallback(callback) }
    }

    private fun getSmartspaceTarget(): SmartspaceTarget {
        return SmartspaceTarget(
            id = "torchStatus",
            headerAction = SmartspaceAction(
                id = "torchStatusAction",
                icon = torchIcon,
                title = context.getString(R.string.torch_status_on),
                subtitle = context.getString(R.string.torch_action_off),
                onClick = Runnable {
                    val manager = cameraManager ?: return@Runnable
                    getFlashCameraIds().forEach { cameraId ->
                        try {
                            manager.setTorchMode(cameraId, false)
                        } catch (e: CameraAccessException) {
                            Log.e(
                                TAG,
                                "Failed to turn off torch for camera $cameraId: ${e.message}",
                                e,
                            )
                        }
                    }
                },
            ),
            score = SmartspaceScores.SCORE_FLASHLIGHT,
            featureType = SmartspaceTarget.FeatureType.FEATURE_FLASHLIGHT,
        )
    }

    companion object {
        private const val TAG = "TorchProvider"
    }
}
