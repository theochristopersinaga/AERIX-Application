package com.aerix.itera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val historyList: List<SensorHistory>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvCO: TextView = view.findViewById(R.id.tvCO)

        val tvPM: TextView = view.findViewById(R.id.tvPM)
        val tvVin: TextView = view.findViewById(R.id.tvVin)
        val tvI: TextView = view.findViewById(R.id.tvI)
        val tvP: TextView = view.findViewById(R.id.tvP)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]

        holder.tvTime.text = item.timeStr
        holder.tvCO.text = "CO: %.1f ppm".format(item.CO_PPM)
        holder.tvPM.text = "PM 2.5: %.1f µg/m³".format(item.PM25)
        holder.tvVin.text = "V: %.2f V".format(item.VIN_INA)
        holder.tvI.text = "I: %.2f mA".format(item.ARUS_INA)   // sudah mA
        holder.tvP.text = "P: %.2f W".format(item.POWER)

    }

    override fun getItemCount(): Int = historyList.size
}
