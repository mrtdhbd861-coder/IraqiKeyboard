package com.iraqi.customkeyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

class IraqiKeyboardService : InputMethodService() {

    private var isSplitMode = false      // تفكيك الكلمة
    private var isDuplicateMode = false  // تدبيل
    private var isNumbersLayout = false
    private val digitBuffer = StringBuilder()

    private lateinit var keysContainer: LinearLayout
    private lateinit var splitToggleBtn: Button
    private lateinit var dupToggleBtn: Button
    private lateinit var switchLayoutBtn: Button

    // لوحة الحروف - ٣ صفوف
    private val lettersRows = arrayOf(
        arrayOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        arrayOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        arrayOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ", "د", "ذ")
    )

    // لوحة أرقام على شكل Numpad: ١٢٣ / ٤٥٦ / ٧٨٩ / .٠
    private val numpadRows = arrayOf(
        arrayOf("1", "2", "3"),
        arrayOf("4", "5", "6"),
        arrayOf("7", "8", "9"),
        arrayOf(".", "0", "-")
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#1E1E1E"))
        root.setPadding(8, 8, 8, 8)

        // صف الخيارات (تفكيك الكلمة / تدبيل)
        val toggleRow = LinearLayout(this)
        toggleRow.orientation = LinearLayout.HORIZONTAL
        toggleRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        splitToggleBtn = makeToggleButton("تفكيك الكلمة") {
            isSplitMode = !isSplitMode
            updateToggleAppearance()
        }
        dupToggleBtn = makeToggleButton("تدبيل") {
            isDuplicateMode = !isDuplicateMode
            digitBuffer.clear()
            updateToggleAppearance()
        }
        toggleRow.addView(splitToggleBtn)
        toggleRow.addView(dupToggleBtn)
        root.addView(toggleRow)

        // منطقة المفاتيح (تتبدل بين حروف وأرقام)
        keysContainer = LinearLayout(this)
        keysContainer.orientation = LinearLayout.VERTICAL
        root.addView(keysContainer)
        buildKeysLayout()

        // الصف السفلي: تبديل اللوحة / مسافة / حذف / انتر
        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        bottomRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        switchLayoutBtn = makeKey(if (isNumbersLayout) "أبج" else "123") {
            isNumbersLayout = !isNumbersLayout
            digitBuffer.clear()
            switchLayoutBtn.text = if (isNumbersLayout) "أبج" else "123"
            buildKeysLayout()
        }

        val spaceBtn = makeKey("مسافة") { onSpace() }
        spaceBtn.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f
        )

        val backspaceBtn = makeKey("⌫") { onBackspace() }
        val enterBtn = makeKey("⏎") { onEnter() }

        bottomRow.addView(switchLayoutBtn)
        bottomRow.addView(spaceBtn)
        bottomRow.addView(backspaceBtn)
        bottomRow.addView(enterBtn)
        root.addView(bottomRow)

        updateToggleAppearance()
        return root
    }

    private fun makeToggleButton(label: String, onClick: (View) -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 13f
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        b.setOnClickListener { onClick(it) }
        return b
    }

    private fun makeKey(label: String, onClick: (View) -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 16f
        b.setOnClickListener { onClick(it) }
        return b
    }

    private fun updateToggleAppearance() {
        splitToggleBtn.setBackgroundColor(if (isSplitMode) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
        splitToggleBtn.setTextColor(Color.WHITE)
        dupToggleBtn.setBackgroundColor(if (isDuplicateMode) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
        dupToggleBtn.setTextColor(Color.WHITE)
    }

    private fun buildKeysLayout() {
        keysContainer.removeAllViews()

        if (isNumbersLayout) {
            // شكل Numpad: أزرار مربعة كبيرة، ٣ أعمدة × ٤ صفوف
            for (row in numpadRows) {
                val rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                rowLayout.gravity = Gravity.CENTER
                for (key in row) {
                    val keyBtn = makeKey(key) { onKeyPress(key) }
                    keyBtn.textSize = 22f
                    val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    params.setMargins(6, 6, 6, 6)
                    keyBtn.layoutParams = params
                    rowLayout.addView(keyBtn)
                }
                val rowParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
                rowLayout.layoutParams = rowParams
                keysContainer.addView(rowLayout)
            }
            // ارتفاع ثابت مناسب لشكل numpad
            keysContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(220)
            )
        } else {
            keysContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            for (row in lettersRows) {
                val rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                rowLayout.gravity = Gravity.CENTER
                rowLayout.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                for (key in row) {
                    val keyBtn = makeKey(key) { onKeyPress(key) }
                    val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    params.setMargins(2, 2, 2, 2)
                    keyBtn.layoutParams = params
                    rowLayout.addView(keyBtn)
                }
                keysContainer.addView(rowLayout)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun onKeyPress(key: String) {
        val ic = currentInputConnection ?: return
        val isDigit = key.length == 1 && key[0].isDigit()

        if (isDigit && isDuplicateMode) {
            digitBuffer.append(key)
            ic.commitText(key, 1)
            return
        }

        if (!isDigit && digitBuffer.isNotEmpty()) {
            digitBuffer.clear()
        }

        if (!isNumbersLayout && isSplitMode) {
            // تفكيك الكلمة: كل حرف يترافق مع مسافة فوراً
            ic.commitText("$key ", 1)
        } else {
            ic.commitText(key, 1)
        }
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        if (isDuplicateMode && digitBuffer.isNotEmpty()) {
            val num = digitBuffer.toString()
            ic.commitText(" $num ", 1)
            digitBuffer.clear()
        } else {
            ic.commitText(" ", 1)
        }
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (digitBuffer.isNotEmpty()) {
            digitBuffer.deleteCharAt(digitBuffer.length - 1)
        }
        ic.deleteSurroundingText(1, 0)
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        digitBuffer.clear()
        ic.commitText("\n", 1)
    }
}
