package com.email.scenes.mailbox.data

import com.email.R
import com.email.SecureEmail
import com.email.api.HttpClient
import com.email.api.HttpErrorHandlingHelper
import com.email.bgworker.BackgroundWorker
import com.email.bgworker.ProgressReporter
import com.email.db.MailboxLocalDB
import com.email.db.models.ActiveAccount
import com.email.db.models.EmailLabel
import com.email.db.models.Label
import com.email.scenes.label_chooser.SelectedLabels
import com.email.scenes.label_chooser.data.LabelWrapper
import com.email.utils.UIMessage
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError

/**
 * Created by sebas on 04/05/18.
 */

class MoveEmailThreadWorker(
        private val db: MailboxLocalDB,
        private val chosenLabel: String?,
        private val selectedThreadIds: List<String>,
        private val currentLabel: Label,
        httpClient: HttpClient,
        activeAccount: ActiveAccount,
        override val publishFn: (
                MailboxResult.MoveEmailThread) -> Unit)
    : BackgroundWorker<MailboxResult.MoveEmailThread> {

    private val apiClient = MailboxAPIClient(httpClient, activeAccount.jwt)

    private val defaultItems = Label.DefaultItems()
    override val canBeParallelized = false

    override fun catchException(ex: Exception): MailboxResult.MoveEmailThread {

        val message = createErrorMessage(ex)
        return MailboxResult.MoveEmailThread.Failure(
                message = message,
                exception = ex)
    }

    private fun getLabelEmailRelationsFromEmailIds(emailIds: List<Long>, label: String): List<EmailLabel> {
        val selectedLabels = SelectedLabels()
        selectedLabels.add(LabelWrapper(db.getLabelByName(label)))


        return emailIds.flatMap{ emailId ->
                selectedLabels.toIDs().map{ labelId ->
                    EmailLabel(emailId = emailId, labelId = labelId)
                }
            }
    }

    override fun work(reporter: ProgressReporter<MailboxResult.MoveEmailThread>)
            : MailboxResult.MoveEmailThread? {

        if(chosenLabel == null){
            //It means the threads will be deleted permanently
            val result = Result.of {
                apiClient.postThreadDeletedPermanentlyEvent(selectedThreadIds).contains("OK") }
            return when (result) {
                is Result.Success -> {
                    db.deleteThreads(threadIds = selectedThreadIds)
                    MailboxResult.MoveEmailThread.Success()
                }
                is Result.Failure -> {
                    catchException(result.error)
                }
            }
        }

        val rejectedLabels = defaultItems.rejectedLabelsByMailbox(currentLabel).map { it.id }
        val emailIds = selectedThreadIds.flatMap { threadId ->
            db.getEmailsByThreadId(threadId, rejectedLabels).map { it.id }
        }

        val selectedLabels = SelectedLabels()
        selectedLabels.add(LabelWrapper(db.getLabelByName(chosenLabel)))

        val result = Result.of {
            apiClient.postThreadLabelChangedEvent(selectedThreadIds, listOf(currentLabel.text),
                selectedLabels.toList().map { it.text })}
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)

        return when (result) {
            is Result.Success -> {
                if(currentLabel == Label.defaultItems.trash && chosenLabel == SecureEmail.LABEL_SPAM){
                    //Mark as spam from trash
                    db.deleteRelationByLabelAndEmailIds(labelId = defaultItems.trash.id,
                            emailIds = emailIds)
                }

                val labelEmails = getLabelEmailRelationsFromEmailIds(emailIds, chosenLabel)
                db.createLabelEmailRelations(labelEmails)
                MailboxResult.MoveEmailThread.Success()
            }
            is Result.Failure -> {
                catchException(result.error)
            }
        }
    }

    override fun cancel() {
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        UIMessage(resId = R.string.failed_getting_emails)
    }
}
