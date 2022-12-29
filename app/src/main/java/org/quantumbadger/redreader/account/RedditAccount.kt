/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.quantumbadger.redreader.account

import org.quantumbadger.redreader.reddit.api.RedditOAuth.RefreshToken
import org.quantumbadger.redreader.reddit.api.RedditOAuth.AccessToken
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.common.StringUtils
import java.lang.RuntimeException

class RedditAccount(
    username: String,
    refreshToken: RefreshToken,
    usesNewClientId: Boolean,
    priority: Long
) {
    @JvmField
	val username: String
    @JvmField
	val refreshToken: RefreshToken
    @JvmField
	val usesNewClientId: Boolean

    @get:Synchronized
    var mostRecentAccessToken: AccessToken? = null
        private set
    @JvmField
	val priority: Long

    init {
        if (username == null) {
            throw RuntimeException("Null user in RedditAccount")
        }
        this.username = username.trim { it <= ' ' }
        this.refreshToken = refreshToken
        this.usesNewClientId = usesNewClientId
        this.priority = priority
    }

    val isAnonymous: Boolean
        get() = username.isEmpty()
    val isNotAnonymous: Boolean
        get() = !isAnonymous
    val canonicalUsername: String
        get() = StringUtils.asciiLowercase(username.trim { it <= ' ' })

    @Synchronized
    fun setAccessToken(token: AccessToken?) {
        mostRecentAccessToken = token
    }

    override fun equals(o: Any?): Boolean {
        return (o is RedditAccount
                && username.equals(o.username, ignoreCase = true))
    }

    override fun hashCode(): Int {
        return canonicalUsername.hashCode()
    }
}
