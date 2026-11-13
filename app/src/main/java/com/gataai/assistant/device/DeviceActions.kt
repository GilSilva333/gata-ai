
package com.gataai.assistant.device

interface DeviceActions {

    fun openApplication(
        packageName: String
    ): Boolean

    fun openGallery(): Boolean

    fun openMusicPlayer(): Boolean

    fun makePhoneCall(
        phoneNumber: String
    ): Boolean

    fun sendMessage(
        phoneNumber: String,
        message: String
    ): Boolean

    fun openSettings(): Boolean

}
