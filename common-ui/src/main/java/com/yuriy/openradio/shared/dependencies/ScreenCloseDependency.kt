/*
 * Copyright 2026 The "FreeNetRadio" Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.shared.dependencies

import com.yuriy.openradio.shared.presenter.main.ScreenClose

/**
 * Lets a client receive the [ScreenClose] it needs without depending on the whole main screen model.
 */
interface ScreenCloseDependency {

    /**
     * Callback to provide [ScreenClose] to a client.
     */
    fun configureWith(screenClose: ScreenClose)
}
