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
package org.quantumbadger.redreader.common

import android.app.AlertDialog
import android.content.*
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_albumview_mode
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_usecustomtabs
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_useinternalbrowser
import org.quantumbadger.redreader.common.PrefsUtility.pref_menus_link_context_items
import org.quantumbadger.redreader.common.FileUtils.shareImageAtUri
import org.quantumbadger.redreader.common.FileUtils.saveImageAtUri
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_sharing_dialog
import kotlin.jvm.JvmOverloads
import androidx.appcompat.app.AppCompatActivity
import org.quantumbadger.redreader.reddit.things.RedditPost
import org.quantumbadger.redreader.activities.ImageViewActivity
import org.quantumbadger.redreader.common.PrefsUtility.AlbumViewMode
import org.quantumbadger.redreader.common.PrefsUtility
import org.quantumbadger.redreader.activities.AlbumListingActivity
import android.os.Build
import org.quantumbadger.redreader.reddit.url.RedditURLParser.RedditURL
import org.quantumbadger.redreader.reddit.url.RedditURLParser
import org.quantumbadger.redreader.activities.PostListingActivity
import org.quantumbadger.redreader.activities.CommentListingActivity
import org.quantumbadger.redreader.activities.PMSendActivity
import org.quantumbadger.redreader.reddit.url.ComposeMessageURL
import org.quantumbadger.redreader.fragments.UserProfileDialog
import org.quantumbadger.redreader.activities.BaseActivity
import org.quantumbadger.redreader.common.LinkHandler.LinkMenuItem
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Parcelable
import org.quantumbadger.redreader.activities.WebViewActivity
import androidx.annotation.RequiresApi
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.cache.CacheRequest
import org.quantumbadger.redreader.common.LinkHandler.ImageInfoRetryListener
import org.quantumbadger.redreader.cache.CacheRequest.RequestFailureType
import org.quantumbadger.redreader.http.FailedRequestBody
import org.quantumbadger.redreader.common.LinkHandler.AlbumInfoRetryListener
import org.quantumbadger.redreader.image.ImageInfo.HasAudio
import org.quantumbadger.redreader.fragments.ShareOrderDialog
import org.quantumbadger.redreader.image.*
import java.lang.Exception
import java.util.*
import java.util.regex.Pattern

object LinkHandler {
    val youtubeDotComPattern = Pattern.compile("^https?://[.\\w]*youtube\\.\\w+/.*")
    val youtuDotBePattern = Pattern.compile(
        "^https?://[.\\w]*youtu\\.be/([A-Za-z0-9\\-_]+)(\\?.*|).*"
    )
    val vimeoPattern = Pattern.compile("^https?://[.\\w]*vimeo\\.\\w+/.*")
    val googlePlayPattern = Pattern.compile(
        "^https?://[.\\w]*play\\.google\\.\\w+/.*"
    )

    @JvmStatic
	@JvmOverloads
    fun onLinkClicked(
        activity: AppCompatActivity,
        url: String?,
        forceNoImage: Boolean = false,
        post: RedditPost? = null,
        albumInfo: AlbumInfo? = null,
        albumImageIndex: Int = 0,
        fromExternalIntent: Boolean =
            false
    ) {
        var url = url
        if (url == null) {
            General.quickToast(activity, R.string.link_does_not_exist)
            return
        }
        if (url.startsWith("rr://")) {
            val rrUri = Uri.parse(url)
            if (rrUri.authority == "msg") {
                Handler().post {
                    val builder = AlertDialog.Builder(
                        activity
                    )
                    builder.setTitle(rrUri.getQueryParameter("title"))
                    builder.setMessage(rrUri.getQueryParameter("message"))
                    val alert = builder.create()
                    alert.show()
                }
                return
            }
        }
        if (url.startsWith("r/") || url.startsWith("u/")) {
            url = "/$url"
        }
        if (url.startsWith("/")) {
            url = "https://reddit.com$url"
        }
        if (!url.contains("://")) {
            url = "http://$url"
        }
        if (!forceNoImage && isProbablyAnImage(url)) {
            val intent = Intent(activity, ImageViewActivity::class.java)
            intent.data = Uri.parse(url)
            intent.putExtra("post", post)
            if (albumInfo != null) {
                intent.putExtra("albumUrl", albumInfo.url)
                intent.putExtra("albumImageIndex", albumImageIndex)
            }
            activity.startActivity(intent)
            return
        }
        if (!forceNoImage && (imgurAlbumPattern.matcher(url).matches()
                    || redditGalleryPattern.matcher(url).matches())
        ) {
            val albumViewMode = pref_behaviour_albumview_mode()
            when (albumViewMode) {
                AlbumViewMode.INTERNAL_LIST -> {
                    val intent = Intent(
                        activity,
                        AlbumListingActivity::class.java
                    )
                    intent.data = Uri.parse(url)
                    intent.putExtra("post", post)
                    activity.startActivity(intent)
                    return
                }
                AlbumViewMode.INTERNAL_BROWSER -> {
                    if (pref_behaviour_usecustomtabs()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                    ) {
                        openCustomTab(activity, Uri.parse(url), post)
                    } else {
                        openInternalBrowser(activity, url, post)
                    }
                    return
                }
                AlbumViewMode.EXTERNAL_BROWSER -> {
                    openWebBrowser(activity, Uri.parse(url), fromExternalIntent)
                    return
                }
            }
        }
        val redditURL = RedditURLParser.parse(Uri.parse(url))
        if (redditURL != null) {
            when (redditURL.pathType()) {
                RedditURLParser.SUBREDDIT_POST_LISTING_URL, RedditURLParser.MULTIREDDIT_POST_LISTING_URL, RedditURLParser.USER_POST_LISTING_URL, RedditURLParser.SEARCH_POST_LISTING_URL, RedditURLParser.UNKNOWN_POST_LISTING_URL -> {
                    val intent = Intent(activity, PostListingActivity::class.java)
                    intent.data = redditURL.generateJsonUri()
                    activity.startActivityForResult(intent, 1)
                    return
                }
                RedditURLParser.POST_COMMENT_LISTING_URL, RedditURLParser.USER_COMMENT_LISTING_URL, RedditURLParser.UNKNOWN_COMMENT_LISTING_URL -> {
                    val intent = Intent(
                        activity,
                        CommentListingActivity::class.java
                    )
                    intent.data = redditURL.generateJsonUri()
                    activity.startActivityForResult(intent, 1)
                    return
                }
                RedditURLParser.COMPOSE_MESSAGE_URL -> {
                    val intent = Intent(
                        activity,
                        PMSendActivity::class.java
                    )
                    val cmUrl = redditURL.asComposeMessageURL()
                    if (cmUrl.recipient != null) {
                        intent.putExtra(PMSendActivity.EXTRA_RECIPIENT, cmUrl.recipient)
                    }
                    if (cmUrl.subject != null) {
                        intent.putExtra(PMSendActivity.EXTRA_SUBJECT, cmUrl.subject)
                    }
                    if (cmUrl.message != null) {
                        intent.putExtra(PMSendActivity.EXTRA_TEXT, cmUrl.message)
                    }
                    activity.startActivityForResult(intent, 1)
                    return
                }
                RedditURLParser.USER_PROFILE_URL -> {
                    UserProfileDialog.newInstance(redditURL.asUserProfileURL().username)
                        .show(activity.supportFragmentManager, null)
                    return
                }
            }
        }

        // Use a browser
        if (!pref_behaviour_useinternalbrowser()) {
            if (openWebBrowser(activity, Uri.parse(url), fromExternalIntent)) {
                return
            }
        }
        if (youtubeDotComPattern.matcher(url).matches()
            || vimeoPattern.matcher(url).matches()
            || googlePlayPattern.matcher(url).matches()
        ) {
            if (openWebBrowser(activity, Uri.parse(url), fromExternalIntent)) {
                return
            }
        }
        val youtuDotBeMatcher = youtuDotBePattern.matcher(url)
        if (youtuDotBeMatcher.find() && youtuDotBeMatcher.group(1) != null) {
            val youtuBeUrl = ("http://youtube.com/watch?v="
                    + youtuDotBeMatcher.group(1)
                    + if (!youtuDotBeMatcher.group(2).isEmpty()) "&" + youtuDotBeMatcher.group(2)
                .substring(1) else "")
            if (openWebBrowser(activity, Uri.parse(youtuBeUrl), fromExternalIntent)) {
                return
            }
        }
        if (pref_behaviour_usecustomtabs()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        ) {
            openCustomTab(activity, Uri.parse(url), post)
        } else {
            openInternalBrowser(activity, url, post)
        }
    }

    @JvmStatic
	@JvmOverloads
    fun onLinkLongClicked(
        activity: BaseActivity,
        uri: String?,
        forceNoImage: Boolean = false
    ) {
        if (uri == null) {
            return
        }
        val itemPref = pref_menus_link_context_items()
        if (itemPref.isEmpty()) {
            return
        }
        val menu = ArrayList<LinkMenuItem>()
        if (itemPref.contains(LinkAction.COPY_URL)) {
            menu.add(
                LinkMenuItem(
                    activity,
                    R.string.action_copy_link,
                    LinkAction.COPY_URL
                )
            )
        }
        if (itemPref.contains(LinkAction.EXTERNAL)) {
            menu.add(
                LinkMenuItem(
                    activity,
                    R.string.action_external,
                    LinkAction.EXTERNAL
                )
            )
        }
        if (itemPref.contains(LinkAction.SAVE_IMAGE)
            && isProbablyAnImage(uri)
            && !forceNoImage
        ) {
            menu.add(
                LinkMenuItem(
                    activity,
                    R.string.action_save_image,
                    LinkAction.SAVE_IMAGE
                )
            )
        }
        if (itemPref.contains(LinkAction.SHARE)) {
            menu.add(LinkMenuItem(activity, R.string.action_share, LinkAction.SHARE))
        }
        if (itemPref.contains(LinkAction.SHARE_IMAGE)
            && isProbablyAnImage(uri)
            && !forceNoImage
        ) {
            menu.add(
                LinkMenuItem(
                    activity,
                    R.string.action_share_image,
                    LinkAction.SHARE_IMAGE
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
                uri,
                activity,
                menu[which].action
            )
        }

        //builder.setNeutralButton(R.string.dialog_cancel, null);
        val alert = builder.create()
        alert.setCanceledOnTouchOutside(true)
        alert.show()
    }

    fun onActionMenuItemSelected(
        uri: String?,
        activity: BaseActivity,
        action: LinkAction
    ) {
        when (action) {
            LinkAction.SHARE -> shareText(activity, null, uri)
            LinkAction.COPY_URL -> {
                val clipboardManager =
                    activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboardManager != null) {
                    // Using newPlainText here instead of newRawUri because links from
                    // comments/self-text are often not valid URIs
                    val data = ClipData.newPlainText(null, uri)
                    clipboardManager.setPrimaryClip(data)
                    General.quickToast(
                        activity.applicationContext,
                        R.string.link_copied_to_clipboard
                    )
                }
            }
            LinkAction.EXTERNAL -> try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(uri)
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                General.quickToast(
                    activity,
                    R.string.error_no_suitable_apps_available
                )
            }
            LinkAction.SHARE_IMAGE -> shareImageAtUri(activity, uri)
            LinkAction.SAVE_IMAGE -> saveImageAtUri(activity, uri)
        }
    }

    @JvmStatic
	fun openWebBrowser(
        activity: AppCompatActivity,
        uri: Uri,
        fromExternalIntent: Boolean
    ): Boolean {
        if (!fromExternalIntent) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = uri
                activity.startActivity(intent)
                return true
            } catch (e: Exception) {
                General.quickToast(
                    activity, String.format(
                        activity.getString(
                            R.string.error_toast_failed_open_external_browser
                        ),
                        uri.toString()
                    )
                )
            }
        } else {

            // We want to make sure we don't just pass this back to ourselves
            val baseIntent = Intent(Intent.ACTION_VIEW)
            baseIntent.data = uri
            val targetIntents = ArrayList<Intent>()
            for (info in activity.packageManager
                .queryIntentActivities(baseIntent, 0)) {
                val packageName = info.activityInfo.packageName
                if (packageName != null && !packageName.startsWith(
                        "org.quantumbadger.redreader"
                    )
                ) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = uri
                    intent.setPackage(packageName)
                    targetIntents.add(intent)
                }
            }
            if (!targetIntents.isEmpty()) {
                val chooserIntent = Intent.createChooser(
                    targetIntents.removeAt(0),
                    activity.getString(R.string.open_with)
                )
                if (!targetIntents.isEmpty()) {
                    chooserIntent.putExtra(
                        Intent.EXTRA_INITIAL_INTENTS,
                        targetIntents.toArray(arrayOf<Parcelable>())
                    )
                }
                activity.startActivity(chooserIntent)
                return true
            }
        }
        return false
    }

    fun openInternalBrowser(
        activity: AppCompatActivity,
        url: String?,
        post: RedditPost?
    ) {
        val intent = Intent()
        intent.setClass(activity, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("post", post)
        activity.startActivity(intent)
    }

    @JvmStatic
	@RequiresApi(18)
    fun openCustomTab(
        activity: AppCompatActivity,
        uri: Uri,
        post: RedditPost?
    ) {
        try {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val bundle = Bundle()
            bundle.putBinder("android.support.customtabs.extra.SESSION", null)
            intent.putExtras(bundle)
            intent.putExtra("android.support.customtabs.extra.SHARE_MENU_ITEM", true)
            val typedValue = TypedValue()
            activity.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
            intent.putExtra(
                "android.support.customtabs.extra.TOOLBAR_COLOR",
                typedValue.data
            )
            intent.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true)
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No suitable web browser installed. Use internal browser.
            openInternalBrowser(activity, uri.toString(), post)
        }
    }

    val imgurPattern = Pattern.compile(".*[^A-Za-z]imgur\\.com/(\\w+).*")
    @JvmField
	val imgurAlbumPattern = Pattern.compile(".*[^A-Za-z]imgur\\.com/(a|gallery)/(\\w+).*")
    val redditGalleryPattern = Pattern.compile(".*[^A-Za-z]reddit\\.com/gallery/(\\w+).*")
    val qkmePattern1 = Pattern.compile(".*[^A-Za-z]qkme\\.me/(\\w+).*")
    val qkmePattern2 = Pattern.compile(".*[^A-Za-z]quickmeme\\.com/meme/(\\w+).*")
    val lvmePattern = Pattern.compile(".*[^A-Za-z]livememe\\.com/(\\w+).*")
    val gfycatPattern = Pattern.compile(".*[^A-Za-z]gfycat\\.com/(?:gifs/detail/)?(\\w+).*")
    val redgifsPattern = Pattern.compile(".*[^A-Za-z]redgifs\\.com/watch/(?:gifs/detail/)?(\\w+).*")
    val streamablePattern = Pattern.compile(".*[^A-Za-z]streamable\\.com/(\\w+).*")
    val reddituploadsPattern = Pattern.compile(".*[^A-Za-z]i\\.reddituploads\\.com/(\\w+).*")
    val redditVideosPattern = Pattern.compile(".*[^A-Za-z]v.redd.it/(\\w+).*")
    val imgflipPattern = Pattern.compile(".*[^A-Za-z]imgflip\\.com/i/(\\w+).*")
    val makeamemePattern = Pattern.compile(".*[^A-Za-z]makeameme\\.org/meme/([\\w\\-]+).*")
    val deviantartPattern =
        Pattern.compile("https://www\\.deviantart\\.com/([\\w\\-]+)/art/([\\w\\-]+)")
    val giphyPattern = Pattern.compile(".*[^A-Za-z]giphy\\.com/gifs/(\\w+).*")
    fun isProbablyAnImage(url: String?): Boolean {
        if (url == null) {
            return false
        }
        run {
            val matchImgur = imgurPattern.matcher(url)
            if (matchImgur.find()) {
                val imgId = matchImgur.group(1)
                if (imgId.length > 2 && !imgId.startsWith("gallery")) {
                    return true
                }
            }
        }
        run {
            val matchGfycat = gfycatPattern.matcher(url)
            if (matchGfycat.find()) {
                val imgId = matchGfycat.group(1)
                if (imgId.length > 5) {
                    return true
                }
            }
        }
        run {
            val matchRedgifs = redgifsPattern.matcher(url)
            if (matchRedgifs.find()) {
                val imgId = matchRedgifs.group(1)
                if (imgId.length > 5) {
                    return true
                }
            }
        }
        run {
            val matchStreamable = streamablePattern.matcher(url)
            if (matchStreamable.find()) {
                val imgId = matchStreamable.group(1)
                if (imgId.length > 2) {
                    return true
                }
            }
        }
        run {
            val matchRedditUploads = reddituploadsPattern.matcher(url)
            if (matchRedditUploads.find()) {
                val imgId = matchRedditUploads.group(1)
                if (imgId.length > 10) {
                    return true
                }
            }
        }
        run {
            val matchImgflip = imgflipPattern.matcher(url)
            if (matchImgflip.find()) {
                val imgId = matchImgflip.group(1)
                if (imgId.length > 3) {
                    return true
                }
            }
        }
        run {
            val matchMakeameme = makeamemePattern.matcher(url)
            if (matchMakeameme.find()) {
                val imgId = matchMakeameme.group(1)
                if (imgId.length > 3) {
                    return true
                }
            }
        }
        run {
            val matchDeviantart = deviantartPattern.matcher(url)
            if (matchDeviantart.find()) {
                if (url.length > 40) {
                    return true
                }
            }
        }
        run {
            val matchRedditVideos = redditVideosPattern.matcher(url)
            if (matchRedditVideos.find()) {
                val imgId = matchRedditVideos.group(1)
                if (imgId.length > 3) {
                    return true
                }
            }
        }
        return getImageUrlPatternMatch(url) != null
    }

    @JvmStatic
	fun getImgurImageInfo(
        context: Context?,
        imgId: String,
        priority: Priority,
        returnUrlOnFailure: Boolean,
        listener: GetImageInfoListener
    ) {
        if (General.isSensitiveDebugLoggingEnabled()) {
            Log.i("getImgurImageInfo", "Image $imgId: trying API v3 with auth")
        }
        ImgurAPIV3.getImageInfo(
            context,
            imgId,
            priority,
            true,
            object : ImageInfoRetryListener(listener) {
                override fun onFailure(
                    @RequestFailureType type: Int,
                    t: Throwable,
                    status: Int,
                    readableMessage: String,
                    firstBody: Optional<FailedRequestBody>
                ) {
                    if (General.isSensitiveDebugLoggingEnabled()) {
                        Log.i(
                            "getImgurImageInfo",
                            "Image $imgId: trying API v3 without auth"
                        )
                    }
                    ImgurAPIV3.getImageInfo(
                        context,
                        imgId,
                        priority,
                        false,
                        object : ImageInfoRetryListener(listener) {
                            override fun onFailure(
                                @RequestFailureType type: Int,
                                t: Throwable,
                                status: Int,
                                readableMessage: String,
                                body: Optional<FailedRequestBody>
                            ) {
                                if (General.isSensitiveDebugLoggingEnabled()) {
                                    Log.i(
                                        "getImgurImageInfo",
                                        "Image $imgId: trying API v2"
                                    )
                                }
                                ImgurAPI.getImageInfo(
                                    context,
                                    imgId,
                                    priority,
                                    object : ImageInfoRetryListener(listener) {
                                        override fun onFailure(
                                            @RequestFailureType type: Int,
                                            t: Throwable,
                                            status: Int,
                                            readableMessage: String,
                                            body: Optional<FailedRequestBody>
                                        ) {
                                            Log.i(
                                                "getImgurImageInfo",
                                                "All API requests failed!"
                                            )
                                            if (returnUrlOnFailure) {
                                                listener.onSuccess(
                                                    ImageInfo(
                                                        "https://i.imgur.com/"
                                                                + imgId
                                                                + ".jpg",
                                                        null,
                                                        HasAudio.MAYBE_AUDIO
                                                    )
                                                )
                                            } else {
                                                listener.onFailure(
                                                    type,
                                                    t,
                                                    status,
                                                    readableMessage,
                                                    firstBody
                                                )
                                            }
                                        }
                                    })
                            }
                        })
                }
            })
    }

    fun getImgurAlbumInfo(
        context: Context?,
        albumUrl: String?,
        albumId: String,
        priority: Priority,
        listener: GetAlbumInfoListener
    ) {
        if (General.isSensitiveDebugLoggingEnabled()) {
            Log.i("getImgurAlbumInfo", "Album $albumId: trying API v3 with auth")
        }
        ImgurAPIV3.getAlbumInfo(
            context,
            albumUrl,
            albumId,
            priority,
            true,
            object : AlbumInfoRetryListener(listener) {
                override fun onFailure(
                    @RequestFailureType type: Int,
                    t: Throwable,
                    status: Int,
                    readableMessage: String,
                    firstBody: Optional<FailedRequestBody>
                ) {
                    if (General.isSensitiveDebugLoggingEnabled()) {
                        Log.i(
                            "getImgurAlbumInfo",
                            "Album $albumId: trying API v3 without auth"
                        )
                    }
                    ImgurAPIV3.getAlbumInfo(
                        context,
                        albumUrl,
                        albumId,
                        priority,
                        false,
                        object : AlbumInfoRetryListener(listener) {
                            override fun onFailure(
                                @RequestFailureType type: Int,
                                t: Throwable,
                                status: Int,
                                readableMessage: String,
                                body: Optional<FailedRequestBody>
                            ) {
                                if (General.isSensitiveDebugLoggingEnabled()) {
                                    Log.i(
                                        "getImgurAlbumInfo",
                                        "Album $albumId: trying API v2"
                                    )
                                }
                                ImgurAPI.getAlbumInfo(
                                    context,
                                    albumUrl,
                                    albumId,
                                    priority,
                                    object : AlbumInfoRetryListener(listener) {
                                        override fun onFailure(
                                            @RequestFailureType type: Int,
                                            t: Throwable,
                                            status: Int,
                                            readableMessage: String,
                                            body: Optional<FailedRequestBody>
                                        ) {
                                            Log.i(
                                                "getImgurImageInfo",
                                                "All API requests failed!"
                                            )
                                            listener.onFailure(
                                                type,
                                                t,
                                                status,
                                                readableMessage,
                                                firstBody
                                            )
                                        }
                                    })
                            }
                        })
                }
            })
    }

    @JvmStatic
	fun getAlbumInfo(
        context: Context?,
        url: String,
        priority: Priority,
        listener: GetAlbumInfoListener
    ) {
        run {
            val matchImgur = imgurAlbumPattern.matcher(url)
            if (matchImgur.find()) {
                val albumId = matchImgur.group(2)
                if (albumId.length > 2) {
                    getImgurAlbumInfo(context, url, albumId, priority, listener)
                    return
                }
            }
        }
        run {
            val matchReddit = redditGalleryPattern.matcher(url)
            if (matchReddit.find()) {
                val albumId = matchReddit.group(1)
                if (albumId.length > 2) {
                    RedditGalleryAPI.getAlbumInfo(
                        context,
                        url,
                        albumId,
                        priority,
                        listener
                    )
                    return
                }
            }
        }
        listener.onFailure(
            CacheRequest.REQUEST_FAILURE_MALFORMED_URL,
            null,
            null,
            "Cannot parse '$url' as an album URL",
            Optional.empty()
        )
    }

    fun getImageInfo(
        context: Context?,
        url: String?,
        priority: Priority,
        listener: GetImageInfoListener
    ) {
        if (url == null) {
            listener.onNotAnImage()
            return
        }
        run {
            val matchImgur = imgurPattern.matcher(url)
            if (matchImgur.find()) {
                val imgId = matchImgur.group(1)
                if (imgId.length > 2 && !imgId.startsWith("gallery")) {
                    getImgurImageInfo(context, imgId, priority, true, listener)
                    return
                }
            }
        }
        run {
            val matchGfycat = gfycatPattern.matcher(url)
            if (matchGfycat.find()) {
                val imgId = matchGfycat.group(1)
                if (imgId.length > 5) {
                    GfycatAPI.getImageInfo(context, imgId, priority, listener)
                    return
                }
            }
        }
        run {
            val matchRedgifs = redgifsPattern.matcher(url)
            if (matchRedgifs.find()) {
                val imgId = matchRedgifs.group(1)
                if (imgId.length > 5) {
                    RedgifsAPIV2.getImageInfo(
                        context,
                        imgId,
                        priority,
                        object : ImageInfoRetryListener(listener) {
                            override fun onFailure(
                                type: Int,
                                t: Throwable,
                                status: Int,
                                readableMessage: String,
                                body: Optional<FailedRequestBody>
                            ) {
                                Log.e(
                                    "getImageInfo",
                                    "RedGifs V2 failed, trying V1 ($readableMessage)",
                                    t
                                )
                                RedgifsAPI.getImageInfo(
                                    context,
                                    imgId,
                                    priority,
                                    object : ImageInfoRetryListener(listener) {
                                        override fun onFailure(
                                            type: Int,
                                            t: Throwable,
                                            status: Int,
                                            readableMessage: String,
                                            body: Optional<FailedRequestBody>
                                        ) {

                                            // Retry V2 so that the final error which is logged
                                            // relates to the V2 API
                                            Log.e(
                                                "getImageInfo",
                                                "RedGifs V1 also failed, retrying V2",
                                                t
                                            )
                                            RedgifsAPIV2.getImageInfo(
                                                context,
                                                imgId,
                                                priority,
                                                listener
                                            )
                                        }
                                    })
                            }
                        })
                    return
                }
            }
        }
        run {
            val matchStreamable = streamablePattern.matcher(url)
            if (matchStreamable.find()) {
                val imgId = matchStreamable.group(1)
                if (imgId.length > 2) {
                    StreamableAPI.getImageInfo(
                        context,
                        imgId,
                        priority,
                        listener
                    )
                    return
                }
            }
        }
        run {
            val matchDeviantart = deviantartPattern.matcher(url)
            if (matchDeviantart.find()) {
                if (url.length > 40) {
                    DeviantArtAPI.getImageInfo(
                        context,
                        url,
                        priority,
                        listener
                    )
                    return
                }
            }
        }
        run {
            val matchRedditVideos = redditVideosPattern.matcher(url)
            if (matchRedditVideos.find()) {
                val imgId = matchRedditVideos.group(1)
                if (imgId.length > 3) {
                    RedditVideosAPI.getImageInfo(
                        context,
                        imgId,
                        priority,
                        listener
                    )
                    return
                }
            }
        }
        val imageUrlPatternMatch = getImageUrlPatternMatch(url)
        if (imageUrlPatternMatch != null) {
            listener.onSuccess(imageUrlPatternMatch)
        } else {
            listener.onNotAnImage()
        }
    }

    private fun getImageUrlPatternMatch(url: String): ImageInfo? {
        val urlLower = StringUtils.asciiLowercase(url)
        run {
            val matchRedditUploads = reddituploadsPattern.matcher(url)
            if (matchRedditUploads.find()) {
                val imgId = matchRedditUploads.group(1)
                if (imgId.length > 10) {
                    return ImageInfo(
                        url,
                        ImageInfo.MediaType.IMAGE,
                        HasAudio.NO_AUDIO
                    )
                }
            }
        }
        run {
            val matchImgflip = imgflipPattern.matcher(url)
            if (matchImgflip.find()) {
                val imgId = matchImgflip.group(1)
                if (imgId.length > 3) {
                    val imageUrl = "https://i.imgflip.com/$imgId.jpg"
                    return ImageInfo(
                        imageUrl,
                        ImageInfo.MediaType.IMAGE,
                        HasAudio.NO_AUDIO
                    )
                }
            }
        }
        run {
            val matchMakeameme = makeamemePattern.matcher(url)
            if (matchMakeameme.find()) {
                val imgId = matchMakeameme.group(1)
                if (imgId.length > 3) {
                    val imageUrl = ("https://media.makeameme.org/created/"
                            + imgId
                            + ".jpg")
                    return ImageInfo(
                        imageUrl,
                        ImageInfo.MediaType.IMAGE,
                        HasAudio.NO_AUDIO
                    )
                }
            }
        }
        run {
            val matchGiphy = giphyPattern.matcher(url)
            if (matchGiphy.find()) {
                return ImageInfo(
                    "https://media.giphy.com/media/"
                            + matchGiphy.group(1)
                            + "/giphy.mp4",
                    ImageInfo.MediaType.VIDEO,
                    HasAudio.NO_AUDIO
                )
            }
        }
        val imageExtensions = arrayOf(".jpg", ".jpeg", ".png")
        val videoExtensions = arrayOf(
            ".webm",
            ".mp4",
            ".h264",
            ".gifv",
            ".mkv",
            ".3gp"
        )
        for (ext in imageExtensions) {
            if (urlLower.endsWith(ext)) {
                return ImageInfo(
                    url,
                    ImageInfo.MediaType.IMAGE,
                    HasAudio.MAYBE_AUDIO
                )
            }
        }
        for (ext in videoExtensions) {
            if (urlLower.endsWith(ext)) {
                return ImageInfo(
                    url,
                    ImageInfo.MediaType.VIDEO,
                    HasAudio.MAYBE_AUDIO
                )
            }
        }
        if (urlLower.endsWith(".gif")) {
            val audio: HasAudio
            audio = if (urlLower.contains(".redd.it")) { // preview.redd.it or i.redd.it
                HasAudio.NO_AUDIO
            } else {
                HasAudio.MAYBE_AUDIO
            }
            return ImageInfo(url, ImageInfo.MediaType.GIF, audio)
        }
        if (url.contains("?")) {
            val urlBeforeQ = urlLower.split("\\?").toTypedArray()[0]
            for (ext in imageExtensions) {
                if (urlBeforeQ.endsWith(ext)) {
                    return ImageInfo(
                        url,
                        ImageInfo.MediaType.IMAGE,
                        HasAudio.MAYBE_AUDIO
                    )
                }
            }
            for (ext in videoExtensions) {
                if (urlBeforeQ.endsWith(ext)) {
                    return ImageInfo(
                        url,
                        ImageInfo.MediaType.VIDEO,
                        HasAudio.MAYBE_AUDIO
                    )
                }
            }
            if (urlBeforeQ.endsWith(".gif")) {
                val audio: HasAudio
                audio = if (urlLower.contains(".redd.it")) { // preview.redd.it or i.redd.it
                    HasAudio.NO_AUDIO
                } else {
                    HasAudio.MAYBE_AUDIO
                }
                return ImageInfo(url, ImageInfo.MediaType.GIF, audio)
            }
        }
        val matchQkme1 = qkmePattern1.matcher(url)
        if (matchQkme1.find()) {
            val imgId = matchQkme1.group(1)
            if (imgId.length > 2) {
                return ImageInfo(
                    String.format(
                        Locale.US,
                        "http://i.qkme.me/%s.jpg",
                        imgId
                    ), ImageInfo.MediaType.IMAGE, HasAudio.NO_AUDIO
                )
            }
        }
        val matchQkme2 = qkmePattern2.matcher(url)
        if (matchQkme2.find()) {
            val imgId = matchQkme2.group(1)
            if (imgId.length > 2) {
                return ImageInfo(
                    String.format(
                        Locale.US,
                        "http://i.qkme.me/%s.jpg",
                        imgId
                    ), ImageInfo.MediaType.IMAGE, HasAudio.NO_AUDIO
                )
            }
        }
        val matchLvme = lvmePattern.matcher(url)
        if (matchLvme.find()) {
            val imgId = matchLvme.group(1)
            if (imgId.length > 2) {
                return ImageInfo(
                    String.format(
                        Locale.US,
                        "http://www.livememe.com/%s.jpg",
                        imgId
                    ), ImageInfo.MediaType.IMAGE, HasAudio.NO_AUDIO
                )
            }
        }
        return null
    }

    @JvmStatic
	fun computeAllLinks(text: String?): LinkedHashSet<String> {
        val result = LinkedHashSet<String>()

        // From http://stackoverflow.com/a/1806161/1526861
        // TODO may not handle .co.uk, similar (but should handle .co/.us/.it/etc fine)
        val urlPattern = Pattern.compile(
            "\\b((((ht|f)tp(s?)\\:\\/\\/|~\\/|\\/)|www.)" +
                    "(\\w+:\\w+@)?(([-\\w]+\\.)+(com|org|net|gov" +
                    "|mil|biz|info|mobi|name|aero|jobs|museum" +
                    "|travel|[a-z]{2}))(:[\\d]{1,5})?" +
                    "(((\\/([-\\w~!$+|.,=]|%[a-f\\d]{2})+)+|\\/)+|\\?|#)?" +
                    "((\\?([-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
                    "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)" +
                    "(&(?:[-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
                    "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)*)*" +
                    "(#([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)?)\\b"
        )
        val urlMatcher = urlPattern.matcher(text)
        while (urlMatcher.find()) {
            result.add(urlMatcher.group(1))
        }
        val subredditMatcher = Pattern.compile("(?<!\\w)(/?[ru]/\\w+)\\b")
            .matcher(text)
        while (subredditMatcher.find()) {
            result.add(subredditMatcher.group(1))
        }
        return result
    }

    @JvmStatic
	fun shareText(
        activity: AppCompatActivity,
        subject: String?,
        text: String?
    ) {
        var text = text
        if (text == null) {
            text = "<null>"
        }
        val mailer = Intent(Intent.ACTION_SEND)
        mailer.type = "text/plain"
        mailer.putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) {
            mailer.putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        if (pref_behaviour_sharing_dialog()) {
            ShareOrderDialog.newInstance(mailer).show(
                activity.supportFragmentManager,
                null
            )
        } else {
            activity.startActivity(
                Intent.createChooser(
                    mailer,
                    activity.getString(R.string.action_share)
                )
            )
        }
    }

    enum class LinkAction(val descriptionResId: Int) {
        SHARE(R.string.action_share), COPY_URL(R.string.action_copy_link), SHARE_IMAGE(R.string.action_share_image), SAVE_IMAGE(
            R.string.action_save
        ),
        EXTERNAL(R.string.action_external);
    }

    private abstract class ImageInfoRetryListener constructor(private val mListener: GetImageInfoListener) :
        GetImageInfoListener {
        override fun onSuccess(info: ImageInfo) {
            mListener.onSuccess(info)
        }

        override fun onNotAnImage() {
            mListener.onNotAnImage()
        }
    }

    private abstract class AlbumInfoRetryListener constructor(private val mListener: GetAlbumInfoListener) :
        GetAlbumInfoListener {
        override fun onGalleryRemoved() {
            mListener.onGalleryRemoved()
        }

        override fun onGalleryDataNotPresent() {
            mListener.onGalleryDataNotPresent()
        }

        override fun onSuccess(info: AlbumInfo) {
            mListener.onSuccess(info)
        }
    }

    private class LinkMenuItem constructor(
        context: Context,
        titleRes: Int,
        action: LinkAction
    ) {
        val title: String
        val action: LinkAction

        init {
            title = context.getString(titleRes)
            this.action = action
        }
    }
}
