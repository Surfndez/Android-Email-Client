package com.criptext.mail.scenes.settings.pinlock

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import com.criptext.mail.BaseActivity
import com.criptext.mail.ExternalActivityParams
import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.EventLocalDB
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.signal.SignalClient
import com.criptext.mail.signal.SignalStoreCriptext
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource
import com.criptext.mail.websocket.WebSocketSingleton


class PinLockActivity: BaseActivity(){

    override val layoutId = R.layout.activity_pin
    override val toolbarId = R.id.mailbox_toolbar

    override fun initController(receivedModel: Any, savedInstanceState: Bundle?): SceneController {
        val model = receivedModel as PinLockModel
        val view = findViewById<ViewGroup>(R.id.main_content)
        val scene = PinLockScene.Default(view)
        val appDB = AppDatabase.getAppDatabase(this)
        val activeAccount = ActiveAccount.loadFromStorage(this)!!
        val signalClient = SignalClient.Default(SignalStoreCriptext(appDB, activeAccount))
        val storage = KeyValueStorage.SharedPrefs(this)

        val jwts = storage.getString(KeyValueStorage.StringKey.JWTS, "")
        val webSocketEvents = if(jwts.isNotEmpty())
            WebSocketSingleton.getInstance(jwts)
        else
            WebSocketSingleton.getInstance(activeAccount.jwt)

        val generalDataSource = GeneralDataSource(
                signalClient = signalClient,
                eventLocalDB = EventLocalDB(appDB, this.filesDir, this.cacheDir),
                storage = storage,
                db = appDB,
                runner = AsyncTaskWorkRunner(),
                activeAccount = activeAccount,
                httpClient = HttpClient.Default(),
                filesDir = this.filesDir
        )
        return PinLockController(
                activeAccount = activeAccount,
                model = model,
                scene = scene,
                storage = storage,
                websocketEvents = webSocketEvents,
                generalDataSource = generalDataSource,
                keyboardManager = KeyboardManager(this),
                host = this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ExternalActivityParams.PIN_REQUEST_CODE -> {
                when(resultCode){
                    PIN_RESULT_FALSE -> setActivityMessage(ActivityMessage.ActivatePin(false))
                    PIN_RESULT_SUCCESS -> setActivityMessage(ActivityMessage.ActivatePin(true))
                }

            }
        }
    }

    companion object {
        private const val PIN_RESULT_SUCCESS = -1
        private const val PIN_RESULT_FALSE = 0
    }
}