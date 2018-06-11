package com.email.scenes.emaildetail.data

import com.email.db.MailFolders
import com.email.scenes.label_chooser.SelectedLabels

/**
 * Created by sebas on 3/12/18.
 */

sealed class EmailDetailRequest{

    data class GetSelectedLabels(val threadId: String): EmailDetailRequest()

    class LoadFullEmailsFromThreadId(
            val threadId: String): EmailDetailRequest()

    class UnsendFullEmailFromEmailId(
            val emailId: Long, val position: Int): EmailDetailRequest()

    data class UpdateEmailThreadsLabelsRelations(
            val chosenLabel: MailFolders?,
            val selectedLabels: SelectedLabels?,
            val threadId: String
    ): EmailDetailRequest()

    data class UpdateUnreadStatus(
            val threadId: String,
            val updateUnreadStatus: Boolean
    ): EmailDetailRequest()
}
