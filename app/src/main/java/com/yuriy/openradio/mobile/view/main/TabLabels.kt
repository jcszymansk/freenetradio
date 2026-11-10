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

package com.yuriy.openradio.mobile.view.main

import androidx.annotation.StringRes
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.Tab

/**
 * The tab's label. [Tab] lives in `:common-ui`, which does not own the main screen's strings.
 */
@get:StringRes
val Tab.labelRes: Int
    get() = when (this) {
        Tab.FAV -> R.string.main_tab_fav
        Tab.MINE -> R.string.main_tab_mine
        Tab.POPULAR -> R.string.main_tab_popular
        Tab.NEW -> R.string.main_tab_new
        Tab.COUNTRY -> R.string.main_tab_country
        Tab.GENRE -> R.string.main_tab_genre
    }
