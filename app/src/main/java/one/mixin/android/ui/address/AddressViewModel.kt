package one.mixin.android.ui.address

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.RefreshAddressJob
import one.mixin.android.repository.TokenRepository
import javax.inject.Inject

@HiltViewModel
class AddressViewModel
    @Inject
    internal constructor(
        private val tokenRepository: TokenRepository,
        private val jobManager: MixinJobManager,
    ) : ViewModel() {
        fun addresses(id: String) = tokenRepository.addresses(id)

        fun addressesFlow(id: String) = tokenRepository.addressesFlow(id)

        fun refreshAddressesByAssetId(assetId: String) {
            jobManager.addJobInBackground(RefreshAddressJob(assetId))
        }
    }
