package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class P2PChat(
    @PrimaryKey val partnerEmail: String,
    val lastMessage: String,
    val lastUpdated: Long,
    val unreadCount: Int = 0
)

@Entity(tableName = "messages")
data class P2PMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val partnerEmail: String,
    val senderEmail: String,
    val recipientEmail: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastUpdated DESC")
    fun getChatsFlow(): Flow<List<P2PChat>>

    @Query("SELECT * FROM messages WHERE partnerEmail = :partnerEmail ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(partnerEmail: String): Flow<List<P2PMessage>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 100")
    suspend fun getAllRecentMessages(): List<P2PMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: P2PChat)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: P2PMessage)

    @Query("DELETE FROM chats WHERE partnerEmail = :partnerEmail")
    suspend fun deleteChat(partnerEmail: String)

    @Query("DELETE FROM messages WHERE partnerEmail = :partnerEmail")
    suspend fun deleteMessagesForChat(partnerEmail: String)

    @Query("DELETE FROM chats")
    suspend fun clearChats()

    @Query("DELETE FROM messages")
    suspend fun clearMessages()
}

@Database(entities = [P2PChat::class, P2PMessage::class], version = 1, exportSchema = false)
abstract class P2PDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
