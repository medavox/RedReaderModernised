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
package org.quantumbadger.redreader.activities

import android.annotation.SuppressLint
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost.Companion.onActionMenuItemSelected
import org.quantumbadger.redreader.views.RedditPostView.PostSelectionListener
import org.quantumbadger.redreader.views.imageview.ImageViewDisplayListManager
import android.opengl.GLSurfaceView
import org.quantumbadger.redreader.views.video.ExoPlayerWrapperView
import org.quantumbadger.redreader.views.HorizontalSwipeProgressOverlay
import org.quantumbadger.redreader.reddit.things.RedditPost
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.content.res.Configuration
import org.quantumbadger.redreader.cache.CacheRequest.RequestFailureType
import org.quantumbadger.redreader.http.FailedRequestBody
import com.github.lzyzsd.circleprogress.DonutProgress
import android.view.ViewGroup.MarginLayoutParams
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost
import org.quantumbadger.redreader.reddit.prepared.RedditParsedPost
import org.quantumbadger.redreader.cache.CacheManager
import org.quantumbadger.redreader.views.bezelmenu.SideToolbarOverlay
import org.quantumbadger.redreader.views.bezelmenu.BezelSwipeOverlay
import org.quantumbadger.redreader.views.bezelmenu.BezelSwipeOverlay.BezelSwipeListener
import org.quantumbadger.redreader.views.bezelmenu.BezelSwipeOverlay.SwipeEdge
import org.quantumbadger.redreader.common.datastream.SeekableInputStream
import org.quantumbadger.redreader.fragments.ImageInfoDialog
import org.quantumbadger.redreader.common.PrefsUtility.VideoViewMode
import org.quantumbadger.redreader.common.PrefsUtility.GifViewMode
import org.quantumbadger.redreader.common.PrefsUtility.ImageViewMode
import org.quantumbadger.redreader.reddit.url.PostCommentListingURL
import org.quantumbadger.redreader.account.RedditAccountManager
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfNotCached
import org.quantumbadger.redreader.cache.CacheRequestCallbacks
import org.quantumbadger.redreader.views.liststatus.ErrorView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import org.quantumbadger.redreader.views.video.ExoPlayerSeekableInputStreamDataSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import org.quantumbadger.redreader.views.video.ExoPlayerSeekableInputStreamDataSource
import com.google.android.exoplayer2.source.MergingMediaSource
import org.quantumbadger.redreader.views.imageview.BasicGestureHandler
import android.graphics.Movie
import org.quantumbadger.redreader.views.GIFView
import org.quantumbadger.redreader.image.GifDecoderThread.OnGifLoadedListener
import org.quantumbadger.redreader.views.imageview.ImageTileSource
import org.quantumbadger.redreader.views.imageview.ImageTileSourceWholeBitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.google.android.exoplayer2.MediaItem
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.cache.CacheRequest
import org.quantumbadger.redreader.common.*
import org.quantumbadger.redreader.common.Optional
import org.quantumbadger.redreader.image.*
import org.quantumbadger.redreader.views.glview.RRGLSurfaceView
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.net.URI
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ImageViewActivity : BaseActivity(), PostSelectionListener,
    ImageViewDisplayListManager.Listener {
    private var mProgressText: TextView? = null
    private var surfaceView: GLSurfaceView? = null
    private var imageView: ImageView? = null
    private var gifThread: GifDecoderThread? = null
    private var mVideoPlayerWrapper: ExoPlayerWrapperView? = null
    private var mUrl: String? = null
    private var mIsPaused = true
    private var mIsDestroyed = false
    private val mActionsOnResume = ArrayList<Runnable>()
    private var mImageOrVideoRequest: CacheRequest? = null
    private var mAudioRequest: CacheRequest? = null
    private var mHaveReverted = false
    private var mImageViewDisplayerManager: ImageViewDisplayListManager? = null
    private var mSwipeOverlay: HorizontalSwipeProgressOverlay? = null
    private var mSwipeCancelled = false
    private var mPost: RedditPost? = null
    private var mImageInfo: ImageInfo? = null
    private var mAlbumInfo: AlbumInfo? = null
    private var mAlbumImageIndex = 0
    private var mLayout: FrameLayout? = null
    private var mGallerySwipeLengthPx = 0
    private var mFloatingToolbar: LinearLayout? = null
    override fun baseActivityIsToolbarActionBarEnabled(): Boolean {
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
	override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PrefsUtility.pref_appearance_android_status()
            == PrefsUtility.AppearanceStatusBarMode.HIDE_ON_MEDIA
        ) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = Color.BLACK
        }
        setTitle(R.string.accessibility_image_viewer_title)
        val gallerySwipeLengthDp = PrefsUtility.pref_behaviour_gallery_swipe_length_dp()
        mGallerySwipeLengthPx = General.dpToPixels(this, gallerySwipeLengthDp.toFloat())
        val intent = intent
        mUrl = intent.dataString
        if (mUrl == null) {
            finish()
            return
        }
        mPost = intent.getParcelableExtra("post")
        if (intent.hasExtra("albumUrl")) {
            LinkHandler.getAlbumInfo(
                this,
                intent.getStringExtra("albumUrl"),
                Priority(Constants.Priority.IMAGE_VIEW),
                object : GetAlbumInfoListener {
                    override fun onFailure(
                        @RequestFailureType type: Int,
                        t: Throwable,
                        status: Int,
                        readableMessage: String,
                        body: Optional<FailedRequestBody>
                    ) {

                        // Do nothing
                    }

                    override fun onGalleryRemoved() {
                        // Do nothing
                    }

                    override fun onGalleryDataNotPresent() {
                        // Do nothing
                    }

                    override fun onSuccess(info: AlbumInfo) {
                        AndroidCommon.UI_THREAD_HANDLER.post {
                            mAlbumInfo = info
                            mAlbumImageIndex = intent.getIntExtra(
                                "albumImageIndex",
                                0
                            )
                        }
                    }
                }
            )
        }
        val progressBar = DonutProgress(this)
        progressBar.setIndeterminate(true)
        progressBar.setFinishedStrokeColor(Color.rgb(200, 200, 200))
        progressBar.setUnfinishedStrokeColor(Color.rgb(50, 50, 50))
        progressBar.setAspectIndicatorStrokeColor(Color.rgb(200, 200, 200))
        val progressStrokeWidthPx = General.dpToPixels(this, 15f)
        progressBar.setUnfinishedStrokeWidth(progressStrokeWidthPx.toFloat())
        progressBar.setFinishedStrokeWidth(progressStrokeWidthPx.toFloat())
        progressBar.setAspectIndicatorStrokeWidth(General.dpToPixels(this, 1f).toFloat())
        progressBar.startingDegree = -90
        progressBar.initPainters()
        val progressTextLayout = LinearLayout(this)
        progressTextLayout.orientation = LinearLayout.VERTICAL
        progressTextLayout.gravity = Gravity.CENTER_HORIZONTAL
        progressTextLayout.addView(progressBar)
        val progressDimensionsPx = General.dpToPixels(this, 150f)
        progressBar.layoutParams.width = progressDimensionsPx
        progressBar.layoutParams.height = progressDimensionsPx
        mProgressText = TextView(this)
        mProgressText!!.setText(R.string.download_loading)
        mProgressText!!.isAllCaps = true
        mProgressText!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        mProgressText!!.gravity = Gravity.CENTER_HORIZONTAL
        progressTextLayout.addView(mProgressText)
        mProgressText!!.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mProgressText!!.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        (mProgressText!!.layoutParams as MarginLayoutParams).topMargin =
            General.dpToPixels(this, 10f)
        val progressLayout = RelativeLayout(this)
        progressLayout.addView(progressTextLayout)
        (progressTextLayout.layoutParams as RelativeLayout.LayoutParams).addRule(
            RelativeLayout.CENTER_IN_PARENT
        )
        General.setLayoutMatchWidthWrapHeight(progressTextLayout)
        mLayout = FrameLayout(this)
        mLayout!!.addView(progressLayout)
        LinkHandler.getImageInfo(
            this,
            mUrl,
            Priority(Constants.Priority.IMAGE_VIEW),
            object : GetImageInfoListener {
                override fun onFailure(
                    @RequestFailureType type: Int,
                    t: Throwable,
                    status: Int,
                    readableMessage: String,
                    body: Optional<FailedRequestBody>
                ) {
                    General.quickToast(
                        this@ImageViewActivity,
                        R.string.imageview_image_info_failed
                    )
                    revertToWeb()
                }

                override fun onSuccess(info: ImageInfo) {
                    mImageInfo = info
                    val uri = General.uriFromString(info.urlOriginal)
					if (uri == null) {
                        General.quickToast(
                            this@ImageViewActivity,
                            R.string.imageview_image_info_failed
                        )
                        revertToWeb()
                        return
                    }
					val audioUri: URI? = if (info.urlAudioStream == null) {
						null
					} else {
						General.uriFromString(info.urlAudioStream)
					}
                    openImage(progressBar, uri, audioUri)
                }

                override fun onNotAnImage() {
                    revertToWeb()
                }
            })
		val post: RedditPreparedPost? = if (mPost != null) {
			val parsedPost = RedditParsedPost(this, mPost, false)
			RedditPreparedPost(
				this,
				CacheManager.getInstance(this),
				0,
				parsedPost,
				-1,
				showSubreddit = false,
				showThumbnails = false,
				allowHighResThumbnails = false,
				mShowInlinePreviews = false
			)
		} else {
			null
		}
        val hiddenAccessibilityLayout = LayoutInflater.from(this)
            .inflate(R.layout.image_view_hidden_accessibility_layout, null)
        run {
            val commentsButton = hiddenAccessibilityLayout.findViewById<View>(
                R.id.image_view_hidden_accessibility_view_comments
            )
            val backButton = hiddenAccessibilityLayout.findViewById<View>(
                R.id.image_view_hidden_accessibility_go_back
            )
            if (post != null) {
                commentsButton.setOnClickListener { v: View? ->
                    onActionMenuItemSelected(
                        post,
                        this,
                        RedditPreparedPost.Action.COMMENTS_SWITCH
                    )
                }
            } else {
                commentsButton.contentDescription = null
                commentsButton.isClickable = false
                commentsButton.isFocusable = false
                commentsButton.visibility = View.GONE
            }
            backButton.setOnClickListener { v: View? -> finish() }

            //Consume & ignore touch events, so loading images aren't closed by tapping.
            backButton.setOnTouchListener { _: View?, _: MotionEvent? -> true }
        }
        val outerFrame = FrameLayout(this)
        outerFrame.addView(hiddenAccessibilityLayout)
        outerFrame.addView(mLayout)
        General.setLayoutMatchParent(mLayout)
        if (PrefsUtility.pref_appearance_image_viewer_show_floating_toolbar()) {
			val floatingToolbarLocalVal =  LayoutInflater.from(this).inflate(
                    R.layout.floating_toolbar,
                    outerFrame,
                    false
                )!! as LinearLayout
			mFloatingToolbar = floatingToolbarLocalVal
            outerFrame.addView(floatingToolbarLocalVal)
			floatingToolbarLocalVal.visibility = View.GONE
        }
        if (post != null) {
            val toolbarOverlay = SideToolbarOverlay(this)
            val bezelOverlay = BezelSwipeOverlay(
                this,
                object : BezelSwipeListener {
                    override fun onSwipe(@SwipeEdge edge: Int): Boolean {
                        toolbarOverlay.setContents(
                            post.generateToolbar(
                                this@ImageViewActivity,
                                false,
                                toolbarOverlay
                            )
                        )
                        toolbarOverlay.show(if (edge == BezelSwipeOverlay.LEFT) SideToolbarOverlay.SideToolbarPosition.LEFT else SideToolbarOverlay.SideToolbarPosition.RIGHT)
                        return true
                    }

                    override fun onTap(): Boolean {
                        if (toolbarOverlay.isShown) {
                            toolbarOverlay.hide()
                            return true
                        }
                        return false
                    }
                })
            outerFrame.addView(bezelOverlay)
            outerFrame.addView(toolbarOverlay)
            General.setLayoutMatchParent(bezelOverlay)
            General.setLayoutMatchParent(toolbarOverlay)
        }
        setBaseActivityListing(outerFrame)
    }

    private fun setMainView(v: View) {
        mLayout!!.removeAllViews()
        mLayout!!.addView(v)
        mSwipeOverlay = HorizontalSwipeProgressOverlay(this)
        mLayout!!.addView(mSwipeOverlay)
        General.setLayoutMatchParent(v)
    }

    private fun onImageStreamReady(
        isNetwork: Boolean,
        videoStream: GenericFactory<SeekableInputStream, IOException>,
        audioStream: GenericFactory<SeekableInputStream, IOException>?,
        mimetype: String?,
        videoStreamUri: Uri
    ) {
        General.startNewThread("ImageViewActivity") {
            Log.i(TAG, "Image stream ready")
            if (mimetype == null) {
                revertToWeb()
                return@startNewThread
            }
            val isOctetStream = Constants.Mime.isOctetStream(mimetype)
            val isImage = (Constants.Mime.isImage(mimetype)
                    || mImageInfo!!.mediaType == ImageInfo.MediaType.IMAGE && isOctetStream)
            val isVideo = (Constants.Mime.isVideo(mimetype)
                    || mImageInfo!!.mediaType == ImageInfo.MediaType.VIDEO && isOctetStream)
            val isGif = !isVideo && !isImage && Constants.Mime.isImageGif(mimetype)
            if (!isImage && !isVideo && !isGif) {
                Log.e(TAG, "Cannot play mimetype: $mimetype")
                revertToWeb()
                return@startNewThread
            }
            if (mImageInfo != null && (mImageInfo!!.title != null && !mImageInfo!!.title.isEmpty()
                        || mImageInfo!!.caption != null && !mImageInfo!!.caption.isEmpty())
            ) {
                AndroidCommon.UI_THREAD_HANDLER.post {
                    addFloatingToolbarButton(
                        R.drawable.ic_action_info_dark,
                        R.string.props_image_title
                    ) { view: View? ->
                        ImageInfoDialog.newInstance(mImageInfo).show(
                            supportFragmentManager,
                            null
                        )
                    }
                }
            }
            val fullyDownloadBeforePlaying = PrefsUtility.pref_videos_download_before_playing()
            if (isNetwork && fullyDownloadBeforePlaying && (isVideo || isGif)) {
                Log.i(TAG, "Fully downloading before starting playback")
                try {
                    videoStream.create()
                        .readRemainingAsBytes { buf: ByteArray?, offset: Int, length: Int ->
                            Log.i(
                                TAG, "Video fully downloaded, starting playback"
                            )
                        }
                } catch (e: IOException) {
                    Log.e(TAG, "Got exception while fully buffering", e)
                    General.quickToast(this, R.string.imageview_download_failed)
                    revertToWeb()
                    return@startNewThread
                }
            }
            if (isVideo) {
                AndroidCommon.UI_THREAD_HANDLER.post {
                    if (mIsDestroyed) {
                        return@post
                    }
                    val videoViewMode = PrefsUtility.pref_behaviour_videoview_mode()
                    if (videoViewMode == VideoViewMode.INTERNAL_BROWSER) {
                        revertToWeb()
                    } else if (videoViewMode == VideoViewMode.EXTERNAL_BROWSER) {
                        openInExternalBrowser()
                    } else if (videoViewMode == VideoViewMode.EXTERNAL_APP_VLC) {
                        cancelCacheRequests()
                        launchVlc(videoStreamUri)
                    } else {
                        playWithExoplayer(isNetwork, videoStream, audioStream)
                    }
                }
            } else if (isGif) {
                val gifViewMode = PrefsUtility.pref_behaviour_gifview_mode()
                if (gifViewMode == GifViewMode.INTERNAL_BROWSER) {
                    revertToWeb()
                    return@startNewThread
                } else if (gifViewMode == GifViewMode.EXTERNAL_BROWSER) {
                    openInExternalBrowser()
                    return@startNewThread
                }
                if (gifViewMode == GifViewMode.INTERNAL_MOVIE) {
                    playGIFWithMovie(videoStream)
                } else {
                    playGIFWithLegacyDecoder(videoStream)
                }
            } else {
                val imageViewMode = PrefsUtility.pref_behaviour_imageview_mode()
                if (imageViewMode == ImageViewMode.INTERNAL_BROWSER) {
                    revertToWeb()
                } else if (imageViewMode == ImageViewMode.EXTERNAL_BROWSER) {
                    openInExternalBrowser()
                } else {
                    showImageWithInternalViewer(videoStream)
                }
            }
        }
    }

    override fun onPostSelected(post: RedditPreparedPost) {
        LinkHandler.onLinkClicked(this, post.src.url, false, post.src.src)
    }

    override fun onPostCommentsSelected(post: RedditPreparedPost) {
        LinkHandler.onLinkClicked(
            this,
            PostCommentListingURL.forPostId(post.src.idAlone)
                .generateJsonUri()
                .toString(),
            false
        )
    }

    override fun onBackPressed() {
        if (General.onBackPressed()) {
            super.onBackPressed()
        }
    }

    private fun revertToWeb() {
        Log.i(TAG, "Using internal browser", RuntimeException())
        val r: Runnable = object : Runnable {
            override fun run() {
                if (mIsPaused) {
                    Log.i(TAG, "Not reverting as we are paused. Queuing for later.")
                    mActionsOnResume.add(this)
                    return
                }
                if (mIsDestroyed) {
                    Log.i(TAG, "Not reverting as we are destroyed")
                    return
                }
                if (!mHaveReverted) {
                    mHaveReverted = true
                    LinkHandler.onLinkClicked(this@ImageViewActivity, mUrl, true)
                    finish()
                }
            }
        }
        AndroidCommon.runOnUiThread(r)
    }

    private fun openInExternalBrowser() {
        Log.i(TAG, "Using external browser")
        val r = Runnable {
            LinkHandler.openWebBrowser(this, Uri.parse(mUrl), false)
            finish()
        }
        if (General.isThisUIThread()) {
            r.run()
        } else {
            AndroidCommon.UI_THREAD_HANDLER.post(r)
        }
    }

    public override fun onPause() {
        if (mIsPaused) {
            throw RuntimeException()
        }
        mIsPaused = true
        super.onPause()
        if (surfaceView != null) {
            surfaceView!!.onPause()
        }
    }

    public override fun onResume() {
        if (!mIsPaused) {
            throw RuntimeException()
        }
        mIsPaused = false
        super.onResume()
        if (surfaceView != null) {
            surfaceView!!.onResume()
        }
        for (runnable in mActionsOnResume) {
            runnable.run()
        }
        mActionsOnResume.clear()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mIsDestroyed = true
        cancelCacheRequests()
        if (gifThread != null) {
            gifThread!!.stopPlaying()
        }
        if (mVideoPlayerWrapper != null) {
            mVideoPlayerWrapper!!.release()
            mVideoPlayerWrapper = null
        }
    }

    private fun cancelCacheRequests() {
        if (mImageOrVideoRequest != null) {
            mImageOrVideoRequest!!.cancel()
        }
        if (mAudioRequest != null) {
            mAudioRequest!!.cancel()
        }
    }

    override fun onSingleTap() {
        if (PrefsUtility.pref_behaviour_video_playback_controls()
            && mVideoPlayerWrapper != null
        ) {
            mVideoPlayerWrapper!!.handleTap()
            if (mFloatingToolbar != null) {
                if (mVideoPlayerWrapper!!.isControlViewVisible == View.VISIBLE) {
                    mFloatingToolbar!!.visibility = View.GONE
                } else {
                    mFloatingToolbar!!.visibility = View.VISIBLE
                }
            }
        } else if (PrefsUtility.pref_behaviour_imagevideo_tap_close()) {
            finish()
        }
    }

    override fun onHorizontalSwipe(pixels: Float) {
        if (mSwipeCancelled) {
            return
        }
        if (mSwipeOverlay != null && mAlbumInfo != null) {
            mSwipeOverlay!!.onSwipeUpdate(pixels, mGallerySwipeLengthPx.toFloat())
            if (pixels >= mGallerySwipeLengthPx) {
                // Back
                mSwipeCancelled = true
                if (mSwipeOverlay != null) {
                    mSwipeOverlay!!.onSwipeEnd()
                }
                if (mAlbumImageIndex > 0) {
                    LinkHandler.onLinkClicked(
                        this,
                        mAlbumInfo!!.images[mAlbumImageIndex - 1].urlOriginal,
                        false,
                        mPost,
                        mAlbumInfo,
                        mAlbumImageIndex - 1
                    )
                    finish()
                } else {
                    General.quickToast(this, R.string.album_already_first_image)
                }
            } else if (pixels <= -mGallerySwipeLengthPx) {
                // Forwards
                mSwipeCancelled = true
                if (mSwipeOverlay != null) {
                    mSwipeOverlay!!.onSwipeEnd()
                }
                if (mAlbumImageIndex < mAlbumInfo!!.images.size - 1) {
                    LinkHandler.onLinkClicked(
                        this,
                        mAlbumInfo!!.images[mAlbumImageIndex + 1].urlOriginal,
                        false,
                        mPost,
                        mAlbumInfo,
                        mAlbumImageIndex + 1
                    )
                    finish()
                } else {
                    General.quickToast(this, R.string.album_already_last_image)
                }
            }
        }
    }

    override fun onHorizontalSwipeEnd() {
        mSwipeCancelled = false
        if (mSwipeOverlay != null) {
            mSwipeOverlay!!.onSwipeEnd()
        }
    }

    override fun onImageViewDLMOutOfMemory() {
        if (!mHaveReverted) {
            General.quickToast(this, R.string.imageview_oom)
            revertToWeb()
        }
    }

    override fun onImageViewDLMException(t: Throwable) {
        if (!mHaveReverted) {
            General.quickToast(this, R.string.imageview_decode_failed)
            revertToWeb()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mImageViewDisplayerManager != null) {
            mImageViewDisplayerManager!!.resetTouchState()
        }
    }

    private fun openImage(
        progressBar: DonutProgress,
        uri: URI,
        audioUri: URI?
    ) {
        if (mImageInfo!!.mediaType != null) {
            Log.i(TAG, "Media type " + mImageInfo!!.mediaType + " detected")
            if (mImageInfo!!.mediaType == ImageInfo.MediaType.IMAGE) {
                val imageViewMode = PrefsUtility.pref_behaviour_imageview_mode()
                if (imageViewMode == ImageViewMode.EXTERNAL_BROWSER) {
                    openInExternalBrowser()
                    return
                } else if (imageViewMode == ImageViewMode.INTERNAL_BROWSER) {
                    revertToWeb()
                    return
                }
            } else if (mImageInfo!!.mediaType == ImageInfo.MediaType.GIF) {
                val gifViewMode = PrefsUtility.pref_behaviour_gifview_mode()
                if (gifViewMode == GifViewMode.EXTERNAL_BROWSER) {
                    openInExternalBrowser()
                    return
                } else if (gifViewMode == GifViewMode.INTERNAL_BROWSER) {
                    revertToWeb()
                    return
                }
            } else if (mImageInfo!!.mediaType == ImageInfo.MediaType.VIDEO) {
                val videoViewMode = PrefsUtility.pref_behaviour_videoview_mode()
                if (videoViewMode == VideoViewMode.EXTERNAL_BROWSER) {
                    openInExternalBrowser()
                    return
                } else if (videoViewMode == VideoViewMode.INTERNAL_BROWSER) {
                    revertToWeb()
                    return
                } else if (videoViewMode == VideoViewMode.EXTERNAL_APP_VLC) {
                    launchVlc(Uri.parse(uri.toString()))
                }
            }
        }
        Log.i(TAG, "Proceeding with download")
        makeCacheRequest(progressBar, uri, audioUri)
    }

    private fun manageAspectRatioIndicator(progressBar: DonutProgress) {
        if (PrefsUtility.pref_appearance_show_aspect_ratio_indicator()) {
            if (mImageInfo!!.width != null && mImageInfo!!.height != null && mImageInfo!!.width > 0 && mImageInfo!!.height > 0) {
                progressBar.setLoadingImageAspectRatio(mImageInfo!!.width.toFloat() / mImageInfo!!.height)
            	progressBar.setAspectIndicatorDisplay(true)
            } else {
        		progressBar.setAspectIndicatorDisplay(false)
            }
        }
    }

    private fun makeCacheRequest(
        progressBar: DonutProgress,
        uri: URI,
        audioUri: URI?
    ) {
        val resultLock = Any()
        val failed = AtomicBoolean(false)
        val audio = AtomicReference<GenericFactory<SeekableInputStream, IOException>?>()
        val video = AtomicReference<GenericFactory<SeekableInputStream, IOException>>()
        val videoMimetype = AtomicReference<String?>()
        CacheManager.getInstance(this).makeRequest(CacheRequest(
            uri,
            RedditAccountManager.getAnon(),
            null,
            Priority(Constants.Priority.IMAGE_VIEW),
            DownloadStrategyIfNotCached.INSTANCE,
            Constants.FileType.IMAGE,
            CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
            this,
            object : CacheRequestCallbacks {
                private var mProgressTextSet = false
                override fun onFailure(
                    type: Int,
                    t: Throwable?,
                    httpStatus: Int?,
                    readableMessage: String?,
                    body: Optional<FailedRequestBody>
                ) {
                    synchronized(resultLock) {
                        if (!failed.getAndSet(true)) {
                            if (type == CacheRequest.REQUEST_FAILURE_CONNECTION
                                && uri.host.contains("redgifs")
                            ) {

                                // Redgifs have lots of server issues
                                revertToWeb()
                                return
                            }
                            val error = General.getGeneralErrorForFailure(
                                this@ImageViewActivity,
                                type,
                                t,
                                httpStatus,
                                uri.toString(),
                                body
                            )
                            AndroidCommon.UI_THREAD_HANDLER.post {
                                val layout = LinearLayout(this@ImageViewActivity)
                                val errorView = ErrorView(
                                    this@ImageViewActivity,
                                    error
                                )
                                layout.addView(errorView)
                                General.setLayoutMatchWidthWrapHeight(errorView)
                                setMainView(layout)
                            }
                        }
                    }
                }

                override fun onDownloadNecessary() {
                    AndroidCommon.runOnUiThread {
                        progressBar.visibility = View.VISIBLE
                        progressBar.setIndeterminate(true)
                        manageAspectRatioIndicator(progressBar)
                    }
                }

                override fun onProgress(
                    authorizationInProgress: Boolean,
                    bytesRead: Long,
                    totalBytes: Long
                ) {
                    AndroidCommon.runOnUiThread {
                        progressBar.visibility = View.VISIBLE
                        progressBar.setIndeterminate(authorizationInProgress)
                        progressBar.progress = (1000 * bytesRead / totalBytes).toFloat() / 1000
                        manageAspectRatioIndicator(progressBar)
                        if (!mProgressTextSet) {
                            mProgressText!!.text = General.bytesToMegabytes(totalBytes)
                            mProgressTextSet = true
                        }
                    }
                }

                override fun onDataStreamAvailable(
                    streamFactory: GenericFactory<SeekableInputStream, IOException>,
                    timestamp: Long,
                    session: UUID,
                    fromCache: Boolean,
                    mimetype: String?
                ) {
                    synchronized(resultLock) {
                        if (audio.get() != null || audioUri == null) {
                            onImageStreamReady(
                                !fromCache,
                                streamFactory,
                                audio.get(),
                                mimetype,
                                Uri.parse(uri.toString())
                            )
                        } else {
                            video.set(streamFactory)
                            videoMimetype.set(mimetype)
                        }
                    }
                }
            }).also { mImageOrVideoRequest = it })
        if (audioUri != null) {
            CacheManager.getInstance(this).makeRequest(CacheRequest(
                audioUri,
                RedditAccountManager.getAnon(),
                null,
                Priority(Constants.Priority.IMAGE_VIEW),
                DownloadStrategyIfNotCached.INSTANCE,
                Constants.FileType.IMAGE,
                CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
                this,
                object : CacheRequestCallbacks {
                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        httpStatus: Int?,
                        readableMessage: String?,
                        body: Optional<FailedRequestBody>
                    ) {
                        synchronized(resultLock) {
                            if (!failed.getAndSet(true)) {
                                val error = General.getGeneralErrorForFailure(
                                    this@ImageViewActivity,
                                    type,
                                    t,
                                    httpStatus,
                                    audioUri.toString(),
                                    body
                                )
                                AndroidCommon.runOnUiThread {
                                    val layout = LinearLayout(this@ImageViewActivity)
                                    val errorView = ErrorView(
                                        this@ImageViewActivity,
                                        error
                                    )
                                    layout.addView(errorView)
                                    General.setLayoutMatchWidthWrapHeight(errorView)
                                    setMainView(layout)
                                }
                            }
                        }
                    }

                    override fun onDataStreamAvailable(
                        streamFactory: GenericFactory<SeekableInputStream, IOException>,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean,
                        mimetype: String?
                    ) {
                        synchronized(resultLock) {
                            if (video.get() != null) {
                                onImageStreamReady(
                                    !fromCache,
                                    video.get(),
                                    streamFactory,
                                    videoMimetype.get(),
                                    Uri.parse(uri.toString())
                                )
                            } else {
                                audio.set(streamFactory)
                            }
                        }
                    }
                }).also { mAudioRequest = it })
        }
    }

    private fun addFloatingToolbarButton(
        @DrawableRes drawable: Int,
        @StringRes description: Int,
        listener: View.OnClickListener
    ): ImageButton? {
        if (mFloatingToolbar == null) {
            return null
        }
        mFloatingToolbar!!.visibility = View.VISIBLE
        val ib = LayoutInflater.from(this).inflate(
            R.layout.flat_image_button,
            mFloatingToolbar,
            false
        ) as ImageButton
        val buttonPadding = General.dpToPixels(this, 10f)
        ib.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
        ib.setImageResource(drawable)
        ib.contentDescription = resources.getString(description)
        ib.setOnClickListener(listener)
        mFloatingToolbar!!.addView(ib)
        return ib
    }

    private fun launchVlc(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setClassName(
            "org.videolan.vlc",
            "org.videolan.vlc.gui.video.VideoPlayerActivity"
        )
        intent.setDataAndType(uri, "video/*")
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            General.quickToast(this, R.string.videoview_mode_app_vlc_launch_failed)
            Log.e(TAG, "VLC failed to launch", t)
        }
        finish()
    }

    @SuppressLint("ClickableViewAccessibility")
	@UiThread
    private fun playWithExoplayer(
        isNetwork: Boolean,
        videoStream: GenericFactory<SeekableInputStream, IOException>,
        audioStream: GenericFactory<SeekableInputStream, IOException>?
    ) {
        General.checkThisIsUIThread()
        try {
            Log.i(TAG, "Playing video using ExoPlayer")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val layout = RelativeLayout(this)
            layout.gravity = Gravity.CENTER
            val videoDataSourceFactory =
                ExoPlayerSeekableInputStreamDataSourceFactory(isNetwork, videoStream)
            val mediaSource: MediaSource
            val videoMediaSource: MediaSource =
                ProgressiveMediaSource.Factory(videoDataSourceFactory)
                    .createMediaSource(
                        MediaItem.fromUri(
                            ExoPlayerSeekableInputStreamDataSource.URI
                        )
                    )
            mediaSource = if (audioStream == null) {
                videoMediaSource
            } else {
                val audioDataSourceFactory =
                    ExoPlayerSeekableInputStreamDataSourceFactory(isNetwork, audioStream)
                MergingMediaSource(
                    videoMediaSource,
                    ProgressiveMediaSource.Factory(audioDataSourceFactory)
                        .createMediaSource(
                            MediaItem.fromUri(
                                ExoPlayerSeekableInputStreamDataSource.URI
                            )
                        )
                )
            }
            mVideoPlayerWrapper = ExoPlayerWrapperView(
                this,
                mediaSource, { revertToWeb() },
                0
            )
            layout.addView(mVideoPlayerWrapper)
            setMainView(layout)
            General.setLayoutMatchParent(layout)
            General.setLayoutMatchParent(mVideoPlayerWrapper)
            val gestureHandler = BasicGestureHandler(this)
            mVideoPlayerWrapper!!.setOnTouchListener(gestureHandler)
            layout.setOnTouchListener(gestureHandler)
            val muteByDefault = PrefsUtility.pref_behaviour_video_mute_default()
            mVideoPlayerWrapper!!.isMuted = muteByDefault
            val iconMuted = R.drawable.ic_volume_off_white_24dp
            val iconUnmuted = R.drawable.ic_volume_up_white_24dp
            if (mImageInfo != null
                && mImageInfo!!.hasAudio
                != ImageInfo.HasAudio.NO_AUDIO
            ) {
                val muteButton = AtomicReference<ImageButton?>()
                muteButton.set(addFloatingToolbarButton(
                    if (muteByDefault) iconMuted else iconUnmuted,
                    if (muteByDefault) R.string.video_unmute else R.string.video_mute
                ) { view: View? ->
                    val button = muteButton.get()
                    if (mVideoPlayerWrapper!!.isMuted) {
                        mVideoPlayerWrapper!!.isMuted = false
                        button!!.setImageResource(iconUnmuted)
                        button.contentDescription = resources.getString(R.string.video_mute)
                    } else {
                        mVideoPlayerWrapper!!.isMuted = true
                        button!!.setImageResource(iconMuted)
                        button.contentDescription = resources.getString(R.string.video_unmute)
                    }
                })
            }
        } catch (e: OutOfMemoryError) {
            General.quickToast(this, R.string.imageview_oom)
            revertToWeb()
        } catch (e: Throwable) {
            General.quickToast(this, R.string.imageview_invalid_video)
            revertToWeb()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
	private fun playGIFWithMovie(
        streamFactory: GenericFactory<SeekableInputStream, IOException>
    ) {
        Log.i(TAG, "Playing GIF using Movie API")
        try {
            streamFactory.create().use { `is` ->
                Log.i(TAG, "Got input stream of type " + `is`.javaClass.canonicalName)
                `is`.readRemainingAsBytes { buf: ByteArray?, offset: Int, length: Int ->
                    Log.i(TAG, "Got byte array")
                    val movie: Movie
                    movie = try {
                        GIFView.prepareMovie(buf!!, offset, length)
                    } catch (e: OutOfMemoryError) {
                        General.quickToast(this, R.string.imageview_oom)
                        revertToWeb()
                        return@readRemainingAsBytes
                    } catch (e: Throwable) {
                        General.quickToast(this, R.string.imageview_invalid_gif)
                        revertToWeb()
                        return@readRemainingAsBytes
                    }
                    AndroidCommon.UI_THREAD_HANDLER.post {
                        if (mIsDestroyed) {
                            return@post
                        }
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val gifView = GIFView(this, movie)
                        setMainView(gifView)
                        gifView.setOnTouchListener(BasicGestureHandler(this))
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read GIF data", e)
            General.quickToast(this, R.string.imageview_download_failed)
            revertToWeb()
        }
    }

    private fun playGIFWithLegacyDecoder(
        streamFactory: GenericFactory<SeekableInputStream, IOException>
    ) {
        Log.i(TAG, "Playing GIF using legacy decoder")

        // The GIF decoder thread will close this itself
		val `is`: InputStream = try {
			streamFactory.create()
		} catch (e: IOException) {
			General.quickToast(this, R.string.imageview_download_failed)
			revertToWeb()
			return
		}
        gifThread = GifDecoderThread(
            `is`,
            object : OnGifLoadedListener {
                @SuppressLint("ClickableViewAccessibility")
				override fun onGifLoaded() {
                    AndroidCommon.UI_THREAD_HANDLER.post {
                        if (mIsDestroyed) {
                            return@post
                        }
                        imageView = ImageView(this@ImageViewActivity)
                        imageView!!.scaleType = ImageView.ScaleType.FIT_CENTER
                        setMainView(imageView!!)
                        gifThread!!.setView(imageView)
                        imageView!!.setOnTouchListener(
                            BasicGestureHandler(
                                this@ImageViewActivity
                            )
                        )
                    }
                }

                override fun onOutOfMemory() {
                    General.quickToast(
                        this@ImageViewActivity,
                        R.string.imageview_oom
                    )
                    revertToWeb()
                }

                override fun onGifInvalid() {
                    General.quickToast(
                        this@ImageViewActivity,
                        R.string.imageview_invalid_gif
                    )
                    revertToWeb()
                }
            })
        gifThread!!.start()
    }

    private fun showImageWithInternalViewer(
        streamFactory: GenericFactory<SeekableInputStream, IOException>
    ) {
        Log.i(TAG, "Showing image using internal viewer")
        val imageTileSource: ImageTileSource
        try {
            try {
                streamFactory.create().use { `is` ->
                    imageTileSource = ImageTileSourceWholeBitmap(
                        BitmapFactory.decodeStream(`is`)
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception when creating ImageTileSource", t)
                General.quickToast(this, R.string.imageview_decode_failed)
                revertToWeb()
                return
            }
        } catch (e: OutOfMemoryError) {
            General.quickToast(this, R.string.imageview_oom)
            revertToWeb()
            return
        }
        AndroidCommon.UI_THREAD_HANDLER.post {
            if (mIsDestroyed) {
                return@post
            }
            mImageViewDisplayerManager = ImageViewDisplayListManager(imageTileSource, this)
			val surfaceViewLocal = RRGLSurfaceView(this, mImageViewDisplayerManager)
            surfaceView = surfaceViewLocal
            setMainView(surfaceViewLocal)
            if (mIsPaused) {
                surfaceViewLocal.onPause()
            } else {
                surfaceViewLocal.onResume()
            }
        }
    }

    companion object {
        private const val TAG = "ImageViewActivity"
    }
}
