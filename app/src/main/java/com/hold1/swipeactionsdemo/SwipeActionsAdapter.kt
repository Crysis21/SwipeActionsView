package com.hold1.swipeactionsdemo

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * Created by Cristian Holdunu on 20/11/2018.
 */
class SwipeActionsAdapter : RecyclerView.Adapter<SwipeActionsAdapter.SwipeViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, position: Int): SwipeViewHolder {
        return SwipeViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.swipe_item, parent, false))
    }

    override fun getItemCount(): Int {
        return 40
    }

    override fun onBindViewHolder(parent: SwipeViewHolder, position: Int) {

    }

    class SwipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }

}