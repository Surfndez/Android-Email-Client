package com.email.scenes.emaildetail.ui

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.email.R
import com.email.db.models.FullEmail
import com.email.scenes.emaildetail.AttachmentHistoryListener
import com.email.utils.DateUtils
import com.email.utils.Utility
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by sebas on 3/15/18.
 */

class AttachmentHistoryPopUp(private val anchorView: View) {
    private val context = anchorView.context

    fun createPopup(
            fullEmail: FullEmail,
            attachmentHistoryListener: AttachmentHistoryListener?
    ) {

        val inflater = context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE ) as LayoutInflater
        val layout = inflater.inflate( R.layout.layout_attachments_history, null)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.attachments_history_recycler)

        val mockedAttachmentContacts = getMockedAttachmentContacts()
        AttachmentContactsRecyclerView(recyclerView, mockedAttachmentContacts)

        Utility.createPopUpWindow(
                context = context,
                contentView = layout,
                anchorView = anchorView)

        bindFullEmail(fullEmail = fullEmail,
                view = layout)

    }

    private fun bindFullEmail(fullEmail: FullEmail, view: View) {
        val refresher = view.findViewById<SwipeRefreshLayout>(R.id.attachments_history_refresher)
    }

    private fun getMockedAttachmentContacts(): List<MockedAttachmentContact> {
        val sdf = SimpleDateFormat("yyyy-MM-dd")

        val array = ArrayList<MockedAttachmentContact>()
        array.add(MockedAttachmentContact(
                name = "Sebastian Caceres",
                date = sdf.parse("2018-02-11"),
                file = "Look at ma sheep.pdf",
                action = "DOWNLOAD",
                fileType = "PDF"
                ))

        array.add(MockedAttachmentContact(
                name = "Gianni Carlo",
                date = sdf.parse("2018-03-16"),
                file = "Sheep relevance.pdf",
                action = "OPEN",
                fileType = "DOCX"
        ))
        return array
    }


    inner class AttachmentContactsRecyclerView(val recyclerView: RecyclerView,
                                               mockedAttachmentList: List<MockedAttachmentContact>
    ) {

        val ctx: Context = recyclerView.context
        private val contactsListAdapter = AttachmentContactsAdapter(
                mContext = ctx,
                contacts = mockedAttachmentList)

        init {
            recyclerView.layoutManager = LinearLayoutManager(ctx)
            recyclerView.adapter = contactsListAdapter
        }
    }

    inner class AttachmentContactsAdapter(private val mContext: Context,
                                          private val contacts: List<MockedAttachmentContact>
    ) : RecyclerView.Adapter<AttachmentContactHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): AttachmentContactHolder {
            val mView = LayoutInflater.from(mContext).
                    inflate(R.layout.attachment_history_item, null)
            return AttachmentContactHolder(mView)
        }

        override fun getItemCount(): Int {
            return contacts.size
        }

        override fun onBindViewHolder(holder: AttachmentContactHolder?, position: Int) {
            val contact = contacts[position]
            holder?.bindAttachmentContact(contact)
        }
    }

    inner class AttachmentContactHolder(val view: View): RecyclerView.ViewHolder(view) {
        private val context = view.context

        private val name: TextView
        private val action: TextView
        private val fileType: ImageView
        private val fileName: TextView
        private val date: TextView

        fun bindAttachmentContact(contact: MockedAttachmentContact){
            name.text = contact.name
            date.text = DateUtils.getFormattedDate(contact.date.time)
            action.text = when(contact.action) {
                "DOWNLOAD", "download" -> {
                    "downloaded"
                }

                "OPEN", "open" -> {
                    "opened"
                } else -> {
                    "not registered"
                }
            }
            if(contact.fileType in listOf("pdf", "PDF", "Pdf")) {
                fileType.setImageResource(R.drawable.pdf_eliminar_esto)
            } else {
                fileType.setImageResource(R.drawable.word_eliminar_esto)
            }

            fileName.text = contact.file

        }

        init {
            name = view.findViewById(R.id.name)
            action = view.findViewById(R.id.action)
            fileType = view.findViewById(R.id.file_type)
            fileName = view.findViewById(R.id.file_name)
            date = view.findViewById(R.id.date)
        }
    }

    data class MockedAttachmentContact(val name: String,
                                       val date: Date,
                                       val file: String,
                                       val action: String,
                                       val fileType: String)
}

