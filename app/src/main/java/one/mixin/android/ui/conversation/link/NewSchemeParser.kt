package one.mixin.android.ui.conversation.link

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.response.PaymentStatus
import one.mixin.android.extension.isUUID
import one.mixin.android.extension.nowInUtc
import one.mixin.android.pay.parseExternalTransferUri
import one.mixin.android.session.Session
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.common.UtxoConsolidationBottomSheetDialogFragment
import one.mixin.android.ui.common.WaitingBottomSheetDialogFragment
import one.mixin.android.ui.common.biometric.AddressTransferBiometricItem
import one.mixin.android.ui.common.biometric.AssetBiometricItem
import one.mixin.android.ui.common.biometric.TransferBiometricItem
import one.mixin.android.ui.common.biometric.WithdrawBiometricItem
import one.mixin.android.ui.common.biometric.buildAddressBiometricItem
import one.mixin.android.ui.common.biometric.buildTransferBiometricItem
import one.mixin.android.ui.conversation.TransferFragment
import one.mixin.android.ui.wallet.NetworkFee
import one.mixin.android.ui.wallet.transfer.TransferBottomSheetDialogFragment
import one.mixin.android.util.ErrorHandler
import one.mixin.android.vo.Address
import one.mixin.android.vo.MixAddressPrefix
import one.mixin.android.vo.safe.TokenItem
import one.mixin.android.vo.toMixAddress
import one.mixin.android.vo.toUser
import java.io.UnsupportedEncodingException
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class PayType {
    Uuid,
    XinAddress,
    MixAddress,
}

class NewSchemeParser(
    private val bottomSheet: LinkBottomSheetDialogFragment,
) {
    companion object {
        const val INSUFFICIENT_BALANCE = -1
        const val FAILURE = 0
        const val SUCCESS = 1
    }
    private val linkViewModel = bottomSheet.linkViewModel
    
    suspend fun parse(
        text: String,
        from: Int,
    ): Int {
        val uri = text.toUri()
        val lastPath = uri.lastPathSegment ?: return FAILURE

        val payType =
            if (lastPath.isUUID()) {
                PayType.Uuid
            } else if (lastPath.startsWith("XIN")) {
                PayType.XinAddress
            } else if (lastPath.startsWith(MixAddressPrefix)) {
                PayType.MixAddress
            } else {
                return FAILURE
            }
        val asset = uri.getQueryParameter("asset")
        if (asset != null && !asset.isUUID()) {
            return FAILURE
        }
        var amount = uri.getQueryParameter("amount")
        if (amount != null) {
            val a = amount.toBigDecimalOrNull()
            if (a == null || a <= BigDecimal.ZERO) {
                return FAILURE
            }
            amount = a.stripTrailingZeros().toPlainString()
        }
        val memo =
            uri.getQueryParameter("memo")?.run {
                Uri.decode(this)
            }
        val trace = uri.getQueryParameter("trace")
        if (trace != null && !trace.isUUID()) {
            return FAILURE
        }
        val returnTo =
            uri.getQueryParameter("return_to")?.run {
                if (from == LinkBottomSheetDialogFragment.FROM_EXTERNAL) {
                    try {
                        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
                    } catch (e: UnsupportedEncodingException) {
                        this
                    }
                } else {
                    null
                }
            }

        val traceId = trace ?: UUID.randomUUID().toString()
        if (asset != null && amount != null) {
            val status = getPaymentStatus(traceId) ?: return FAILURE
            val token: TokenItem = checkToken(asset) ?: return FAILURE // TODO 404?

            val tokensExtra = linkViewModel.findTokensExtra(asset)
            if (tokensExtra == null) {
                return INSUFFICIENT_BALANCE
            } else if (BigDecimal(tokensExtra.balance ?: "0") < BigDecimal(amount)) {
                return INSUFFICIENT_BALANCE
            }

            if (payType == PayType.Uuid) {
                val user = linkViewModel.refreshUser(lastPath) ?: return FAILURE

                val biometricItem = TransferBiometricItem(listOf(user), 1, traceId, token, amount, memo, status, null, returnTo)
                checkRawTransaction(biometricItem)
            } else if (payType == PayType.MixAddress) {
                val mixAddress = lastPath.toMixAddress() ?: return FAILURE
                if (mixAddress.uuidMembers.isNotEmpty()) {
                    val users = linkViewModel.findOrRefreshUsers(mixAddress.uuidMembers)
                    if (users.isEmpty() || users.size < mixAddress.uuidMembers.size) {
                        return FAILURE
                    }
                    val biometricItem = TransferBiometricItem(users, mixAddress.threshold, traceId, token, amount, memo, status, null, returnTo)
                    checkRawTransaction(biometricItem)
                } else if (mixAddress.xinMembers.isNotEmpty()) {
                    val addressTransferBiometricItem = AddressTransferBiometricItem(mixAddress.xinMembers.first().string(), traceId, token, amount, memo, status, returnTo)
                    checkRawTransaction(addressTransferBiometricItem)
                } else {
                    return FAILURE
                }
            } else {
                // TODO verify address?
                val addressTransferBiometricItem = AddressTransferBiometricItem(lastPath, traceId, token, amount, memo, status, returnTo)
                checkRawTransaction(addressTransferBiometricItem)
            }
        } else {
            val token: TokenItem? =
                if (asset != null) {
                    checkToken(asset) ?: return FAILURE // TODO 404?
                } else {
                    null
                }
            val transferFragment: TransferFragment? =
                if (payType == PayType.Uuid) {
                    val user = linkViewModel.refreshUser(lastPath) ?: return FAILURE // TODO 404?
                    TransferFragment.newInstance(buildTransferBiometricItem(user, token, amount ?: "", traceId, memo, returnTo))
                } else if (payType == PayType.MixAddress) {
                    val mixAddress = lastPath.toMixAddress()
                    val members = mixAddress?.uuidMembers
                    if (!members.isNullOrEmpty()) {
                        if (members.size == 1) {
                            val user = linkViewModel.refreshUser(members.first()) ?: return FAILURE // TODO 404?
                            TransferFragment.newInstance(buildTransferBiometricItem(user, token, amount ?: "", traceId, memo, returnTo))
                        } else {
                            val users = linkViewModel.findOrRefreshUsers(members)
                            if (users.isEmpty() || users.size < members.size) {
                                return FAILURE
                            }
                            val item = TransferBiometricItem(users, mixAddress.threshold, traceId, token, amount ?: "", memo, PaymentStatus.pending.name, null, returnTo)
                            TransferFragment.newInstance(item)
                        }
                    } else if (mixAddress?.xinMembers?.size == 1) { // TODO Support for multiple address
                        TransferFragment.newInstance(buildAddressBiometricItem(mixAddress.xinMembers.first().string(), traceId, token, amount ?: "", memo, returnTo, from))
                    } else {
                        null
                    }
                } else {
                    TransferFragment.newInstance(buildAddressBiometricItem(lastPath, traceId, token, amount ?: "", memo, returnTo, from))
                }
            if (transferFragment == null) return FAILURE
            transferFragment.show(bottomSheet.parentFragmentManager, TransferFragment.TAG)
        }
        return SUCCESS
    }

    suspend fun parseExternalTransferUrl(url: String) {
        var errorMsg: String? = null
        val result =
            parseExternalTransferUri(url, { assetId, destination ->
                handleMixinResponse(
                    invokeNetwork = {
                        linkViewModel.validateExternalAddress(assetId, destination, null)
                    },
                    successBlock = {
                        return@handleMixinResponse it.data
                    },
                )
            }, { assetId, destination ->
                handleMixinResponse(
                    invokeNetwork = {
                        linkViewModel.getFees(assetId, destination)
                    },
                    successBlock = {
                        return@handleMixinResponse it.data
                    },
                )
            }, { assetKey ->
                val assetId = linkViewModel.findAssetIdByAssetKey(assetKey)
                if (assetId == null) {
                    errorMsg = bottomSheet.getString(R.string.external_pay_no_asset_found)
                }
                return@parseExternalTransferUri assetId
            }, { assetId ->
                handleMixinResponse(
                    invokeNetwork = {
                        linkViewModel.getAssetPrecisionById(assetId)
                    },
                    successBlock = {
                        return@handleMixinResponse it.data
                    },
                )
            })

        errorMsg?.let {
            bottomSheet.showError(it)
            return
        }

        if (result == null) {
            QrScanBottomSheetDialogFragment.newInstance(url)
                .show(bottomSheet.parentFragmentManager, QrScanBottomSheetDialogFragment.TAG)
        } else {
            val asset = checkToken(result.assetId)
            if (asset == null) {
                bottomSheet.showError(R.string.Asset_not_found)
                bottomSheet.dismiss()
                return
            }
            val feeAsset = checkToken(result.feeAssetId!!)
            if (feeAsset == null) {
                bottomSheet.showError(R.string.Asset_not_found)
                bottomSheet.dismiss()
                return
            }

            val traceId = UUID.randomUUID().toString()
            val status = getPaymentStatus(traceId)
            if (status == null) {
                bottomSheet.showError()
                bottomSheet.dismiss()
                return
            }
            val amount = result.amount
            val destination = result.destination

            val address = Address("", "address", asset.assetId, destination, "ExternalAddress", nowInUtc(), "0", result.fee?.toPlainString() ?: "", null, null, asset.chainId)
            val fee = NetworkFee(feeAsset, result.fee!!.toPlainString())
            val withdrawBiometricItem = WithdrawBiometricItem(address, fee, null, traceId, asset, amount, result.memo, status, null)
            checkRawTransaction(withdrawBiometricItem)
        }
        bottomSheet.dismiss()
    }

    private suspend fun checkRawTransaction(biometricItem: AssetBiometricItem) {
        val rawTransaction = linkViewModel.firstUnspentTransaction()
        if (rawTransaction != null) {
            WaitingBottomSheetDialogFragment.newInstance().showNow(bottomSheet.parentFragmentManager, WaitingBottomSheetDialogFragment.TAG)
        } else {
            checkUtxo(biometricItem) {
                linkViewModel.viewModelScope.launch {
                    showPreconditionBottom(biometricItem)
                }
            }
        }
    }

    private suspend fun checkUtxo(
        t: AssetBiometricItem,
        callback: () -> Unit,
    ) {
        val token = t.asset ?: return
        var amount = t.amount
        if (amount.isBlank()) {
            callback.invoke()
        }
        val consolidationAmount = linkViewModel.checkUtxoSufficiency(token.assetId, amount)
        if (consolidationAmount != null) {
            UtxoConsolidationBottomSheetDialogFragment.newInstance(buildTransferBiometricItem(Session.getAccount()!!.toUser(), t.asset, consolidationAmount, UUID.randomUUID().toString(), null, null))
                .show(bottomSheet.parentFragmentManager, UtxoConsolidationBottomSheetDialogFragment.TAG)
        } else {
            callback.invoke()
        }
    }

    private suspend fun showPreconditionBottom(biometricItem: AssetBiometricItem) {
        if (biometricItem is TransferBiometricItem && biometricItem.users.size == 1) {
            val pair = linkViewModel.findLatestTrace(biometricItem.users.first().userId, null, null, biometricItem.amount, biometricItem.asset?.assetId ?: "")
            if (pair.second) {
                bottomSheet.showError(bottomSheet.getString(R.string.check_trace_failed))
                return
            }
            biometricItem.trace = pair.first
        }
        bottomSheet.syncUtxo()
        val bottom = TransferBottomSheetDialogFragment.newInstance(biometricItem)
        bottom.show(bottomSheet.parentFragmentManager, TransferBottomSheetDialogFragment.TAG)
        bottomSheet.dismiss()
    }

    private suspend fun checkToken(assetId: String): TokenItem? {
        var asset = linkViewModel.findAssetItemById(assetId)
        if (asset == null) {
            asset = linkViewModel.refreshAsset(assetId)
        }
        if (asset != null && asset.assetId != asset.chainId && linkViewModel.findAssetItemById(asset.chainId) == null) {
            linkViewModel.refreshAsset(asset.chainId)
        }
        return linkViewModel.findAssetItemById(assetId)
    }

    private suspend fun getPaymentStatus(traceId: String): String? {
        var isTraceNotFound = false
        val tx =
            handleMixinResponse(
                invokeNetwork = { linkViewModel.getTransactionsById(traceId) },
                successBlock = { r -> r.data },
                failureBlock = {
                    isTraceNotFound = it.errorCode == ErrorHandler.NOT_FOUND
                    return@handleMixinResponse isTraceNotFound
                },
            )
        return if (isTraceNotFound) {
            PaymentStatus.pending.name
        } else if (tx != null) {
            PaymentStatus.paid.name
        } else {
            null
        }
    }
}
