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
package org.quantumbadger.redreader.cache

import android.database.Cursor
import org.quantumbadger.redreader.cache.CacheCompressionType.Companion.fromDatabaseId
import org.quantumbadger.redreader.common.General
import java.net.URI
import java.util.*

class CacheEntry internal constructor(cursor: Cursor) {
    @JvmField
	val id: Long
    val url: URI?
    @JvmField
	val session: UUID
    @JvmField
	val timestamp: Long
    @JvmField
	val mimetype: String
    @JvmField
	val cacheCompressionType: CacheCompressionType
    val lengthCompressed: Long
    val lengthUncompressed: Long

    init {
        id = cursor.getLong(0)
        url = General.uriFromString(cursor.getString(1))
        session = UUID.fromString(cursor.getString(2))
        timestamp = cursor.getLong(3)
        mimetype = cursor.getString(4)
        cacheCompressionType = fromDatabaseId(
            cursor.getInt(5)
        )
        lengthCompressed = cursor.getLong(6)
        lengthUncompressed = cursor.getLong(7)
    }

    companion object {
        @JvmField
		val DB_FIELDS = arrayOf(
            CacheDbManager.FIELD_ID,
            CacheDbManager.FIELD_URL,
            CacheDbManager.FIELD_SESSION,
            CacheDbManager.FIELD_TIMESTAMP,
            CacheDbManager.FIELD_MIMETYPE,
            CacheDbManager.FIELD_COMPRESSION_TYPE,
            CacheDbManager.FIELD_LENGTH_COMPRESSED,
            CacheDbManager.FIELD_LENGTH_UNCOMPRESSED
        )
    }
}
