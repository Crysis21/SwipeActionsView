package com.hold1.swipeactionsdemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import com.hold1.swipeactionsdemo.R.id.swipeFeed
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeFeed.layoutManager = LinearLayoutManager(this)
        swipeFeed.adapter = SwipeActionsAdapter()
    }
}
