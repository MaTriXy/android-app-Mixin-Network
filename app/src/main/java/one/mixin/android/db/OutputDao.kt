package one.mixin.android.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import one.mixin.android.ui.home.web3.components.InscriptionState
import one.mixin.android.vo.UtxoItem
import one.mixin.android.vo.safe.Output
import one.mixin.android.vo.safe.SafeInscription

@Dao
interface OutputDao : BaseDao<Output> {
    @Query("SELECT * FROM outputs WHERE state = 'unspent' AND asset = :asset AND (inscription_hash IS NULL OR inscription_hash = '') ORDER BY sequence ASC LIMIT :limit")
    suspend fun findUnspentOutputsByAsset(
        limit: Int,
        asset: String,
    ): List<Output>

    @Query("SELECT * FROM outputs WHERE state = 'unspent' AND asset = :asset AND inscription_hash = :inscriptionHash ORDER BY sequence ASC LIMIT :limit")
    suspend fun findUnspentOutputsByAsset(
        limit: Int,
        asset: String,
        inscriptionHash: String,
    ): List<Output>

    @Query("SELECT * FROM outputs WHERE state = 'unspent' AND asset = :asset AND (inscription_hash IS NULL OR inscription_hash = '') ORDER BY sequence ASC LIMIT :limit OFFSET :offset")
    suspend fun findUnspentOutputsByAssetOffset(
        limit: Int,
        asset: String,
        offset: Int,
    ): List<Output>

    @Query("SELECT output_id FROM outputs WHERE output_id IN (:ids) AND state = 'signed'")
    fun findSignedOutput(ids: List<String>): List<String>

    @Query("SELECT sequence FROM outputs ORDER BY sequence DESC LIMIT 1")
    suspend fun findLatestOutputSequence(): Long?

    @Query("SELECT sequence FROM outputs WHERE asset = :asset ORDER BY sequence DESC LIMIT 1")
    suspend fun findLatestOutputSequenceByAsset(asset: String): Long?

    @Query("UPDATE outputs SET state = 'signed' WHERE output_id IN (:outputIds)")
    fun updateUtxoToSigned(outputIds: List<String>): Int

    @Query("SELECT output_id FROM outputs WHERE state != 'signed' AND output_id IN (:outputIds)")
    fun getUnsignedOutputs(outputIds: List<String>): List<String>

    @Query("SELECT * FROM outputs WHERE asset = :asset ORDER BY created_at DESC, rowid DESC")
    fun utxoItem(asset: String): PagingSource<Int, UtxoItem>

    @Query("DELETE FROM outputs WHERE asset = :asset AND sequence >= :offset")
    suspend fun deleteByKernelAssetIdAndOffset(
        asset: String,
        offset: Long,
    )

    @Query("DELETE FROM outputs WHERE output_id =:outputId")
    suspend fun removeUtxo(outputId: String)

    @Query(
        """
        SELECT i.collection_hash, i.inscription_hash, ic.name, i.sequence, i.content_type, i.content_url, t.icon_url FROM outputs o 
        LEFT JOIN inscription_items i ON i.inscription_hash == o.inscription_hash
        LEFT JOIN inscription_collections ic on ic.collection_hash = i.collection_hash
        LEFT JOIN tokens t on t.collection_hash = i.collection_hash
        WHERE i.inscription_hash IS NOT NULL AND ic.collection_hash IS NOT NULL AND o.state = 'unspent' ORDER BY o.sequence ASC
        """,
    )
    fun inscriptions(): LiveData<List<SafeInscription>>

    // Get the latest inscription, inscription UTXO cannot be separated
    @Query(
        """
        SELECT ic.name, i.sequence, o.amount, t.symbol, t.price_usd, t.icon_url, o.state, i.content_url
        FROM outputs o 
        LEFT JOIN inscription_items i ON i.inscription_hash == o.inscription_hash
        LEFT JOIN inscription_collections ic on ic.collection_hash = i.collection_hash
        LEFT JOIN tokens t on t.collection_hash = i.collection_hash
        WHERE o.inscription_hash = :hash
        ORDER BY o.sequence DESC LIMIT 1
    """,
    )
    fun inscriptionStateByHash(hash: String): LiveData<InscriptionState?>

    @Query(
        """
        SELECT * FROM outputs WHERE inscription_hash = :inscriptionHash AND state = 'unspent'
    """,
    )
    suspend fun findUnspentOutputByHash(inscriptionHash: String): Output?

    // Get the latest inscription, inscription UTXO cannot be separated
    @Query(
        """
        SELECT * FROM outputs WHERE inscription_hash = :inscriptionHash ORDER BY sequence DESC LIMIT 1
    """,
    )
    suspend fun findOutputByHash(inscriptionHash: String): Output?

    @Query(
        """
        SELECT DISTINCT inscription_hash FROM outputs WHERE inscription_hash IS NOT NULL AND state = 'unspent' 
    """,
    )
    suspend fun findUnspentInscriptionHash(): List<String>
}
