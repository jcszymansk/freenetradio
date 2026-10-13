package com.yuriy.openradio.shared.view.dialog

import com.yuriy.openradio.shared.model.media.RadioStationToAdd

interface AddEditStationDialogPresenter {

    fun addRadioStation(
        radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )

    fun editRadioStation(
        mediaId: String, radioStation: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )
}
