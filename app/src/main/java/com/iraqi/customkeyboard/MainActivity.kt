package com.iraqi.customkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }

        val info = TextView(this).apply {
            text = "خطوات التفعيل:\n\n" +
                    "1. اضغط الزر تحت لفتح إعدادات لوحات المفاتيح\n" +
                    "2. فعّل \"كيبورد عراقي\" من القائمة\n" +
                    "3. بأي تطبيق، اضغط مطولاً على مسافة الكيبورد أو ايقونة الكرة الأرضية واختر \"كيبورد عراقي\""
            textSize = 16f
        }

        val openSettingsBtn = Button(this).apply {
            text = "فتح إعدادات لوحات المفاتيح"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val pickerBtn = Button(this).apply {
            text = "اختيار كيبورد عراقي كلوحة حالية"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        layout.addView(info)
        layout.addView(openSettingsBtn)
        layout.addView(pickerBtn)
        setContentView(layout)
    }
}
