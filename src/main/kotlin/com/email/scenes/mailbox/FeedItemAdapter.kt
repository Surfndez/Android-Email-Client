package com.email.scenes.mailbox

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.email.R
import com.email.scenes.mailbox.data.ActivityFeed
import com.email.scenes.mailbox.holders.FeedItemHolder
import com.chauthai.swipereveallayout.ViewBinderHelper
import android.os.Bundle

/**
 * Created by danieltigse on 2/7/18.
 */

class FeedItemAdapter(private val listener: FeedItemHolder.FeedClickListener): RecyclerView.Adapter<FeedItemHolder>() {

    lateinit var feeds : MutableList<ActivityFeed>
    private val viewBinderHelper = ViewBinderHelper()

    init {
        viewBinderHelper.setOpenOnlyOne(true)
    }

    override fun onBindViewHolder(holder: FeedItemHolder?, position: Int) {
        if(holder != null) {
            val feed = feeds[position]
            holder.bindFeed(feed, listener)
            viewBinderHelper.bind(holder.swipeView, feed.feedId.toString())
        }
    }

    override fun getItemCount(): Int {
        return feeds.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedItemHolder {
        val inflatedView = LayoutInflater.from(parent.context).inflate(R.layout.feed_item, parent, false)
        return FeedItemHolder(inflatedView)
    }

}
