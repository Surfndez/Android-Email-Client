package com.criptext.mail.push.data

import android.content.res.Resources
import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.EventLocalDB
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.models.Label
import com.criptext.mail.email_preview.EmailPreview
import com.criptext.mail.scenes.mailbox.data.MailboxAPIClient
import com.criptext.mail.scenes.mailbox.data.UpdateBannerData
import com.criptext.mail.signal.SignalClient
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import org.whispersystems.libsignal.DuplicateMessageException
import java.io.File
import com.squareup.picasso.Picasso
import android.graphics.Bitmap
import com.criptext.mail.api.Hosts
import com.criptext.mail.utils.*


/**
 * Created by sebas on 3/22/18.
 */

class UpdateMailboxWorker(
        signalClient: SignalClient,
        private val dbEvents: EventLocalDB,
        activeAccount: ActiveAccount,
        storage: KeyValueStorage,
        private val loadedThreadsCount: Int?,
        private val label: Label,
        private val pushData: Map<String, String>,
        private val shouldPostNotification: Boolean,
        httpClient: HttpClient,
        override val publishFn: (
                PushResult.UpdateMailbox) -> Unit)
    : BackgroundWorker<PushResult.UpdateMailbox> {


    override val canBeParallelized = false

    private val eventHelper = EventHelper(dbEvents, httpClient, storage, activeAccount, signalClient, false)
    private val apiClient = MailboxAPIClient(httpClient, activeAccount.jwt)
    private var shouldCallAgain = false

    override fun catchException(ex: Exception): PushResult.UpdateMailbox {
        val message = createErrorMessage(ex)
        return PushResult.UpdateMailbox.Failure(label, message, ex, pushData, shouldPostNotification)
    }

    private fun processFailure(failure: Result.Failure<EventHelperResultData,
            Exception>): PushResult.UpdateMailbox {
        return if (failure.error is EventHelper.NothingNewException)
            PushResult.UpdateMailbox.Success(
                    mailboxLabel = label,
                    isManual = true,
                    mailboxThreads = null,
                    shouldPostNotification = shouldPostNotification,
                    pushData = pushData,
                    senderImage = null)
        else
            PushResult.UpdateMailbox.Failure(
                    mailboxLabel = label,
                    message = createErrorMessage(failure.error),
                    exception = failure.error,
                    pushData = pushData,
                    shouldPostNotification = shouldPostNotification)
    }

    override fun work(reporter: ProgressReporter<PushResult.UpdateMailbox>)
            : PushResult.UpdateMailbox? {
        eventHelper.setupForMailbox(label, loadedThreadsCount)
        val requestEvents = EventLoader.getEvents(apiClient)
        shouldCallAgain = (requestEvents as? Result.Success)?.value?.second ?: false
        val operationResult = requestEvents
                .flatMap(eventHelper.processEvents)

        val newData = mutableMapOf<String, String>()
        newData.putAll(pushData)


        return when(operationResult) {
            is Result.Success -> {
                val metadataKey = newData["metadataKey"]?.toLong()
                if(metadataKey != null) {
                    val email = dbEvents.getEmailByMetadataKey(metadataKey)
                    if(email != null){
                        val files = dbEvents.getFullEmailById(emailId = email.id)!!.files
                        newData["preview"] = email.preview
                        newData["subject"] = email.subject
                        newData["hasInlineImages"] = (files.firstOrNull { it.cid != null }  != null).toString()
                        newData["name"] = dbEvents.getFromContactByEmailId(email.id)[0].name
                        newData["email"] = dbEvents.getFromContactByEmailId(email.id)[0].email
                        val emailAddress = newData["email"]
                        val bm = try {
                            if(emailAddress != null && EmailAddressUtils.isFromCriptextDomain(emailAddress))
                                Picasso.get().load(Hosts.restApiBaseUrl
                                        .plus("/user/avatar/${EmailAddressUtils.extractRecipientIdFromCriptextAddress(emailAddress)}")).get()
                            else
                                null
                        } catch (ex: Exception){
                            null
                        }

                        if(shouldCallAgain) {
                            callAgainResult(operationResult.value.emailPreviews, newData, bm)
                        }else{
                            PushResult.UpdateMailbox.Success(
                                    mailboxLabel = label,
                                    isManual = true,
                                    mailboxThreads = operationResult.value.emailPreviews,
                                    pushData = newData,
                                    shouldPostNotification = shouldPostNotification,
                                    senderImage = bm
                            )
                        }
                    }else{
                        PushResult.UpdateMailbox.Failure(
                                mailboxLabel = label,
                                message = createErrorMessage(Resources.NotFoundException()),
                                exception = Resources.NotFoundException(),
                                pushData = pushData,
                                shouldPostNotification = shouldPostNotification)
                    }
                }else {
                    if(shouldCallAgain) {
                        callAgainResult(operationResult.value.emailPreviews, newData, null)
                    } else {
                        PushResult.UpdateMailbox.Failure(
                                mailboxLabel = label,
                                message = createErrorMessage(Resources.NotFoundException()),
                                exception = Resources.NotFoundException(),
                                pushData = pushData,
                                shouldPostNotification = shouldPostNotification)
                    }
                }
            }

            is Result.Failure -> processFailure(operationResult)
        }
    }

    private fun callAgainResult(emailPreviews: List<EmailPreview>,
                                newData: Map<String, String>, bm: Bitmap?): PushResult.UpdateMailbox? {
        return PushResult.UpdateMailbox.SuccessAndRepeat(
                mailboxLabel = label,
                isManual = true,
                mailboxThreads = emailPreviews,
                pushData = newData,
                shouldPostNotification = shouldPostNotification,
                senderImage = bm
        )
    }

    override fun cancel() {
        TODO("CANCEL IS NOT IMPLEMENTED")
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        when(ex) {
            is DuplicateMessageException ->
                UIMessage(resId = R.string.email_already_decrypted)
            else -> {
                UIMessage(resId = R.string.failed_getting_emails)
            }
        }
    }
}
