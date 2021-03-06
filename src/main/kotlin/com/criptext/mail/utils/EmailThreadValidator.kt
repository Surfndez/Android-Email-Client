package com.criptext.mail.utils

import com.criptext.mail.db.models.Label

/**
 * Created by sebas on 1/24/18.
 */

class EmailThreadValidator {

   companion object {
      fun isLabelInList (labels: List<Label>, textLabel: String): Boolean{
           return labels.find { it.text == textLabel } != null
      }
   }
}

