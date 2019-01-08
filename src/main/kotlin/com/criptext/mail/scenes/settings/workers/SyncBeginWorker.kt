package com.criptext.mail.scenes.settings.workers

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.settings.data.SettingsResult
import com.criptext.mail.utils.ServerErrorCodes
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralAPIClient
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.generaldatasource.data.UserDataWriter
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError


class SyncBeginWorker(val httpClient: HttpClient,
                      private val activeAccount: ActiveAccount,
                      private val storage: KeyValueStorage,
                      private val accountDao: AccountDao,
                      override val publishFn: (SettingsResult) -> Unit)
    : BackgroundWorker<SettingsResult.SyncBegin> {

    private val apiClient = GeneralAPIClient(httpClient, activeAccount.jwt)

    override val canBeParallelized = false

    override fun catchException(ex: Exception): SettingsResult.SyncBegin {
        when(ex){
            is ServerErrorException -> {
                when(ex.errorCode){
                    ServerErrorCodes.BadRequest -> return SettingsResult.SyncBegin.NoDevicesAvailable(createErrorMessage(ex))
                }
            }
        }
        return SettingsResult.SyncBegin.Failure(createErrorMessage(ex))
    }

    override fun work(reporter: ProgressReporter<SettingsResult.SyncBegin>): SettingsResult.SyncBegin? {
        val result = workOperation()

        val sessionExpired = HttpErrorHandlingHelper.didFailBecauseInvalidSession(result)

        val finalResult = if(sessionExpired)
            newRetryWithNewSessionOperation()
        else
            result

        return when (finalResult) {
            is Result.Success ->{
                SettingsResult.SyncBegin.Success()
            }
            is Result.Failure -> catchException(finalResult.error)
        }
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use CRFile | Settings | CRFile Templates.
    }

    private fun workOperation() : Result<String, Exception> =
            Result.of { apiClient.postSyncBegin(UserDataWriter.FILE_SYNC_VERSION) }

    private fun newRetryWithNewSessionOperation()
            : Result<String, Exception> {
        val refreshOperation =  HttpErrorHandlingHelper.newRefreshSessionOperation(apiClient, activeAccount, storage, accountDao)
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
        return when(refreshOperation){
            is Result.Success -> {
                val account = ActiveAccount.loadFromStorage(storage)!!
                apiClient.token = account.jwt
                workOperation()
            }
            is Result.Failure -> {
                Result.of { throw refreshOperation.error }
            }
        }
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        when(ex){
            is ServerErrorException -> {
                when(ex.errorCode){
                    ServerErrorCodes.BadRequest -> UIMessage(resId = R.string.no_devices_available)
                    ServerErrorCodes.TooManyRequests -> UIMessage(resId = R.string.too_many_login_attempts)
                    ServerErrorCodes.TooManyDevices -> UIMessage(resId = R.string.too_many_devices)
                    else -> UIMessage(resId = R.string.server_bad_status, args = arrayOf(ex.errorCode))
                }
            }
            else -> UIMessage(resId = R.string.forgot_password_error)
        }
    }

}