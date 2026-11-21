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

import android.content.Context
import android.widget.Toast
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecentsGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        withContext(Dispatchers.Main.immediate) {
            if (LawnchairApp.isRecentsEnabled) {
                launcher.stateManager.goToState(LauncherState.OVERVIEW)
            } else {
                Toast.makeText(
                    launcher,
                    R.string.gesture_unavailable_without_quickstep,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
