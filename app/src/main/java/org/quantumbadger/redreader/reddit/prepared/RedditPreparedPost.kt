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
package org.quantumbadger.redreader.reddit.prepared

import android.app.AlertDialog
import android.content.*
import org.quantumbadger.redreader.cache.CacheManager
import org.quantumbadger.redreader.reddit.prepared.RedditParsedPost
import org.quantumbadger.redreader.reddit.prepared.RedditChangeDataManager
import kotlin.jvm.Volatile
import android.graphics.Bitmap
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost.ThumbnailLoadedCallback
import org.quantumbadger.redreader.views.RedditPostView
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.account.RedditAccountManager
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost
import org.quantumbadger.redreader.jsonwrap.JsonObject
import org.quantumbadger.redreader.activities.BaseActivity
import org.quantumbadger.redreader.common.PrefsUtility.AppearancePostSubtitleItem
import android.content.res.TypedArray
import androidx.annotation.StringRes
import org.quantumbadger.redreader.reddit.prepared.RedditParsedPost.ImagePreviewDetails
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfNotCached
import org.quantumbadger.redreader.cache.CacheRequestCallbacks
import org.quantumbadger.redreader.common.datastream.SeekableInputStream
import org.quantumbadger.redreader.http.FailedRequestBody
import androidx.appcompat.app.AppCompatActivity
import org.quantumbadger.redreader.reddit.RedditAPI.RedditAction
import org.quantumbadger.redreader.reddit.RedditAPI
import android.widget.Toast
import org.quantumbadger.redreader.reddit.APIResponseHandler.ActionResponseHandler
import org.quantumbadger.redreader.activities.BugReportActivity
import org.quantumbadger.redreader.reddit.APIResponseHandler.APIFailureType
import org.quantumbadger.redreader.views.bezelmenu.SideToolbarOverlay
import org.quantumbadger.redreader.views.bezelmenu.VerticalToolbar
import android.widget.ImageButton
import android.view.LayoutInflater
import androidx.appcompat.widget.TooltipCompat
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import org.apache.commons.text.StringEscapeUtils
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.image.ThumbnailScaler
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost.RPVMenuItem
import org.quantumbadger.redreader.reddit.things.SubredditCanonicalId
import org.quantumbadger.redreader.reddit.api.RedditSubredditSubscriptionManager
import org.quantumbadger.redreader.reddit.api.SubredditSubscriptionState
import org.quantumbadger.redreader.reddit.things.InvalidSubredditNameException
import org.quantumbadger.redreader.activities.CommentEditActivity
import org.quantumbadger.redreader.activities.WebViewActivity
import org.quantumbadger.redreader.fragments.ShareOrderDialog
import org.quantumbadger.redreader.activities.PostListingActivity
import org.quantumbadger.redreader.reddit.url.SubredditPostListURL
import org.quantumbadger.redreader.reddit.url.UserProfileURL
import org.quantumbadger.redreader.fragments.PostPropertiesDialog
import org.quantumbadger.redreader.views.RedditPostView.PostSelectionListener
import org.quantumbadger.redreader.activities.MainActivity
import org.quantumbadger.redreader.activities.CommentReplyActivity
import org.quantumbadger.redreader.cache.CacheRequest
import org.quantumbadger.redreader.common.*
import org.quantumbadger.redreader.common.Optional
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.util.*

class RedditPreparedPost(
    context: Context,
    cm: CacheManager,
    val listId: Int,
    @JvmField val src: RedditParsedPost,
    timestamp: Long,
    private val showSubreddit: Boolean,
    showThumbnails: Boolean,
    allowHighResThumbnails: Boolean,
    private val mShowInlinePreviews: Boolean
) : RedditChangeDataManager.Listener {
    private val mChangeDataManager: RedditChangeDataManager
    val isArchived: Boolean
    @JvmField
	val isLocked: Boolean
    val canModerate: Boolean
    @JvmField val hasThumbnail: Boolean
    val mIsProbablyAnImage: Boolean

    // TODO make it possible to turn off in-memory caching when out of memory
    @Volatile
    private var thumbnailCache: Bitmap? = null
    private var thumbnailCallback: ThumbnailLoadedCallback? = null
    private var usageId = -1
    var lastChange: Long
    private var mBoundView: RedditPostView? = null

    enum class Action(val descriptionResId: Int) {
        UPVOTE(R.string.action_upvote), UNVOTE(R.string.action_vote_remove), DOWNVOTE(R.string.action_downvote), SAVE(
            R.string.action_save
        ),
        HIDE(R.string.action_hide), UNSAVE(R.string.action_unsave), UNHIDE(
            R.string.action_unhide
        ),
        EDIT(R.string.action_edit), DELETE(R.string.action_delete), REPORT(
            R.string.action_report
        ),
        SHARE(R.string.action_share), REPLY(R.string.action_reply), USER_PROFILE(
            R.string.action_user_profile
        ),
        EXTERNAL(R.string.action_external), PROPERTIES(R.string.action_properties), COMMENTS(
            R.string.action_comments
        ),
        LINK(R.string.action_link), COMMENTS_SWITCH(R.string.action_comments_switch), LINK_SWITCH(
            R.string.action_link_switch
        ),
        SHARE_COMMENTS(R.string.action_share_comments), SHARE_IMAGE(
            R.string.action_share_image
        ),
        GOTO_SUBREDDIT(R.string.action_gotosubreddit), ACTION_MENU(
            R.string.action_actionmenu
        ),
        SAVE_IMAGE(R.string.action_save_image), COPY(R.string.action_copy_link), COPY_SELFTEXT(
            R.string.action_copy_selftext
        ),
        SELFTEXT_LINKS(R.string.action_selftext_links), BACK(
            R.string.action_back
        ),
        BLOCK(R.string.action_block_subreddit), UNBLOCK(R.string.action_unblock_subreddit), PIN(
            R.string.action_pin_subreddit
        ),
        UNPIN(R.string.action_unpin_subreddit), SUBSCRIBE(R.string.action_subscribe_subreddit), UNSUBSCRIBE(
            R.string.action_unsubscribe_subreddit
        );
    }

    // TODO too many parameters
    init {
        val user = RedditAccountManager.getInstance(context).defaultAccount
        mChangeDataManager = RedditChangeDataManager.getInstance(user)
        isArchived = src.isArchived
        isLocked = src.isLocked
        canModerate = src.canModerate()
        mIsProbablyAnImage = LinkHandler.isProbablyAnImage(src.url)
        hasThumbnail = showThumbnails && hasThumbnail(src)
        val thumbnailWidth = General.dpToPixels(
            context,
            PrefsUtility.images_thumbnail_size_dp().toFloat()
        )
        if (hasThumbnail && hasThumbnail(src) && !shouldShowInlinePreview()) {
            downloadThumbnail(context, allowHighResThumbnails, thumbnailWidth, cm, listId)
        }
        lastChange = timestamp
        mChangeDataManager.update(timestamp, src.src)
    }

    fun shouldShowInlinePreview(): Boolean {
        return mShowInlinePreviews && (src.isPreviewEnabled
                || "gfycat.com" == src.domain || "i.imgur.com" == src.domain || "streamable.com" == src.domain || "i.redd.it" == src.domain || "v.redd.it" == src.domain)
    }

    val isVideoPreview: Boolean
        get() {
            val preview = src.src.preview ?: return false
            return (java.lang.Boolean.TRUE == src.src.is_video || preview.getAtPath(
                "images",
                0,
                "variants",
                "mp4"
            ).isPresent
                    || preview.getObject("reddit_video_preview") != null || "v.redd.it" == src.domain || "streamable.com" == src.domain || "gfycat.com" == src.domain)
        }

    fun performAction(activity: BaseActivity, action: Action?) {
        onActionMenuItemSelected(this, activity, action)
    }

    fun computeScore(): Int {
        var score = src.scoreExcludingOwnVote
        if (isUpvoted) {
            score++
        } else if (isDownvoted) {
            score--
        }
        return score
    }

    fun buildSubtitle(
        context: Context,
        headerMode: Boolean
    ): SpannableStringBuilder {
        val mPostSubtitleItems: EnumSet<AppearancePostSubtitleItem>
        val mPostAgeUnits: Int
        if (headerMode
            && PrefsUtility.appearance_post_subtitle_items_use_different_settings()
        ) {
            mPostSubtitleItems = PrefsUtility.appearance_post_header_subtitle_items()
            mPostAgeUnits = PrefsUtility.appearance_post_header_age_units()
        } else {
            mPostSubtitleItems = PrefsUtility.appearance_post_subtitle_items()
            mPostAgeUnits = PrefsUtility.appearance_post_age_units()
        }
        val appearance = context.obtainStyledAttributes(
            intArrayOf(
                R.attr.rrPostSubtitleBoldCol,
                R.attr.rrPostSubtitleUpvoteCol,
                R.attr.rrPostSubtitleDownvoteCol,
                R.attr.rrFlairBackCol,
                R.attr.rrFlairTextCol,
                R.attr.rrGoldTextCol,
                R.attr.rrGoldBackCol
            )
        )
		val boldCol: Int = if (headerMode) {
			Color.WHITE
		} else {
			appearance.getColor(0, 255)
		}
        val rrPostSubtitleUpvoteCol = appearance.getColor(1, 255)
        val rrPostSubtitleDownvoteCol = appearance.getColor(2, 255)
        val rrFlairBackCol = appearance.getColor(3, 255)
        val rrFlairTextCol = appearance.getColor(4, 255)
        val rrGoldTextCol = appearance.getColor(5, 255)
        val rrGoldBackCol = appearance.getColor(6, 255)
        appearance.recycle()
        val postListDescSb = BetterSSB()
        val pointsCol: Int
        val score = computeScore()
        pointsCol = if (isUpvoted) {
            rrPostSubtitleUpvoteCol
        } else if (isDownvoted) {
            rrPostSubtitleDownvoteCol
        } else {
            boldCol
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SPOILER)) {
            if (src.isSpoiler) {
                postListDescSb.append(
                    " SPOILER ",
                    BetterSSB.BOLD
                            or BetterSSB.FOREGROUND_COLOR
                            or BetterSSB.BACKGROUND_COLOR,
                    Color.WHITE,
                    Color.rgb(50, 50, 50),
                    1f
                )
                postListDescSb.append("  ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.STICKY)) {
            if (src.isStickied) {
                postListDescSb.append(
                    " STICKY ",
                    BetterSSB.BOLD
                            or BetterSSB.FOREGROUND_COLOR
                            or BetterSSB.BACKGROUND_COLOR,
                    Color.WHITE,
                    Color.rgb(0, 170, 0),
                    1f
                ) // TODO color?
                postListDescSb.append("  ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.NSFW)) {
            if (src.isNsfw) {
                postListDescSb.append(
                    " NSFW ",
                    BetterSSB.BOLD
                            or BetterSSB.FOREGROUND_COLOR
                            or BetterSSB.BACKGROUND_COLOR,
                    Color.WHITE,
                    Color.RED,
                    1f
                ) // TODO color?
                postListDescSb.append("  ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.FLAIR)) {
            if (src.flairText != null) {
                postListDescSb.append(
                    " "
                            + src.flairText
                            + General.LTR_OVERRIDE_MARK
                            + " ",
                    BetterSSB.BOLD
                            or BetterSSB.FOREGROUND_COLOR
                            or BetterSSB.BACKGROUND_COLOR,
                    rrFlairTextCol,
                    rrFlairBackCol,
                    1f
                )
                postListDescSb.append("  ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.COMMENTS)) {
            postListDescSb.append(
                src.commentCount.toString(),
                BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                boldCol,
                0,
                1f
            )
            postListDescSb.append(
                BetterSSB.NBSP.toString() + context.getString(R.string.subtitle_comments) + " ",
                0
            )
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SCORE)) {
            postListDescSb.append(
                score.toString(),
                BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                pointsCol,
                0,
                1f
            )
            postListDescSb.append(
                BetterSSB.NBSP.toString() + context.getString(R.string.subtitle_points) + " ",
                0
            )
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.UPVOTE_RATIO)) {
            postListDescSb.append("(", 0)
            postListDescSb.append(
                src.upvotePercentage.toString() + "%",
                BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                boldCol,
                0,
                1f
            )
            postListDescSb.append(
                BetterSSB.NBSP.toString() + context.getString(R.string.subtitle_upvote_ratio) + ") ",
                0
            )
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.GOLD)) {
            if (src.goldAmount > 0) {
                if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SCORE)
                    || mPostSubtitleItems.contains(
                        AppearancePostSubtitleItem.UPVOTE_RATIO
                    )
                ) {
                    postListDescSb.append(" ", 0)
                }
                postListDescSb.append(
                    " "
                            + context.getString(R.string.gold)
                            + BetterSSB.NBSP
                            + "x"
                            + src.goldAmount
                            + " ",
                    BetterSSB.FOREGROUND_COLOR or BetterSSB.BACKGROUND_COLOR,
                    rrGoldTextCol,
                    rrGoldBackCol,
                    1f
                )
                postListDescSb.append("  ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.AGE)) {
            postListDescSb.append(
                RRTime.formatDurationFrom(
                    context,
                    src.createdTimeSecsUTC * 1000,
                    R.string.time_ago,
                    mPostAgeUnits
                ),
                BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                boldCol,
                0,
                1f
            )
            postListDescSb.append(" ", 0)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.AUTHOR)) {
            postListDescSb.append(context.getString(R.string.subtitle_by) + " ", 0)
            val setBackgroundColour: Boolean
            val backgroundColour: Int // TODO color from theme
            if ("moderator" == src.distinguished) {
                setBackgroundColour = true
                backgroundColour = Color.rgb(0, 170, 0)
            } else if ("admin" == src.distinguished) {
                setBackgroundColour = true
                backgroundColour = Color.rgb(170, 0, 0)
            } else {
                setBackgroundColour = false
                backgroundColour = 0
            }
            if (setBackgroundColour) {
                postListDescSb.append(
                    BetterSSB.NBSP.toString() + src.author + BetterSSB.NBSP,
                    BetterSSB.BOLD
                            or BetterSSB.FOREGROUND_COLOR
                            or BetterSSB.BACKGROUND_COLOR,
                    Color.WHITE,
                    backgroundColour,
                    1f
                )
            } else {
                postListDescSb.append(
                    src.author,
                    BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                    boldCol,
                    0,
                    1f
                )
            }
            postListDescSb.append(" ", 0)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SUBREDDIT)) {
            if (showSubreddit) {
                postListDescSb.append(context.getString(R.string.subtitle_to) + " ", 0)
                postListDescSb.append(
                    src.subreddit,
                    BetterSSB.BOLD or BetterSSB.FOREGROUND_COLOR,
                    boldCol,
                    0,
                    1f
                )
                postListDescSb.append(" ", 0)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.DOMAIN)) {
            postListDescSb.append("(" + src.domain + ")", 0)
        }
        return postListDescSb.get()
    }

    fun buildAccessibilitySubtitle(
        context: Context,
        headerMode: Boolean
    ): String {
        val mPostSubtitleItems: EnumSet<AppearancePostSubtitleItem>
        val mPostAgeUnits: Int
        if (headerMode
            && PrefsUtility.appearance_post_subtitle_items_use_different_settings()
        ) {
            mPostSubtitleItems = PrefsUtility.appearance_post_header_subtitle_items()
            mPostAgeUnits = PrefsUtility.appearance_post_header_age_units()
        } else {
            mPostSubtitleItems = PrefsUtility.appearance_post_subtitle_items()
            mPostAgeUnits = PrefsUtility.appearance_post_age_units()
        }
        val accessibilitySubtitle = StringBuilder()
        val score = computeScore()
        val separator = " \n"
        val conciseMode = PrefsUtility.pref_accessibility_concise_mode()

        // When not in concise mode, add embellishments to the subtitle for greater clarity and
        // retention of familiar behaviour.
        if (!conciseMode) {
            accessibilitySubtitle.append(buildAccessibilityEmbellishments(context, headerMode))
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.COMMENTS)) {
            accessibilitySubtitle
                .append(
                    context.resources.getQuantityString(
                        R.plurals.accessibility_subtitle_comments_withperiod_plural,
                        src.commentCount,
                        src.commentCount
                    )
                )
                .append(separator)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SCORE)) {
            accessibilitySubtitle
                .append(
                    context.resources.getQuantityString(
                        if (conciseMode) R.plurals.accessibility_subtitle_points_withperiod_concise_plural else R.plurals.accessibility_subtitle_points_withperiod_plural,
                        score,
                        score
                    )
                )
                .append(separator)
            if (isUpvoted) {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            R.string.accessibility_subtitle_upvoted_withperiod
                        )
                    )
                    .append(separator)
            }
            if (isDownvoted) {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            R.string.accessibility_subtitle_downvoted_withperiod
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.UPVOTE_RATIO)) {
            accessibilitySubtitle
                .append(
                    context.getString(
                        if (conciseMode) R.string.accessibility_subtitle_upvote_ratio_withperiod_concise else R.string.accessibility_subtitle_upvote_ratio_withperiod,
                        src.upvotePercentage
                    )
                )
                .append(separator)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.GOLD)) {
            if (src.goldAmount > 0) {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            R.string.accessibility_subtitle_gold_withperiod,
                            src.goldAmount
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.AGE)) {
            accessibilitySubtitle
                .append(
                    context.getString(
                        R.string.accessibility_subtitle_age_withperiod,
                        RRTime.formatDurationFrom(
                            context,
                            src.createdTimeSecsUTC * 1000,
                            R.string.time_ago,
                            mPostAgeUnits
                        )
                    )
                )
                .append(separator)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SUBREDDIT)) {
            if (showSubreddit) {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            if (conciseMode) R.string.accessibility_subtitle_subreddit_withperiod_concise else R.string.accessibility_subtitle_subreddit_withperiod,
                            ScreenreaderPronunciation.getPronunciation(
                                context,
                                src.subreddit
                            )
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.DOMAIN)) {
            val domain = src.domain.lowercase()
            if (src.isSelfPost) {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            if (conciseMode) R.string.accessibility_subtitle_selfpost_withperiod_concise else R.string.accessibility_subtitle_selfpost_withperiod
                        )
                    )
                    .append(separator)
            } else {
                accessibilitySubtitle
                    .append(
                        context.getString(
                            if (conciseMode) R.string.accessibility_subtitle_domain_withperiod_concise else R.string.accessibility_subtitle_domain_withperiod,
                            ScreenreaderPronunciation.getPronunciation(context, domain)
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.AUTHOR)) {
            @StringRes val authorString: Int
            authorString = if ("moderator" == src.distinguished) {
                if (conciseMode) R.string.accessibility_subtitle_author_moderator_withperiod_concise_post else R.string.accessibility_subtitle_author_moderator_withperiod
            } else if ("admin" == src.distinguished) {
                if (conciseMode) R.string.accessibility_subtitle_author_admin_withperiod_concise_post else R.string.accessibility_subtitle_author_admin_withperiod
            } else {
                if (conciseMode) R.string.accessibility_subtitle_author_withperiod_concise_post else R.string.accessibility_subtitle_author_withperiod
            }
            accessibilitySubtitle
                .append(
                    context.getString(
                        authorString,
                        ScreenreaderPronunciation.getPronunciation(
                            context,
                            src.author
                        )
                    )
                )
                .append(separator)
        }
        return accessibilitySubtitle.toString()
    }

    fun buildAccessibilityTitle(
        context: Context,
        headerMode: Boolean
    ): String {
        val a11yTitle = StringBuilder()

        // When in concise mode, add embellishments to the title for greater interruptability when
        // navigating quickly.
        if (PrefsUtility.pref_accessibility_concise_mode()) {
            a11yTitle.append(buildAccessibilityEmbellishments(context, headerMode))
        }
        a11yTitle.append(src.title)

        // Append full stop so that subtitle doesn't become part of title
        a11yTitle.append(".\n")
        return a11yTitle.toString()
    }

    private fun buildAccessibilityEmbellishments(
        context: Context,
        headerMode: Boolean
    ): String {
        val mPostSubtitleItems: EnumSet<AppearancePostSubtitleItem>
        mPostSubtitleItems = if (headerMode
            && PrefsUtility.appearance_post_subtitle_items_use_different_settings()
        ) {
            PrefsUtility.appearance_post_header_subtitle_items()
        } else {
            PrefsUtility.appearance_post_subtitle_items()
        }
        val a11yEmbellish = StringBuilder()
        val separator = " \n"
        val conciseMode = PrefsUtility.pref_accessibility_concise_mode()
        if (isRead) {
            a11yEmbellish
                .append(
                    ScreenreaderPronunciation.getAccessibilityString(
                        context,
                        R.string.accessibility_post_already_read_withperiod
                    )
                )
                .append(separator)
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.SPOILER)) {
            if (src.isSpoiler) {
                a11yEmbellish
                    .append(
                        context.getString(
                            R.string.accessibility_subtitle_spoiler_withperiod
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.STICKY)) {
            if (src.isStickied) {
                a11yEmbellish
                    .append(
                        context.getString(
                            R.string.accessibility_subtitle_sticky_withperiod
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.NSFW)) {
            if (src.isNsfw) {
                a11yEmbellish
                    .append(
                        context.getString(
                            if (conciseMode) R.string.accessibility_subtitle_nsfw_withperiod_concise else R.string.accessibility_subtitle_nsfw_withperiod
                        )
                    )
                    .append(separator)
            }
        }
        if (mPostSubtitleItems.contains(AppearancePostSubtitleItem.FLAIR)) {
            if (src.flairText != null) {
                a11yEmbellish
                    .append(
                        context.getString(
                            if (conciseMode) R.string.accessibility_subtitle_flair_withperiod_concise else R.string.accessibility_subtitle_flair_withperiod,
                            src.flairText
                                    + General.LTR_OVERRIDE_MARK
                        )
                    )
                    .append(separator)
            }
        }
        return a11yEmbellish.toString()
    }

    private fun downloadThumbnail(
        context: Context,
        allowHighRes: Boolean,
        sizePixels: Int,
        cm: CacheManager,
        listId: Int
    ) {
        val preview = if (allowHighRes) src.getPreview(sizePixels, sizePixels) else null
        val uriStr: String?
        uriStr = preview?.url ?: src.thumbnailUrl
        val uri = General.uriFromString(uriStr)
        val priority = Constants.Priority.THUMBNAIL
        val fileType = Constants.FileType.THUMBNAIL
        val anon = RedditAccountManager.getAnon()
        cm.makeRequest(
            CacheRequest(
                uri,
                anon,
                null,
                Priority(priority, listId),
                DownloadStrategyIfNotCached.INSTANCE,
                fileType,
                CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
                context,
                object : CacheRequestCallbacks {
                    override fun onDataStreamComplete(
                        factory: GenericFactory<SeekableInputStream, IOException>,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean,
                        mimetype: String?
                    ) {
                        onThumbnailStreamAvailable(factory, sizePixels)
                    }

                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        httpStatus: Int?,
                        readableMessage: String?,
                        body: Optional<FailedRequestBody>
                    ) {
                        if (General.isSensitiveDebugLoggingEnabled()) {
                            Log.e(
                                TAG,
                                "Failed to download thumbnail "
                                        + uriStr
                                        + " with status "
                                        + httpStatus,
                                t
                            )
                        }
                    }
                })
        )
    }

    // These operations are ordered so as to avoid race conditions
    fun getThumbnail(
        callback: ThumbnailLoadedCallback?,
        usageId: Int
    ): Bitmap? {
        thumbnailCallback = callback
        this.usageId = usageId
        return thumbnailCache
    }

    val isSelf: Boolean
        get() = src.isSelfPost
    val isRead: Boolean
        get() = mChangeDataManager.isRead(src)

    fun bind(boundView: RedditPostView?) {
        mBoundView = boundView
        mChangeDataManager.addListener(src, this)
    }

    fun unbind(boundView: RedditPostView) {
        if (mBoundView == boundView) {
            mBoundView = null
            mChangeDataManager.removeListener(src, this)
        }
    }

    override fun onRedditDataChange(thingIdAndType: String) {
        if (mBoundView != null) {
            val context = mBoundView!!.context
            if (context != null) {
                mBoundView!!.updateAppearance()
            }
        }
    }

    // TODO handle download failure - show red "X" or something
    interface ThumbnailLoadedCallback {
        fun betterThumbnailAvailable(thumbnail: Bitmap?, usageId: Int)
    }

    fun markAsRead(context: Context?) {
        val user = RedditAccountManager.getInstance(context).defaultAccount
        RedditChangeDataManager.getInstance(user)
            .markRead(RRTime.utcCurrentTimeMillis(), src)
    }

    fun action(
        activity: AppCompatActivity?,
        @RedditAction action: Int
    ) {
        val user = RedditAccountManager.getInstance(activity).defaultAccount
        if (user.isAnonymous) {
            AndroidCommon.UI_THREAD_HANDLER.post { General.showMustBeLoggedInDialog(activity) }
            return
        }
        val lastVoteDirection = voteDirection
        val archived = src.isArchived
        val now = RRTime.utcCurrentTimeMillis()
        when (action) {
            RedditAPI.ACTION_DOWNVOTE -> if (!archived) {
                mChangeDataManager.markDownvoted(now, src)
            }
            RedditAPI.ACTION_UNVOTE -> if (!archived) {
                mChangeDataManager.markUnvoted(now, src)
            }
            RedditAPI.ACTION_UPVOTE -> if (!archived) {
                mChangeDataManager.markUpvoted(now, src)
            }
            RedditAPI.ACTION_SAVE -> mChangeDataManager.markSaved(now, src, true)
            RedditAPI.ACTION_UNSAVE -> mChangeDataManager.markSaved(now, src, false)
            RedditAPI.ACTION_HIDE -> mChangeDataManager.markHidden(now, src, true)
            RedditAPI.ACTION_UNHIDE -> mChangeDataManager.markHidden(now, src, false)
            RedditAPI.ACTION_REPORT -> {}
            RedditAPI.ACTION_DELETE -> {}
            else -> throw RuntimeException("Unknown post action")
        }
        val vote = (action == RedditAPI.ACTION_DOWNVOTE
                ) or (action == RedditAPI.ACTION_UPVOTE
                ) or (action == RedditAPI.ACTION_UNVOTE)
        if (archived && vote) {
            Toast.makeText(activity, R.string.error_archived_vote, Toast.LENGTH_SHORT)
                .show()
            return
        }
        RedditAPI.action(CacheManager.getInstance(activity),
            object : ActionResponseHandler(activity) {
                override fun onCallbackException(t: Throwable) {
                    BugReportActivity.handleGlobalError(context, t)
                }

                override fun onFailure(
                    type: Int,
                    t: Throwable?,
                    httpStatus: Int?,
                    readableMessage: String?,
                    response: Optional<FailedRequestBody>
                ) {
                    revertOnFailure()
                    val error = General.getGeneralErrorForFailure(
                        context,
                        type,
                        t,
                        httpStatus,
                        "Reddit API action code: "
                                + action
                                + " "
                                + src.idAndType,
                        response
                    )
                    General.showResultDialog(activity, error)
                }

                override fun onFailure(
                    type: APIFailureType,
                    debuggingContext: String?,
                    response: Optional<FailedRequestBody>
                ) {
                    revertOnFailure()
                    val error = General.getGeneralErrorForFailure(
                        context,
                        type,
                        debuggingContext,
                        response
                    )
                    General.showResultDialog(activity, error)
                }

                override fun onSuccess() {
                    val now = RRTime.utcCurrentTimeMillis()
                    when (action) {
                        RedditAPI.ACTION_DOWNVOTE -> mChangeDataManager.markDownvoted(now, src)
                        RedditAPI.ACTION_UNVOTE -> mChangeDataManager.markUnvoted(now, src)
                        RedditAPI.ACTION_UPVOTE -> mChangeDataManager.markUpvoted(now, src)
                        RedditAPI.ACTION_SAVE -> mChangeDataManager.markSaved(now, src, true)
                        RedditAPI.ACTION_UNSAVE -> mChangeDataManager.markSaved(now, src, false)
                        RedditAPI.ACTION_HIDE -> mChangeDataManager.markHidden(now, src, true)
                        RedditAPI.ACTION_UNHIDE -> mChangeDataManager.markHidden(now, src, false)
                        RedditAPI.ACTION_REPORT -> {}
                        RedditAPI.ACTION_DELETE -> General.quickToast(
                            activity,
                            R.string.delete_success
                        )
                        else -> throw RuntimeException("Unknown post action")
                    }
                }

                private fun revertOnFailure() {
                    val now = RRTime.utcCurrentTimeMillis()
                    when (action) {
                        RedditAPI.ACTION_DOWNVOTE, RedditAPI.ACTION_UNVOTE, RedditAPI.ACTION_UPVOTE -> {
                            when (lastVoteDirection) {
                                -1 -> mChangeDataManager.markDownvoted(now, src)
                                0 -> mChangeDataManager.markUnvoted(now, src)
                                1 -> mChangeDataManager.markUpvoted(now, src)
                            }
                            mChangeDataManager.markSaved(now, src, false)
                        }
                        RedditAPI.ACTION_SAVE -> mChangeDataManager.markSaved(now, src, false)
                        RedditAPI.ACTION_UNSAVE -> mChangeDataManager.markSaved(now, src, true)
                        RedditAPI.ACTION_HIDE -> mChangeDataManager.markHidden(now, src, false)
                        RedditAPI.ACTION_UNHIDE -> mChangeDataManager.markHidden(now, src, true)
                        RedditAPI.ACTION_REPORT -> {}
                        RedditAPI.ACTION_DELETE -> {}
                        else -> throw RuntimeException("Unknown post action")
                    }
                }
            }, user, src.idAndType, action, activity
        )
    }

    val isUpvoted: Boolean
        get() = mChangeDataManager.isUpvoted(src)
    val isDownvoted: Boolean
        get() = mChangeDataManager.isDownvoted(src)
    val voteDirection: Int
        get() = if (isUpvoted) 1 else if (isDownvoted) -1 else 0
    val isSaved: Boolean
        get() = mChangeDataManager.isSaved(src)
    val isHidden: Boolean
        get() = java.lang.Boolean.TRUE == mChangeDataManager.isHidden(src)

    private class RPVMenuItem {
        val title: String
        val action: Action

        constructor(title: String, action: Action) {
            this.title = title
            this.action = action
        }

        constructor(context: Context, titleRes: Int, action: Action) {
            title = context.getString(titleRes)
            this.action = action
        }
    }

    fun generateToolbar(
        activity: BaseActivity,
        isComments: Boolean,
        overlay: SideToolbarOverlay
    ): VerticalToolbar {
        val toolbar = VerticalToolbar(activity)
        val itemsPref = PrefsUtility.pref_menus_post_toolbar_items()
        val possibleItems = arrayOf(
            Action.ACTION_MENU,
            if (isComments) Action.LINK_SWITCH else Action.COMMENTS_SWITCH,
            Action.UPVOTE,
            Action.DOWNVOTE,
            Action.SAVE,
            Action.HIDE,
            Action.DELETE,
            Action.REPLY,
            Action.EXTERNAL,
            Action.SAVE_IMAGE,
            Action.SHARE,
            Action.COPY,
            Action.USER_PROFILE,
            Action.PROPERTIES
        )

        // TODO make static
        val iconsDark = EnumMap<Action, Int>(
            Action::class.java
        )
        iconsDark[Action.ACTION_MENU] = R.drawable.dots_vertical_dark
        iconsDark[Action.COMMENTS_SWITCH] = R.drawable.ic_action_comments_dark
        iconsDark[Action.LINK_SWITCH] =
            if (mIsProbablyAnImage) R.drawable.ic_action_image_dark else R.drawable.ic_action_link_dark
        iconsDark[Action.UPVOTE] = R.drawable.arrow_up_bold_dark
        iconsDark[Action.DOWNVOTE] = R.drawable.arrow_down_bold_dark
        iconsDark[Action.SAVE] = R.drawable.star_dark
        iconsDark[Action.HIDE] = R.drawable.ic_action_cross_dark
        iconsDark[Action.REPLY] = R.drawable.ic_action_reply_dark
        iconsDark[Action.EXTERNAL] = R.drawable.ic_action_external_dark
        iconsDark[Action.SAVE_IMAGE] = R.drawable.ic_action_save_dark
        iconsDark[Action.SHARE] = R.drawable.ic_action_share_dark
        iconsDark[Action.COPY] = R.drawable.ic_action_copy_dark
        iconsDark[Action.USER_PROFILE] = R.drawable.ic_action_person_dark
        iconsDark[Action.PROPERTIES] = R.drawable.ic_action_info_dark
        val iconsLight = EnumMap<Action, Int>(
            Action::class.java
        )
        iconsLight[Action.ACTION_MENU] = R.drawable.dots_vertical_light
        iconsLight[Action.COMMENTS_SWITCH] = R.drawable.ic_action_comments_light
        iconsLight[Action.LINK_SWITCH] =
            if (mIsProbablyAnImage) R.drawable.ic_action_image_light else R.drawable.ic_action_link_light
        iconsLight[Action.UPVOTE] = R.drawable.arrow_up_bold_light
        iconsLight[Action.DOWNVOTE] = R.drawable.arrow_down_bold_light
        iconsLight[Action.SAVE] = R.drawable.star_light
        iconsLight[Action.HIDE] = R.drawable.ic_action_cross_light
        iconsLight[Action.REPLY] = R.drawable.ic_action_reply_light
        iconsLight[Action.EXTERNAL] = R.drawable.ic_action_external_light
        iconsLight[Action.SAVE_IMAGE] = R.drawable.ic_action_save_light
        iconsLight[Action.SHARE] = R.drawable.ic_action_share_light
        iconsLight[Action.COPY] = R.drawable.ic_action_copy_light
        iconsLight[Action.USER_PROFILE] = R.drawable.ic_action_person_light
        iconsLight[Action.PROPERTIES] = R.drawable.ic_action_info_light
        for (action in possibleItems) {
            if (action == Action.SAVE_IMAGE && !mIsProbablyAnImage) {
                continue
            }
            if (itemsPref.contains(action)) {
                val ib = LayoutInflater.from(activity)
                    .inflate(
                        R.layout.flat_image_button,
                        toolbar,
                        false
                    ) as ImageButton
                val buttonPadding = General.dpToPixels(activity, 14f)
                ib.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
                if (action == Action.UPVOTE && isUpvoted || action == Action.DOWNVOTE && isDownvoted || action == Action.SAVE && isSaved || action == Action.HIDE && isHidden) {
                    ib.setBackgroundColor(Color.WHITE)
                    ib.setImageResource(iconsLight[action]!!)
                } else {
                    ib.setImageResource(iconsDark[action]!!)
                    // TODO highlight on click
                }
                ib.setOnClickListener { v: View? ->
                    val actionToTake: Action
                    actionToTake = when (action) {
                        Action.UPVOTE -> if (isUpvoted) Action.UNVOTE else Action.UPVOTE
                        Action.DOWNVOTE -> if (isDownvoted) Action.UNVOTE else Action.DOWNVOTE
                        Action.SAVE -> if (isSaved) Action.UNSAVE else Action.SAVE
                        Action.HIDE -> if (isHidden) Action.UNHIDE else Action.HIDE
                        else -> action
                    }
                    onActionMenuItemSelected(
                        this,
                        activity,
                        actionToTake
                    )
                    overlay.hide()
                }
                var accessibilityAction = action
                if (accessibilityAction == Action.UPVOTE && isUpvoted
                    || accessibilityAction == Action.DOWNVOTE && isDownvoted
                ) {
                    accessibilityAction = Action.UNVOTE
                }
                if (accessibilityAction == Action.SAVE && isSaved) {
                    accessibilityAction = Action.UNSAVE
                }
                if (accessibilityAction == Action.HIDE && isHidden) {
                    accessibilityAction = Action.UNHIDE
                }
                val text = activity.getString(accessibilityAction.descriptionResId)
                ib.contentDescription = text
                TooltipCompat.setTooltipText(ib, text)
                toolbar.addItem(ib)
            }
        }
        return toolbar
    }

    private fun onThumbnailStreamAvailable(
        factory: GenericFactory<SeekableInputStream, IOException>,
        desiredSizePixels: Int
    ) {
        try {
            factory.create().use { seekableInputStream ->
                val justDecodeBounds = BitmapFactory.Options()
                justDecodeBounds.inJustDecodeBounds = true
                BitmapFactory.decodeStream(seekableInputStream, null, justDecodeBounds)
                val width = justDecodeBounds.outWidth
                val height = justDecodeBounds.outHeight
                var factor = 1
                while (width / (factor + 1) > desiredSizePixels
                    && height / (factor + 1) > desiredSizePixels
                ) {
                    factor *= 2
                }
                val scaledOptions = BitmapFactory.Options()
                scaledOptions.inSampleSize = factor
                seekableInputStream.seek(0)
                seekableInputStream.mark(0)
                val data = BitmapFactory.decodeStream(
                    seekableInputStream,
                    null,
                    scaledOptions
                ) ?: return
                thumbnailCache = ThumbnailScaler.scale(data, desiredSizePixels)
                if (thumbnailCache != data) {
                    data.recycle()
                }
                if (thumbnailCallback != null) {
                    thumbnailCallback!!.betterThumbnailAvailable(
                        thumbnailCache,
                        usageId
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "Exception while downloading thumbnail",
                t
            )
        }
    }

    companion object {
        private const val TAG = "RedditPreparedPost"
        @JvmStatic
		fun showActionMenu(
            activity: BaseActivity,
            post: RedditPreparedPost
        ) {
            val itemPref = PrefsUtility.pref_menus_post_context_items()
            if (itemPref.isEmpty()) {
                return
            }
            val user = RedditAccountManager.getInstance(activity).defaultAccount
            val menu = ArrayList<RPVMenuItem>()
            if (!RedditAccountManager.getInstance(activity)
                    .defaultAccount
                    .isAnonymous
            ) {
                if (itemPref.contains(Action.UPVOTE)) {
                    if (!post.isUpvoted) {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_upvote,
                                Action.UPVOTE
                            )
                        )
                    } else {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_upvote_remove,
                                Action.UNVOTE
                            )
                        )
                    }
                }
                if (itemPref.contains(Action.DOWNVOTE)) {
                    if (!post.isDownvoted) {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_downvote,
                                Action.DOWNVOTE
                            )
                        )
                    } else {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_downvote_remove,
                                Action.UNVOTE
                            )
                        )
                    }
                }
            }
            if (itemPref.contains(Action.COMMENTS)) {
                menu.add(
                    RPVMenuItem(
                        String.format(
                            activity.getText(R.string.action_comments_with_count).toString(),
                            post.src.src.num_comments
                        ),
                        Action.COMMENTS
                    )
                )
            }
            if (!RedditAccountManager.getInstance(activity).defaultAccount.isAnonymous) {
                if (itemPref.contains(Action.SAVE)) {
                    if (!post.isSaved) {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_save,
                                Action.SAVE
                            )
                        )
                    } else {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_unsave,
                                Action.UNSAVE
                            )
                        )
                    }
                }
                if (itemPref.contains(Action.HIDE)) {
                    if (!post.isHidden) {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_hide,
                                Action.HIDE
                            )
                        )
                    } else {
                        menu.add(
                            RPVMenuItem(
                                activity,
                                R.string.action_unhide,
                                Action.UNHIDE
                            )
                        )
                    }
                }
                if (itemPref.contains(Action.EDIT)
                    && post.isSelf
                    && user.username.equals(post.src.author, ignoreCase = true)
                ) {
                    menu.add(RPVMenuItem(activity, R.string.action_edit, Action.EDIT))
                }
                if (itemPref.contains(Action.DELETE) && user.username.equals(
                        post.src
                            .author, ignoreCase = true
                    )
                ) {
                    menu.add(
                        RPVMenuItem(
                            activity,
                            R.string.action_delete,
                            Action.DELETE
                        )
                    )
                }
                if (itemPref.contains(Action.REPORT)) {
                    menu.add(
                        RPVMenuItem(
                            activity,
                            R.string.action_report,
                            Action.REPORT
                        )
                    )
                }
                if (itemPref.contains(Action.REPLY)
                    && !post.isArchived
                    && !(post.isLocked && !post.canModerate)
                ) {
                    menu.add(
                        RPVMenuItem(
                            activity,
                            R.string.action_reply,
                            Action.REPLY
                        )
                    )
                }
            }
            if (itemPref.contains(Action.EXTERNAL)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_external,
                        Action.EXTERNAL
                    )
                )
            }
            if (itemPref.contains(Action.SELFTEXT_LINKS)
                && post.src.rawSelfTextMarkdown != null && post.src.rawSelfTextMarkdown.length > 1
            ) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_selftext_links,
                        Action.SELFTEXT_LINKS
                    )
                )
            }
            if (itemPref.contains(Action.SAVE_IMAGE) && post.mIsProbablyAnImage) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_save_image,
                        Action.SAVE_IMAGE
                    )
                )
            }
            if (itemPref.contains(Action.GOTO_SUBREDDIT)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_gotosubreddit,
                        Action.GOTO_SUBREDDIT
                    )
                )
            }
            if (post.showSubreddit) {
                try {
                    val subredditCanonicalId = SubredditCanonicalId(post.src.subreddit)
                    if (itemPref.contains(Action.BLOCK)) {
                        if (PrefsUtility.pref_blocked_subreddits_check(subredditCanonicalId)) {
                            menu.add(
                                RPVMenuItem(
                                    activity,
                                    R.string.action_unblock_subreddit,
                                    Action.UNBLOCK
                                )
                            )
                        } else {
                            menu.add(
                                RPVMenuItem(
                                    activity,
                                    R.string.action_block_subreddit,
                                    Action.BLOCK
                                )
                            )
                        }
                    }
                    if (itemPref.contains(Action.PIN)) {
                        if (PrefsUtility.pref_pinned_subreddits_check(subredditCanonicalId)) {
                            menu.add(
                                RPVMenuItem(
                                    activity,
                                    R.string.action_unpin_subreddit,
                                    Action.UNPIN
                                )
                            )
                        } else {
                            menu.add(
                                RPVMenuItem(
                                    activity,
                                    R.string.action_pin_subreddit,
                                    Action.PIN
                                )
                            )
                        }
                    }
                    if (!RedditAccountManager.getInstance(activity)
                            .defaultAccount
                            .isAnonymous
                    ) {
                        if (itemPref.contains(Action.SUBSCRIBE)) {
                            val subscriptionManager = RedditSubredditSubscriptionManager
                                .getSingleton(
                                    activity,
                                    RedditAccountManager.getInstance(activity)
                                        .defaultAccount
                                )
                            if (subscriptionManager.areSubscriptionsReady()) {
                                if (subscriptionManager.getSubscriptionState(
                                        subredditCanonicalId
                                    )
                                    == SubredditSubscriptionState.SUBSCRIBED
                                ) {
                                    menu.add(
                                        RPVMenuItem(
                                            activity,
                                            R.string.action_unsubscribe_subreddit,
                                            Action.UNSUBSCRIBE
                                        )
                                    )
                                } else {
                                    menu.add(
                                        RPVMenuItem(
                                            activity,
                                            R.string.action_subscribe_subreddit,
                                            Action.SUBSCRIBE
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (ex: InvalidSubredditNameException) {
                    throw RuntimeException(ex)
                }
            }
            val url = post.src.url
            val isRedditVideo = url != null && url.contains("v.redd.it")
            if (itemPref.contains(Action.SHARE)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_share,
                        if (isRedditVideo) Action.SHARE_COMMENTS else Action.SHARE
                    )
                )
            }
            if (itemPref.contains(Action.SHARE_COMMENTS)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_share_comments,
                        Action.SHARE_COMMENTS
                    )
                )
            }
            if (itemPref.contains(Action.SHARE_IMAGE) && post.mIsProbablyAnImage) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_share_image,
                        Action.SHARE_IMAGE
                    )
                )
            }
            if (itemPref.contains(Action.COPY)) {
                menu.add(RPVMenuItem(activity, R.string.action_copy_link, Action.COPY))
            }
            if (itemPref.contains(Action.COPY_SELFTEXT)
                && post.src.rawSelfTextMarkdown != null && post.src.rawSelfTextMarkdown.length > 1
            ) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_copy_selftext,
                        Action.COPY_SELFTEXT
                    )
                )
            }
            if (itemPref.contains(Action.USER_PROFILE)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_user_profile,
                        Action.USER_PROFILE
                    )
                )
            }
            if (itemPref.contains(Action.PROPERTIES)) {
                menu.add(
                    RPVMenuItem(
                        activity,
                        R.string.action_properties,
                        Action.PROPERTIES
                    )
                )
            }
            val menuText = arrayOfNulls<String>(menu.size)
            for (i in menuText.indices) {
                menuText[i] = menu[i].title
            }
            val builder = AlertDialog.Builder(activity)
            builder.setItems(
                menuText
            ) { dialog: DialogInterface?, which: Int ->
                onActionMenuItemSelected(
                    post,
                    activity,
                    menu[which].action
                )
            }

            //builder.setNeutralButton(R.string.dialog_cancel, null);
            val alert = builder.create()
            alert.setCanceledOnTouchOutside(true)
            alert.show()
        }

        @JvmStatic
		fun onActionMenuItemSelected(
            post: RedditPreparedPost,
            activity: BaseActivity,
            action: Action?
        ) {
            when (action) {
                Action.UPVOTE -> post.action(activity, RedditAPI.ACTION_UPVOTE)
                Action.DOWNVOTE -> post.action(activity, RedditAPI.ACTION_DOWNVOTE)
                Action.UNVOTE -> post.action(activity, RedditAPI.ACTION_UNVOTE)
                Action.SAVE -> post.action(activity, RedditAPI.ACTION_SAVE)
                Action.UNSAVE -> post.action(activity, RedditAPI.ACTION_UNSAVE)
                Action.HIDE -> post.action(activity, RedditAPI.ACTION_HIDE)
                Action.UNHIDE -> post.action(activity, RedditAPI.ACTION_UNHIDE)
                Action.EDIT -> {
                    val editIntent = Intent(activity, CommentEditActivity::class.java)
                    editIntent.putExtra("commentIdAndType", post.src.idAndType)
                    editIntent.putExtra(
                        "commentText",
                        StringEscapeUtils.unescapeHtml4(post.src.rawSelfTextMarkdown)
                    )
                    editIntent.putExtra("isSelfPost", true)
                    activity.startActivity(editIntent)
                }
                Action.DELETE -> AlertDialog.Builder(activity)
                    .setTitle(R.string.accounts_delete)
                    .setMessage(R.string.delete_confirm)
                    .setPositiveButton(
                        R.string.action_delete
                    ) { dialog: DialogInterface?, which: Int ->
                        post.action(
                            activity,
                            RedditAPI.ACTION_DELETE
                        )
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
                Action.REPORT -> AlertDialog.Builder(activity)
                    .setTitle(R.string.action_report)
                    .setMessage(R.string.action_report_sure)
                    .setPositiveButton(
                        R.string.action_report
                    ) { dialog: DialogInterface?, which: Int ->
                        post.action(
                            activity,
                            RedditAPI.ACTION_REPORT
                        )
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
                Action.EXTERNAL -> {
                    try {
                        val url =
                            if (activity is WebViewActivity) activity.currentUrl else post.src.url
                        if (url == null) {
                            General.quickToast(activity, R.string.link_does_not_exist)
                            return
                        }
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        activity.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        General.quickToast(
                            activity,
                            R.string.action_not_handled_by_installed_app_toast
                        )
                    }
                }
                Action.SELFTEXT_LINKS -> {
                    val linksInComment: HashSet<String> = LinkHandler.computeAllLinks(
                        StringEscapeUtils.unescapeHtml4(
                            post.src
                                .rawSelfTextMarkdown
                        )
                    )
                    if (linksInComment.isEmpty()) {
                        General.quickToast(activity, R.string.error_toast_no_urls_in_self)
                    } else {
                        val linksArr = linksInComment.toTypedArray()
                        val builder = AlertDialog.Builder(activity)
                        builder.setItems(linksArr) { dialog: DialogInterface, which: Int ->
                            LinkHandler.onLinkClicked(
                                activity,
                                linksArr[which],
                                false,
                                post.src.src
                            )
                            dialog.dismiss()
                        }
                        val alert = builder.create()
                        alert.setTitle(R.string.action_selftext_links)
                        alert.setCanceledOnTouchOutside(true)
                        alert.show()
                    }
                }
                Action.SAVE_IMAGE -> {
                    FileUtils.saveImageAtUri(activity, post.src.url)
                }
                Action.SHARE -> {
                    val subject =
                        if (PrefsUtility.pref_behaviour_sharing_dialog()) post.src.title else null
                    LinkHandler.shareText(
                        activity,
                        subject,
                        post.src.url
                    )
                }
                Action.SHARE_COMMENTS -> {
                    val shareAsPermalink = PrefsUtility.pref_behaviour_share_permalink()
                    val mailer = Intent(Intent.ACTION_SEND)
                    mailer.type = "text/plain"
                    if (PrefsUtility.pref_behaviour_sharing_include_desc()) {
                        mailer.putExtra(
                            Intent.EXTRA_SUBJECT, String.format(
                                activity.getText(R.string.share_comments_for)
                                    .toString(), post.src.title
                            )
                        )
                    }
                    if (shareAsPermalink) {
                        mailer.putExtra(
                            Intent.EXTRA_TEXT,
                            Constants.Reddit.getNonAPIUri(post.src.permalink)
                                .toString()
                        )
                    } else {
                        mailer.putExtra(
                            Intent.EXTRA_TEXT,
                            Constants.Reddit.getNonAPIUri(
                                Constants.Reddit.PATH_COMMENTS
                                        + post.src.idAlone
                            )
                                .toString()
                        )
                    }
                    if (PrefsUtility.pref_behaviour_sharing_dialog()) {
                        ShareOrderDialog.newInstance(mailer)
                            .show(activity.supportFragmentManager, null)
                    } else {
                        activity.startActivity(
                            Intent.createChooser(
                                mailer,
                                activity.getString(R.string.action_share)
                            )
                        )
                    }
                }
                Action.SHARE_IMAGE -> {
                    FileUtils.shareImageAtUri(activity, post.src.url)
                }
                Action.COPY -> {
                    val clipboardManager =
                        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboardManager != null) {
                        val data = ClipData.newPlainText(
                            post.src.author,
                            post.src.url
                        )
                        clipboardManager.setPrimaryClip(data)
                        General.quickToast(
                            activity.applicationContext,
                            R.string.post_link_copied_to_clipboard
                        )
                    }
                }
                Action.COPY_SELFTEXT -> {
                    val clipboardManager =
                        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboardManager != null) {
                        val data = ClipData.newPlainText(
                            post.src.author,
                            post.src.rawSelfTextMarkdown
                        )
                        clipboardManager.setPrimaryClip(data)
                        General.quickToast(
                            activity.applicationContext,
                            R.string.post_text_copied_to_clipboard
                        )
                    }
                }
                Action.GOTO_SUBREDDIT -> {
                    try {
                        val intent = Intent(activity, PostListingActivity::class.java)
                        intent.data = SubredditPostListURL.getSubreddit(post.src.subreddit)
                            .generateJsonUri()
                        activity.startActivityForResult(intent, 1)
                    } catch (e: InvalidSubredditNameException) {
                        Toast.makeText(
                            activity,
                            R.string.invalid_subreddit_name,
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        BugReportActivity.handleGlobalError(
                            activity,
                            RuntimeException(
                                "Got exception for subreddit: " + post.src.subreddit,
                                e
                            )
                        )
                    }
                }
                Action.USER_PROFILE -> LinkHandler.onLinkClicked(
                    activity,
                    UserProfileURL(post.src.author).toString()
                )
                Action.PROPERTIES -> PostPropertiesDialog.newInstance(post.src.src)
                    .show(activity.supportFragmentManager, null)
                Action.COMMENTS -> {
                    (activity as PostSelectionListener).onPostCommentsSelected(post)
                    object : Thread() {
                        override fun run() {
                            post.markAsRead(activity)
                        }
                    }.start()
                }
                Action.LINK -> (activity as PostSelectionListener).onPostSelected(post)
                Action.COMMENTS_SWITCH -> {
                    if (activity !is MainActivity) {
                        activity.finish()
                    }
                    (activity as PostSelectionListener).onPostCommentsSelected(
                        post
                    )
                }
                Action.LINK_SWITCH -> {
                    if (activity !is MainActivity) {
                        activity.finish()
                    }
                    (activity as PostSelectionListener).onPostSelected(post)
                }
                Action.ACTION_MENU -> showActionMenu(activity, post)
                Action.REPLY -> {
                    if (post.isArchived) {
                        General.quickToast(
                            activity,
                            R.string.error_archived_reply,
                            Toast.LENGTH_SHORT
                        )
                        return
                    } else if (post.isLocked && !post.canModerate) {
                        General.quickToast(
                            activity,
                            R.string.error_locked_reply,
                            Toast.LENGTH_SHORT
                        )
                        return
                    }
                    val intent = Intent(activity, CommentReplyActivity::class.java)
                    intent.putExtra(
                        CommentReplyActivity.PARENT_ID_AND_TYPE_KEY,
                        post.src.idAndType
                    )
                    intent.putExtra(
                        CommentReplyActivity.PARENT_MARKDOWN_KEY,
                        post.src.unescapedSelfText
                    )
                    activity.startActivity(intent)
                }
                Action.BACK -> activity.onBackPressed()
                Action.PIN -> try {
                    PrefsUtility.pref_pinned_subreddits_add(
                        activity,
                        SubredditCanonicalId(post.src.subreddit)
                    )
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                Action.UNPIN -> try {
                    PrefsUtility.pref_pinned_subreddits_remove(
                        activity,
                        SubredditCanonicalId(post.src.subreddit)
                    )
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                Action.BLOCK -> try {
                    PrefsUtility.pref_blocked_subreddits_add(
                        activity,
                        SubredditCanonicalId(post.src.subreddit)
                    )
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                Action.UNBLOCK -> try {
                    PrefsUtility.pref_blocked_subreddits_remove(
                        activity,
                        SubredditCanonicalId(post.src.subreddit)
                    )
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                Action.SUBSCRIBE -> try {
                    val subredditCanonicalId = SubredditCanonicalId(post.src.subreddit)
                    val subMan = RedditSubredditSubscriptionManager
                        .getSingleton(
                            activity,
                            RedditAccountManager.getInstance(activity)
                                .defaultAccount
                        )
                    if (subMan.getSubscriptionState(subredditCanonicalId)
                        == SubredditSubscriptionState.NOT_SUBSCRIBED
                    ) {
                        subMan.subscribe(subredditCanonicalId, activity)
                        Toast.makeText(
                            activity,
                            R.string.options_subscribing,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            activity,
                            R.string.mainmenu_toast_subscribed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                Action.UNSUBSCRIBE -> try {
                    val subredditCanonicalId = SubredditCanonicalId(post.src.subreddit)
                    val subMan = RedditSubredditSubscriptionManager
                        .getSingleton(
                            activity,
                            RedditAccountManager.getInstance(activity)
                                .defaultAccount
                        )
                    if (subMan.getSubscriptionState(subredditCanonicalId)
                        == SubredditSubscriptionState.SUBSCRIBED
                    ) {
                        subMan.unsubscribe(subredditCanonicalId, activity)
                        Toast.makeText(
                            activity,
                            R.string.options_unsubscribing,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            activity,
                            R.string.mainmenu_toast_not_subscribed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
				null -> return
			}
        }

        // lol, reddit api
        private fun hasThumbnail(post: RedditParsedPost): Boolean {
            val url = post.thumbnailUrl
            return (url != null && !url.isEmpty()
                    && !url.equals("nsfw", ignoreCase = true)
                    && !url.equals("self", ignoreCase = true)
                    && !url.equals("default", ignoreCase = true))
        }
    }
}
