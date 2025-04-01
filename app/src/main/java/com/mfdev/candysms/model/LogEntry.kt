package com.mfdev.candysms.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.mfdev.candysms.R
import java.util.Date
import java.util.Locale

data class LogEntry(
    val title: String,
    val summary: String?,
    val time: Date,
    val type: Type
) {
    fun toTextLine(): String {
        return String.format(
            Locale.getDefault(),
            "%1\$s''%2\$s''%3\$d''%4\$s",
            title,
            summary ?: "null",
            time.time,
            type.name
        )
    }

    enum class Type(
        @ColorRes val colorRes: Int,
        @DrawableRes val iconRes: Int
    ) {
        Error(R.color.colorError, R.drawable.ic_error),
        Success(R.color.colorSuccess, R.drawable.ic_checked_circle),
        Info(R.color.colorInfo, R.drawable.ic_info),
        Task(R.color.colorTask, R.drawable.ic_task)
    }

    companion object {
        fun parseFromTextLine(textLine: String): LogEntry? {
            return try {
                val parts = textLine.split("''")
                val title = parts[0]
                var summary = parts[1]
                if (summary.isEmpty() || summary == "null") {
                    summary = "None"
                }
                val time = Date(parts[2].toLong())
                val type = Type.valueOf(parts[3])
                LogEntry(title, summary, time, type)
            } catch (e: Exception) {
                null
            }
        }
    }
}