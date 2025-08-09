package com.yvesds.voicetally3.ui.bluetooth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally3.R

class HidDeviceAdapter : RecyclerView.Adapter<HidDeviceAdapter.HidViewHolder>() {

    private var deviceList = listOf<BluetoothDeviceWrapper>()

    fun submitList(list: List<BluetoothDeviceWrapper>) {
        deviceList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HidViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hid_device, parent, false)
        return HidViewHolder(view)
    }

    override fun onBindViewHolder(holder: HidViewHolder, position: Int) {
        val deviceWrapper = deviceList[position]
        holder.bind(deviceWrapper)
    }

    override fun getItemCount(): Int = deviceList.size

    class HidViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDeviceName: TextView = itemView.findViewById(R.id.textDeviceName)
        private val textDeviceInfo: TextView = itemView.findViewById(R.id.textDeviceInfo)

        fun bind(wrapper: BluetoothDeviceWrapper) {
            val name = wrapper.device.name ?: wrapper.device.address
            textDeviceName.text = name
            textDeviceInfo.text = "${wrapper.type} ${wrapper.rssi?.let { "RSSI: $it" } ?: ""}".trim()
        }
    }
}
