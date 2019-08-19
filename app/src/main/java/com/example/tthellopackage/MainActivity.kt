package com.example.tthellopackage

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bt_call.setOnClickListener {
            startActivity(SecondActivity.intent(this, "6446160.vidyocloud.com",
                    "2c5rCt5fKa", "android test", "")
            )
        }
    }
}
