package com.drivecinema.app.ui.setup

/** Milestone Q wording (design: `docs/design/q0-device-sync.md`), the phone/TV Settings switch and its messages. */
object DeviceSyncTexts {
    const val SWITCH_CAPTION = com.drivecinema.app.core.devicesync.DeviceSyncController.SWITCH_CAPTION
    val TV_NEEDS_PAIRING = "Turn on \"${SettingsLabels.SYNC_WITH_MY_DEVICES}\" on your phone, then pair this TV again."
    const val PHONE_NEEDS_PERMISSION = "Google didn't give DriveCinema access to its app folder. Turn sync on again to allow it."
}
