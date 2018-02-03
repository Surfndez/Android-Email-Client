package com.email.scenes.mailbox

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.email.MailboxActivity
import com.email.R
import com.email.scenes.mailbox.data.EmailThread
import com.email.scenes.mailbox.holders.EmailHolder

/**
 * Created by sebas on 1/23/18.
 */

class EmailThreadAdapter(val mContext : Context,
                         var threadListener : OnThreadEventListener?,
                         val threadListHandler: MailboxActivity.ThreadListHandler)
    : RecyclerView.Adapter<EmailHolder>() {

    var isMultiSelectMode = false

    override fun onBindViewHolder(holder: EmailHolder?, position: Int) {
        val mail = threadListHandler.getThreadFromIndex(position)
        holder?.bindMail(mail)

        if (isMultiSelectMode) {
            holder?.itemView?.setOnClickListener({
                threadListener?.onToggleThreadSelection(mail, position)
            })
        } else {
            holder?.itemView?.setOnClickListener({
                threadListener?.onToggleThreadSelection(mail, position)
            })
        }

        holder?.itemView?.setOnLongClickListener(
                {
                    threadListener?.onToggleThreadSelection(mail, position)
                    true
                } )

        if (isMultiSelectMode) {
            holder?.toggleMultiselect(mail.isSelected)
        } else {
            holder?.hideMultiselect()
        }
    }

    override fun getItemCount(): Int {
        return threadListHandler.getEmailThreadsCount()
    }


    private fun createMailItemView(): View {
        val mailItemView = View.inflate(mContext, R.layout.mail_item, null)
        return mailItemView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailHolder {
        val itemView : View = createMailItemView()
        return EmailHolder(itemView)
    }



    interface OnThreadEventListener{
        fun onToggleThreadSelection(thread: EmailThread, position: Int)
    }
}
