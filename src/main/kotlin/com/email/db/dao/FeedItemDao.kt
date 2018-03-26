package com.email.db.dao

import android.arch.persistence.room.*
import com.email.db.models.FeedItem

/**
 * Created by danieltigse on 2/7/18.
 */

@Dao
interface FeedDao {
    @Insert
    fun insertAll(feedItems: List<FeedItem>)

    @Query("SELECT * FROM feedItem")
    fun getAll() : List<FeedItem>

    @Delete
    fun deleteAll(feedItems: List<FeedItem>)

    @Query("""UPDATE feedItem
            SET isMuted = :isMuted
            WHERE id=:id""")
    fun toggleMute(id: Int, isMuted: Boolean)

    @Query("""DELETE FROM feedItem
           WHERE id=:id""")
    fun delete(id: Int)
}