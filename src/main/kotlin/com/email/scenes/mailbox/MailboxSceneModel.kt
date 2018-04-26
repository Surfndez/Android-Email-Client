package com.email.scenes.mailbox

import com.email.db.models.Label
import com.email.scenes.SceneModel
import com.email.scenes.composer.data.ComposerInputData
import com.email.scenes.mailbox.data.EmailThread
import com.email.scenes.mailbox.data.LoadingType
import com.email.scenes.mailbox.feed.FeedModel

/**
 * Created by sebas on 1/25/18.
 */

class MailboxSceneModel : SceneModel {
    var loadingType = LoadingType.FULL
    var selectedLabel: Label = Label.defaultItems.inbox // default label
    val threads : ArrayList<EmailThread> = ArrayList()
    val selectedThreads = SelectedThreads()
    val hasSelectedUnreadMessages: Boolean
        get() = selectedThreads.hasUnreadThreads
    val feedModel = FeedModel()
    val isInUnreadMode: Boolean
        get() = selectedThreads.isInUnreadMode
    var isInMultiSelect = false
    var hasReachedEnd = true
    var mailToSend: ComposerInputData? = null
}
