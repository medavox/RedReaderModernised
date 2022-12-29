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

import android.Manifest
import android.content.*
import kotlin.Throws
import org.quantumbadger.redreader.common.PrefsUtility
import android.os.StatFs
import android.os.Build
import org.quantumbadger.redreader.activities.BaseActivity
import org.quantumbadger.redreader.common.FileUtils.DownloadImageToSaveSuccessCallback
import org.quantumbadger.redreader.cache.CacheManager.ReadableCacheFile
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import org.quantumbadger.redreader.fragments.ShareOrderDialog
import androidx.annotation.RequiresApi
import android.provider.MediaStore
import android.util.Log
import org.quantumbadger.redreader.common.DialogUtils
import org.quantumbadger.redreader.common.PrefsUtility.SaveLocation
import org.quantumbadger.redreader.common.FileUtils.CacheFileDataSource
import org.quantumbadger.redreader.common.FunctionOneArgWithReturn
import org.quantumbadger.redreader.image.LegacySaveImageCallback
import org.quantumbadger.redreader.activities.BugReportActivity
import org.quantumbadger.redreader.common.RRError
import org.quantumbadger.redreader.account.RedditAccountManager
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfNotCached
import org.quantumbadger.redreader.cache.CacheRequest.RequestFailureType
import org.quantumbadger.redreader.http.FailedRequestBody
import org.quantumbadger.redreader.cache.CacheManager.WritableCacheFile
import org.quantumbadger.redreader.common.MediaUtils
import org.quantumbadger.redreader.common.GenericFactory
import org.quantumbadger.redreader.common.FunctionOneArgNoReturn
import org.quantumbadger.redreader.image.GetImageInfoListener
import android.widget.Toast
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.cache.*
import org.quantumbadger.redreader.image.ImageInfo
import java.io.*
import java.lang.Exception
import java.lang.RuntimeException
import java.nio.file.Files
import java.util.*

object FileUtils {
    private const val TAG = "FileUtils"
    private val MIMETYPE_TO_EXTENSION: Map<String, String> = mapOf (
        "audio/3gpp2" to "3g2",
        "video/3gpp2" to "3g2",
        "audio/3gpp" to "3gp",
        "video/3gpp" to "3gp",
        "application/x-7z-compressed" to "7z",
        "audio/aac" to "aac",
        "application/x-abiword" to "abw",
        "application/x-freearc" to "arc",
        "video/x-msvideo" to "avi",
        "application/vnd.amazon.ebook" to "azw",
        "application/octet-stream" to "bin",
        "image/bmp" to "bmp",
        "application/x-bzip2" to "bz2",
        "application/x-bzip" to "bz",
        "application/x-csh" to "csh",
        "text/css" to "css",
        "text/csv" to "csv",
        "application/msword" to "doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.ms-fontobject" to "eot",
        "application/epub+zip" to "epub",
        "image/gif" to "gif",
        "application/gzip" to "gz",
        "video/h263" to "h263",
        "video/h264" to "h264",
        "video/h265" to "h265",
        "image/heic " to "heic",
        "image/heic-sequence " to "heic",
        "image/heif " to "heif",
        "image/heif-sequence" to "heif",
        "text/html" to "html",
        "image/vnd.microsoft.icon" to "ico",
        "text/calendar" to "ics",
        "application/java-archive" to "jar",
        "image/jpeg" to "jpg",
        "application/json" to "json",
        "application/ld+json" to "jsonld",
        "text/javascript" to "js",
        "audio/midi audio/x-midi" to "mid",
        "audio/mpeg" to "mp3",
        "video/mp4" to "mp4",
        "application/dash+xml" to "mpd",
        "video/mpeg" to "mpeg",
        "application/vnd.apple.installer+xml" to "mpkg",
        "video/mpv" to "mpv",
        "application/vnd.oasis.opendocument.presentation" to "odp",
        "application/vnd.oasis.opendocument.spreadsheet" to "ods",
        "application/vnd.oasis.opendocument.text" to "odt",
        "audio/ogg" to "oga",
        "video/ogg" to "ogv",
        "application/ogg" to "ogx",
        "audio/opus" to "opus",
        "font/otf" to "otf",
        "application/pdf" to "pdf",
        "application/x-httpd-php" to "php",
        "image/png" to "png",
        "application/vnd.ms-powerpoint" to "ppt",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        "application/vnd.rar" to "rar",
        "application/rtf" to "rtf",
        "application/x-sh" to "sh",
        "image/svg+xml" to "svg",
        "application/x-shockwave-flash" to "swf",
        "application/x-tar" to "tar",
        "image/tiff" to "tiff",
        "video/mp2t" to "ts",
        "font/ttf" to "ttf",
        "text/plain" to "txt",
        "application/vnd.visio" to "vsd",
        "audio/wav" to "wav",
        "audio/webm" to "weba",
        "video/webm" to "webm",
        "image/webp" to "webp",
        "font/woff2" to "woff2",
        "font/woff" to "woff",
        "application/xhtml+xml" to "xhtml",
        "application/vnd.ms-excel" to "xls",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/xml" to "xml",
        "text/xml" to "xml",
        "application/vnd.mozilla.xul+xml" to "xul",
        "application/zip" to "zip",
	)

    @JvmStatic
	fun getExtensionForMimetype(mimetype: String): Optional<String> {
		val splitType: String = if (mimetype.contains(";")) {
			mimetype.split(";").toTypedArray()[0]
		} else {
			mimetype
		}
        return Optional.ofNullable(
            MIMETYPE_TO_EXTENSION[StringUtils.asciiLowercase(
                splitType
            )]
        )
    }

    @JvmStatic
	@Throws(IOException::class)
    fun moveFile(src: File, dst: File?) {
        if (!src.renameTo(dst)) {
            copyFile(src, dst)
            if (!src.delete()) {
                src.deleteOnExit()
            }
        }
    }

    @Throws(IOException::class)
    fun copyFile(src: File?, dst: File?) {
        FileInputStream(src).use { fis -> copyFile(fis, dst) }
    }

    @JvmStatic
	@Throws(IOException::class)
    fun copyFile(fis: InputStream?, dst: File?) {
        FileOutputStream(dst).use { fos ->
            General.copyStream(fis, fos)
            fos.flush()
        }
    }

    @JvmStatic
	fun isCacheDiskFull(context: Context?): Boolean {
        val space = getFreeSpaceAvailable(PrefsUtility.pref_cache_location(context!!))
        return space < 128 * 1024 * 1024
    }

    /// Get the number of free bytes that are available on the external storage.
	@JvmStatic
	fun getFreeSpaceAvailable(path: String?): Long {
        val stat = StatFs(path)
        val availableBlocks: Long
        val blockSize: Long
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            availableBlocks = stat.availableBlocksLong
            blockSize = stat.blockSizeLong
        } else {
            availableBlocks = stat.availableBlocks.toLong()
            blockSize = stat.blockSize.toLong()
        }
        return availableBlocks * blockSize
    }

    @JvmStatic
	fun shareImageAtUri(
        activity: BaseActivity,
        uri: String?
    ) {
        if (uri == null) {
            return
        }
        downloadImageToSave(
            activity,
            uri,
			object : DownloadImageToSaveSuccessCallback {
				override fun onSuccess(
					info: ImageInfo,
					cacheFile: ReadableCacheFile,
					mimetype: String?
				) {
					val externalUri = CacheContentProvider.getUriForFile(
						cacheFile.id,
						mimetype!!,
						getExtensionFromPath(info.urlOriginal).orElse("jpg")
					)
					Log.i(TAG, "Sharing image with external uri: $externalUri")
					val shareIntent = Intent()
						.setAction(Intent.ACTION_SEND)
						.putExtra(Intent.EXTRA_STREAM, externalUri)
						.setType(mimetype)
						.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

					// Workaround for third party share apps
					shareIntent.clipData = ClipData.newRawUri(null, externalUri)
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

						// Due to bugs in the API before Android Lollipop, we have to
						// grant permission for every single app on the system to read
						// this file!
						for (resolveInfo in activity.packageManager.queryIntentActivities(
							shareIntent,
							PackageManager.MATCH_DEFAULT_ONLY
						)) {
							Log.i(
								TAG, "Legacy OS: granting permission to "
										+ resolveInfo.activityInfo.packageName
										+ " to read "
										+ externalUri
							)
							activity.grantUriPermission(
								resolveInfo.activityInfo.packageName,
								externalUri,
								Intent.FLAG_GRANT_WRITE_URI_PERMISSION
							)
						}
					}
					if (PrefsUtility.pref_behaviour_sharing_dialog()) {
						ShareOrderDialog.newInstance(shareIntent)
							.show(activity.supportFragmentManager, null)
					} else {
						activity.startActivity(
							Intent.createChooser(
								shareIntent,
								activity.getString(R.string.action_share)
							)
						)
					}
				}
			})
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreDownloadsInsertFile(
        activity: BaseActivity,
        name: String,
        mimetype: String?,
        fileSize: Long,
        source: FileDataSource,
        onSuccess: Runnable
    ) {
        val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        Log.i(TAG, "Got downloads URI: $downloads")
        val fileMetadata = ContentValues()
        fileMetadata.put(MediaStore.Downloads.DISPLAY_NAME, name)
        fileMetadata.put(MediaStore.Downloads.SIZE, fileSize)
        if (mimetype != null) {
            fileMetadata.put(MediaStore.Downloads.MIME_TYPE, mimetype)
        }
        fileMetadata.put(MediaStore.Downloads.IS_PENDING, true)
        val resolver = activity.contentResolver
        val fileUri = resolver.insert(downloads, fileMetadata)
        Log.i(TAG, "Got file URI: " + fileUri.toString())
        Thread(Runnable {
            try {
                resolver.openOutputStream(fileUri!!).use { os ->
                    source.writeTo(os!!)
                    os.flush()
                }
            } catch (e: IOException) {
                showUnexpectedStorageErrorDialog(
                    activity,
                    e,
                    fileUri.toString()
                )
                resolver.delete(fileUri!!, null, null)
                return@Runnable
            }
            fileMetadata.put(MediaStore.Downloads.IS_PENDING, false)
            resolver.update(fileUri, fileMetadata, null, null)
            onSuccess.run()
        }).start()
    }

    @RequiresApi(19)
    private fun createSAFDocumentWithIntent(
        activity: BaseActivity,
        filename: String,
        mimetype: String?,
        source: FileDataSource,
        onSuccess: Runnable
    ) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(mimetype)
            .putExtra(Intent.EXTRA_TITLE, filename)
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            activity.startActivityForResultWithCallback(
                intent
            ) { resultCode: Int, data: Intent? ->
                if (data == null || data.data == null) {
                    return@startActivityForResultWithCallback
                }
                Thread {
                    try {
                        activity.contentResolver
                            .openOutputStream(data.data!!).use { outputStream ->
                                source.writeTo(outputStream!!)
                                onSuccess.run()
                            }
                    } catch (e: IOException) {
                        showUnexpectedStorageErrorDialog(
                            activity,
                            e,
                            data.data.toString()
                        )
                    }
                }.start()
            }
        } catch (e: ActivityNotFoundException) {
            DialogUtils.showDialog(
                activity,
                R.string.error_no_file_manager_title,
                R.string.error_no_file_manager_message
            )
        }
    }

    @JvmStatic
	fun saveImageAtUri(
        activity: BaseActivity,
        uri: String?
    ) {
        if (uri == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val saveLocation = PrefsUtility.pref_behaviour_save_location()
            when (saveLocation) {
                SaveLocation.PROMPT_EVERY_TIME -> {
                    Log.i(TAG, "Android version Lollipop or higher, showing SAF prompt")
                    downloadImageToSave(
                        activity,
                        uri,
						object : DownloadImageToSaveSuccessCallback {
							override fun onSuccess(
								info: ImageInfo,
								cacheFile: ReadableCacheFile,
								mimetype: String?
							) {
								val filename = General.filenameFromString(info.urlOriginal)
								createSAFDocumentWithIntent(
									activity,
									filename,
									mimetype,
									CacheFileDataSource(cacheFile)
								) {
									General.quickToast(
										activity,
										R.string.action_save_image_success_no_path
									)
								}
							}
						})
                }
                SaveLocation.SYSTEM_DEFAULT -> {
                    Log.i(TAG, "Android version Lollipop or higher, saving to Downloads")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.i(TAG, "Android version Q or higher, saving with MediaStore")
                        downloadImageToSave(
                            activity,
                            uri,
							object : DownloadImageToSaveSuccessCallback {
								override fun onSuccess(
									info: ImageInfo,
									cacheFile: ReadableCacheFile,
									mimetype: String?
								) {
									val filename = General.filenameFromString(info.urlOriginal)
									mediaStoreDownloadsInsertFile(
										activity,
										filename,
										mimetype,
										cacheFile.file.map { obj: File -> obj.length() }.orElse(0L),
										CacheFileDataSource(cacheFile)
									) {
										General.quickToast(
											activity,
											R.string.action_save_image_success_no_path
										)
									}
								}
							})
                    } else {
                        Log.i(TAG, "Android version below Q, saving with legacy method")
                        activity.requestPermissionWithCallback(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            LegacySaveImageCallback(activity, uri)
                        )
                    }
                }
                else -> {
                    BugReportActivity.handleGlobalError(
                        activity, RuntimeException(
                            "Missing handler for preference value $saveLocation"
                        )
                    )
                }
            }
        } else {
            Log.i(TAG, "Android version before Lollipop, using legacy save method")
            activity.requestPermissionWithCallback(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                LegacySaveImageCallback(activity, uri)
            )
        }
    }

    private fun showUnexpectedStorageErrorDialog(
        activity: BaseActivity,
        throwable: Throwable,
        uri: String
    ) {
        General.showResultDialog(
            activity, RRError(
                activity.getString(R.string.error_unexpected_storage_title),
                activity.getString(R.string.error_unexpected_storage_message),
                true,
                throwable,
                null,
                uri,
                null
            )
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun internalDownloadImageToSaveAudio(
        activity: BaseActivity,
        info: ImageInfo,
        video: ReadableCacheFile,
        callback: DownloadImageToSaveSuccessCallback
    ) {
        val cacheManager = CacheManager.getInstance(activity)
        cacheManager.makeRequest(
            CacheRequest(
                General.uriFromString(info.urlAudioStream),
                RedditAccountManager.getAnon(),
                null,
                Priority(Constants.Priority.IMAGE_VIEW),
                DownloadStrategyIfNotCached.INSTANCE,
                Constants.FileType.IMAGE,
                CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
                activity,
                object : CacheRequestCallbacks {
                    override fun onDownloadNecessary() {}
                    override fun onFailure(
                        @RequestFailureType type: Int,
                        t: Throwable?,
                        status: Int?,
                        readableMessage: String?,
                        body: Optional<FailedRequestBody>
                    ) {
                        General.showResultDialog(
                            activity,
                            General.getGeneralErrorForFailure(
                                activity,
                                type,
                                t,
                                status,
                                info.urlAudioStream,
                                body
                            )
                        )
                    }

                    override fun onCacheFileWritten(
                        cacheFile: ReadableCacheFile,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean,
                        mimetype: String?
                    ) {
                        try {
                            val output = cacheManager.openNewCacheFile(
								General.uriFromString(
									"redreader://muxedmedia/"
											+ UUID.randomUUID()
											+ ".mp4"
								)!!
                                ,
                                RedditAccountManager.getAnon(),
                                Constants.FileType.IMAGE,
                                session,
                                "video/mp4",
                                CacheCompressionType.NONE
                            )
                            val file = output.writeExternally()
                            MediaUtils.muxFiles(
                                file, arrayOf(
                                    cacheFile.file.orThrow {
                                        RuntimeException(
                                            "Audio file not found"
                                        )
                                    },
                                    video.file.orThrow {
                                        RuntimeException(
                                            "Video file not found"
                                        )
                                    }
                                ),
                                {
                                    try {
                                        output.onWriteFinished()
                                        callback.onSuccess(
                                            info,
                                            output.readableCacheFile,
                                            "video/mp4"
                                        )
                                    } catch (e: Exception) {
                                        General.showResultDialog(
                                            activity,
                                            General.getGeneralErrorForFailure(
                                                activity,
                                                CacheRequest.REQUEST_FAILURE_STORAGE,
                                                e,
                                                null,
                                                info.urlOriginal,
                                                Optional.empty()
                                            )
                                        )
                                    }
                                }
                            ) { e: Exception? ->
                                General.showResultDialog(
                                    activity,
                                    RRError(
                                        activity.resources.getString(
                                            R.string.error_title_muxing_failed
                                        ),
                                        activity.resources.getString(
                                            R.string.error_message_muxing_failed
                                        ),
                                        true,
                                        e,
                                        null,
                                        info.urlOriginal,
                                        null
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            General.showResultDialog(
                                activity,
                                General.getGeneralErrorForFailure(
                                    activity,
                                    CacheRequest.REQUEST_FAILURE_STORAGE,
                                    e,
                                    null,
                                    info.urlOriginal,
                                    Optional.empty()
                                )
                            )
                        }
                    }
                })
        )
    }

    @JvmStatic
	fun downloadImageToSave(
        activity: BaseActivity,
        uri: String,
        callback: DownloadImageToSaveSuccessCallback
    ) {
        LinkHandler.getImageInfo(
            activity,
            uri,
            Priority(Constants.Priority.IMAGE_VIEW),
            object : GetImageInfoListener {
                override fun onFailure(
                    @RequestFailureType type: Int,
                    t: Throwable,
                    status: Int,
                    readableMessage: String,
                    body: Optional<FailedRequestBody>
                ) {
                    val error = General.getGeneralErrorForFailure(
                        activity,
                        type,
                        t,
                        status,
                        uri,
                        body
                    )
                    General.showResultDialog(activity, error)
                }

                override fun onSuccess(info: ImageInfo) {
                    CacheManager.getInstance(activity).makeRequest(
                        CacheRequest(
                            General.uriFromString(info.urlOriginal),
                            RedditAccountManager.getAnon(),
                            null,
                            Priority(Constants.Priority.IMAGE_VIEW),
                            DownloadStrategyIfNotCached.INSTANCE,
                            Constants.FileType.IMAGE,
                            CacheRequest.DOWNLOAD_QUEUE_IMMEDIATE,
                            activity,
                            object : CacheRequestCallbacks {
                                override fun onDownloadNecessary() {
                                    General.quickToast(
                                        activity,
                                        R.string.download_downloading,
                                        Toast.LENGTH_SHORT
                                    )
                                }

                                override fun onFailure(
                                    @RequestFailureType type: Int,
                                    t: Throwable?,
                                    status: Int?,
                                    readableMessage: String?,
                                    body: Optional<FailedRequestBody>
                                ) {
                                    General.showResultDialog(
                                        activity,
                                        General.getGeneralErrorForFailure(
                                            activity,
                                            type,
                                            t,
                                            status,
                                            info.urlOriginal,
                                            body
                                        )
                                    )
                                }

                                override fun onCacheFileWritten(
                                    cacheFile: ReadableCacheFile,
                                    timestamp: Long,
                                    session: UUID,
                                    fromCache: Boolean,
                                    mimetype: String?
                                ) {
                                    if (info.urlAudioStream != null
                                        && Build.VERSION.SDK_INT >= 18
                                    ) {
                                        Log.i(TAG, "Also downloading audio stream...")
                                        internalDownloadImageToSaveAudio(
                                            activity,
                                            info,
                                            cacheFile,
                                            callback
                                        )
                                    } else {
                                        callback.onSuccess(info, cacheFile, mimetype)
                                    }
                                }
                            })
                    )
                }

                override fun onNotAnImage() {
                    General.quickToast(activity, R.string.selected_link_is_not_image)
                }
            })
    }

    @JvmStatic
	fun getExtensionFromPath(path: String): Optional<String> {
        val pathSegments = path.split("/").toTypedArray()
        if (pathSegments.size == 0) {
            return Optional.empty()
        }
        val dotSegments = pathSegments[pathSegments.size - 1].split("\\.").toTypedArray()
        if (dotSegments.size < 2) {
            return Optional.empty()
        }
        return if (dotSegments.size == 2 && dotSegments[0].isEmpty()) {
            Optional.empty()
        } else Optional.of(
            dotSegments[dotSegments.size - 1]
        )
    }

    @JvmStatic
	fun buildPath(
        base: File,
        vararg components: String
    ): File {
        var result = base
        for (component in components) {
            result = File(result, component)
        }
        return result
    }

    private val sMkdirsLock = Any()
    @JvmStatic
	@Throws(IOException::class)
    fun mkdirs(file: File) {
        synchronized(sMkdirsLock) {
            if (file.isDirectory) {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.createDirectories(file.toPath())
                } catch (e: Exception) {
                    throw IOException(
                        "Failed to create dirs " + file.absolutePath,
                        e
                    )
                }
            } else {
                if (!file.mkdirs()) {
                    throw IOException("Failed to create dirs " + file.absolutePath)
                } else {

				}
			}
        }
    }

    private interface FileDataSource {
        @Throws(IOException::class)
        fun writeTo(outputStream: OutputStream)
    }

    private class CacheFileDataSource(
        private val mCacheFile: ReadableCacheFile
    ) : FileDataSource {
        @Throws(IOException::class)
        override fun writeTo(outputStream: OutputStream) {
            mCacheFile.inputStream.use { inputStream ->
                General.copyStream(inputStream, outputStream)
                outputStream.flush()
            }
        }
    }

    interface DownloadImageToSaveSuccessCallback {
        fun onSuccess(
            info: ImageInfo,
            cacheFile: ReadableCacheFile,
            mimetype: String?
        )
    }
}
