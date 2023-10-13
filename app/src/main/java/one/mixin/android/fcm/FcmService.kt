package one.mixin.android.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import one.mixin.android.MixinApplication
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.RefreshFcmJob
import one.mixin.android.session.Session
import timber.log.Timber

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var jobManager: MixinJobManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: " + remoteMessage.from)
    }

    override fun onNewToken(token: String) {
        if (Session.checkToken() && MixinApplication.get().isOnline.get()) {
            jobManager.addJobInBackground(RefreshFcmJob(token))
        }
    }
}
