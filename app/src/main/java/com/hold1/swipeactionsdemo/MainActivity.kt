package com.hold1.swipeactionsdemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeFeed.layoutManager = LinearLayoutManager(this)
        swipeFeed.adapter = SwipeActionsAdapter()

        magicButton.setOnClickListener { Toast.makeText(this, "that was fucking magic",Toast.LENGTH_SHORT).show() }
        contentView.setOnClickListener { Toast.makeText(this, "content magic wow",Toast.LENGTH_SHORT).show() }

    }
}
