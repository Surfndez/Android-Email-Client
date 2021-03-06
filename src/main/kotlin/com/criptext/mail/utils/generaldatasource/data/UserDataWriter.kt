package com.criptext.mail.utils.generaldatasource.data

import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.DeliveryTypes
import com.criptext.mail.db.LabelTypes
import com.criptext.mail.db.dao.*
import com.criptext.mail.db.models.*
import com.criptext.mail.utils.DateAndTimeUtils
import com.criptext.mail.utils.EmailUtils
import com.criptext.mail.utils.exceptions.SyncFileException
import com.github.kittinunf.result.Result
import org.json.JSONObject
import java.io.File

class UserDataWriter(private val db: AppDatabase, private val filesDir: File)
{

    data class DataMapper(val idsMap: MutableMap<Long, Long>)

    fun createFile(account: Account):String? {
        try {


            val tmpFileLinkData = createTempFile()

            val metadata = BackupFileMetadata(FILE_SYNC_VERSION, account.recipientId, account.domain)
            tmpFileLinkData.appendText("${BackupFileMetadata.toJSON(metadata)}\n")
            addContactsToFile(db.contactDao(), tmpFileLinkData, account.id)
            addLabelsToFile(db.labelDao(), tmpFileLinkData, account.id)
            addMailsToFile(db.emailDao(), tmpFileLinkData, account.id)
            addFilesToFile(db.fileDao(), tmpFileLinkData, account.id)
            addMailsAndLabelsRelationToFile(db.emailLabelDao(), tmpFileLinkData, account.id)
            addMailsAndContactsRelationToFile(db.emailContactDao(), tmpFileLinkData, account.id)

            return tmpFileLinkData.absolutePath
        }catch (ex:Exception){
            return null
        }
    }

    fun createDBFromFile(file: File) {
        val account = ActiveAccount.loadFromDB(db.accountDao().getLoggedInAccount()!!)!!
        val contactDataMapper = DataMapper(mutableMapOf())
        val contactWriter = ContactDataWriter(db.contactDao(), db.accountContactDao(), activeAccount = account,
                dataMapper = contactDataMapper)
        val labelDataMapper = DataMapper(mutableMapOf())
        val labelWriter = LabelDataWriter(db.labelDao(), activeAccount = account, dataMapper = labelDataMapper)
        val emaillDataMapper = DataMapper(mutableMapOf())
        val emailWriter = EmailDataWriter(
                emailDao = db.emailDao(),
                activeAccount = account,
                filesDir = filesDir,
                dataMapper = emaillDataMapper
        )
        val fileWriter = FileDataWriter(db.fileDao(), listOf(emailWriter), emaillDataMapper)
        val emailLabelWriter = EmailLabelDataWriter(db.emailLabelDao(), listOf(labelWriter, emailWriter),
                emailDataMapper = emaillDataMapper, labelDataMapper = labelDataMapper)
        val emailContactWriter = EmailContactDataWriter(db.emailContactDao(), listOf(contactWriter, emailWriter),
                emailDataMapper = emaillDataMapper, contactDataMapper = contactDataMapper)
        val data = file.bufferedReader()
        var line = data.readLine()
        val operation = Result.of {
            val metadata = BackupFileMetadata.fromJSON(line)
            if (metadata.fileVersion == FILE_SYNC_VERSION) {
                if (account.userEmail != metadata.recipientId.plus("@${metadata.domain}")) {
                    throw SyncFileException.UserNotValidException()
                } else {
                    line = data.readLine()
                }
            } else {
                throw SyncFileException.OutdatedException()
            }
        }

        when(operation){
            is Result.Failure -> {
                if(operation.error is SyncFileException)
                    throw operation.error
            }
        }
        db.beginTransaction()
        while(line != null && line.isNotEmpty()) {
            val json = JSONObject(line)
            when (json.getString("table")) {
                "contact" -> contactWriter.insert(json.get("object").toString())
                "label" -> labelWriter.insert(json.get("object").toString())
                "email" -> {
                    emailWriter.insert(json.get("object").toString())
                }
                "file" ->  {
                    fileWriter.insert(json.get("object").toString())
                }
                "email_label" -> emailLabelWriter.insert(json.get("object").toString())
                "email_contact" -> emailContactWriter.insert(json.get("object").toString())
            }
            line = data.readLine()
        }
        contactWriter.flush()
        labelWriter.flush()
        emailWriter.flush()
        fileWriter.flush()
        emailLabelWriter.flush()
        emailContactWriter.flush()


        db.setTransactionSuccessful()
        db.endTransaction()
    }

    private fun addContactsToFile(contactDao: ContactDao, tmpFile: File, accountId: Long)
    {
        fun flushContacts(allContacts: List<Contact>): Long {
            var lastId = 0L
            val jsonArrayAllContacts = mutableListOf<String>()
            for (contact in allContacts){
                if(contact == allContacts.last())
                    lastId = contact.id
                val jsonObject = JSONObject()
                jsonObject.put("id", contact.id)
                jsonObject.put("email", contact.email)
                jsonObject.put("name", contact.name)
                jsonObject.put("isTrusted", contact.isTrusted)
                jsonObject.put("spamScore", contact.spamScore)
                jsonArrayAllContacts.add(jsonObject.toString())
                tmpFile.appendText("${JSONObject("{table: contact, object: $jsonObject}")}\n")
            }
            return lastId
        }
        var lastId = 0L
        do {
            val contacts = contactDao.getAllForLinkFile(DB_READING_LIMIT, lastId, accountId)
            if(contacts.isNotEmpty()) {
                lastId = flushContacts(contacts)
            }
        }while (contacts.isNotEmpty())
    }

    private fun addLabelsToFile(labelDao: LabelDao, tmpFile: File, accountId: Long)
    {
        fun flushLabels(allLabels: List<Label>): Long {
            var lastId = 0L
            val jsonArrayAllLabels = mutableListOf<String>()
            for (label in allLabels){
                if(label == allLabels.last())
                    lastId = label.id
                if(label.type == LabelTypes.CUSTOM) {
                    val jsonObject = JSONObject()
                    jsonObject.put("id", label.id)
                    jsonObject.put("color", label.color)
                    jsonObject.put("text", label.text)
                    jsonObject.put("type", label.type.name.toLowerCase())
                    jsonObject.put("uuid", label.uuid)
                    jsonObject.put("visible", label.visible)
                    jsonArrayAllLabels.add(jsonObject.toString())
                    tmpFile.appendText("${JSONObject("{table: label, object: $jsonObject}")}\n")
                }
            }
            return lastId
        }
        var lastId = 0L
        do {
            val labels = labelDao.getAllForLinkFile(DB_READING_LIMIT, lastId, accountId)
            if(labels.isNotEmpty()) {
                lastId = flushLabels(labels)
            }
        }while (labels.isNotEmpty())

    }

    private fun addFilesToFile(fileDao: FileDao, tmpFile: File, accountId: Long)
    {
        fun flushFiles(allFiles: List<CRFile>): Long {
            var lastId = 0L
            val jsonArrayAllFiles = mutableListOf<String>()
            for (file in allFiles){
                if(file == allFiles.last())
                    lastId = file.id

                val key = if(file.fileKey.isNotEmpty())
                    file.fileKey
                else
                    null
                val jsonObject = JSONObject()
                jsonObject.put("id", file.id)
                jsonObject.put("token", file.token)
                jsonObject.put("name", file.name)
                jsonObject.put("size", file.size)
                jsonObject.put("status", file.status)
                jsonObject.put("date", DateAndTimeUtils.printDateWithServerFormat(file.date))
                jsonObject.put("readOnly", file.readOnly)
                if(file.cid != null && file.cid != "")
                    jsonObject.put("cid", file.cid)
                jsonObject.put("emailId", file.emailId)
                jsonObject.put("mimeType", com.criptext.mail.utils.file.FileUtils.getMimeType(file.name))
                if(key != null && key.contains(":")) {
                    jsonObject.put("key", key.substringBefore(":"))
                    jsonObject.put("iv", key.substringAfter(":"))
                }
                jsonArrayAllFiles.add(jsonObject.toString())
                tmpFile.appendText("${JSONObject("{table: file, object: $jsonObject}")}\n")
            }
            return lastId
        }
        var lastId = 0L
        do {
            val files = fileDao.getAllForLinkFile(DB_READING_LIMIT, lastId, accountId)
            if(files.isNotEmpty()) {
                lastId = flushFiles(files)
            }
        }while (files.isNotEmpty())
    }

    private fun addMailsToFile(emailDao: EmailDao, tmpFile: File, accountId: Long)
    {
        fun flushMails(allMails: List<Email>): Long {
            val account = db.accountDao().getLoggedInAccount()!!
            var lastId = 0L
            val jsonArrayAllMails = mutableListOf<String>()
            for (mail in allMails) {
                if(mail == allMails.last())
                    lastId = mail.id
                val emailContent = EmailUtils.getEmailContentFromFileSystem(
                        filesDir, mail.metadataKey, mail.content,
                        account.recipientId,
                        account.domain)
                val jsonObject = JSONObject()
                jsonObject.put("id", mail.id)
                jsonObject.put("messageId", mail.messageId)
                jsonObject.put("threadId", mail.threadId)
                jsonObject.put("unread", mail.unread)
                jsonObject.put("secure", mail.secure)
                jsonObject.put("content", emailContent.first)
                jsonObject.put("preview", mail.preview)
                jsonObject.put("fromAddress", mail.fromAddress)
                if (emailContent.second != null)
                    jsonObject.put("headers", emailContent.second)
                jsonObject.put("boundary", mail.boundary)
                jsonObject.put("subject", mail.subject)
                jsonObject.put("status", DeliveryTypes.getTrueOrdinal(mail.delivered))
                jsonObject.put("date", DateAndTimeUtils.printDateWithServerFormat(mail.date))
                jsonObject.put("key", mail.metadataKey)
                jsonObject.put("isMuted", mail.isMuted)
                if (mail.replyTo != null)
                    jsonObject.put("replyTo", mail.replyTo)
                if (mail.unsentDate != null)
                    jsonObject.put("unsentDate", DateAndTimeUtils.printDateWithServerFormat(mail.unsentDate!!))
                if (mail.trashDate != null)
                    jsonObject.put("trashDate", DateAndTimeUtils.printDateWithServerFormat(mail.trashDate!!))
                jsonArrayAllMails.add(jsonObject.toString())
                tmpFile.appendText("${JSONObject("{table: email, object: $jsonObject}")}\n")
            }
            return lastId
        }
        var lastId = 0L
        do {
            val emails = emailDao.getAllForLinkFile(DB_READING_LIMIT, lastId, accountId)
            if(emails.isNotEmpty()) {
                lastId = flushMails(emails)
            }
        }while (emails.isNotEmpty())
    }

    private fun addMailsAndLabelsRelationToFile(emailLabelDao: EmailLabelDao, tmpFile: File,
                                                accountId: Long)
    {
        val allMailsAndLabelsRelation = mutableListOf<EmailLabel>()
        var offset = 0
        do {
            val emailLabels = emailLabelDao.getAllForLinkFile(DB_READING_LIMIT, offset, accountId)
            if(emailLabels.isNotEmpty()) {
                allMailsAndLabelsRelation.addAll(emailLabels)
                offset += DB_READING_LIMIT
            }
        }while (emailLabels.isNotEmpty())
        val jsonArrayAllMails = mutableListOf<String>()
        for (mail_label in allMailsAndLabelsRelation){
            val jsonObject = JSONObject()
            jsonObject.put("emailId", mail_label.emailId)
            jsonObject.put("labelId", mail_label.labelId)
            jsonArrayAllMails.add(jsonObject.toString())
            tmpFile.appendText("${JSONObject("{table: email_label, object: $jsonObject}")}\n")
        }
    }

    private fun addMailsAndContactsRelationToFile(emailContactJoinDao: EmailContactJoinDao, tmpFile: File,
                                                  accountId: Long)
    {
        val allMailsAndContacts = mutableListOf<EmailContact>()
        var offset = 0
        do {
            val mailContacts = emailContactJoinDao.getAllForLinkFile(DB_READING_LIMIT, offset, accountId)
            if(mailContacts.isNotEmpty()) {
                allMailsAndContacts.addAll(mailContacts)
                offset += DB_READING_LIMIT
            }
        }while (mailContacts.isNotEmpty())
        val jsonArrayAllMails = mutableListOf<String>()
        for (mail_contact in allMailsAndContacts){
            val jsonObject = JSONObject()
            jsonObject.put("id", mail_contact.id)
            jsonObject.put("emailId", mail_contact.emailId)
            jsonObject.put("contactId", mail_contact.contactId)
            jsonObject.put("type", mail_contact.type.name.toLowerCase())
            jsonArrayAllMails.add(jsonObject.toString())
            tmpFile.appendText("${JSONObject("{table: email_contact, object: $jsonObject}")}\n")
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val EMAIL_BATCH_SIZE = 10
        const val RELATIONS_BATCH_SIZE = 100

        const val DB_READING_LIMIT = 500
        const val FILE_SYNC_VERSION = 5

        const val FILE_ENCRYPTED_EXTENSION = "enc"
        const val FILE_UNENCRYPTED_EXTENSION = "db"
        const val FILE_GZIP_EXTENSION = "gz"
    }
}