package com.pamurlykin.sportsactivityassistant.text

import androidx.annotation.StringRes

/** Application resources, also available to validators running outside a composition.
 * The resolver owns no Activity. JVM tests install a resolver for the same XML resources.
 */
object AppText {
    @Volatile private var resolver: ((Int, Array<out Any?>) -> String)? = null

    fun install(resolve: (Int, Array<out Any?>) -> String) { resolver = resolve }

    fun get(@StringRes id: Int, vararg arguments: Any?): String =
        checkNotNull(resolver) { "Application text resources have not been initialized" }(id, arguments)
}
