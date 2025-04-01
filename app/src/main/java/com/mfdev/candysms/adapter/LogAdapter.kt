package com.mfdev.candysms.adapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.RecyclerView
import com.mfdev.candysms.R
import com.mfdev.candysms.model.LogEntry
import java.text.SimpleDateFormat
import java.util.Locale

class LogAdapter(private var logs: List<LogEntry>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logIconImageView: ImageView = itemView.findViewById(R.id.logIconImageView)
        val logTitleTextView: TextView = itemView.findViewById(R.id.logTitleTextView)
        val logSummaryTextView: TextView = itemView.findViewById(R.id.logSummaryTextView)
        val logTimeTextView: TextView = itemView.findViewById(R.id.logTimeTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val logEntry = logs[position]
        holder.logTitleTextView.text = logEntry.title
        holder.logSummaryTextView.text = logEntry.summary
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedTime = timeFormat.format(logEntry.time)
        holder.logTimeTextView.text = formattedTime
        when (logEntry.type) {
            LogEntry.Type.Info -> holder.logIconImageView.setImageResource(R.drawable.ic_info)
            LogEntry.Type.Success -> holder.logIconImageView.setImageResource(R.drawable.ic_check_circle)
            LogEntry.Type.Error -> holder.logIconImageView.setImageResource(R.drawable.ic_error)
            LogEntry.Type.Task -> holder.logIconImageView.setImageResource(R.drawable.ic_task)
        }
    }

    override fun getItemCount(): Int {
        return logs.size
    }

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}