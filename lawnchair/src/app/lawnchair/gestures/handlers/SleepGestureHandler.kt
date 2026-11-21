/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.gestures.handlers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.util.requireSystemService
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.R
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.first

class SleepGestureHandler(context: Context) : GestureHandler(context) {

    private val rootMethod = SleepMethodRoot(context)
    private val timeoutMethod = SleepMethodTimeout(context)
    private val deviceAdminMethod = SleepMethodDeviceAdmin(context)

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val pref = PreferenceManager2.getInstance(context).sleepMode.get().first()
        val method = when (pref) {
            SleepMode.AUTO -> when {
                rootMethod.isSupported() -> rootMethod
                timeoutMethod.hasPermission() -> timeoutMethod
                deviceAdminMethod.hasPermission() -> deviceAdminMethod
                else -> timeoutMethod
            }

            SleepMode.ROOT -> if (rootMethod.isSupported()) rootMethod else timeoutMethod

            SleepMode.TIMEOUT -> timeoutMethod

            SleepMode.DEVICE_ADMIN -> deviceAdminMethod
        }

        method.sleep(launcher)
    }

    sealed class SleepMethod(protected val context: Context) {
        abstract suspend fun isSupported(): Boolean
        abstract suspend fun sleep(launcher: LawnchairLauncher)
    }
}

class SleepMethodRoot(context: Context) : SleepGestureHandler.SleepMethod(context) {

    override suspend fun isSupported() = Shell.getShell().isRoot

    override suspend fun sleep(launcher: LawnchairLauncher) {
        Shell.cmd("input keyevent 26").exec()
    }
}

class SleepMethodTimeout(context: Context) : SleepGestureHandler.SleepMethod(context) {

    fun hasPermission(): Boolean = Settings.System.canWrite(context)

    override suspend fun isSupported() = true

    override suspend fun sleep(launcher: LawnchairLauncher) {
        if (!hasPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ComposeBottomSheet.show(launcher) {
                SleepPermissionDialog(
                    title = R.string.dt2s_timeout_hint_title,
                    message = R.string.dt2s_timeout_hint,
                    settingsIntent = intent,
                ) { close(true) }
            }
            return
        }
        val sleepIntent = Intent(launcher, SleepTimeoutActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launcher.startActivity(sleepIntent)
    }
}

class SleepMethodDeviceAdmin(context: Context) : SleepGestureHandler.SleepMethod(context) {
    private val devicePolicyManager: DevicePolicyManager = context.requireSystemService()
    private val adminComponent = ComponentName(context, SleepDeviceAdmin::class.java)

    fun hasPermission(): Boolean = devicePolicyManager.isAdminActive(adminComponent)

    override suspend fun isSupported() = true

    override suspend fun sleep(launcher: LawnchairLauncher) {
        if (!hasPermission()) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    launcher.getString(R.string.dt2s_admin_hint),
                )
            ComposeBottomSheet.show(launcher) {
                SleepPermissionDialog(
                    title = R.string.dt2s_admin_hint_title,
                    message = R.string.dt2s_admin_hint,
                    note = R.string.dt2s_admin_biometric_note,
                    settingsIntent = intent,
                ) { close(true) }
            }
            return
        }
        devicePolicyManager.lockNow()
    }

    class SleepDeviceAdmin : DeviceAdminReceiver() {

        override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
            return context.getString(R.string.dt2s_admin_warning)
        }
    }
}

@Composable
fun SleepPermissionDialog(
    title: Int,
    message: Int,
    settingsIntent: Intent,
    modifier: Modifier = Modifier,
    note: Int? = null,
    handleClose: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheetContent(
        modifier = modifier.padding(top = 16.dp),
        title = { Text(text = stringResource(id = title)) },
        text = {
            Column {
                Text(text = stringResource(id = message))
                if (note != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = stringResource(id = note))
                }
            }
        },
        buttons = {
            OutlinedButton(
                onClick = handleClose,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(settingsIntent)
                    } catch (_: ActivityNotFoundException) {
                        if (settingsIntent.action == Settings.ACTION_MANAGE_WRITE_SETTINGS &&
                            settingsIntent.data != null
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                    handleClose()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = R.string.open_permission_settings))
            }
        },
    )
}
