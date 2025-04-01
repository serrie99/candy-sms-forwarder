package com.mfdev.candysms.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mfdev.candysms.model.ForwardTask

class TaskStorage(context: Context) {
    private val PREF_NAME = "forward_tasks"
    private val TASKS_KEY = "tasks"
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveTasks(tasks: List<ForwardTask>) {
        val tasksJson = gson.toJson(tasks)
        sharedPreferences.edit().putString(TASKS_KEY, tasksJson).apply()
    }

    fun getTasks(): List<ForwardTask> {
        val tasksJson = sharedPreferences.getString(TASKS_KEY, null)
        return if (tasksJson != null) {
            val type = object : TypeToken<List<ForwardTask>>() {}.type
            gson.fromJson(tasksJson, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun clearTasks() {
        sharedPreferences.edit {
            remove(TASKS_KEY).apply()
        }
    }
}