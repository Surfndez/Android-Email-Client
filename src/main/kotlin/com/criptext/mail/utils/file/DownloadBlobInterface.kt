package com.criptext.mail.utils.file

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.criptext.mail.BuildConfig

/**
 * Created by hirobreak on 02/08/17.
 */
class DownloadBlobInterface(val mContext: Context, val filename: String) {

    @JavascriptInterface
    fun retrieveData(data: String) {
        //val notificator = CriptextNotification(mContext)
        val bytesData = Base64.decode(data, 0)
        val file = AndroidFs.getFileFromDownloadsDir(filename)
        AndroidFs.writeByteArraysToFile(file, bytesData)

        val intent = Intent()
        intent.action = android.content.Intent.ACTION_VIEW
        intent.setDataAndType(FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID +
                ".provider", file), FileUtils.getMimeType(file.name))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //notificator.showNotificationFileDownloaded(file.name, intent)

        (mContext as AppCompatActivity).runOnUiThread {
            Toast.makeText(mContext, "Downloading file $filename",
                    Toast.LENGTH_LONG).show()
        }
    }

}
