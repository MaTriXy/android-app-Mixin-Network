package one.mixin.android.web3.details

import android.content.ClipData
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.response.Web3Token
import one.mixin.android.api.response.isSolToken
import one.mixin.android.api.response.isSolana
import one.mixin.android.api.response.solLamportToAmount
import one.mixin.android.databinding.FragmentWeb3TransactionDetailsBinding
import one.mixin.android.databinding.ViewWalletWeb3TokenBottomBinding
import one.mixin.android.extension.getClipboardManager
import one.mixin.android.extension.getParcelableArrayListCompat
import one.mixin.android.extension.getParcelableCompat
import one.mixin.android.extension.navTo
import one.mixin.android.extension.openUrl
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.extension.withArgs
import one.mixin.android.tip.Tip
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.home.web3.StakeAccountSummary
import one.mixin.android.ui.home.web3.Web3ViewModel
import one.mixin.android.ui.home.web3.stake.StakeFragment
import one.mixin.android.ui.home.web3.stake.StakingFragment
import one.mixin.android.ui.home.web3.stake.ValidatorsFragment
import one.mixin.android.ui.home.web3.swap.SwapFragment
import one.mixin.android.util.analytics.AnalyticsTracker
import one.mixin.android.util.viewBinding
import one.mixin.android.web3.details.Web3TransactionFragment.Companion.ARGS_CHAIN
import one.mixin.android.web3.receive.Web3AddressFragment
import one.mixin.android.ui.address.TransferDestinationInputFragment
import one.mixin.android.widget.BottomSheet
import javax.inject.Inject

@AndroidEntryPoint
class Web3TransactionDetailsFragment : BaseFragment(R.layout.fragment_web3_transaction_details) {
    companion object {
        const val TAG = "Web3TransactionDetailsFragment"
        const val ARGS_TOKEN = "args_token"
        const val ARGS_TOKENS = "args_tokens"
        const val ARGS_CHAIN_TOKEN = "args_chain_token"
        const val ARGS_ADDRESS = "args_address"

        fun newInstance(
            address: String,
            chain: String,
            web3Token: Web3Token,
            chainToken: Web3Token?,
            tokens: List<Web3Token>? = null
        ) =
            Web3TransactionDetailsFragment().withArgs {
                putString(ARGS_ADDRESS, address)
                putString(ARGS_CHAIN, chain)
                putParcelable(ARGS_TOKEN, web3Token)
                putParcelable(ARGS_CHAIN_TOKEN, chainToken)
                putParcelableArrayList(ARGS_TOKENS, arrayListOf<Web3Token>().apply {
                    add(web3Token)
                    tokens?.let {
                        addAll(tokens.filter { it != web3Token })
                    }
                })
            }
    }

    private val binding by viewBinding(FragmentWeb3TransactionDetailsBinding::bind)
    private val web3ViewModel by viewModels<Web3ViewModel>()

    private var _bottomBinding: ViewWalletWeb3TokenBottomBinding? = null
    private val bottomBinding get() = requireNotNull(_bottomBinding) { "required _bottomBinding is null" }

    @Inject
    lateinit var tip: Tip

    private val address: String by lazy {
        requireNotNull(requireArguments().getString(ARGS_ADDRESS))
    }

    private val chain: String by lazy {
        requireNotNull(requireArguments().getString(ARGS_CHAIN))
    }

    private val web3tokens by lazy {
        requireArguments().getParcelableArrayListCompat(ARGS_TOKENS, Web3Token::class.java)!!
    }

    private val token: Web3Token by lazy {
        requireArguments().getParcelableCompat(ARGS_TOKEN, Web3Token::class.java)!!
    }

    private val chainToken: Web3Token? by lazy {
        requireArguments().getParcelableCompat(ARGS_CHAIN_TOKEN, Web3Token::class.java)
    }

    private val adapter by lazy {
        Web3TransactionAdapter(token).apply {
            setOnClickAction { id ->
                when (id) {
                    R.id.send -> {
                        navTo(TransferDestinationInputFragment.newInstance(address, token, chainToken), TransferDestinationInputFragment.TAG)
                    }

                    R.id.receive -> {
                        navTo(Web3AddressFragment(), Web3AddressFragment.TAG)
                    }

                    R.id.swap -> {
                        AnalyticsTracker.trackSwapStart("solana", "solana")
                        navTo(SwapFragment.newInstance<Web3Token>(web3tokens), SwapFragment.TAG)
                    }

                    R.id.more -> {
                        val builder = BottomSheet.Builder(requireActivity())
                        _bottomBinding = ViewWalletWeb3TokenBottomBinding.bind(View.inflate(ContextThemeWrapper(requireActivity(), R.style.Custom), R.layout.view_wallet_web3_token_bottom, null))
                        builder.setCustomView(bottomBinding.root)
                        val bottomSheet = builder.create()
                        bottomBinding.apply {
                            title.text = token.name
                            addressTv.text = token.assetKey
                            view.setOnClickListener {
                                if (token.isSolana()) {
                                    context?.openUrl("https://solscan.io/token/" + token.assetKey)
                                } else {
                                    // TODO more evm
                                    context?.openUrl("https://etherscan.io/token/" + token.assetKey)
                                }
                                bottomSheet.dismiss()
                            }
                            stakeSolTv.isVisible = token.isSolToken()
                            stakeSolTv.setOnClickListener {
                                this@Web3TransactionDetailsFragment.navTo(ValidatorsFragment.newInstance().apply {
                                    setOnSelect { v ->
                                        this@Web3TransactionDetailsFragment.navTo(StakeFragment.newInstance(v, token.balance), StakeFragment.TAG)
                                    }
                                }, ValidatorsFragment.TAG)
                                bottomSheet.dismiss()
                            }
                            copy.setOnClickListener {
                                context?.getClipboardManager()?.setPrimaryClip(ClipData.newPlainText(null, token.assetKey))
                                toast(R.string.copied_to_clipboard)
                                bottomSheet.dismiss()
                            }
                            cancel.setOnClickListener { bottomSheet.dismiss() }
                        }

                        bottomSheet.show()
                    }

                    R.id.stake_rl -> {
                        navTo(StakingFragment.newInstance(ArrayList(this.stakeAccounts ?: emptyList()), token.balance), StakingFragment.TAG)
                    }
                }
            }
            setOnClickListener { transaction ->
                navTo(Web3TransactionFragment.newInstance(transaction, chain, token), Web3TransactionFragment.TAG)
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleView.apply {
            leftIb.setOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        binding.transactionsRv.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.transactionsRv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        binding.transactionsRv.adapter = adapter
        web3ViewModel // keep init
        lifecycleScope.launch {
            binding.progress.isVisible = true
            handleMixinResponse(invokeNetwork = {
                web3ViewModel.web3Transaction(address, token.chainId, token.fungibleId, token.assetKey)
            }, successBlock = { result ->
                if (!viewDestroyed()) adapter.transactions = result.data ?: emptyList()
            }, endBlock = {
                if (!viewDestroyed()) binding.progress.isVisible = false
            })
        }

        if (token.isSolToken()) {
            lifecycleScope.launch {
                getStakeAccounts(address)
            }
        }
    }

    private suspend fun getStakeAccounts(address: String) {
        val stakeAccounts = web3ViewModel.getStakeAccounts(address)
        if (stakeAccounts.isNullOrEmpty()) {
            adapter.setStake(emptyList(), StakeAccountSummary(0, "0"))
            return
        }

        var amount: Long = 0
        var count = 0
        stakeAccounts.forEach { a ->
            count++
            amount += (a.account.data.parsed.info.stake.delegation.stake.toLongOrNull() ?: 0)
        }
        adapter.setStake(stakeAccounts, StakeAccountSummary(count, amount.solLamportToAmount().stripTrailingZeros().toPlainString()))
    }
}
