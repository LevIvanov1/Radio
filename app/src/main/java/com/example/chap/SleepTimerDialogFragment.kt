package com.example.chap

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class SleepTimerDialogFragment(private val onTimerSelected: (Long) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Таймер сна")
            .setItems(arrayOf("15 минут", "30 минут", "60 минут", "Выключить")) { _, which ->
                when (which) {
                    0 -> onTimerSelected(15 * 60 * 1000L)
                    1 -> onTimerSelected(30 * 60 * 1000L)
                    2 -> onTimerSelected(60 * 60 * 1000L)
                    3 -> onTimerSelected(0L)
                }
            }
        return builder.create()
    }
}