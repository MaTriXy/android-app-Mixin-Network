package one.mixin.android.ui.wallet

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.databinding.FragmentAddressChangedBottomBinding
import one.mixin.android.extension.getParcelableCompat
import one.mixin.android.extension.toast
import one.mixin.android.ui.common.MixinBottomSheetDialogFragment
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.setting.SettingViewModel
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.safe.TokenItem
import one.mixin.android.widget.BottomSheet

@AndroidEntryPoint
class AddressChangedBottomSheet : MixinBottomSheetDialogFragment() {
    companion object {
        const val TAG = "AddressChangedBottomSheet"

        fun newInstance(
            tokenItem: TokenItem,
        ) = AddressChangedBottomSheet().apply {
            arguments =
                Bundle().apply {
                    putParcelable("token", tokenItem)
                }
        }
    }

    private val token: TokenItem by lazy {
        requireArguments().getParcelableCompat("token", TokenItem::class.java)!!
    }
    private val viewModel by viewModels<SettingViewModel>()
    private val bottomSendBinding by viewBinding(FragmentAddressChangedBottomBinding::inflate)

    @SuppressLint("RestrictedApi")
    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        contentView = bottomSendBinding.root
        (dialog as BottomSheet).setCustomView(contentView)
        bottomSendBinding.apply {
            assetIcon.loadToken(token)
            title.text = getString(R.string.depost_address_updated, token.symbol)
            content.text = getString(R.string.depost_address_updated_description, token.symbol)
            gotItTv.setOnClickListener { dismiss() }
            contactSupport.setOnClickListener {
                lifecycleScope.launch {
                    val userTeamMixin = viewModel.refreshUser(Constants.TEAM_MIXIN_USER_ID)
                    if (userTeamMixin == null) {
                        toast(R.string.Data_error)
                    } else {
                        ConversationActivity.show(requireContext(), recipientId = Constants.TEAM_MIXIN_USER_ID)
                        dismiss()
                    }
                }
            }
        }
    }
}
