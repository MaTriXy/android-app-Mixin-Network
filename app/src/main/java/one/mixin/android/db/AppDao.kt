package one.mixin.android.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import one.mixin.android.db.BaseDao.Companion.ESCAPE_SUFFIX
import one.mixin.android.vo.App

@Dao
interface AppDao : BaseDao<App> {

    @Query(
        "SELECT a.* FROM apps a, participants p, users u WHERE p.conversation_id = :conversationId" +
            " AND p.user_id = u.user_id AND a.app_id = u.app_id"
    )
    fun getGroupConversationApp(conversationId: String): LiveData<List<App>>

    @Query("SELECT a.* FROM apps a, users u WHERE u.user_id = :userId AND a.app_id = u.app_id")
    fun getConversationApp(userId: String?): LiveData<List<App>>

    @Query("SELECT * FROM apps WHERE app_id = :id")
    suspend fun findAppById(id: String): App?

    @Query("SELECT * FROM apps WHERE app_id IN(:appIds)")
    fun findAppsByIds(appIds: List<String>): List<App>

    @Query(" SELECT a.* FROM apps a WHERE a.home_uri LIKE :query $ESCAPE_SUFFIX")
    suspend fun searchAppByHost(query: String): List<App>

    @Query("SELECT a.* FROM apps a")
    suspend fun getApps(): List<App>

    @Query("SELECT a.* FROM favorite_apps fa LEFT JOIN apps a ON fa.app_id = a.app_id WHERE fa.user_id =:userId ORDER BY fa.created_at ASC")
    suspend fun getFavoriteAppsByUserId(userId: String): List<App>

    @Query("SELECT a.* FROM apps a WHERE a.app_id NOT IN (SELECT fa.app_id FROM favorite_apps fa)")
    suspend fun getUnfavoriteApps(): List<App>
}
