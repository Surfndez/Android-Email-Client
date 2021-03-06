package com.criptext.mail.scenes.mailbox.feed.data

import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.dao.FeedItemDao

/**
 * Created by gabriel on 2/21/18.
 */

class MuteFeedItemWorker(private val db: FeedItemDao,
                         private val params: FeedRequest.MuteFeedItem,
                         override val publishFn: (FeedResult.MuteFeedItem) -> Unit)
    : BackgroundWorker<FeedResult.MuteFeedItem> {
    override val canBeParallelized = false

    override fun catchException(ex: Exception): FeedResult.MuteFeedItem {
        val message = "Unexpected error: " + ex.message
        return FeedResult.MuteFeedItem.Failure(message = message, id = params.id,
                lastKnownPosition = params.position, isMuted = !params.isMuted)
    }

    override fun work(reporter: ProgressReporter<FeedResult.MuteFeedItem>): FeedResult.MuteFeedItem? {
        //Disabled for now. Mute functionality is postponed after launch
        //db.toggleMuteFeedItem(params.id, params.isMuted)
        return FeedResult.MuteFeedItem.Success()
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use CRFile | Settings | CRFile Templates.
    }

}