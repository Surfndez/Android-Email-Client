package com.criptext.mail.utils

import com.criptext.mail.api.PeerAPIClient
import com.criptext.mail.db.dao.PendingEventDao
import com.criptext.mail.db.models.PendingEvent
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import org.json.JSONArray
import org.json.JSONObject

abstract class PeerQueue{
    abstract fun enqueue(jsonObject: JSONObject)
    abstract fun pick(batchSize: Int = QUEUE_BATCH_SIZE): List<PendingEvent>
    abstract fun dequeue(ids: List<Long>)
    abstract fun dispatchAndDequeue(picks: List<PendingEvent>): Result<Unit, Exception>
    abstract fun isEmpty(): Boolean
    companion object {
        protected const val QUEUE_BATCH_SIZE = 100
    }

    class EventQueue(private val apiClient: PeerAPIClient,
                     private val pendingEventDao: PendingEventDao): PeerQueue(){

        override fun enqueue(jsonObject: JSONObject) {
            val peerEvent = PendingEvent(0, jsonObject.toString())
            pendingEventDao.insert(peerEvent)
            val op = dispatchAndDequeue(pick())
        }

        override fun pick(batchSize: Int): List<PendingEvent> {
            return pendingEventDao.getByBatch(batchSize)
        }

        override fun dequeue(ids: List<Long>) {
            pendingEventDao.deleteByBatch(ids)
        }

        override fun isEmpty(): Boolean {
            return pick().isEmpty()
        }

        override fun dispatchAndDequeue(picks: List<PendingEvent>): Result<Unit, Exception> {
            val array = JSONArray(picks.map { it.data })
            val jsonObj = JSONObject()
            jsonObj.put("peerEvents", array)
            return Result.of { apiClient.postPeerEvents(jsonObj) }
                    .flatMap { _ -> Result.of { dequeue(picks.map { it.id }) } }
        }
    }
}