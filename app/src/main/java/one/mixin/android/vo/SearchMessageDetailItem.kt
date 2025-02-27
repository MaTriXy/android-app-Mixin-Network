package one.mixin.android.vo

import androidx.recyclerview.widget.DiffUtil

data class SearchMessageDetailItem(
    val messageId: String,
    override val type: String,
    val content: String?,
    val createdAt: String,
    val mediaName: String?,
    val userId: String,
    val userFullName: String?,
    val userAvatarUrl: String?,
    val userIdentityNumber: String?,
    val appId: String?,
    val isVerified: Boolean,
    val membership: Membership?
) : ICategory {
    fun isMembership(): Boolean {
        return membership?.isMembership() == true
    }

    fun isProsperity(): Boolean {
        return membership?.isProsperity() == true
    }

    companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<SearchMessageDetailItem>() {
                override fun areItemsTheSame(
                    oldItem: SearchMessageDetailItem,
                    newItem: SearchMessageDetailItem,
                ) =
                    oldItem.messageId == newItem.messageId

                override fun areContentsTheSame(
                    oldItem: SearchMessageDetailItem,
                    newItem: SearchMessageDetailItem,
                ) =
                    oldItem == newItem
            }
    }
}
