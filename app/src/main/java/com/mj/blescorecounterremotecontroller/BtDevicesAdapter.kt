package com.mj.blescorecounterremotecontroller

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BtDevicesAdapter(
    private var names: List<String>,
    private var addresses: List<String>,
    private val btDeviceClickListener: BtDeviceClickListener
    ) : RecyclerView.Adapter<BtDevicesAdapter.BtDeviceViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class BtDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textViewNumber : TextView = itemView.findViewById(R.id.text_view_bt_device_number)
        var textViewName : TextView = itemView.findViewById(R.id.text_view_bt_device_name)
        var textViewMac : TextView = itemView.findViewById(R.id.text_view_bt_device_mac)
        var cardViewDevice : CardView = itemView.findViewById(R.id.card_view_bt_device)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BtDeviceViewHolder {
        val view : View = LayoutInflater.from(parent.context).inflate(R.layout.bt_device, parent, false)

        return BtDeviceViewHolder(view)
    }

    override fun getItemCount(): Int {
        return names.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BtDeviceViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.textViewNumber.text = "${position + 1}."
        holder.textViewName.text =
            holder.itemView.context.getString(R.string.bt_device_name, names[position])
        holder.textViewMac.text =
            holder.itemView.context.getString(R.string.bt_device_mac_address, addresses[position])

        if (position == this.selectedPosition) {
            this.markCardView(holder)
        }
        else {
            this.unmarkCardView(holder)
        }

        holder.cardViewDevice.setOnClickListener {
            this.selectedPosition = position

            this.btDeviceClickListener.onBtDeviceClicked(position)

            this.notifyDataSetChanged()
        }
    }

    private fun markCardView(holder: BtDeviceViewHolder) {
        val cardViewBg = holder.cardViewDevice.background
        cardViewBg.setTint(Color.BLACK)
        holder.cardViewDevice.background = cardViewBg

        holder.textViewNumber.setTextColor(Color.WHITE)
        holder.textViewName.setTextColor(Color.WHITE)
        holder.textViewMac.setTextColor(Color.WHITE)
    }

    private fun unmarkCardView(holder: BtDeviceViewHolder) {
        val cardViewBg = holder.cardViewDevice.background
        val colorResId = com.google.android.material.R.color.material_dynamic_secondary90
        cardViewBg.setTint(ContextCompat.getColor(holder.itemView.context, colorResId))
        holder.cardViewDevice.background = cardViewBg

        holder.textViewNumber.setTextColor(Color.BLACK)
        holder.textViewName.setTextColor(Color.BLACK)
        holder.textViewMac.setTextColor(Color.BLACK)
    }
}