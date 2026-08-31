package com.geomeasure.app.rtk

/** Contract for Bluetooth/USB GNSS receiver adapters. */
interface ReceiverTransport {
    val name: String
    fun connect()
    fun disconnect()
    fun writeRtcm(frame: ByteArray)
    fun setNmeaListener(listener: (String) -> Unit)
}
