package io.github.asadhabib.datausage.data

import java.util.Locale

/** Follows the phone language: Indonesian when the system is set to it, English otherwise. */
object L {
    val id: Boolean get() = Locale.getDefault().language.let { it == "in" || it == "id" }
    fun t(en: String, id: String) = if (this.id) id else en
}