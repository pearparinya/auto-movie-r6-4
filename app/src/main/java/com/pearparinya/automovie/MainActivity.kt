package com.pearparinya.automovie

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(42, 70, 42, 42)
            setBackgroundColor(Color.rgb(248, 249, 252))
        }
        val title = TextView(this).apply {
            text = "AUTO-MOVIE ENGINE 2.0"
            textSize = 26f
            setTextColor(Color.rgb(25, 35, 55))
            gravity = Gravity.CENTER
        }
        val version = TextView(this).apply {
            text = "R6.4 ANDROID PORTABLE"
            textSize = 16f
            setPadding(0, 12, 0, 36)
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "พร้อมทำงาน"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val input = EditText(this).apply {
            hint = "พิมพ์ชื่อเรื่องหรือคำสั่งสร้าง EP"
            minLines = 4
            gravity = Gravity.TOP
        }
        val button = Button(this).apply {
            text = "เริ่มสร้าง EP"
            setOnClickListener {
                val value = input.text.toString().trim()
                status.text = if (value.isEmpty()) "กรุณาใส่ชื่อเรื่องหรือคำสั่ง" else "รับคำสั่งแล้ว: $value\nโหมดฉากมาตรฐาน 8 วินาที"
            }
        }
        val note = TextView(this).apply {
            text = "Portable Runtime • Writer-first • Scene 8s • Flow/Veo 3.1 workflow shell"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        root.addView(title)
        root.addView(version)
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(note)
        setContentView(root)
    }
}
