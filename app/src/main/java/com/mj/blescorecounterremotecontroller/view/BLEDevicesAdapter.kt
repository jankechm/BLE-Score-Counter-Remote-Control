package com.mj.blescorecounterremotecontroller.view

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mj.blescorecounterremotecontroller.R

class BLEDevicesAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.Adapter<BLEDevicesAdapter.BLEDeviceViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class BLEDeviceViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val textViewNumber : TextView = itemView.findViewById(R.id.text_view_bt_device_number)
        val textViewName : TextView = itemView.findViewById(R.id.text_view_bt_device_name)
        val textViewMac : TextView = itemView.findViewById(R.id.text_view_bt_device_mac)
        val cardViewDevice : CardView = itemView.findViewById(R.id.card_view_bt_device)

        val context: Context
            get() = itemView.context

        @SuppressLint("MissingPermission", "SetTextI18n")
        fun bind(position: Int) {
            val res = items[position]

            textViewNumber.text = "${position + 1}."
            textViewName.text = context.getString(
                R.string.bt_device_name, (res.device.name ?: "<Unnamed>"))
            textViewMac.text = context.getString(
                R.string.bt_device_mac_address, res.device.address)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view : View = LayoutInflater.from(parent.context)
            .inflate(R.layout.bt_device, parent, false)

        return BLEDeviceViewHolder(view)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: BLEDeviceViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.bind(position)

        if (position == this.selectedPosition) {
            this.markCardView(holder)
        }
        else {
            this.unmarkCardView(holder)
        }

        holder.cardViewDevice.setOnClickListener {
            this.selectedPosition = position

            // For some reason I get smoother visual update using notifyDataSetChanged
            // instead of 2x notifyItemChanged (for previous and current position)
            this.notifyDataSetChanged()

            this.onClickListener.invoke(items[position])
        }
    }

    fun resetSelectedPosition() {
        selectedPosition = RecyclerView.NO_POSITION
    }

    private fun markCardView(holder: BLEDeviceViewHolder) {
        val cardViewBg = holder.cardViewDevice.background
        cardViewBg.setTint(Color.BLACK)
        holder.cardViewDevice.background = cardViewBg

        holder.textViewNumber.setTextColor(Color.WHITE)
        holder.textViewName.setTextColor(Color.WHITE)
        holder.textViewMac.setTextColor(Color.WHITE)
    }

    private fun unmarkCardView(holder: BLEDeviceViewHolder) {
        val cardViewBg = holder.cardViewDevice.background
        val colorResId = com.google.android.material.R.color.material_dynamic_secondary90
        cardViewBg.setTint(ContextCompat.getColor(holder.context, colorResId))
        holder.cardViewDevice.background = cardViewBg

        holder.textViewNumber.setTextColor(Color.BLACK)
        holder.textViewName.setTextColor(Color.BLACK)
        holder.textViewMac.setTextColor(Color.BLACK)
    }
}