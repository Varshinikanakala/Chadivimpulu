package com.chadivimpulu.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Entry(
    val id: Long,
    val name: String,
    val place: String,
    val amount: Long,
    val gift: String,
    val function: String,
    val date: String,
    val received: Boolean,
) {
    val personKey: String get() = personKeyOf(name, place)
}

fun personKeyOf(name: String, place: String): String =
    name.trim().lowercase() + "|" + place.trim().lowercase()

data class Totals(val received: Long, val given: Long, val count: Int)

fun List<Entry>.totals() = Totals(
    received = filter { it.received }.sumOf { it.amount },
    given = filter { !it.received }.sumOf { it.amount },
    count = size,
)

data class Person(val name: String, val place: String, val key: String)

fun List<Entry>.people(): List<Person> =
    groupBy { it.personKey }.map { (key, list) ->
        val latest = list.maxBy { it.id }
        Person(latest.name, latest.place, key)
    }

private val indianFormat = NumberFormat.getInstance(Locale("en", "IN"))
fun rupees(value: Long): String = "₹" + indianFormat.format(value)

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
fun today(): String = LocalDate.now().format(dateFormat)

class EntryStore(context: Context) {
    private val file = File(context.filesDir, "entries.json")

    fun load(): List<Entry> = try {
        if (file.exists()) fromJson(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(entries: List<Entry>) {
        val temp = File(file.parentFile, "entries.tmp")
        temp.writeText(toJson(entries))
        temp.renameTo(file)
    }

    companion object {
        fun toJson(entries: List<Entry>): String {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("place", e.place)
                        .put("amount", e.amount)
                        .put("gift", e.gift)
                        .put("function", e.function)
                        .put("date", e.date)
                        .put("received", e.received)
                )
            }
            return JSONObject()
                .put("app", "chadivimpulu")
                .put("version", 1)
                .put("entries", array)
                .toString(2)
        }

        fun fromJson(text: String): List<Entry> {
            val array = JSONObject(text).getJSONArray("entries")
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(
                    id = o.getLong("id"),
                    name = o.optString("name"),
                    place = o.optString("place"),
                    amount = o.optLong("amount"),
                    gift = o.optString("gift"),
                    function = o.optString("function"),
                    date = o.optString("date"),
                    received = o.optBoolean("received", true),
                )
            }
        }
    }
}
