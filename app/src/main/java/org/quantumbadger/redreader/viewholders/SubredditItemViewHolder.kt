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
package org.quantumbadger.redreader.viewholders

import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.view.ViewGroup
import org.quantumbadger.redreader.activities.BaseActivity
import android.view.LayoutInflater
import android.view.View
import org.quantumbadger.redreader.common.RRThemeAttributes
import android.widget.FrameLayout
import org.apache.commons.text.StringEscapeUtils
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.common.LinkHandler
import org.quantumbadger.redreader.common.Optional
import org.quantumbadger.redreader.views.SubredditToolbar
import org.quantumbadger.redreader.common.PrefsUtility
import org.quantumbadger.redreader.reddit.SubredditDetails
import org.quantumbadger.redreader.reddit.prepared.bodytext.BodyElement
import org.quantumbadger.redreader.reddit.prepared.html.HtmlReader
import java.text.NumberFormat
import java.util.*

class SubredditItemViewHolder(
    parent: ViewGroup,
    private val mActivity: BaseActivity
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context)
        .inflate(R.layout.subreddit_item_view, parent, false)
) {
    private val mTheme: RRThemeAttributes
    private val mBodyFontScale: Float
    private val mPrimaryText: TextView
    private val mSubText: TextView
    private val mSupportingText: FrameLayout
    private val mActions: SubredditToolbar
    private val mGoButton: View

    init {
        mTheme = RRThemeAttributes(mActivity)
        mBodyFontScale = PrefsUtility.appearance_fontscale_bodytext()
        mPrimaryText = itemView.findViewById(R.id.subreddit_item_view_primary_text)
        mSubText = itemView.findViewById(R.id.subreddit_item_view_sub_text)
        mSupportingText = itemView.findViewById(R.id.subreddit_item_view_supporting_text)
        mActions = itemView.findViewById(R.id.subreddit_item_view_actions)
        mGoButton = itemView.findViewById(R.id.subreddit_item_view_go)
    }

    fun bind(subreddit: SubredditDetails) {
        mPrimaryText.text = subreddit.name
        val subtitle: String?
        subtitle = if (subreddit.subscribers == null) {
            null
        } else {
            mActivity.getString(
                R.string.header_subscriber_count,
                NumberFormat.getNumberInstance(Locale.getDefault())
                    .format(subreddit.subscribers)
            )
        }
        if (subtitle == null) {
            mSubText.visibility = View.GONE
        } else {
            mSubText.visibility = View.VISIBLE
            mSubText.text = subtitle
        }
        mSupportingText.removeAllViews()
        if (subreddit.publicDescriptionHtmlEscaped != null
            && !subreddit.publicDescriptionHtmlEscaped.trim { it <= ' ' }.isEmpty()
        ) {
            val body = HtmlReader.parse(
                StringEscapeUtils.unescapeHtml4(subreddit.publicDescriptionHtmlEscaped),
                mActivity
            )
            mSupportingText.visibility = View.VISIBLE
            mSupportingText.addView(
                body.generateView(
                    mActivity,
                    mTheme.rrCommentBodyCol,
                    13.0f * mBodyFontScale,
                    false
                )
            )
        } else {
            mSupportingText.visibility = View.GONE
        }
        mActions.bindSubreddit(subreddit, Optional.empty())
        mGoButton.setOnClickListener { v: View? ->
            LinkHandler.onLinkClicked(
                mActivity,
                subreddit.url
            )
        }
    }
}
