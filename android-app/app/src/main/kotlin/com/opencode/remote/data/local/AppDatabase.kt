package com.opencode.remote.data.local

import androidx.room.*
import com.opencode.remote.data.model.*
import kotlinx.serialization.json.Json
import java.util.*

/** 會話記錄實體 */
@Entity(tableName = "sessions",
    indices = [Index(value = ["sessionId"], unique = true), Index(value = ["lastActiveAt"])])
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val name: String,
    val projectPath: String,
    val status: String,
    val createdAt: Long,
    val lastActiveAt: Long,
    val deviceInfoJson: String? = null,
) {
    /** 轉換為 Domain 模型 */
    fun toSessionInfo(): SessionInfo = SessionInfo(
        id = sessionId,
        name = name,
        projectPath = projectPath,
        status = status,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt,
    )
}

/** 指令歷史實體 */
@Entity(tableName = "command_history",
    indices = [Index(value = ["sessionId", "timestamp"])])
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val command: String,
    val argsJson: String,
    val output: String? = null,
    val exitCode: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isComplete: Boolean = true,
)

/** 專案快取實體 */
@Entity(tableName = "projects",
    indices = [Index(value = ["path"], unique = true)])
data class ProjectEntity(
    @PrimaryKey val path: String,
    val name: String,
    val gitBranch: String? = null,
    val gitStatus: String? = null,
    val lastOpened: Long,
    val fileTreeJson: String? = null,
)

/** 配對裝置實體 */
@Entity(tableName = "paired_devices",
    indices = [Index(value = ["clientId"], unique = true)])
data class PairedDeviceEntity(
    @PrimaryKey val clientId: String,
    val name: String,
    val type: String,
    val version: String,
    val platform: String,
    val lastPairedAt: Long,
    val tokenHash: String? = null,
)

/** DAO: 會話 */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)
    
    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): SessionEntity?
    
    @Query("SELECT * FROM sessions ORDER BY lastActiveAt DESC")
    fun getAll(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    fun getByIdFlow(sessionId: String): Flow<SessionEntity?>
    
    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String): Int
    
    @Query("DELETE FROM sessions WHERE lastActiveAt < :cutoff")
    suspend fun deleteOld(cutoff: Long): Int
    
    @Query("UPDATE sessions SET lastActiveAt = :now WHERE sessionId = :sessionId")
    suspend fun touch(sessionId: String, now: Long): Int
}

/** DAO: 指令歷史 */
@Dao
interface CommandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: CommandHistoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(histories: List<CommandHistoryEntity>)
    
    @Query("SELECT * FROM command_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getBySession(sessionId: String, limit: Int, offset: Int): Flow<List<CommandHistoryEntity>>
    
    @Query("SELECT * FROM command_history WHERE sessionId = :sessionId AND command LIKE :query ORDER BY timestamp DESC LIMIT :limit")
    fun searchBySession(sessionId: String, query: String, limit: Int): Flow<List<CommandHistoryEntity>>
    
    @Query("DELETE FROM command_history WHERE sessionId = :sessionId AND timestamp < :cutoff")
    suspend fun deleteOld(sessionId: String, cutoff: Long): Int
    
    @Query("DELETE FROM command_history WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int
}

/** DAO: 專案 */
@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>)
    
    @Query("SELECT * FROM projects ORDER BY lastOpened DESC")
    fun getAll(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM projects WHERE path = :path")
    suspend fun getByPath(path: String): ProjectEntity?
    
    @Query("DELETE FROM projects WHERE path = :path")
    suspend fun delete(path: String): Int
}

/** DAO: 配對裝置 */
@Dao
interface PairedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: PairedDeviceEntity): Long
    
    @Query("SELECT * FROM paired_devices ORDER BY lastPairedAt DESC")
    fun getAll(): Flow<List<PairedDeviceEntity>>
    
    @Query("SELECT * FROM paired_devices WHERE clientId = :clientId")
    suspend fun getById(clientId: String): PairedDeviceEntity?
    
    @Query("DELETE FROM paired_devices WHERE clientId = :clientId")
    suspend fun delete(clientId: String): Int
}

/** 資料庫 */
@Database(
    entities = [
        SessionEntity::class,
        CommandHistoryEntity::class,
        ProjectEntity::class,
        PairedDeviceEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencode_database"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

/** Type Converters */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    
    @TypeConverter
    fun fromJson(json: String?): Map<String, Any>? = json?.let { json.decodeFromString<Map<String, Any>>(it) }
    
    @TypeConverter
    fun toJson(map: Map<String, Any>?): String? = map?.let { json.encodeToString(it) }
    
    @TypeConverter
    fun listFromJson(json: String?): List<String>? = json?.let { json.decodeFromString<List<String>>(it) }
    
    @TypeConverter
    fun listToJson(list: List<String>?): String? = list?.let { json.encodeToString(it) }
}