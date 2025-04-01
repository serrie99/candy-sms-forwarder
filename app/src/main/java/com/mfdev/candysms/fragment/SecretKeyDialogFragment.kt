package com.mfdev.candysms.fragment

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mfdev.candysms.R

class SecretKeyDialogFragment :DialogFragment(){
    private var listener: OnSecretKeyUpdatedListener? = null
    private lateinit var secretKeyEditText: TextInputEditText
    private lateinit var secretKeyInputLayout: TextInputLayout
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.DialogThemeDark)
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.secret_key_dialog, null)

        secretKeyEditText = view.findViewById(R.id.secretKeyEditText)
        secretKeyInputLayout = view.findViewById(R.id.secretKeyInputLayout)

        sharedPreferences =
            requireContext().getSharedPreferences("candy_sms_pref", Context.MODE_PRIVATE)
        val secretKey = sharedPreferences.getString("secret_key", "")
        secretKeyEditText.setText(secretKey)

        val clearButton = view.findViewById<MaterialButton>(R.id.clearButton)
        val setButton = view.findViewById<MaterialButton>(R.id.setButton)

        clearButton.setOnClickListener {
            sharedPreferences.edit().remove("secret_key").apply()
            listener?.onSecretKeyUpdated()
            dismiss()
        }

        setButton.setOnClickListener {
            val newSecretKey = secretKeyEditText.text.toString()
            if(newSecretKey.isNotEmpty()){
                sharedPreferences.edit().putString("secret_key", newSecretKey).apply()
                listener?.onSecretKeyUpdated()
            }

            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }
    interface OnSecretKeyUpdatedListener {
        fun onSecretKeyUpdated()
    }
    fun setOnSecretKeyUpdatedListener(listener: OnSecretKeyUpdatedListener) {
        this.listener = listener
    }
}