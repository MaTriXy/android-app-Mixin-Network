package one.mixin.android.web3.details

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter
import one.mixin.android.R
import one.mixin.android.api.response.Web3Token
import one.mixin.android.api.response.Web3Transaction
import one.mixin.android.api.response.isSolana
import one.mixin.android.api.response.web3.StakeAccount
import one.mixin.android.databinding.ItemWeb3TokenHeaderBinding
import one.mixin.android.databinding.ItemWeb3TransactionBinding
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.hashForDate
import one.mixin.android.extension.inflate
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.textColor
import one.mixin.android.extension.textColorResource
import one.mixin.android.ui.home.web3.StakeAccountSummary
import one.mixin.android.ui.wallet.adapter.SnapshotHeaderViewHolder
import one.mixin.android.vo.Fiats
import one.mixin.android.widget.GrayscaleTransformation
import java.math.BigDecimal
import kotlin.math.abs

class Web3TransactionAdapter(val token: Web3Token) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyRecyclerHeadersAdapter<SnapshotHeaderViewHolder> {
    fun isEmpty() = transactions.isEmpty()

    var transactions: List<Web3Transaction> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private var onClickListener: ((Web3Transaction) -> Unit)? = null
    private var onClickAction: ((Int) -> Unit)? = null

    var stakeAccounts: List<StakeAccount>? = null
    private var stakeAccountSummary: StakeAccountSummary? = null

    fun setStake(stakeAccounts: List<StakeAccount>, stakeAccountSummary: StakeAccountSummary) {
        this.stakeAccounts = stakeAccounts
        this.stakeAccountSummary = stakeAccountSummary
        notifyItemRangeChanged(0, 2)
    }

    fun setOnClickAction(onClickListener: (Int) -> Unit) {
        this.onClickAction = onClickListener
    }

    fun setOnClickListener(onClickListener: (Web3Transaction) -> Unit) {
        this.onClickListener = onClickListener
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            Web3HeaderHolder(ItemWeb3TokenHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)).apply {
                if (token.isSolana()) {
                    enableSwap()
                }
            }
        } else {
            Web3TransactionHolder(ItemWeb3TransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getHeaderId(position: Int): Long {
        if (position == 0) return -1
        val transaction = transactions[position - 1]
        return abs(transaction.createdAt.hashForDate())
    }

    override fun onCreateHeaderViewHolder(parent: ViewGroup) = SnapshotHeaderViewHolder(parent.inflate(R.layout.item_transaction_header, false))

    override fun onBindHeaderViewHolder(
        holder: SnapshotHeaderViewHolder,
        position: Int,
    ) {
        val transaction = transactions[position - 1]
        holder.bind(transaction.createdAt)
    }

    override fun getItemCount(): Int {
        return transactions.size + 1
    }

    override fun getItemViewType(position: Int): Int = if (position == 0) 0 else 1

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        if (position > 0) {
            (holder as Web3TransactionHolder).bind(transactions[position - 1])
            holder.itemView.setOnClickListener {
                onClickListener?.invoke(transactions[position - 1])
            }
        } else {
            (holder as Web3HeaderHolder).bind(token, stakeAccountSummary) { id ->
                onClickAction?.invoke(id)
            }
        }
    }
}

class Web3TransactionHolder(val binding: ItemWeb3TransactionBinding) : RecyclerView.ViewHolder(binding.root) {
    @SuppressLint("SetTextI18n")
    fun bind(transaction: Web3Transaction) {
        binding.apply {
            titleTv.text = transaction.title(root.context)
            subTitleTv.text = transaction.subTitle
            when (transaction.operationType) {
                Web3TransactionType.Send.value -> {
                    if (transaction.sender == transaction.receiver) {
                        avatar.bg.setImageResource(R.drawable.ic_snapshot_deposit)
                    } else {
                        avatar.bg.setImageResource(R.drawable.ic_snapshot_withdrawal)
                    }
                    avatar.badge.isVisible = false

                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.Out.value }?.let { outTransfer ->
                            inTv.textColorResource = R.color.wallet_pink
                            inTv.text = "-${outTransfer.amount}"
                            inSymbolTv.text = outTransfer.symbol
                            outSymbolTv.text = outTransfer.amountFormat
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_assist)
                            outTv.isVisible = false
                        }
                    }
                }

                Web3TransactionType.Receive.value -> {
                    avatar.bg.setImageResource(R.drawable.ic_snapshot_deposit)
                    avatar.badge.isVisible = false
                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.In.value }?.let { inTransfer ->
                            inTv.textColorResource = R.color.wallet_green
                            inTv.text = "+${inTransfer.amount}"
                            inSymbolTv.text = inTransfer.symbol
                            outSymbolTv.text = inTransfer.amountFormat
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_assist)
                            outTv.isVisible = false
                        }
                    }
                }

                Web3TransactionType.Withdraw.value -> {
                    avatar.bg.setImageResource(R.drawable.ic_snapshot_withdrawal)
                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.In.value }?.let { inTransfer ->
                            inTv.textColorResource = R.color.wallet_green
                            inTv.text = "+${inTransfer.amount}"
                            inSymbolTv.text = inTransfer.symbol
                            avatar.badge.loadImage(inTransfer.iconUrl, holder = R.drawable.ic_avatar_place_holder)
                            avatar.badge.isVisible = true
                        }
                        transaction.transfers.find { it.direction == Web3TransactionDirection.Out.value }?.let { outTransfer ->
                            outTv.isVisible = true
                            outTv.textColorResource = R.color.wallet_pink
                            outTv.text = "-${outTransfer.amount}"
                            outSymbolTv.text = outTransfer.symbol
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_primary)
                        }
                    }
                }

                Web3TransactionType.Execute.value -> {
                    avatar.bg.setImageResource(R.drawable.ic_snapshot_withdrawal)
                    avatar.badge.isVisible = false

                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.Out.value }?.let { outTransfer ->
                            inTv.textColorResource = R.color.wallet_pink
                            inTv.text = "-${outTransfer.amount}"
                            inSymbolTv.text = outTransfer.symbol
                            outSymbolTv.text = outTransfer.amountFormat
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_assist)
                            outTv.isVisible = false
                        }
                    }
                }

                Web3TransactionType.Approve.value -> {
                    avatar.bg.loadImage(
                        transaction.fee.iconUrl,
                        holder = R.drawable.ic_avatar_place_holder,
                        transformation =
                            if (transaction.status == Web3TransactionStatus.Failed.value) {
                                GrayscaleTransformation()
                            } else {
                                null
                            },
                    )
                    avatar.badge.loadImage(transaction.approvals.firstOrNull()?.iconUrl, holder = R.drawable.ic_no_dapp)
                    avatar.badge.isVisible = true
                    inTv.textColorResource = R.color.wallet_pink
                    inTv.text = "-${transaction.fee.amount}"
                    inSymbolTv.text = transaction.fee.symbol
                    outSymbolTv.text = transaction.fee.amountFormat
                    outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_assist)
                    outTv.isVisible = false
                }

                Web3TransactionType.Mint.value -> {
                    avatar.bg.loadImage(
                        transaction.fee.iconUrl,
                        holder = R.drawable.ic_avatar_place_holder,
                        transformation =
                            if (transaction.status == Web3TransactionStatus.Failed.value) {
                                GrayscaleTransformation()
                            } else {
                                null
                            },
                    )
                    avatar.badge.loadImage(transaction.approvals.firstOrNull()?.iconUrl, holder = R.drawable.ic_no_dapp)
                    avatar.badge.isVisible = true
                    inTv.textColorResource = R.color.wallet_pink
                    inTv.text = "-${transaction.fee.amount}"
                    inSymbolTv.text = transaction.fee.symbol
                    outSymbolTv.text = transaction.fee.amountFormat
                    outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_assist)
                    outTv.isVisible = false
                }

                Web3TransactionType.Trade.value -> {
                    avatar.badge.loadImage(transaction.appMetadata?.iconUrl, holder = R.drawable.ic_no_dapp)
                    avatar.badge.isVisible = true
                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.In.value }?.let { inTransfer ->
                            avatar.bg.loadImage(
                                inTransfer.iconUrl,
                                holder = R.drawable.ic_avatar_place_holder,
                                transformation =
                                    if (transaction.status == Web3TransactionStatus.Failed.value) {
                                        GrayscaleTransformation()
                                    } else {
                                        null
                                    },
                            )
                            inTv.textColorResource = R.color.wallet_green
                            inTv.text = "+${inTransfer.amount}"
                            inSymbolTv.text = inTransfer.symbol
                        }
                        transaction.transfers.find { it.direction == Web3TransactionDirection.Out.value }?.let { outTransfer ->
                            outTv.isVisible = true
                            outTv.textColorResource = R.color.wallet_pink
                            outTv.text = "-${outTransfer.amount}"
                            outSymbolTv.text = outTransfer.symbol
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_primary)
                        }
                    }
                }

                Web3TransactionType.Deposit.value -> {
                    avatar.badge.loadImage(transaction.appMetadata?.iconUrl, holder = R.drawable.ic_no_dapp)
                    avatar.badge.isVisible = true
                    if (transaction.transfers.isNotEmpty()) {
                        transaction.transfers.find { it.direction == Web3TransactionDirection.In.value }?.let { inTransfer ->
                            avatar.bg.loadImage(
                                inTransfer.iconUrl,
                                holder = R.drawable.ic_avatar_place_holder,
                                transformation =
                                    if (transaction.status == Web3TransactionStatus.Failed.value) {
                                        GrayscaleTransformation()
                                    } else {
                                        null
                                    },
                            )
                            inTv.textColorResource = R.color.wallet_green
                            inTv.text = "+${inTransfer.amount}"
                            inSymbolTv.text = inTransfer.symbol
                        }
                        transaction.transfers.find { it.direction == Web3TransactionDirection.Out.value }?.let { outTransfer ->
                            outTv.isVisible = true
                            outTv.textColorResource = R.color.wallet_pink
                            outTv.text = "-${outTransfer.amount}"
                            outSymbolTv.text = outTransfer.symbol
                            outSymbolTv.textColor = root.context.colorFromAttribute(R.attr.text_primary)
                        }
                    }
                }
                else -> {
                    avatar.bg.setImageResource(R.drawable.ic_no_dapp)
                    avatar.badge.isVisible = false
                    outSymbolTv.text = ""
                    outTv.text = ""
                    inSymbolTv.text = ""
                    inTv.text = ""
                }
            }
        }
    }
}

class Web3HeaderHolder(val binding: ItemWeb3TokenHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(
        token: Web3Token,
        summary: StakeAccountSummary?,
        onClickListener: ((Int) -> Unit)?,
    ) {
        binding.header.setToken(token)
        binding.header.setOnClickAction(onClickListener)
        binding.header.showStake(summary)
    }

    fun enableSwap() {
        binding.header.enableSwap()
    }
}
