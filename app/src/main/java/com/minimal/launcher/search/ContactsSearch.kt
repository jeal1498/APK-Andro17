package com.minimal.launcher.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log

/** Contacts lookup through the system provider. Returns nothing unless READ_CONTACTS was granted. */
object ContactsSearch {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(context: Context, query: String, limit: Int = 5): List<Contact> {
        val q = query.trim()
        if (q.length < 2 || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(q))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER), null, null, null)?.use { c ->
                val out = LinkedHashMap<Long, Contact>()
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    if (id !in out) out[id] = Contact(id, c.getString(1) ?: continue, c.getString(2) ?: continue)
                }
                out.values.toList()
            }.orEmpty()
        }.getOrElse { Log.w("ContactsSearch", "query failed", it); emptyList() }
    }
}
