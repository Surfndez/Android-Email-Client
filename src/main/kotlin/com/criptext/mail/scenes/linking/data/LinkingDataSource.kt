package com.criptext.mail.scenes.linking.data

import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.BackgroundWorkManager
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.WorkRunner
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.SettingsLocalDB
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.ActiveAccount

class LinkingDataSource(
        private val accountDao: AccountDao,
        private val storage: KeyValueStorage,
        var activeAccount: ActiveAccount,
        private val httpClient: HttpClient,
        override val runner: WorkRunner)
    : BackgroundWorkManager<LinkingRequest, LinkingResult>(){

    override fun createWorkerFromParams(params: LinkingRequest,
                                        flushResults: (LinkingResult) -> Unit): BackgroundWorker<*> {

        return when(params){
            is LinkingRequest.CheckForKeyBundle -> CheckForKeyBundleWorker(
                    storage = storage,
                    accountDao = accountDao,
                    deviceId = params.deviceId,
                    activeAccount = params.incomingAccount,
                    httpClient = httpClient,
                    publishFn = { result ->
                        flushResults(result) }
            )
        }
    }
}