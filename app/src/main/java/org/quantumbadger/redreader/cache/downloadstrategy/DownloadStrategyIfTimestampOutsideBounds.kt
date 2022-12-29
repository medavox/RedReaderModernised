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
package org.quantumbadger.redreader.cache.downloadstrategy

import org.quantumbadger.redreader.cache.CacheEntry
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategy
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyNever
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyAlways
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfNotCached
import org.quantumbadger.redreader.common.TimestampBound

class DownloadStrategyIfTimestampOutsideBounds(private val mTimestampBound: TimestampBound) :
    DownloadStrategy {
    override fun shouldDownloadWithoutCheckingCache(): Boolean {
        return false
    }

    override fun shouldDownloadIfCacheEntryFound(entry: CacheEntry): Boolean {
        return !mTimestampBound.verifyTimestamp(entry.timestamp)
    }

    override fun shouldDownloadIfNotCached(): Boolean {
        return true
    }
}
