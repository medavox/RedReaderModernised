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
package org.quantumbadger.redreader.fragments

import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_post_count
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_enable_swipe_refresh
import org.quantumbadger.redreader.common.PrefsUtility.pref_cache_rerequest_postlist_age_ms
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_nsfw
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_hide_read_posts
import org.quantumbadger.redreader.common.PrefsUtility.images_inline_image_previews
import org.quantumbadger.redreader.common.PrefsUtility.images_inline_image_previews_nsfw
import org.quantumbadger.redreader.common.PrefsUtility.images_inline_image_previews_spoiler
import org.quantumbadger.redreader.common.PrefsUtility.appearance_thumbnails_show
import org.quantumbadger.redreader.common.PrefsUtility.images_high_res_thumbnails
import org.quantumbadger.redreader.common.PrefsUtility.appearance_thumbnails_nsfw_show
import org.quantumbadger.redreader.common.PrefsUtility.appearance_thumbnails_spoiler_show
import org.quantumbadger.redreader.common.PrefsUtility.cache_precache_images
import org.quantumbadger.redreader.common.FileUtils.isCacheDiskFull
import org.quantumbadger.redreader.common.PrefsUtility.cache_precache_comments
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_imageview_mode
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_gifview_mode
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_videoview_mode
import org.quantumbadger.redreader.common.PrefsUtility.pref_appearance_left_handed
import org.quantumbadger.redreader.common.PrefsUtility.pref_blocked_subreddits
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.quantumbadger.redreader.fragments.RRFragment
import org.quantumbadger.redreader.views.RedditPostView.PostSelectionListener
import org.quantumbadger.redreader.reddit.url.PostListingURL
import org.quantumbadger.redreader.reddit.things.RedditSubreddit
import android.widget.TextView
import org.quantumbadger.redreader.adapters.PostListingManager
import androidx.recyclerview.widget.RecyclerView
import org.quantumbadger.redreader.fragments.PostListingFragment
import org.quantumbadger.redreader.reddit.url.RedditURLParser
import android.widget.Toast
import org.quantumbadger.redreader.views.liststatus.ErrorView
import org.quantumbadger.redreader.common.PrefsUtility.PostCount
import org.quantumbadger.redreader.views.ScrollbarRecyclerViewManager
import org.quantumbadger.redreader.activities.OptionsMenuUtility.OptionsMenuPostsListener
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import androidx.recyclerview.widget.LinearLayoutManager
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategy
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyAlways
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfTimestampOutsideBounds
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfNotCached
import org.quantumbadger.redreader.account.RedditAccountManager
import org.quantumbadger.redreader.views.SearchListingHeader
import org.quantumbadger.redreader.reddit.url.SearchPostListURL
import org.quantumbadger.redreader.cache.CacheManager
import org.quantumbadger.redreader.reddit.url.SubredditPostListURL
import org.quantumbadger.redreader.io.RequestResponseHandler
import org.quantumbadger.redreader.reddit.api.SubredditRequestFailure
import org.quantumbadger.redreader.reddit.RedditSubredditManager
import org.quantumbadger.redreader.reddit.things.SubredditCanonicalId
import org.quantumbadger.redreader.reddit.things.InvalidSubredditNameException
import org.quantumbadger.redreader.activities.BugReportActivity
import org.quantumbadger.redreader.reddit.PostSort
import org.quantumbadger.redreader.views.PostListingHeader
import android.view.View.OnLongClickListener
import org.quantumbadger.redreader.adapters.MainMenuListingManager
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyNever
import android.view.LayoutInflater
import org.quantumbadger.redreader.reddit.api.RedditSubredditSubscriptionManager
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.cache.CacheRequestJSONParser
import org.quantumbadger.redreader.jsonwrap.JsonValue
import org.quantumbadger.redreader.activities.BaseActivity
import org.quantumbadger.redreader.activities.SessionChangeListener
import org.quantumbadger.redreader.jsonwrap.JsonObject
import org.quantumbadger.redreader.jsonwrap.JsonArray
import org.quantumbadger.redreader.common.PrefsUtility.ImageViewMode
import org.quantumbadger.redreader.common.PrefsUtility.GifViewMode
import org.quantumbadger.redreader.common.PrefsUtility.VideoViewMode
import org.quantumbadger.redreader.reddit.RedditPostListItem
import org.quantumbadger.redreader.reddit.things.RedditThing
import org.quantumbadger.redreader.reddit.things.RedditPost
import org.quantumbadger.redreader.reddit.prepared.RedditParsedPost
import org.quantumbadger.redreader.image.GetImageInfoListener
import org.quantumbadger.redreader.cache.CacheRequest.RequestFailureType
import org.quantumbadger.redreader.http.FailedRequestBody
import androidx.annotation.StringRes
import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.View
import org.apache.commons.text.StringEscapeUtils
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.listingcontrollers.CommentListingController
import org.quantumbadger.redreader.reddit.url.PostCommentListingURL
import org.quantumbadger.redreader.cache.CacheRequestCallbacks
import org.quantumbadger.redreader.cache.CacheManager.ReadableCacheFile
import org.quantumbadger.redreader.cache.CacheRequest
import org.quantumbadger.redreader.common.*
import org.quantumbadger.redreader.common.Optional
import org.quantumbadger.redreader.image.ImageInfo
import java.lang.ClassCastException
import java.lang.RuntimeException
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class PostListingFragment(
    parent: AppCompatActivity?,
    savedInstanceState: Bundle?,
    url: Uri,
    session: UUID?,
    forceDownload: Boolean
) : RRFragment(parent!!, savedInstanceState), PostSelectionListener {
    var postListingURL: PostListingURL
        private set
    var subreddit: RedditSubreddit? = null
    private var mSession: UUID?
    private var mPostCountLimit = 0
    private var mLoadMoreView: TextView? = null
    private val mPostListingManager: PostListingManager
    private val mRecyclerView: RecyclerView
    private val mOuter: View
    private var mAfter: String? = null
    private var mLastAfter: String? = null
    private var mRequest: CacheRequest?
    private var mReadyToDownloadMore = false
    private var mTimestamp: Long = 0
    private var mPostCount = 0
    private var mPostsNotShown = false
    private val mPostRefreshCount = AtomicInteger(0)
    private val mPostIds = HashSet<String>(200)
    private var mPreviousFirstVisibleItemPosition: Int? = null

    // Session may be null
    init {
        mPostListingManager = PostListingManager(parent)
        if (savedInstanceState != null) {
            mPreviousFirstVisibleItemPosition = savedInstanceState.getInt(
                SAVEDSTATE_FIRST_VISIBLE_POS
            )
        }
        try {
            postListingURL = RedditURLParser.parseProbablePostListing(url) as PostListingURL
        } catch (e: ClassCastException) {
            Toast.makeText(activity, "Invalid post listing URL.", Toast.LENGTH_LONG)
                .show()
            throw RuntimeException(e)
        }
        mSession = session
        val context = context

        // TODO output failed URL
        if (postListingURL == null) {
            mPostListingManager.addFooterError(
                ErrorView(
                    activity,
                    RRError(
                        "Invalid post listing URL",
                        "Could not navigate to that URL.",
                        true,
                        RuntimeException(),
                        null,
                        url.toString(),
                        null
                    )
                )
            )
            throw RuntimeException("Invalid post listing URL")
        }
        mPostCountLimit = when (pref_behaviour_post_count()) {
            PostCount.ALL -> -1
            PostCount.R25 -> 25
            PostCount.R50 -> 50
            PostCount.R100 -> 100
            else -> 0
        }
        if (mPostCountLimit > 0) {
            restackRefreshCount()
        }
        val recyclerViewManager = ScrollbarRecyclerViewManager(context, null, false)
        if (parent is OptionsMenuPostsListener
            && pref_behaviour_enable_swipe_refresh()
        ) {
            recyclerViewManager.enablePullToRefresh { (parent as OptionsMenuPostsListener).onRefreshPosts() }
        }
        mRecyclerView = recyclerViewManager.recyclerView
        mPostListingManager.setLayoutManager(mRecyclerView.layoutManager as LinearLayoutManager?)
        mRecyclerView.adapter = mPostListingManager.adapter
        mOuter = recyclerViewManager.outerView
        mRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int
            ) {
                onLoadMoreItemsCheck()
            }
        })
        General.setLayoutMatchParent(mRecyclerView)
        val downloadStrategy: DownloadStrategy
        downloadStrategy = if (forceDownload) {
            DownloadStrategyAlways.INSTANCE
        } else if (session == null && savedInstanceState == null && General.isNetworkConnected(
                context
            )
        ) {
            val maxAgeMs = pref_cache_rerequest_postlist_age_ms()
            DownloadStrategyIfTimestampOutsideBounds(
                TimestampBound
                    .notOlderThan(
                        maxAgeMs
                    )
            )
        } else {
            DownloadStrategyIfNotCached.INSTANCE
        }
        mRequest = createPostListingRequest(
            postListingURL.generateJsonUri(),
            RedditAccountManager.getInstance(context).defaultAccount,
            session,
            downloadStrategy,
            true
        )
        when (postListingURL.pathType()) {
            RedditURLParser.SEARCH_POST_LISTING_URL -> {
                setHeader(
                    SearchListingHeader(
                        activity,
                        postListingURL as SearchPostListURL
                    )
                )
                CacheManager.getInstance(context).makeRequest(mRequest)
            }
            RedditURLParser.USER_POST_LISTING_URL, RedditURLParser.MULTIREDDIT_POST_LISTING_URL -> {
                setHeader(
                    postListingURL.humanReadableName(activity, true),
                    postListingURL.humanReadableUrl(),
                    null
                )
                CacheManager.getInstance(context).makeRequest(mRequest)
            }
            RedditURLParser.SUBREDDIT_POST_LISTING_URL -> {
                val subredditPostListURL = postListingURL as SubredditPostListURL
                when (subredditPostListURL.type) {
                    SubredditPostListURL.Type.FRONTPAGE, SubredditPostListURL.Type.ALL, SubredditPostListURL.Type.SUBREDDIT_COMBINATION, SubredditPostListURL.Type.ALL_SUBTRACTION, SubredditPostListURL.Type.POPULAR -> {
                        setHeader(
                            postListingURL.humanReadableName(activity, true),
                            postListingURL.humanReadableUrl(),
                            null
                        )
                        CacheManager.getInstance(context).makeRequest(mRequest)
                    }
                    SubredditPostListURL.Type.RANDOM, SubredditPostListURL.Type.SUBREDDIT -> {


                        // Request the subreddit data
                        val subredditHandler = object :
                                RequestResponseHandler<RedditSubreddit?, SubredditRequestFailure?> {
                                override fun onRequestFailed(
                                    failureReason: SubredditRequestFailure?
                                ) {
                                    // Ignore
                                    AndroidCommon.UI_THREAD_HANDLER.post {
                                        CacheManager.getInstance(
                                            context
                                        ).makeRequest(mRequest)
                                    }
                                }

                                override fun onRequestSuccess(
                                    result: RedditSubreddit?,
                                    timeCached: Long
                                ) {
                                    AndroidCommon.UI_THREAD_HANDLER.post {
                                        subreddit = result
                                        if (subreddit!!.over18
                                            && !pref_behaviour_nsfw()
                                        ) {
                                            mPostListingManager.setLoadingVisible(false)
                                            val title =
                                                R.string.error_nsfw_subreddits_disabled_title
                                            val message =
                                                R.string.error_nsfw_subreddits_disabled_message
                                            mPostListingManager.addFooterError(
                                                ErrorView(
                                                    activity,
                                                    RRError(
                                                        context.getString(title),
                                                        context.getString(message),
                                                        false
                                                    )
                                                )
                                            )
                                        } else {
                                            onSubredditReceived()
                                            CacheManager.getInstance(context)
                                                .makeRequest(mRequest)
                                        }
                                    }
                                }
                            }
                        try {
                            RedditSubredditManager
                                .getInstance(
                                    activity,
                                    RedditAccountManager.getInstance(activity)
                                        .defaultAccount
                                )
                                .getSubreddit(
                                    SubredditCanonicalId(
                                        subredditPostListURL.subreddit!!
                                    ),
                                    TimestampBound.NONE,
                                    subredditHandler,
                                    null
                                )
                        } catch (e: InvalidSubredditNameException) {
                            throw RuntimeException(e)
                        }
                    }
                }
            }
            RedditURLParser.POST_COMMENT_LISTING_URL, RedditURLParser.UNKNOWN_COMMENT_LISTING_URL, RedditURLParser.UNKNOWN_POST_LISTING_URL, RedditURLParser.USER_COMMENT_LISTING_URL, RedditURLParser.USER_PROFILE_URL, RedditURLParser.COMPOSE_MESSAGE_URL -> BugReportActivity.handleGlobalError(
                activity, RuntimeException(
                    "Unknown url type "
                            + postListingURL.pathType()
                            + ": "
                            + postListingURL.toString()
                )
            )
        }
    }

    override fun getListingView(): View {
        return mOuter
    }

    override fun onSaveInstanceState(): Bundle {
        val bundle = Bundle()
        val layoutManager = mRecyclerView.layoutManager as LinearLayoutManager?
        bundle.putInt(
            SAVEDSTATE_FIRST_VISIBLE_POS,
            layoutManager!!.findFirstVisibleItemPosition()
        )
        return bundle
    }

    fun cancel() {
        if (mRequest != null) {
            mRequest!!.cancel()
        }
    }

    @Synchronized
    fun restackRefreshCount() {
        while (mPostRefreshCount.get() <= 0) {
            mPostRefreshCount.addAndGet(mPostCountLimit)
        }
    }

    private fun onSubredditReceived() {
        if (postListingURL.pathType() == RedditURLParser.SUBREDDIT_POST_LISTING_URL
            && postListingURL.asSubredditPostListURL().type
            == SubredditPostListURL.Type.RANDOM
        ) {
            try {
                postListingURL = postListingURL.asSubredditPostListURL()
                    .changeSubreddit(RedditSubreddit.stripRPrefix(subreddit!!.url))
                mRequest = createPostListingRequest(
                    postListingURL.generateJsonUri(),
                    RedditAccountManager.getInstance(context)
                        .defaultAccount,
                    mSession,
                    mRequest!!.downloadStrategy,
                    true
                )
            } catch (e: InvalidSubredditNameException) {
                throw RuntimeException(e)
            }
        }
		val subtitle: String = if (postListingURL.order == null
			|| postListingURL.order == PostSort.HOT
		) {
			with(subreddit) {
				if (this == null) {
					getString(R.string.header_subscriber_count_unknown)
				} else {
					context.getString(
						R.string.header_subscriber_count,
						NumberFormat.getNumberInstance(Locale.getDefault())
							.format(this.subscribers)
					)
				}
			}

		} else {
			postListingURL.humanReadableUrl()
		}
        activity.runOnUiThread {
            setHeader(
                StringEscapeUtils.unescapeHtml4(subreddit!!.title),
                subtitle,
                subreddit
            )
            activity.invalidateOptionsMenu()
        }
    }

    private fun setHeader(
        title: String,
        subtitle: String,
        subreddit: RedditSubreddit?
    ) {
        val postListingHeader = PostListingHeader(
            activity,
            title,
            subtitle,
            postListingURL,
            subreddit
        )
        setHeader(postListingHeader)
        if (subreddit != null) {
            postListingHeader.setOnLongClickListener { view: View? ->
                try {
                    MainMenuListingManager.showActionMenu(
                        activity,
                        subreddit.canonicalId
                    )
                } catch (e: InvalidSubredditNameException) {
                    throw RuntimeException(e)
                }
                true
            }
        }
    }

    private fun setHeader(view: View) {
        activity.runOnUiThread { mPostListingManager.addPostListingHeader(view) }
    }

    override fun onPostSelected(post: RedditPreparedPost) {
        (activity as PostSelectionListener).onPostSelected(post)
        object : Thread() {
            override fun run() {
                post.markAsRead(activity)
            }
        }.start()
    }

    override fun onPostCommentsSelected(post: RedditPreparedPost) {
        (activity as PostSelectionListener).onPostCommentsSelected(post)
        object : Thread() {
            override fun run() {
                post.markAsRead(activity)
            }
        }.start()
    }

    private fun onLoadMoreItemsCheck() {
        General.checkThisIsUIThread()
        if (mReadyToDownloadMore && mAfter != null && mAfter != mLastAfter) {
            val layoutManager = mRecyclerView.layoutManager as LinearLayoutManager?
            if (layoutManager!!.itemCount - layoutManager.findLastVisibleItemPosition()
                < 20
                && (mPostCountLimit <= 0 || mPostRefreshCount.get() > 0)
                || (mPreviousFirstVisibleItemPosition != null
                        && layoutManager.itemCount
                        <= mPreviousFirstVisibleItemPosition!!)
            ) {
                mLastAfter = mAfter
                mReadyToDownloadMore = false
                val newUri = postListingURL.after(mAfter).generateJsonUri()

                // TODO customise (currently 3 hrs)
                val strategy = if (RRTime.since(mTimestamp)
                    < 3 * 60 * 60 * 1000
                ) DownloadStrategyIfNotCached.INSTANCE else DownloadStrategyNever.INSTANCE
                mRequest = createPostListingRequest(
                    newUri,
                    RedditAccountManager.getInstance(activity)
                        .defaultAccount,
                    mSession,
                    strategy,
                    false
                )
                mPostListingManager.setLoadingVisible(true)
                CacheManager.getInstance(activity).makeRequest(mRequest)
            } else if (mPostCountLimit > 0 && mPostRefreshCount.get() <= 0) {
                if (mLoadMoreView == null) {
                    mLoadMoreView = LayoutInflater.from(context)
                        .inflate(R.layout.load_more_posts, null) as TextView
                    mLoadMoreView!!.setOnClickListener { view: View? ->
                        mPostListingManager.removeLoadMoreButton()
                        mLoadMoreView = null
                        restackRefreshCount()
                        onLoadMoreItemsCheck()
                    }
                    mPostListingManager.addLoadMoreButton(mLoadMoreView)
                }
            }
        }
    }

    fun onSubscribe() {
        if (postListingURL.pathType() != RedditURLParser.SUBREDDIT_POST_LISTING_URL) {
            return
        }
        try {
            RedditSubredditSubscriptionManager
                .getSingleton(
                    activity,
                    RedditAccountManager.getInstance(activity)
                        .defaultAccount
                )
                .subscribe(
                    SubredditCanonicalId(
                        postListingURL.asSubredditPostListURL().subreddit!!
                    ),
                    activity
                )
        } catch (e: InvalidSubredditNameException) {
            throw RuntimeException(e)
        }
    }

    fun onUnsubscribe() {
        if (subreddit == null) {
            return
        }
        try {
			subreddit?.let {
				RedditSubredditSubscriptionManager
					.getSingleton(
						activity,
						RedditAccountManager.getInstance(activity)
							.defaultAccount
					)
					.unsubscribe(it.canonicalId, activity)
			}
        } catch (e: InvalidSubredditNameException) {
            throw RuntimeException(e)
        }
    }

    fun onPostsAdded() {
        if (mPreviousFirstVisibleItemPosition == null) {
            return
        }
        val layoutManager = mRecyclerView.layoutManager as LinearLayoutManager?
        if (layoutManager!!.itemCount > mPreviousFirstVisibleItemPosition!!) {
            layoutManager.scrollToPositionWithOffset(
                mPreviousFirstVisibleItemPosition!!,
                0
            )
            mPreviousFirstVisibleItemPosition = null
        } else {
            layoutManager.scrollToPosition(layoutManager.itemCount - 1)
        }
    }

    private fun createPostListingRequest(
        url: Uri,
        user: RedditAccount,
        requestSession: UUID?,
        downloadStrategy: DownloadStrategy,
        firstDownload: Boolean
    ): CacheRequest {
        val activity = activity
        return CacheRequest(
            General.uriFromString(url.toString()),
            user,
            requestSession,
            Priority(Constants.Priority.API_POST_LIST),
            downloadStrategy,
            Constants.FileType.POST_LIST,
            CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
            activity,
            CacheRequestJSONParser(activity, object : CacheRequestJSONParser.Listener {
                override fun onJsonParsed(
                    value: JsonValue,
                    timestamp: Long,
                    session: UUID,
                    fromCache: Boolean
                ) {
                    val activity = getActivity() as BaseActivity

                    // One hour (matches default refresh value)
                    if (firstDownload && fromCache && RRTime.since(timestamp) > 60 * 60 * 1000) {
                        AndroidCommon.UI_THREAD_HANDLER.post {
                            val cacheNotif = LayoutInflater.from(activity).inflate(
                                R.layout.cached_header,
                                null,
                                false
                            ) as TextView
                            cacheNotif.text = getActivity().getString(
                                R.string.listing_cached,
                                RRTime.formatDateTime(timestamp, getActivity())
                            )
                            mPostListingManager.addNotification(cacheNotif)
                        }
                    } // TODO resuming a copy
                    if (firstDownload) {
                        (activity as SessionChangeListener).onSessionChanged(
                            session,
                            SessionChangeListener.SessionChangeType.POSTS,
                            timestamp
                        )
                        mSession = session
                        mTimestamp = timestamp
                    }

                    // TODO {"error": 403} is received for unauthorized subreddits
                    try {
                        val thing = value.asObject()
                        val listing = thing!!.getObject("data")
                        val posts = listing!!.getArray("children")
                        val isNsfwAllowed = pref_behaviour_nsfw()
                        val hideReadPosts = (pref_behaviour_hide_read_posts()
                                && postListingURL.pathType()
                                != RedditURLParser.USER_POST_LISTING_URL)
                        val isConnectionWifi = General.isConnectionWifi(activity)
                        val inlinePreviews = images_inline_image_previews()
                            .isEnabled(isConnectionWifi)
                        val showNsfwPreviews = images_inline_image_previews_nsfw()
                        val showSpoilerPreviews = images_inline_image_previews_spoiler()
                        val downloadThumbnails = appearance_thumbnails_show()
                            .isEnabled(isConnectionWifi)
                        val allowHighResThumbnails = (downloadThumbnails
                                && images_high_res_thumbnails()
                            .isEnabled(isConnectionWifi))
                        val showNsfwThumbnails = appearance_thumbnails_nsfw_show()
                        val showSpoilerThumbnails = appearance_thumbnails_spoiler_show()
                        val precacheImages = (!inlinePreviews
                                && cache_precache_images()
                            .isEnabled(isConnectionWifi)
                                && !isCacheDiskFull(activity))
                        val precacheComments = cache_precache_comments()
                            .isEnabled(isConnectionWifi)
                        val imageViewMode = pref_behaviour_imageview_mode()
                        val gifViewMode = pref_behaviour_gifview_mode()
                        val videoViewMode = pref_behaviour_videoview_mode()
                        val leftHandedMode = pref_appearance_left_handed()
                        val subredditFilteringEnabled = (postListingURL.pathType()
                                == RedditURLParser.SUBREDDIT_POST_LISTING_URL
                                && ((postListingURL.asSubredditPostListURL().type
                                == SubredditPostListURL.Type.ALL
                                ) || (postListingURL.asSubredditPostListURL().type
                                == SubredditPostListURL.Type.ALL_SUBTRACTION
                                ) || (postListingURL.asSubredditPostListURL().type
                                == SubredditPostListURL.Type.POPULAR
                                ) || (postListingURL.asSubredditPostListURL().type
                                == SubredditPostListURL.Type.FRONTPAGE)))

                        // Grab this so we don't have to pull from the prefs every post
                        val blockedSubreddits = HashSet(pref_blocked_subreddits())
                        Log.i(
                            TAG, "Inline previews: "
                                    + if (inlinePreviews) "ON" else "OFF"
                        )
                        Log.i(
                            TAG, "Precaching images: "
                                    + if (precacheImages) "ON" else "OFF"
                        )
                        Log.i(
                            TAG, "Precaching comments: "
                                    + if (precacheComments) "ON" else "OFF"
                        )
                        val cm = CacheManager.getInstance(activity)
                        val showSubredditName =
                            !(postListingURL != null && (postListingURL.pathType()
                                    == RedditURLParser.SUBREDDIT_POST_LISTING_URL) && (postListingURL.asSubredditPostListURL().type
                                    == SubredditPostListURL.Type.SUBREDDIT))
                        val downloadedPosts = ArrayList<RedditPostListItem>(25)
                        for (postThingValue in posts!!) {
                            val postThing = postThingValue.asObject(
                                RedditThing::class.java
                            )
                            if (postThing!!.getKind() != RedditThing.Kind.POST) {
                                continue
                            }
                            val post = postThing.asPost()
                            mAfter = post.name
                            val isPostBlocked = (subredditFilteringEnabled
                                    && blockedSubreddits.contains(
                                SubredditCanonicalId(post.subreddit)
                            ))
                            if (!isPostBlocked
                                && (!post.over_18 || isNsfwAllowed)
                                && mPostIds.add(post.idAlone)
                            ) {
                                val downloadThisThumbnail = (downloadThumbnails
                                        && (!post.over_18 || showNsfwThumbnails)
                                        && (!post.spoiler!! || showSpoilerThumbnails))
                                val downloadThisPreview = (inlinePreviews
                                        && (!post.over_18 || showNsfwPreviews)
                                        && (!post.spoiler!! || showSpoilerPreviews))
                                val positionInList = mPostCount
                                val parsedPost = RedditParsedPost(
                                    activity,
                                    post,
                                    false
                                )
                                val preparedPost = RedditPreparedPost(
                                    activity,
                                    cm,
                                    positionInList,
                                    parsedPost,
                                    timestamp,
                                    showSubredditName,
                                    downloadThisThumbnail,
                                    allowHighResThumbnails,
                                    downloadThisPreview
                                )

                                // Skip adding this post (go to next iteration) if it
                                // has been clicked on AND read posts should be hidden
                                if (hideReadPosts && preparedPost.isRead) {
                                    mPostsNotShown = true
                                    continue
                                }
                                if (precacheComments) {
                                    precacheComments(activity, preparedPost, positionInList)
                                }
                                LinkHandler.getImageInfo(
                                    activity,
                                    parsedPost.url,
                                    Priority(
                                        Constants.Priority.IMAGE_PRECACHE,
                                        positionInList
                                    ),
                                    object : GetImageInfoListener {
                                        override fun onFailure(
                                            @RequestFailureType type: Int,
                                            t: Throwable,
                                            status: Int,
                                            readableMessage: String,
                                            body: Optional<FailedRequestBody>
                                        ) {
                                        }

                                        override fun onNotAnImage() {}
                                        override fun onSuccess(info: ImageInfo) {
                                            if (!precacheImages) {
                                                return
                                            }
                                            precacheImage(
                                                activity,
                                                info,
                                                positionInList,
                                                gifViewMode,
                                                imageViewMode,
                                                videoViewMode
                                            )
                                        }
                                    })
                                downloadedPosts.add(
                                    RedditPostListItem(
                                        preparedPost,
                                        this@PostListingFragment,
                                        activity,
                                        leftHandedMode
                                    )
                                )
                                mPostCount++
                                mPostRefreshCount.decrementAndGet()
                            } else {
                                mPostsNotShown = true
                            }
                        }
                        AndroidCommon.runOnUiThread {
                            mPostListingManager.addPosts(downloadedPosts)
                            mPostListingManager.setLoadingVisible(false)
                            if (mPostCount == 0
                                && (mAfter == null || mAfter == mLastAfter)
                            ) {
                                @StringRes val emptyViewText: Int
                                emptyViewText = if (mPostsNotShown) {
                                    if (postListingURL.pathType()
                                        == RedditURLParser.SEARCH_POST_LISTING_URL
                                    ) {
                                        R.string.no_search_results_hidden
                                    } else {
                                        R.string.no_posts_yet_hidden
                                    }
                                } else {
                                    if (postListingURL.pathType()
                                        == RedditURLParser.SEARCH_POST_LISTING_URL
                                    ) {
                                        R.string.no_search_results
                                    } else {
                                        R.string.no_posts_yet
                                    }
                                }
                                val emptyView = LayoutInflater.from(context).inflate(
                                    R.layout.no_items_yet,
                                    mRecyclerView,
                                    false
                                )
                                (emptyView.findViewById<View>(R.id.empty_view_text) as TextView)
                                    .setText(emptyViewText)
                                mPostListingManager.addViewToItems(emptyView)
                            }
                            onPostsAdded()
                            mRequest = null
                            mReadyToDownloadMore = true
                            onLoadMoreItemsCheck()
                        }
                    } catch (t: Throwable) {
                        onFailure(
                            CacheRequest.REQUEST_FAILURE_PARSE,
                            t,
                            null,
                            "Parse failure",
                            Optional.of(FailedRequestBody(value))
                        )
                    }
                }

                override fun onFailure(
                    type: Int,
                    t: Throwable?,
                    httpStatus: Int?,
                    readableMessage: String?,
                    body: Optional<FailedRequestBody>
                ) {
                    AndroidCommon.UI_THREAD_HANDLER.post {
                        mPostListingManager.setLoadingVisible(false)
                        val error: RRError
                        error = if (type == CacheRequest.REQUEST_FAILURE_CACHE_MISS) {
                            RRError(
                                activity.getString(R.string.error_postlist_cache_title),
                                activity.getString(R.string.error_postlist_cache_message),
                                false,
                                t,
                                httpStatus,
                                url.toString(),
                                readableMessage,
                                body
                            )
                        } else {
                            General.getGeneralErrorForFailure(
                                activity,
                                type,
                                t,
                                httpStatus,
                                url.toString(),
                                body
                            )
                        }
                        mPostListingManager.addFooterError(
                            ErrorView(
                                activity,
                                error
                            )
                        )
                    }
                }
            })
        )
    }

    private fun precacheComments(
        activity: Activity,
        preparedPost: RedditPreparedPost,
        positionInList: Int
    ) {
        val controller = CommentListingController(
            PostCommentListingURL.forPostId(preparedPost.src.idAlone)
        )
        val url = General.uriFromString(controller.uri.toString())
        if (url == null) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(TAG, String.format("Not precaching '%s': failed to parse URL", url))
            }
            return
        }
        CacheManager.getInstance(activity)
            .makeRequest(
                CacheRequest(
                    url,
                    RedditAccountManager.getInstance(activity).defaultAccount,
                    null,
                    Priority(
                        Constants.Priority.COMMENT_PRECACHE,
                        positionInList
                    ),
                    DownloadStrategyIfTimestampOutsideBounds(
                        TimestampBound.notOlderThan(RRTime.minsToMs(15))
                    ),
                    Constants.FileType.COMMENT_LIST,
                    CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,  // Don't parse the JSON
                    activity,
                    object : CacheRequestCallbacks {
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
                                    "Failed to precache "
                                            + url.toString()
                                            + "(RequestFailureType code: "
                                            + type
                                            + ")"
                                )
                            }
                        }

                        override fun onCacheFileWritten(
                            cacheFile: ReadableCacheFile,
                            timestamp: Long,
                            session: UUID,
                            fromCache: Boolean,
                            mimetype: String?
                        ) {

                            // Successfully precached
                        }
                    })
            )
    }

    private fun precacheImage(
        activity: Activity,
        info: ImageInfo,
        positionInList: Int,
        gifViewMode: GifViewMode,
        imageViewMode: ImageViewMode,
        videoViewMode: VideoViewMode
    ) {

        // Don't precache huge images
        if (info.size != null
            && info.size > 15 * 1024 * 1024
        ) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(
                    TAG, String.format(
                        "Not precaching '%s': too big (%d kB)",
                        info.urlOriginal,
                        info.size / 1024
                    )
                )
            }
            return
        }

        // Don't precache gifs if they're opened externally
        if (ImageInfo.MediaType.GIF == info.mediaType && !gifViewMode.downloadInApp) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(
                    TAG, String.format(
                        "Not precaching '%s': GIFs opened externally",
                        info.urlOriginal
                    )
                )
            }
            return
        }

        // Don't precache images if they're opened externally
        if (ImageInfo.MediaType.IMAGE == info.mediaType && !imageViewMode.downloadInApp) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(
                    TAG, String.format(
                        "Not precaching '%s': images opened externally",
                        info.urlOriginal
                    )
                )
            }
            return
        }


        // Don't precache videos if they're opened externally
        if (ImageInfo.MediaType.VIDEO == info.mediaType && !videoViewMode.downloadInApp) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(
                    TAG, String.format(
                        "Not precaching '%s': videos opened externally",
                        info.urlOriginal
                    )
                )
            }
            return
        }
        precacheImage(
            activity,
            info.urlOriginal,
            positionInList
        )
        if (info.urlAudioStream != null) {
            precacheImage(
                activity,
                info.urlAudioStream,
                positionInList
            )
        }
    }

    private fun precacheImage(
        activity: Activity,
        url: String?,
        positionInList: Int
    ) {
        val uri = General.uriFromString(url)
        if (uri == null) {
            if (General.isSensitiveDebugLoggingEnabled()) {
                Log.i(TAG, String.format("Not precaching '%s': failed to parse URL", url))
            }
            return
        }
        CacheManager.getInstance(activity).makeRequest(
            CacheRequest(
                uri,
                RedditAccountManager.getAnon(),
                null,
                Priority(
                    Constants.Priority.IMAGE_PRECACHE,
                    positionInList
                ),
                DownloadStrategyIfNotCached.INSTANCE,
                Constants.FileType.IMAGE,
                CacheRequest.DOWNLOAD_QUEUE_IMAGE_PRECACHE,
                activity,
                object : CacheRequestCallbacks {
                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        httpStatus: Int?,
                        readableMessage: String?,
                        body: Optional<FailedRequestBody>
                    ) {
                        if (General.isSensitiveDebugLoggingEnabled()) {
                            Log.e(
                                TAG, String.format(
                                    Locale.US, "Failed to precache %s (RequestFailureType %d,"
                                            + " status %s, readable '%s')",
                                    url,
                                    type,
                                    httpStatus?.toString() ?: "NULL",
                                    readableMessage ?: "NULL"
                                )
                            )
                        }
                    }

                    override fun onCacheFileWritten(
                        cacheFile: ReadableCacheFile,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean,
                        mimetype: String?
                    ) {

                        // Successfully precached
                    }
                })
        )
    }

    companion object {
        private const val TAG = "PostListingFragment"
        private const val SAVEDSTATE_FIRST_VISIBLE_POS = "firstVisiblePosition"
    }
}
