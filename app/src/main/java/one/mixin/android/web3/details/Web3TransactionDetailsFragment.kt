package one.mixin.android.web3.details

import android.content.ClipData
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants.ChainId.Solana
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.response.Web3Token
import one.mixin.android.databinding.FragmentWeb3TransactionDetailsBinding
import one.mixin.android.databinding.ViewWalletWeb3BottomBinding
import one.mixin.android.databinding.ViewWalletWeb3TokenBottomBinding
import one.mixin.android.extension.formatPublicKey
import one.mixin.android.extension.getClipboardManager
import one.mixin.android.extension.getParcelableCompat
import one.mixin.android.extension.navTo
import one.mixin.android.extension.toast
import one.mixin.android.extension.withArgs
import one.mixin.android.tip.Tip
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.home.web3.Web3ViewModel
import one.mixin.android.ui.tip.wc.WalletConnectFragment
import one.mixin.android.util.viewBinding
import one.mixin.android.web3.receive.Wbe3ReceiveFragment
import one.mixin.android.web3.send.InputAddressFragment
import one.mixin.android.widget.BottomSheet
import javax.inject.Inject

@AndroidEntryPoint
class Web3TransactionDetailsFragment : BaseFragment(R.layout.fragment_web3_transaction_details) {
    companion object {
        const val TAG = "TransactionsFragment"
        const val ARGS_TOKEN = "args_token"
        const val ARGS_CHAIN_TOKEN = "args_chain_token"
        const val ARGS_ADDRESS = "args_address"

        fun newInstance(
            address: String,
            web3Token: Web3Token,
            chainToken: Web3Token?,
        ) =
            Web3TransactionDetailsFragment().withArgs {
                putString(ARGS_ADDRESS, address)
                putParcelable(ARGS_TOKEN, web3Token)
                putParcelable(ARGS_CHAIN_TOKEN, chainToken)
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
                        navTo(InputAddressFragment.newInstance(address, token, chainToken), InputAddressFragment.TAG)
                    }

                    R.id.receive -> {
                        navTo(Wbe3ReceiveFragment(), Wbe3ReceiveFragment.TAG)
                    }

                    R.id.more ->{
                        val builder = BottomSheet.Builder(requireActivity())
                        _bottomBinding = ViewWalletWeb3TokenBottomBinding.bind(View.inflate(ContextThemeWrapper(requireActivity(), R.style.Custom), R.layout.view_wallet_web3_token_bottom, null))
                        builder.setCustomView(bottomBinding.root)
                        val bottomSheet = builder.create()
                        bottomBinding.apply {
                            title.setText(if (token.chainId == Solana) R.string.Solana_Account else R.string.Ethereum_Account)
                            addressTv.text = address.formatPublicKey()
                            copy.setOnClickListener {
                                context?.getClipboardManager()?.setPrimaryClip(ClipData.newPlainText(null, token.assetKey))
                                toast(R.string.copied_to_clipboard)
                                bottomSheet.dismiss()
                            }
                            cancel.setOnClickListener { bottomSheet.dismiss() }
                        }

                        bottomSheet.show()
                    }
                }
            }
            setOnClickListener { transaction ->
                navTo(Web3Web3TransactionFragment.newInstance(transaction), Web3Web3TransactionFragment.TAG)
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
        binding.transactionsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionsRv.adapter = adapter
        lifecycleScope.launch {
            binding.progress.isVisible = true
            handleMixinResponse(invokeNetwork = {
                web3ViewModel.web3Transaction(address, token.chainId, token.fungibleId)
            }, successBlock = { result ->
                if (isAdded) adapter.transactions = result.data ?: emptyList()
            }, endBlock = {
                if (isAdded) binding.progress.isVisible = false
            })
        }
    }
}
