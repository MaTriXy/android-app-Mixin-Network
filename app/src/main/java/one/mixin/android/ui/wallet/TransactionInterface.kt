package one.mixin.android.ui.wallet

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_transaction.view.*
import kotlinx.android.synthetic.main.view_badge_circle_image.view.*
import kotlinx.android.synthetic.main.view_title.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.extension.fullDate
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.numberFormat
import one.mixin.android.extension.priceFormat
import one.mixin.android.extension.toast
import one.mixin.android.session.Session
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.Fiats
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType
import org.jetbrains.anko.textColorResource
import java.math.BigDecimal

interface TransactionInterface {
    fun initView(
        fragment: Fragment,
        contentView: View,
        lifecycleScope: CoroutineScope,
        walletViewModel: WalletViewModel,
        assetId: String?,
        snapshotId: String?,
        assetItem: AssetItem?,
        snapshotItem: SnapshotItem?
    ) {
        contentView.title_view.right_animator.visibility = View.GONE
        if (snapshotItem == null || assetItem == null) {
            if (snapshotId != null && assetId != null) {
                lifecycleScope.launch {
                    val asset = walletViewModel.simpleAssetItem(assetId)
                    val snapshot = walletViewModel.snapshotLocal(assetId, snapshotId)
                    if (asset == null || snapshot == null) {
                        fragment.context?.toast(R.string.error_data)
                    } else {
                        updateUI(fragment, contentView, asset, snapshot)
                    }
                }
            } else {
                fragment.toast(R.string.error_data)
            }
        } else {
            updateUI(fragment, contentView, assetItem, snapshotItem)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(fragment: Fragment, contentView: View, asset: AssetItem, snapshot: SnapshotItem) {
        if (!fragment.isAdded) return

        val amountVal = snapshot.amount.toFloatOrNull()
        val isPositive = if (amountVal == null) false else amountVal > 0
        contentView.avatar.bg.loadImage(asset.iconUrl, R.drawable.ic_avatar_place_holder)
        contentView.avatar.badge.loadImage(asset.chainIconUrl, R.drawable.ic_avatar_place_holder)
        contentView.value_tv.text = if (isPositive) "+${snapshot.amount.numberFormat()}"
        else snapshot.amount.numberFormat()
        contentView.symbol_tv.text = asset.symbol
        contentView.value_tv.textColorResource = when {
            snapshot.type == SnapshotType.pending.name -> {
                R.color.wallet_text_gray
            }
            isPositive -> {
                R.color.wallet_green
            }
            else -> {
                R.color.wallet_pink
            }
        }
        val amount = (BigDecimal(snapshot.amount) * asset.priceFiat()).priceFormat()
        contentView.value_as_tv.text = "≈ ${Fiats.getSymbol()}$amount"
        contentView.transaction_id_tv.text = snapshot.snapshotId
        contentView.transaction_type_tv.text = getSnapshotType(fragment, snapshot.type)
        contentView.memo_tv.text = snapshot.memo
        contentView.date_tv.text = snapshot.createdAt.fullDate()
        when (snapshot.type) {
            SnapshotType.deposit.name -> {
                contentView.sender_title.text = fragment.getString(R.string.sender)
                contentView.sender_tv.text = snapshot.sender
                contentView.receiver_title.text = fragment.getString(R.string.transaction_hash)
                contentView.receiver_tv.text = snapshot.transactionHash
            }
            SnapshotType.pending.name -> {
                contentView.sender_title.text = fragment.getString(R.string.sender)
                contentView.sender_tv.text = snapshot.sender
                contentView.receiver_title.text = fragment.getString(R.string.transaction_hash)
                contentView.receiver_tv.text = snapshot.transactionHash
                contentView.transaction_status.isVisible = true
                contentView.transaction_status_tv.text =
                    contentView.context.getString(R.string.pending_confirmations, snapshot.confirmations, snapshot.assetConfirmations)
            }
            SnapshotType.transfer.name -> {
                if (isPositive) {
                    contentView.sender_tv.text = snapshot.opponentFullName
                    contentView.receiver_tv.text = Session.getAccount()!!.fullName
                } else {
                    contentView.sender_tv.text = Session.getAccount()!!.fullName
                    contentView.receiver_tv.text = snapshot.opponentFullName
                }
            }
            else -> {
                if (!asset.tag.isNullOrEmpty()) {
                    contentView.receiver_title.text = fragment.getString(R.string.account_name)
                } else {
                    contentView.receiver_title.text = fragment.getString(R.string.receiver)
                }
                contentView.sender_title.text = fragment.getString(R.string.transaction_hash)
                contentView.sender_tv.text = snapshot.transactionHash
                contentView.receiver_tv.text = snapshot.receiver
            }
        }
    }

    fun getSnapshotType(fragment: Fragment, type: String): String {
        val s = when (type) {
            SnapshotType.transfer.name -> R.string.transfer
            SnapshotType.deposit.name, SnapshotType.pending.name -> R.string.wallet_bottom_deposit
            SnapshotType.withdrawal.name -> R.string.withdrawal
            SnapshotType.fee.name -> R.string.fee
            SnapshotType.rebate.name -> R.string.rebate
            SnapshotType.raw.name -> R.string.filters_raw
            else -> R.string.not_any
        }
        return fragment.requireContext().getString(s)
    }
}
