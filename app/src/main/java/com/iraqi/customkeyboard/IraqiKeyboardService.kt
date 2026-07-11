package com.iraqi.customkeyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

class IraqiKeyboardService : InputMethodService() {

    private var isSplitMode = false      // تفكيك الكلمة: ب ي ت
    private var isWordDupMode = false    // تكرار الكلمة: بيت بيت
    private var isDuplicateMode = false  // تدبيل الأرقام
    private var duplicateCount = 2       // عدد مرات تكرار الرقم (٢-٥)
    private var isNumbersLayout = false

    private val digitBuffer = StringBuilder()
    private val wordBuffer = StringBuilder()

    private val autoHandler = Handler(Looper.getMainLooper())
    private val autoDuplicateRunnable = Runnable { performNumberDuplicate() }
    private val AUTO_DELAY_MS = 500L // نص ثانية بعد آخر رقم تكتبه

    private lateinit var keysContainer: LinearLayout
    private lateinit var splitToggleBtn: Button
    private lateinit var wordDupToggleBtn: Button
    private lateinit var dupToggleBtn: Button
    private lateinit var dupCountBtn: Button
    private lateinit var switchLayoutBtn: Button

    // لوحة الحروف - ٣ صفوف (بترتيب الكيبورد العربي الصحيح، من اليمين لليسار)
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
        // نفرض اتجاه ثابت (يسار->يمين) على كل الكيبورد عشان نتحكم بترتيب
        // كل حرف ورقم بنفسنا يدوياً، بدون ما يتدخل النظام ويقلب الترتيب تلقائياً
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR

        // تعطيل Force Dark (خاصية أندرويد اللي تسبب اختفاء ألوان النصوص ببعض الأجهزة مثل سامسونج)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root.isForceDarkAllowed = false
        }

        // صف الخيارات الأول: تفكيك الكلمة / تكرار الكلمة
        val toggleRow1 = LinearLayout(this)
        toggleRow1.orientation = LinearLayout.HORIZONTAL
        toggleRow1.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        splitToggleBtn = makeToggleButton("تفكيك الكلمة") {
            isSplitMode = !isSplitMode
            if (isSplitMode) isWordDupMode = false
            updateToggleAppearance()
        }
        wordDupToggleBtn = makeToggleButton("تكرار الكلمة") {
            isWordDupMode = !isWordDupMode
            if (isWordDupMode) isSplitMode = false
            wordBuffer.clear()
            updateToggleAppearance()
        }
        toggleRow1.addView(splitToggleBtn)
        toggleRow1.addView(wordDupToggleBtn)
        root.addView(toggleRow1)

        // صف الخيارات الثاني: تدبيل الأرقام + عدد التكرار
        val toggleRow2 = LinearLayout(this)
        toggleRow2.orientation = LinearLayout.HORIZONTAL
        toggleRow2.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dupToggleBtn = makeToggleButton("تدبيل") {
            isDuplicateMode = !isDuplicateMode
            digitBuffer.clear()
            updateToggleAppearance()
        }
        dupCountBtn = makeToggleButton("×$duplicateCount") {
            duplicateCount = if (duplicateCount >= 5) 2 else duplicateCount + 1
            dupCountBtn.text = "×$duplicateCount"
        }
        toggleRow2.addView(dupToggleBtn)
        toggleRow2.addView(dupCountBtn)
        root.addView(toggleRow2)

        // منطقة المفاتيح (تتبدل بين حروف وأرقام)
        keysContainer = LinearLayout(this)
        keysContainer.orientation = LinearLayout.VERTICAL
        root.addView(keysContainer)
        buildKeysLayout()

        // الصف السفلي: تبديل اللوحة / مسافة / حذف / انتر (انتر باليمين، أكبر حجم)
        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        bottomRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        switchLayoutBtn = makeKey(if (isNumbersLayout) "أبج" else "123") {
            autoHandler.removeCallbacks(autoDuplicateRunnable)
            isNumbersLayout = !isNumbersLayout
            digitBuffer.clear()
            wordBuffer.clear()
            switchLayoutBtn.text = if (isNumbersLayout) "أبج" else "123"
            buildKeysLayout()
        }

        val spaceBtn = makeKey("مسافة") { onSpace() }
        spaceBtn.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f
        )

        val backspaceBtn = makeKey("⌫") { onBackspace() }

        val enterBtn = makeKey("⏎") { onEnter() }
        enterBtn.textSize = 24f
        enterBtn.setTypeface(null, android.graphics.Typeface.BOLD)
        enterBtn.layoutParams = LinearLayout.LayoutParams(
            0, dpToPx(52), 1.6f
        )

        // ترتيب الصف السفلي: تبديل اللوحة يسار، مسافة، حذف، وانتر أقصى اليمين
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
        b.setTextColor(Color.WHITE)
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        b.setOnClickListener { onClick(it) }
        return b
    }

    private fun makeKey(label: String, onClick: (View) -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 18f
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(Color.parseColor("#3A3A3A"))
        b.setOnClickListener { onClick(it) }
        return b
    }

    private fun updateToggleAppearance() {
        splitToggleBtn.setBackgroundColor(if (isSplitMode) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
        wordDupToggleBtn.setBackgroundColor(if (isWordDupMode) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
        dupToggleBtn.setBackgroundColor(if (isDuplicateMode) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
        dupCountBtn.setBackgroundColor(Color.parseColor("#2979FF"))
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
                // نعكس ترتيب الإضافة عشان أول حرف بالمصفوفة يطلع أقصى اليمين (ترتيب عربي صحيح)
                for (key in row.reversed()) {
                    val keyBtn = makeKey(key) { onKeyPress(key) }
                    keyBtn.textSize = 20f
                    val params = LinearLayout.LayoutParams(0, dpToPx(46), 1f)
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

        if (isNumbersLayout) {
            if (isDigit && isDuplicateMode) {
                digitBuffer.append(key)
                ic.commitText(key, 1)
                // نلغي أي مؤقت سابق ونبدأ عد تنازلي جديد - إذا ما كتب رقم ثاني خلال نص ثانية، يدبلها لحاله
                autoHandler.removeCallbacks(autoDuplicateRunnable)
                autoHandler.postDelayed(autoDuplicateRunnable, AUTO_DELAY_MS)
                return
            }
            ic.commitText(key, 1)
            return
        }

        // وضع الحروف
        if (isSplitMode) {
            // تفكيك الكلمة: كل حرف يترافق مع مسافة فوراً -> ب ي ت
            ic.commitText("$key ", 1)
            return
        }

        if (isWordDupMode) {
            wordBuffer.append(key)
        }
        ic.commitText(key, 1)
    }

    // يشتغل تلقائياً بعد AUTO_DELAY_MS من آخر رقم يكتبه المستخدم بوضع التدبيل
    private fun performNumberDuplicate() {
        val ic = currentInputConnection ?: return
        if (digitBuffer.isNotEmpty()) {
            val num = digitBuffer.toString()
            val repeated = (1 until duplicateCount).joinToString("") { " $num" }
            ic.commitText("$repeated ", 1)
            digitBuffer.clear()
        }
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return

        if (isNumbersLayout && isDuplicateMode && digitBuffer.isNotEmpty()) {
            autoHandler.removeCallbacks(autoDuplicateRunnable)
            performNumberDuplicate()
            return
        }

        if (!isNumbersLayout && isWordDupMode && wordBuffer.isNotEmpty()) {
            val w = wordBuffer.toString()
            ic.commitText(" $w ", 1)
            wordBuffer.clear()
            return
        }

        ic.commitText(" ", 1)
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (isNumbersLayout) {
            autoHandler.removeCallbacks(autoDuplicateRunnable)
            if (digitBuffer.isNotEmpty()) digitBuffer.deleteCharAt(digitBuffer.length - 1)
        } else {
            if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
        }
        ic.deleteSurroundingText(1, 0)
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        autoHandler.removeCallbacks(autoDuplicateRunnable)
        digitBuffer.clear()
        wordBuffer.clear()
        ic.commitText("\n", 1)
    }
}
