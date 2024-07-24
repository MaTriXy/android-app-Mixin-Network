package one.mixin.android.ui.home.web3.swap

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.web3.SwapRequest
import one.mixin.android.api.response.Web3Token
import one.mixin.android.api.response.web3.QuoteResponse
import one.mixin.android.api.response.web3.SwapResponse
import one.mixin.android.api.response.web3.SwapToken
import one.mixin.android.api.service.Web3Service
import one.mixin.android.repository.TokenRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.ui.oldwallet.AssetRepository
import one.mixin.android.vo.safe.TokenItem
import javax.inject.Inject

@HiltViewModel
class SwapViewModel
    @Inject
    internal constructor(
        private val assetRepository: AssetRepository,
        private val tokenRepository: TokenRepository,
        private val userRepository: UserRepository,
        private val web3Service: Web3Service,
    ) : ViewModel() {
        suspend fun getBotPublicKey(botId: String) = userRepository.getBotPublicKey(botId)

        suspend fun web3Tokens(source: String): MixinResponse<List<SwapToken>> = assetRepository.web3Tokens(source)

        suspend fun web3Quote(
            inputMint: String,
            outputMint: String,
            amount: String,
            slippage: String,
            source: String,
        ): MixinResponse<QuoteResponse> = assetRepository.web3Quote(inputMint, outputMint, amount, slippage, source)

        suspend fun web3Swap(
            swapRequest: SwapRequest,
        ): MixinResponse<SwapResponse> = assetRepository.web3Swap(swapRequest)

        suspend fun searchTokens(query: String) = assetRepository.searchTokens(query)

        suspend fun web3Tokens(chain: String, address: List<String>? = null): List<Web3Token> {
            return handleMixinResponse(
                invokeNetwork = { web3Service.web3Tokens(chain = chain, addresses = address?.joinToString(",")) },
                successBlock = {
                    return@handleMixinResponse it.data
                },
            ) ?: emptyList()
        }

        suspend fun syncAndFindTokens(assetIds: List<String>): List<TokenItem> =
            tokenRepository.syncAndFindTokens(assetIds)
    }
