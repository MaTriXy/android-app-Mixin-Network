package one.mixin.android.ui.home.web3

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS
import one.mixin.android.MixinApplication
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.AccountUpdateRequest
import one.mixin.android.api.response.PaymentStatus
import one.mixin.android.api.response.Web3Account
import one.mixin.android.api.response.Web3Token
import one.mixin.android.api.response.copy
import one.mixin.android.api.response.getChainFromName
import one.mixin.android.api.response.getChainIdFromName
import one.mixin.android.api.response.isSolToken
import one.mixin.android.api.response.web3.StakeAccount
import one.mixin.android.api.service.Web3Service
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.repository.AccountRepository
import one.mixin.android.repository.TokenRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.tip.wc.SortOrder
import one.mixin.android.tip.wc.WalletConnect
import one.mixin.android.tip.wc.WalletConnectV2
import one.mixin.android.tip.wc.internal.Chain
import one.mixin.android.tip.wc.internal.toTransaction
import one.mixin.android.ui.common.biometric.NftBiometricItem
import one.mixin.android.ui.home.inscription.component.OwnerState
import one.mixin.android.ui.oldwallet.AssetRepository
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.mlkit.firstUrl
import one.mixin.android.vo.Account
import one.mixin.android.vo.ConnectionUI
import one.mixin.android.vo.Dapp
import one.mixin.android.vo.ParticipantSession
import one.mixin.android.vo.User
import one.mixin.android.vo.safe.SafeCollectible
import one.mixin.android.vo.safe.SafeCollection
import one.mixin.android.vo.toMixAddress
import one.mixin.android.web3.ChainType
import one.mixin.android.web3.js.JsSignMessage
import one.mixin.android.web3.js.getSolanaRpc
import org.sol4k.PublicKey
import org.sol4k.VersionedTransaction
import org.sol4k.api.Commitment
import org.sol4k.lamportToSol
import org.web3j.exceptions.MessageDecodingException
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class Web3ViewModel
    @Inject
    internal constructor(
        private val accountRepository: AccountRepository,
        private val userRepository: UserRepository,
        private val assetRepository: AssetRepository,
        private val tokenRepository: TokenRepository,
        private val web3Service: Web3Service,
    ) : ViewModel() {
        fun disconnect(
            version: WalletConnect.Version,
            topic: String,
        ) {
            when (version) {
                WalletConnect.Version.V2 -> {
                    WalletConnectV2.disconnect(topic)
                }

                WalletConnect.Version.TIP -> {}
            }
        }

        fun getLatestActiveSignSessions(): List<ConnectionUI> {
            val v2List =
                WalletConnectV2.getListOfActiveSessions().mapIndexed { index, wcSession ->
                    ConnectionUI(
                        index = index,
                        icon = wcSession.metaData?.icons?.firstOrNull(),
                        name = wcSession.metaData!!.name.takeIf { it.isNotBlank() } ?: "Dapp",
                        uri = wcSession.metaData!!.url.takeIf { it.isNotBlank() } ?: "Not provided",
                        data = wcSession.topic,
                    )
                }
            return v2List
        }

        fun dapps(chainId: String): List<Dapp> {
            val gson = GsonHelper.customGson
            val dapps = MixinApplication.get().defaultSharedPreferences.getString("dapp_$chainId", null)
            return if (dapps == null) {
                emptyList()
            } else {
                gson.fromJson(dapps, Array<Dapp>::class.java).toList()
            }
        }

        suspend inline fun fuzzySearchUrl(query: String?): String? {
            return if (query.isNullOrEmpty()) {
                null
            } else {
                firstUrl(query)
            }
        }

        suspend fun web3Account(chain: String, address: String): MixinResponse<Web3Account> {
            val response =  web3Service.web3Account(address)
            if (response.isSuccess) {
                updateTokens(chain,response.data!!.tokens)
            }
            return response
        }

        suspend fun web3Token(
            chain: String,
            chainId: String,
            address: String,
        ): Web3Token? {
            return web3Token(chain, chainId + address) ?: web3Service.web3Tokens(chainId, addresses = address)
                .let {
                    if (it.isSuccess) {
                        val token = it.data?.firstOrNull() ?: return null
                        updateToken(token, chain)
                        token
                    } else {
                        null
                    }
                }
        }

        fun web3Token(chain: String, tokenId: String): Web3Token? {
            return if (chain == ChainType.ethereum.name) evmTokenMap[tokenId] else solanaTokenMap[tokenId]
        }

        private fun updateTokens(chain: String, tokens: List<Web3Token>) {
            val tokenMap = if (chain == ChainType.ethereum.name) evmTokenMap else solanaTokenMap
            val newTokenIds = tokens.map { "${it.chainId}${it.assetKey}" }.toSet()

            val missingTokenIds = tokenMap.keys - newTokenIds
            missingTokenIds.forEach { tokenId ->
                val token = tokenMap[tokenId]
                if (token != null) {
                    tokenMap[tokenId] = token.copy(balance = "0")
                }
            }

            tokens.forEach { token ->
                val tokenId = "${token.chainId}${token.assetKey}"
                tokenMap[tokenId] = token
            }
        }

        private fun updateToken(token: Web3Token?, chain: String) {
            token?.let {
                val tokenId = "${it.chainId}${it.assetKey}"
                val tokenMap = if (chain == ChainType.ethereum.name) evmTokenMap else solanaTokenMap
                tokenMap[tokenId] = it
            }
        }

        suspend fun web3Transaction(
            address: String,
            chainId: String,
            fungibleId: String,
            assetKey: String,
        ) = web3Service.transactions(address, chainId, fungibleId, assetKey)

        suspend fun saveSession(participantSession: ParticipantSession) {
            userRepository.saveSession(participantSession)
        }

        suspend fun fetchSessionsSuspend(ids: List<String>) = userRepository.fetchSessionsSuspend(ids)

        suspend fun findBotPublicKey(
            conversationId: String,
            botId: String,
        ) = userRepository.findBotPublicKey(conversationId, botId)

        suspend fun findAddres(token: Web3Token): String? {
            return tokenRepository.findDepositEntry(token.getChainIdFromName())?.destination
        }

        suspend fun findAndSyncDepositEntry(token: Web3Token): String? =
            withContext(Dispatchers.IO) {
                tokenRepository.findAndSyncDepositEntry(token.getChainIdFromName()).first?.destination
            }

        suspend fun web3TokenItems(chainIds: List<String>) = tokenRepository.web3TokenItems(chainIds)

        suspend fun getFees(
            id: String,
            destination: String,
        ) = tokenRepository.getFees(id, destination)

        suspend fun findTokensExtra(assetId: String) =
            withContext(Dispatchers.IO) {
                tokenRepository.findTokensExtra(assetId)
            }

        suspend fun syncAsset(assetId: String) =
            withContext(Dispatchers.IO) {
                tokenRepository.syncAsset(assetId)
            }

        fun collectibles(sortOrder: SortOrder): LiveData<List<SafeCollectible>> = tokenRepository.collectibles(sortOrder)

        fun collectiblesByHash(collectionHash: String): LiveData<List<SafeCollectible>> = tokenRepository.collectiblesByHash(collectionHash)

        fun collections(sortOrder: SortOrder): LiveData<List<SafeCollection>> = tokenRepository.collections(sortOrder)

        fun collectionByHash(hash: String): LiveData<SafeCollection?> = tokenRepository.collectionByHash(hash)

        fun inscriptionByHash(hash: String) = tokenRepository.inscriptionByHash(hash)

        suspend fun buildNftTransaction(
            inscriptionHash: String,
            receiver: User,
            release: Boolean = false,
        ): NftBiometricItem? =
            withContext(Dispatchers.IO) {
                val output = tokenRepository.findUnspentOutputByHash(inscriptionHash) ?: return@withContext null
                val inscriptionItem = tokenRepository.findInscriptionByHash(inscriptionHash) ?: return@withContext null
                val inscriptionCollection = tokenRepository.findInscriptionCollectionByHash(inscriptionHash) ?: return@withContext null
                val asset = tokenRepository.findTokenItemByAsset(output.asset) ?: return@withContext null
                val releaseAmount =
                    if (release) {
                        BigDecimal(output.amount).divide(BigDecimal(2), 8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    } else {
                        null
                    }
                return@withContext NftBiometricItem(
                    asset = asset,
                    traceId = UUID.randomUUID().toString(),
                    amount = output.amount,
                    memo = null,
                    state = PaymentStatus.pending.name,
                    receivers = listOf(receiver),
                    reference = null,
                    inscriptionItem = inscriptionItem,
                    inscriptionCollection = inscriptionCollection,
                    releaseAmount = releaseAmount,
                )
            }

        fun inscriptionStateByHash(inscriptionHash: String) = tokenRepository.inscriptionStateByHash(inscriptionHash)

        suspend fun ethGasPrice(chain: Chain) =
            withContext(Dispatchers.IO) {
                WalletConnectV2.ethGasPrice(chain)?.run {
                    try {
                        this.gasPrice
                    } catch (e: MessageDecodingException) {
                        result?.run { Numeric.toBigInt(this) }
                    }
                }
            }

        suspend fun ethGasLimit(
            chain: Chain,
            transaction: Transaction,
        ) = withContext(Dispatchers.IO) {
            WalletConnectV2.ethEstimateGas(chain, transaction)?.run {
                val defaultLimit = if (chain.chainReference == Chain.Ethereum.chainReference) BigInteger.valueOf(4712380L) else null
                convertToGasLimit(this, defaultLimit)
            }
        }

        private fun convertToGasLimit(
            estimate: EthEstimateGas,
            defaultLimit: BigInteger?,
        ): BigInteger? {
            return if (estimate.hasError()) {
                if (estimate.error.code === -32000) // out of gas
                    {
                        defaultLimit
                    } else {
                    BigInteger.ZERO
                }
            } else if (estimate.amountUsed.compareTo(BigInteger.ZERO) > 0) {
                estimate.amountUsed
            } else if (defaultLimit == null || defaultLimit.equals(BigInteger.ZERO)) {
                BigInteger(DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS)
            } else {
                defaultLimit
            }
        }

        suspend fun ethMaxPriorityFeePerGas(chain: Chain) =
            withContext(Dispatchers.IO) {
                WalletConnectV2.ethMaxPriorityFeePerGas(chain)?.run {
                    try {
                        this.maxPriorityFeePerGas
                    } catch (e: MessageDecodingException) {
                        result?.run { Numeric.toBigInt(this) }
                    }
                }
            }

        private suspend fun getSolMinimumBalanceForRentExemption(address: PublicKey): BigDecimal =
            withContext(Dispatchers.IO) {
                try {
                    val conn = getSolanaRpc()
                    val accountInfo = conn.getAccountInfo(address) ?: return@withContext BigDecimal.ZERO
                    val mb = conn.getMinimumBalanceForRentExemption(accountInfo.space)
                    return@withContext lamportToSol(BigDecimal(mb))
                } catch (e: Exception) {
                    return@withContext BigDecimal.ZERO
                }
            }

        suspend fun calcFee(
            token: Web3Token,
            transaction: JsSignMessage,
            fromAddress: String,
        ): BigDecimal? {
            val chain = token.getChainFromName()
            if (chain == Chain.Solana) {
                if (token.isSolToken()) {
                    val tx = VersionedTransaction.from(transaction.data ?: "")
                    val fee = tx.calcFee()
                    val mb = getSolMinimumBalanceForRentExemption(PublicKey(fromAddress))
                    return fee.add(mb)
                } else {
                    return BigDecimal.ZERO
                }
            } else {
                val gasPrice = ethGasPrice(chain) ?: return null
                val gasLimit =
                    ethGasLimit(chain, transaction.wcEthereumTransaction!!.toTransaction())?.run {
                        if (this == BigInteger.ZERO) {
                            this
                        } else {
                            this.multiply(BigInteger.valueOf(16L)).divide(BigInteger.valueOf(10L)) // 1.6 times the data
                        }
                    } ?: return null
                return Convert.fromWei(gasPrice.run { BigDecimal(this) }.multiply(gasLimit.run { BigDecimal(this) }), Convert.Unit.ETHER)
            }
        }

        suspend fun getWeb3Tx(txhash: String) = assetRepository.getWeb3Tx(txhash)

        suspend fun isBlockhashValid(blockhash: String): Boolean =
            withContext(Dispatchers.IO) {
                return@withContext getSolanaRpc().isBlockhashValid(blockhash, Commitment.PROCESSED)
            }

        suspend fun getBotPublicKey(botId: String) = userRepository.getBotPublicKey(botId)

        fun update(request: AccountUpdateRequest): Observable<MixinResponse<Account>> =
            accountRepository.update(request).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

        fun insertUser(user: User) =
            viewModelScope.launch(Dispatchers.IO) {
                userRepository.upsert(user)
            }

        suspend fun getOwner(hash: String): OwnerState {
            try {
                val item = withContext(Dispatchers.IO) { tokenRepository.getInscriptionItem(hash) } ?: return OwnerState()
                if (item.owner != null) {
                    val mixinAddress = item.owner.toMixAddress() ?: return OwnerState()
                    return if (mixinAddress.uuidMembers.isNotEmpty()) {
                        val users = userRepository.findOrRefreshUsers(mixinAddress.uuidMembers)
                        val title = if (mixinAddress.uuidMembers.size > 1) {
                            "(${mixinAddress.threshold}/${mixinAddress.uuidMembers.size})"
                        } else {
                            null
                        }
                        OwnerState(title = title, users = users)
                    } else {
                        OwnerState(owner = item.owner)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
            return OwnerState()
        }

        suspend fun getStakeAccounts(account: String): List<StakeAccount>? {
            return handleMixinResponse(
                invokeNetwork = { assetRepository.getStakeAccounts(account) },
                successBlock = {
                    it.data
                }
            )
        }

        companion object {
            private val evmTokenMap = mutableMapOf<String, Web3Token>()
            private val solanaTokenMap = mutableMapOf<String, Web3Token>()
        }
    }
