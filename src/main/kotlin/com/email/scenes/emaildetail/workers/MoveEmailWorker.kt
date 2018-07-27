package com.email.scenes.emaildetail.workers

import com.email.R
import com.email.SecureEmail
import com.email.api.HttpClient
import com.email.api.HttpErrorHandlingHelper
import com.email.bgworker.BackgroundWorker
import com.email.bgworker.ProgressReporter
import com.email.db.EmailDetailLocalDB
import com.email.db.dao.EmailDao
import com.email.db.models.ActiveAccount
import com.email.db.models.EmailLabel
import com.email.db.models.Label
import com.email.scenes.emaildetail.data.EmailDetailAPIClient
import com.email.scenes.emaildetail.data.EmailDetailResult
import com.email.scenes.label_chooser.SelectedLabels
import com.email.scenes.label_chooser.data.LabelWrapper
import com.email.utils.UIMessage
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError

/**
 * Created by danieltigse on 5/6/18.
 */

class MoveEmailWorker(
        private val db: EmailDetailLocalDB,
        private val emailDao: EmailDao,
        private val chosenLabel: String?,
        private val emailId: Long,
        private val currentLabel: Label,
        httpClient: HttpClient,
        activeAccount: ActiveAccount,
        override val publishFn: (
                EmailDetailResult.MoveEmailThread) -> Unit)
    : BackgroundWorker<EmailDetailResult.MoveEmailThread> {

    private val apiClient = EmailDetailAPIClient(httpClient, activeAccount.jwt)

    override val canBeParallelized = false

    override fun catchException(ex: Exception): EmailDetailResult.MoveEmailThread {

        val message = createErrorMessage(ex)
        return EmailDetailResult.MoveEmailThread.Failure(
                message = message,
                exception = ex)
    }

    override fun work(reporter: ProgressReporter<EmailDetailResult.MoveEmailThread>): EmailDetailResult.MoveEmailThread? {

        val emailIds = listOf(emailId)

        if(chosenLabel == null){
            //It means the email will be deleted permanently
            val result = Result.of {apiClient.postEmailDeleteEvent(
                    emailDao.getAllEmailsbyId(emailIds).map { it.metadataKey })}
                    .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
            return when (result) {
                is Result.Success -> {
                    db.deleteEmail(emailId)
                    EmailDetailResult.MoveEmailThread.Success(null)
                }
                is Result.Failure -> {
                    val message = createErrorMessage(result.error)
                    EmailDetailResult.MoveEmailThread.Failure(message = message, exception = result.error)
                }
            }
        }

        val selectedLabels = SelectedLabels()
        selectedLabels.add(LabelWrapper(db.getLabelByName(chosenLabel)))

        val result =  Result.of{apiClient.postEmailLabelChangedEvent(
                emailDao.getAllEmailsbyId(emailIds).map { it.metadataKey },
                listOf(currentLabel.text), selectedLabels.toList().map { it.text }).contains("OK")}

        return when (result) {
            is Result.Success -> {
                if(currentLabel == Label.defaultItems.trash && chosenLabel == SecureEmail.LABEL_SPAM){
                    //Mark as spam from trash
                    db.deleteRelationByLabelAndEmailIds(labelId = Label.defaultItems.trash.id,
                            emailIds = emailIds)
                }

                val emailLabels = arrayListOf<EmailLabel>()
                emailIds.flatMap{ emailId ->
                    selectedLabels.toIDs().map{ labelId ->
                        emailLabels.add(EmailLabel(
                                emailId = emailId,
                                labelId = labelId))
                    }
                }
                db.createLabelEmailRelations(emailLabels)

                EmailDetailResult.MoveEmailThread.Success(null)
            }
            is Result.Failure -> {
                val message = createErrorMessage(result.error)
                EmailDetailResult.MoveEmailThread.Failure(message = message, exception = result.error)
            }
        }

    }

    override fun cancel() {
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        UIMessage(resId = R.string.failed_getting_emails)
    }
}
