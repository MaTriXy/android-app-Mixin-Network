package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import com.bugsnag.android.Bugsnag
import one.mixin.android.extension.joinWhiteSpace
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.MessageFts4Helper
import one.mixin.android.vo.*

class SendTranscriptJob(
    val message: Message,
    val transcriptMessages: List<TranscriptMessage>,
    messagePriority: Int = PRIORITY_SEND_MESSAGE
) : MixinJob(Params(messagePriority).groupBy("send_message_group").persist(), message.id) {

    companion object {
        private const val serialVersionUID = 1L
    }

    override fun onAdded() {
        super.onAdded()
        if (chatWebSocket.connected) {
            jobManager.start()
        }
        val conversation = conversationDao.findConversationById(message.conversationId)
        if (conversation != null) {
            messageDao.insert(message)
            transcriptMessageDao.insertList(transcriptMessages)
        } else {
            Bugsnag.notify(Throwable("Insert failed, no conversation exist"))
        }
    }

    override fun cancel() {
    }

    override fun onRun() {
        val list = transcriptMessages.filter { it.isAttachment() }
        val transcripts = transcriptMessageDao.getTranscript(message.id)
        val stringBuffer = StringBuffer()
        transcripts.filter { it.isText() || it.isPost() || it.isData() || it.isContact() }.forEach { transcript ->
            if (transcript.isData()) {
                transcript.mediaName
            } else if (transcript.isContact()) {
                transcript.sharedUserId?.let { userId -> userDao.findUser(userId) }?.fullName
            } else {
                transcript.content
            }?.joinWhiteSpace()?.let {
                stringBuffer.append(it)
            }
        }
        MessageFts4Helper.insertMessageFts4(message.id, stringBuffer.toString())
        if (list.isEmpty()) {
            message.content = GsonHelper.customGson.toJson(transcripts)
            jobManager.addJob(SendMessageJob(message))
        } else {
            messageDao.insert(message)
            list.forEach { t ->
                jobManager.addJob(SendTranscriptAttachmentMessageJob(t, message.isPlain()))
            }
        }
    }
}