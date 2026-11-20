package com.easyphone.senior

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts

data class Person(val id: Long, val name: String, val number: String, val photo: Uri?)

class MainActivity : Activity() {
    private val blue = Color.rgb(25, 103, 210)
    private val ink = Color.rgb(30, 38, 52)
    private val pale = Color.rgb(242, 245, 250)
    private var page = "Contacts"
    private var people: List<Person> = emptyList()
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var search: EditText
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) { loadContacts(); showPage() } else showPermissionMessage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = blue
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildShell()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) loadContacts()
        else permission.launch(Manifest.permission.READ_CONTACTS)
        showPage()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun button(label: String, color: Int = blue, onClick: () -> Unit): TextView =
        text(label, 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = rounded(color, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { onClick() }
        }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pale) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); setBackgroundColor(blue)
        }
        header.addView(text("Phone", 29f, Color.WHITE, true))
        header.addView(text("Favorites  •  Recents  •  Contacts", 14f, Color.WHITE))
        root.addView(header)
        search = EditText(this).apply {
            hint = "Search contacts"; textSize = 18f; singleLine = true
            setPadding(dp(16), 0, dp(16), 0); background = rounded(Color.WHITE, 16)
        }
        val searchWrap = LinearLayout(this).apply { setPadding(dp(14), dp(10), dp(14), dp(6)) }
        searchWrap.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(searchWrap)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(8)) }
        val scroller = ScrollView(this).apply { addView(body) }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { label ->
            val item = text(label, 13f, blue, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(4), dp(14), dp(4), dp(14))
                setOnClickListener { page = label; showPage() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        setContentView(root)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPeople() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadContacts() {
        val result = mutableListOf<Person>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val seen = mutableSetOf<String>()
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(projection[0])
            val nameCol = it.getColumnIndexOrThrow(projection[1])
            val numCol = it.getColumnIndexOrThrow(projection[2])
            val photoCol = it.getColumnIndexOrThrow(projection[3])
            while (it.moveToNext()) {
                val id = it.getLong(idCol); val name = it.getString(nameCol) ?: "Unknown"
                val number = it.getString(numCol) ?: ""
                if (number.isNotBlank() && seen.add("$id:$number")) {
                    val photo = it.getString(photoCol)?.let { p -> Uri.parse(p) }
                    result.add(Person(id, name, number, photo))
                }
            }
        }
        people = result
    }

    private fun showPage() {
        search.visibility = if (page == "Contacts" || page == "Favorites") View.VISIBLE else View.GONE
        search.hint = "Search contacts"
        body.removeAllViews()
        when (page) {
            "Keypad" -> renderKeypad()
            "Recents" -> {
                body.addView(text("Recent calls", 23f, ink, true))
                body.addView(text("Your recent calls will appear here.", 17f))
                val open = button("Open call history", blue) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Calls.CONTENT_URI)) }
                    catch (_: Exception) { Toast.makeText(this, "Call history is unavailable", Toast.LENGTH_SHORT).show() }
                }
                body.addView(open, LinearLayout.LayoutParams(-1, dp(58)))
            }
            else -> renderPeople()
        }
    }

    private fun renderPeople() {
        if (!::body.isInitialized || page == "Keypad" || page == "Recents") return
        body.removeAllViews()
        val title = if (page == "Favorites") "Favorite people" else "Contacts"
        body.addView(text(title, 24f, ink, true).apply { setPadding(dp(6), dp(6), 0, dp(10)) })
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            showPermissionMessage(); return
        }
        val query = if (::search.isInitialized) search.text.toString().trim() else ""
        val filtered = people.filter { it.name.contains(query, true) || it.number.contains(query) }
        if (filtered.isEmpty()) {
            body.addView(text("No contacts found", 19f))
            body.addView(button("Add contact") {
                startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE))
            }, LinearLayout.LayoutParams(-1, dp(58)))
            return
        }
        var row: LinearLayout? = null
        filtered.forEachIndexed { index, person ->
            if (index % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                body.addView(row, LinearLayout.LayoutParams(-1, dp(250)).apply { bottomMargin = dp(10) })
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 18); clipToOutline = true
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            val photo = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackground(rounded(Color.LTGRAY, 14))
                try {
                    if (person.photo != null) setImageURI(person.photo)
                    if (drawable == null) setImageDrawable(null)
                } catch (_: Exception) {}
            }
            if (photo.drawable == null) {
                val initials = text(person.name.take(1).uppercase(), 46f, blue, true)
                initials.gravity = Gravity.CENTER; initials.background = rounded(0xFFE4ECFA.toInt(), 14)
                card.addView(initials, LinearLayout.LayoutParams(-1, 0, 1f))
            } else card.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
            val name = text(person.name, 17f, ink, true).apply {
                gravity = Gravity.CENTER; maxLines = 1; setPadding(dp(2), dp(6), dp(2), dp(2))
            }
            card.addView(name, LinearLayout.LayoutParams(-1, dp(34)))
            card.addView(button("☎  CALL", blue) { dial(person.number) }, LinearLayout.LayoutParams(-1, dp(46)))
            card.setOnLongClickListener {
                Toast.makeText(this, "Change the contact photo in your phone's Contacts app.", Toast.LENGTH_LONG).show()
                true
            }
            val lp = LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (index % 2 == 0) rightMargin = dp(5) else leftMargin = dp(5)
            }
            row?.addView(card, lp)
        }
        body.addView(button("+  Add contact", blue) {
            startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE))
        }, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun renderKeypad() {
        body.addView(text("Dial a number", 24f, ink, true))
        val number = EditText(this).apply {
            hint = "Enter phone number"; textSize = 25f; gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(8), dp(14), dp(8), dp(14)); background = rounded(Color.WHITE, 16)
        }
        body.addView(number, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(12) })
        val digits = listOf("1","2","3","4","5","6","7","8","9","*","0","#")
        for (r in 0..3) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..2) {
                val d = digits[r*3+c]
                val b = text(d, 27f, ink, true).apply {
                    gravity = Gravity.CENTER; background = rounded(Color.WHITE, 18)
                    setOnClickListener { number.append(d) }
                }
                line.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            body.addView(line)
        }
        body.addView(button("☎  CALL", 0xFF159B62.toInt()) { if (number.text.isNotBlank()) dial(number.text.toString()) },
            LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(12) })
    }

    private fun dial(number: String) {
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
        catch (_: Exception) { Toast.makeText(this, "Unable to open phone dialer", Toast.LENGTH_SHORT).show() }
    }

    private fun showPermissionMessage() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        body.addView(text("Allow contact access to show your family photos and phone numbers.", 19f))
        body.addView(button("Allow contacts", blue) { permission.launch(Manifest.permission.READ_CONTACTS) },
            LinearLayout.LayoutParams(-1, dp(60)))
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
            if (page == "Contacts" || page == "Favorites") renderPeople()
        }
    }
}
