package com.yvesds.voicetally3.ui.bluetooth

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

/**
 * Eenvoudige lijst van gevonden HID Bluetooth devices.
 * - ListAdapter + DiffUtil voor efficiënte updates
 * - Stabiele IDs o.b.v. MAC address
 *
 * Vereist een BluetoothDeviceWrapper dat minstens:
 *   val device: android.bluetooth.BluetoothDevice
 *   val type: String
 *   val rssi: Int?
 * bevat.
 */
class HidDeviceAdapter :
    ListAdapter<BluetoothDeviceWrapper, HidDeviceAdapter.HidViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    fun submitDevices(list: List<BluetoothDeviceWrapper>) = submitList(list)

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        // MAC address is uniek → stabiel ID
        return item.device.address.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HidViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hid_device, parent, false)
        return HidViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: HidViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HidViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        private val textDeviceName: TextView = root.findViewById(R.id.textDeviceName)
        private val textDeviceInfo: TextView = root.findViewById(R.id.textDeviceInfo)

        fun bind(wrapper: BluetoothDeviceWrapper) {
            val name = wrapper.device.name ?: wrapper.device.address
            textDeviceName.text = name

            val rssiText = wrapper.rssi?.let { " • RSSI: $it" } ?: ""
            textDeviceInfo.text = "${wrapper.type}$rssiText"
        }
    }

    private object Diff : DiffUtil.ItemCallback<BluetoothDeviceWrapper>() {
        override fun areItemsTheSame(
            oldItem: BluetoothDeviceWrapper,
            newItem: BluetoothDeviceWrapper
        ) = oldItem.device.address == newItem.device.address

        override fun areContentsTheSame(
            oldItem: BluetoothDeviceWrapper,
            newItem: BluetoothDeviceWrapper
        ) = oldItem.type == newItem.type && oldItem.rssi == newItem.rssi
    }
}
