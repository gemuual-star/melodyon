package com.melodyon.desktop.deviceconnect

import com.melodyon.shared.deviceconnect.DeviceConnectDeviceType
import com.melodyon.shared.deviceconnect.DeviceConnectManager

object DesktopDeviceConnect {
    val manager = DeviceConnectManager(localDeviceType = DeviceConnectDeviceType.DESKTOP)
}
