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
package org.quantumbadger.redreader.reddit

import android.content.Context
import org.quantumbadger.redreader.common.PrefsUtility.pref_behaviour_nsfw
import org.quantumbadger.redreader.cache.CacheManager
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.reddit.things.SubredditCanonicalId
import org.quantumbadger.redreader.reddit.RedditAPI.FlairSelectorResponseHandler
import org.quantumbadger.redreader.http.PostField
import org.quantumbadger.redreader.reddit.RedditAPI
import org.quantumbadger.redreader.cache.CacheRequestJSONParser
import org.quantumbadger.redreader.jsonwrap.JsonValue
import org.quantumbadger.redreader.jsonwrap.JsonObject
import org.quantumbadger.redreader.reddit.RedditFlairChoice
import org.quantumbadger.redreader.jsonwrap.JsonArray
import org.quantumbadger.redreader.reddit.APIResponseHandler.APIFailureType
import org.quantumbadger.redreader.http.FailedRequestBody
import org.quantumbadger.redreader.reddit.APIResponseHandler
import org.quantumbadger.redreader.reddit.APIResponseHandler.SubmitResponseHandler
import org.quantumbadger.redreader.reddit.RedditAPI.SubmitJSONListener
import org.quantumbadger.redreader.reddit.APIResponseHandler.ActionResponseHandler
import org.quantumbadger.redreader.reddit.RedditAPI.GenericResponseHandler
import androidx.appcompat.app.AppCompatActivity
import org.quantumbadger.redreader.cache.CacheRequestCallbacks
import org.quantumbadger.redreader.common.datastream.SeekableInputStream
import org.quantumbadger.redreader.reddit.RedditAPI.RedditAction
import org.quantumbadger.redreader.reddit.RedditAPI.RedditSubredditAction
import org.quantumbadger.redreader.reddit.RedditSubredditManager
import org.quantumbadger.redreader.io.RequestResponseHandler
import org.quantumbadger.redreader.reddit.things.RedditSubreddit
import org.quantumbadger.redreader.reddit.api.SubredditRequestFailure
import org.quantumbadger.redreader.reddit.APIResponseHandler.UserResponseHandler
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategy
import org.quantumbadger.redreader.reddit.things.RedditThing
import org.quantumbadger.redreader.reddit.things.RedditUser
import org.quantumbadger.redreader.reddit.APIResponseHandler.ValueResponseHandler
import org.quantumbadger.redreader.reddit.RedditAPI.SubredditListResponse
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyIfTimestampOutsideBounds
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyAlways
import org.quantumbadger.redreader.cache.CacheRequest.RequestFailureType
import org.quantumbadger.redreader.jsonwrap.JsonString
import org.quantumbadger.redreader.http.body.HTTPRequestBodyPostFields
import androidx.annotation.IntDef
import org.quantumbadger.redreader.activities.BugReportActivity
import org.quantumbadger.redreader.cache.CacheRequest
import org.quantumbadger.redreader.common.*
import org.quantumbadger.redreader.common.Optional
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.net.URI
import java.util.*

object RedditAPI {
    const val ACTION_UPVOTE = 0
    const val ACTION_UNVOTE = 1
    const val ACTION_DOWNVOTE = 2
    const val ACTION_SAVE = 3
    const val ACTION_HIDE = 4
    const val ACTION_UNSAVE = 5
    const val ACTION_UNHIDE = 6
    const val ACTION_REPORT = 7
    const val ACTION_DELETE = 8
    const val SUBSCRIPTION_ACTION_SUBSCRIBE = 0
    const val SUBSCRIPTION_ACTION_UNSUBSCRIBE = 1
    @JvmStatic
	fun flairSelectorForNewLink(
        context: Context,
        cm: CacheManager,
        user: RedditAccount,
        subreddit: SubredditCanonicalId,
        responseHandler: FlairSelectorResponseHandler
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("is_newlink", "true"))
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("$subreddit/api/flairselector"),
                user,
                postFields,
                context,
                object : CacheRequestJSONParser.Listener {
                    override fun onJsonParsed(
                        result: JsonValue,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean
                    ) {
                        if (result.asObject() != null
                            && result.asObject()!!.isEmpty
                        ) {
                            responseHandler.onSuccess(emptyList())
                            return
                        }
                        if (result.asString() != null
                            && result.asString()!! == "{}"
                        ) {
                            responseHandler.onSuccess(emptyList())
                            return
                        }
                        val array = result.getArrayAtPath("choices")
                        if (array.isEmpty) {
                            val failureType = findFailureType(result)
                            if (failureType != null) {
                                responseHandler.onFailure(
                                    failureType,
                                    Optional.of(FailedRequestBody(result))
                                )
                            } else {
                                responseHandler.onFailure(
                                    APIFailureType.UNKNOWN,
                                    Optional.of(FailedRequestBody(result))
                                )
                            }
                            return
                        }
                        val choices = RedditFlairChoice.fromJsonList(array.get())
                        if (choices.isEmpty) {
                            responseHandler.onFailure(
                                CacheRequest.REQUEST_FAILURE_PARSE,
                                RuntimeException(),
                                null,
                                "Failed to parse choices list",
                                Optional.of(FailedRequestBody(result))
                            )
                            return
                        }
                        responseHandler.onSuccess(choices.get())
                    }

                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        httpStatus: Int?,
                        readableMessage: String?,
                        response: Optional<FailedRequestBody>
                    ) {
                        if (httpStatus != null && httpStatus == 404) {
                            responseHandler.onSubredditDoesNotExist()
                        } else if (httpStatus != null && httpStatus == 403) {
                            responseHandler.onSubredditPermissionDenied()
                        } else {
                            responseHandler.onFailure(
                                type,
                                t,
                                httpStatus,
                                readableMessage,
                                response
                            )
                        }
                    }
                })
        )
    }

    @JvmStatic
	fun submit(
        cm: CacheManager,
        responseHandler: SubmitResponseHandler,
        user: RedditAccount,
        isSelfPost: Boolean,
        subreddit: String?,
        title: String?,
        body: String?,
        sendRepliesToInbox: Boolean,
        markAsNsfw: Boolean,
        markAsSpoiler: Boolean,
        flairId: String?,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("api_type", "json"))
        postFields.add(PostField("kind", if (isSelfPost) "self" else "link"))
        postFields.add(
            PostField(
                "sendreplies",
                if (sendRepliesToInbox) "true" else "false"
            )
        )
        postFields.add(PostField("nsfw", if (markAsNsfw) "true" else "false"))
        postFields.add(PostField("spoiler", if (markAsSpoiler) "true" else "false"))
        postFields.add(PostField("sr", subreddit))
        postFields.add(PostField("title", title))
        if (flairId != null) {
            postFields.add(PostField("flair_id", flairId))
        }
        if (isSelfPost) {
            postFields.add(PostField("text", body))
        } else {
            postFields.add(PostField("url", body))
        }
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("/api/submit"),
                user,
                postFields,
                context,
                SubmitJSONListener(responseHandler)
            )
        )
    }

    @JvmStatic
	fun compose(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        recipient: String,
        subject: String,
        body: String,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("api_type", "json"))
        postFields.add(PostField("subject", subject))
        postFields.add(PostField("to", recipient))
        postFields.add(PostField("text", body))
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("/api/compose"),
                user,
                postFields,
                context,
                GenericResponseHandler(responseHandler)
            )
        )
    }

    @JvmStatic
	fun comment(
        cm: CacheManager,
        responseHandler: SubmitResponseHandler,
        inboxResponseHandler: ActionResponseHandler,
        user: RedditAccount,
        parentIdAndType: String?,
        markdown: String?,
        sendRepliesToInbox: Boolean,
        context: AppCompatActivity
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("api_type", "json"))
        postFields.add(PostField("thing_id", parentIdAndType))
        postFields.add(PostField("text", markdown))
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("/api/comment"),
                user,
                postFields,
                context,
                SubmitJSONListener(object : SubmitResponseHandler(context) {
                    override fun onSubmitErrors(errors: ArrayList<String>) {
                        responseHandler.onSubmitErrors(errors)
                    }

                    override fun onSuccess(
                        redirectUrl: Optional<String>,
                        thingId: Optional<String>
                    ) {
                        if (!sendRepliesToInbox) {
                            thingId.ifPresent { commentFullname: String? ->
                                sendReplies(
                                    cm,
                                    inboxResponseHandler,
                                    user,
                                    commentFullname,
                                    false,
                                    context
                                )
                            }
                        }
                        responseHandler.onSuccess(redirectUrl, thingId)
                    }

                    override fun onCallbackException(t: Throwable) {
                        responseHandler.onCallbackException(t)
                    }

                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        status: Int?,
                        readableMessage: String?,
                        response: Optional<FailedRequestBody>
                    ) {
                        responseHandler.onFailure(type, t, status, readableMessage, response)
                    }

                    override fun onFailure(
                        type: APIFailureType,
                        debuggingContext: String?,
                        response: Optional<FailedRequestBody>
                    ) {
                        responseHandler.onFailure(type, debuggingContext, response)
                    }
                })
            )
        )
    }

    @JvmStatic
	fun markAllAsRead(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        cm.makeRequest(
            createPostRequestUnprocessedResponse(
                Constants.Reddit.getUri("/api/read_all_messages"),
                user,
                postFields,
                context,
                object : CacheRequestCallbacks {
                    override fun onFailure(
                        type: Int,
                        t: Throwable?,
                        httpStatus: Int?,
                        readableMessage: String?,
                        body: Optional<FailedRequestBody>
                    ) {
                        responseHandler.notifyFailure(
                            type,
                            t,
                            httpStatus,
                            readableMessage,
                            body
                        )
                    }

                    override fun onDataStreamComplete(
                        stream: GenericFactory<SeekableInputStream, IOException>,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean,
                        mimetype: String?
                    ) {
                        responseHandler.notifySuccess()
                    }
                })
        )
    }

    @JvmStatic
	fun editComment(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        commentIdAndType: String?,
        markdown: String?,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("thing_id", commentIdAndType))
        postFields.add(PostField("text", markdown))
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("/api/editusertext"),
                user,
                postFields,
                context,
                GenericResponseHandler(responseHandler)
            )
        )
    }

    @JvmStatic
	fun action(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        idAndType: String?,
        @RedditAction action: Int,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("id", idAndType))
        val url = prepareActionUri(action, postFields)
        cm.makeRequest(
            createPostRequest(
                url,
                user,
                postFields,
                context,
                GenericResponseHandler(responseHandler)
            )
        )
    }

    private fun prepareActionUri(
        @RedditAction action: Int,
        postFields: LinkedList<PostField>
    ): URI {
        return when (action) {
            ACTION_DOWNVOTE -> {
                postFields.add(PostField("dir", "-1"))
                Constants.Reddit.getUri(Constants.Reddit.PATH_VOTE)
            }
            ACTION_UNVOTE -> {
                postFields.add(PostField("dir", "0"))
                Constants.Reddit.getUri(Constants.Reddit.PATH_VOTE)
            }
            ACTION_UPVOTE -> {
                postFields.add(PostField("dir", "1"))
                Constants.Reddit.getUri(Constants.Reddit.PATH_VOTE)
            }
            ACTION_SAVE -> Constants.Reddit.getUri(Constants.Reddit.PATH_SAVE)
            ACTION_HIDE -> Constants.Reddit.getUri(Constants.Reddit.PATH_HIDE)
            ACTION_UNSAVE -> Constants.Reddit.getUri(Constants.Reddit.PATH_UNSAVE)
            ACTION_UNHIDE -> Constants.Reddit.getUri(Constants.Reddit.PATH_UNHIDE)
            ACTION_REPORT -> Constants.Reddit.getUri(Constants.Reddit.PATH_REPORT)
            ACTION_DELETE -> Constants.Reddit.getUri(Constants.Reddit.PATH_DELETE)
            else -> throw RuntimeException("Unknown post/comment action")
        }
    }

    @JvmStatic
	fun subscriptionAction(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        subredditId: SubredditCanonicalId?,
        @RedditSubredditAction action: Int,
        context: Context
    ) {
        RedditSubredditManager.getInstance(context, user).getSubreddit(
			/* subredditCanonicalId = */ subredditId,
			/* timestampBound = */ TimestampBound.ANY,
			/* handler = */ object : RequestResponseHandler<RedditSubreddit, SubredditRequestFailure> {
                override fun onRequestFailed(failureReason: SubredditRequestFailure) {
                    responseHandler.notifyFailure(
                        failureReason.requestFailureType,
                        failureReason.t,
                        failureReason.statusLine,
                        failureReason.readableMessage,
                        Optional.empty()
                    )
                }

                override fun onRequestSuccess(
                    subreddit: RedditSubreddit,
                    timeCached: Long
                ) {
                    val postFields = LinkedList<PostField>()
                    postFields.add(PostField("sr", subreddit.name))
                    val url = subscriptionPrepareActionUri(action, postFields)
                    cm.makeRequest(
                        createPostRequest(
                            url,
                            user,
                            postFields,
                            context,
                            GenericResponseHandler(responseHandler)
                        )
                    )
                }
            },
			/* updatedVersionListener = */ null
        )
    }

    private fun subscriptionPrepareActionUri(
        @RedditSubredditAction action: Int,
        postFields: LinkedList<PostField>
    ): URI {
        return when (action) {
            SUBSCRIPTION_ACTION_SUBSCRIBE -> {
                postFields.add(PostField("action", "sub"))
                Constants.Reddit.getUri(Constants.Reddit.PATH_SUBSCRIBE)
            }
            SUBSCRIPTION_ACTION_UNSUBSCRIBE -> {
                postFields.add(PostField("action", "unsub"))
                Constants.Reddit.getUri(Constants.Reddit.PATH_SUBSCRIBE)
            }
            else -> throw RuntimeException("Unknown subreddit action")
        }
    }

    @JvmStatic
	fun getUser(
        cm: CacheManager,
        usernameToGet: String,
        responseHandler: UserResponseHandler,
        user: RedditAccount,
        downloadStrategy: DownloadStrategy,
        context: Context
    ) {
        val uri = Constants.Reddit.getUri("/user/$usernameToGet/about.json")
        cm.makeRequest(
            createGetRequest(
                uri,
                user,
                Priority(Constants.Priority.API_USER_ABOUT),
                Constants.FileType.USER_ABOUT,
                downloadStrategy,
                context,
                object : CacheRequestJSONParser.Listener {
                    override fun onJsonParsed(
                        result: JsonValue,
                        timestamp: Long,
                        session: UUID,
                        fromCache: Boolean
                    ) {
                        try {
                            val userThing = result.asObject(RedditThing::class.java)
                            val userResult = userThing!!.asUser()
                            responseHandler.notifySuccess(userResult, timestamp)
                        } catch (t: Throwable) {
                            // TODO look for error
                            responseHandler.notifyFailure(
                                CacheRequest.REQUEST_FAILURE_PARSE,
                                t,
                                null,
                                "JSON parse failed for unknown reason",
                                Optional.of(FailedRequestBody(result))
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
                        responseHandler.notifyFailure(
                            type,
                            t,
                            httpStatus,
                            readableMessage,
                            body
                        )
                    }
                })
        )
    }

    fun sendReplies(
        cm: CacheManager,
        responseHandler: ActionResponseHandler,
        user: RedditAccount,
        fullname: String?,
        state: Boolean,
        context: Context
    ) {
        val postFields = LinkedList<PostField>()
        postFields.add(PostField("id", fullname))
        postFields.add(PostField("state", state.toString()))
        cm.makeRequest(
            createPostRequest(
                Constants.Reddit.getUri("/api/sendreplies"),
                user,
                postFields,
                context,
                GenericResponseHandler(responseHandler)
            )
        )
    }

    fun popularSubreddits(
        cm: CacheManager,
        user: RedditAccount,
        context: Context,
        handler: ValueResponseHandler<SubredditListResponse?>,
        after: Optional<String?>
    ) {

        // 1 hour
        val maxCacheAgeMs = (60 * 60 * 1000).toLong()
        val builder = Constants.Reddit.getUriBuilder(
            Constants.Reddit.PATH_SUBREDDITS_POPULAR
        )
        builder.appendQueryParameter("limit", "100")
        after.apply { value: String? -> builder.appendQueryParameter("after", value) }
        val uri = General.uriFromString(builder.build().toString())!!
        requestSubredditList(
            cm,
            uri,
            user,
            context,
            handler,
            DownloadStrategyIfTimestampOutsideBounds(
                TimestampBound.notOlderThan(maxCacheAgeMs)
            )
        )
    }

    @JvmStatic
	fun searchSubreddits(
        cm: CacheManager,
        user: RedditAccount,
        queryString: String,
        context: Context,
        handler: ValueResponseHandler<SubredditListResponse?>,
        after: Optional<String?>
    ) {

        // 60 seconds
        val maxCacheAgeMs = (60 * 1000).toLong()
        val builder = Constants.Reddit.getUriBuilder(
            "/subreddits/search.json"
        )
        builder.appendQueryParameter("q", queryString)
        builder.appendQueryParameter("limit", "100")
        if (pref_behaviour_nsfw()) {
            builder.appendQueryParameter("include_over_18", "on")
        }
        after.apply { value: String? -> builder.appendQueryParameter("after", value) }
        val uri = General.uriFromString(builder.build().toString())!!
        requestSubredditList(
            cm,
            uri,
            user,
            context,
            handler,
            DownloadStrategyIfTimestampOutsideBounds(
                TimestampBound.notOlderThan(maxCacheAgeMs)
            )
        )
    }

    fun subscribedSubreddits(
        cm: CacheManager,
        user: RedditAccount,
        context: AppCompatActivity,
        handler: ValueResponseHandler<ArrayList<RedditSubreddit>>
    ) {
        subscribedSubredditsInternal(
            cm,
            user,
            context,
            handler,
            Optional.empty(),
            ArrayList(128)
        )
    }

    private fun subscribedSubredditsInternal(
        cm: CacheManager,
        user: RedditAccount,
        context: AppCompatActivity,
        handler: ValueResponseHandler<ArrayList<RedditSubreddit>>,
        after: Optional<String>,
        results: ArrayList<RedditSubreddit>
    ) {
        val builder = Constants.Reddit.getUriBuilder(
            Constants.Reddit.PATH_SUBREDDITS_MINE_SUBSCRIBER
        )
        after.apply { value: String? -> builder.appendQueryParameter("after", value) }
        val uri = General.uriFromString(builder.build().toString())!!
        requestSubredditList(
            cm,
            uri,
            user,
            context,
            object : ValueResponseHandler<SubredditListResponse?>(context) {
                override fun onSuccess(value: SubredditListResponse) {
                    results.addAll(value.subreddits)
                    if (value.after.isEmpty) {
                        handler.onSuccess(results)
                    } else {
                        subscribedSubredditsInternal(
                            cm,
                            user,
                            context,
                            handler,
                            value.after,
                            results
                        )
                    }
                }

                override fun onCallbackException(t: Throwable) {
                    handler.onCallbackException(t)
                }

                override fun onFailure(
                    type: Int,
                    t: Throwable?,
                    status: Int?,
                    readableMessage: String?,
                    response: Optional<FailedRequestBody>
                ) {
                    handler.onFailure(type, t, status, readableMessage, response)
                }

                override fun onFailure(
                    type: APIFailureType,
                    debuggingContext: String?,
                    response: Optional<FailedRequestBody>
                ) {
                    handler.onFailure(type, debuggingContext, response)
                }
            },
            DownloadStrategyAlways.INSTANCE
        )
    }

    fun requestSubredditList(
        cm: CacheManager,
        uri: URI,
        user: RedditAccount,
        context: Context,
        handler: ValueResponseHandler<SubredditListResponse?>,
        downloadStrategy: DownloadStrategy
    ) {
        cm.makeRequest(createGetRequest(
            uri,
            user,
            Priority(Constants.Priority.API_SUBREDDIT_LIST),
            Constants.FileType.SUBREDDIT_LIST,
            downloadStrategy,
            context,
            object : CacheRequestJSONParser.Listener {
                override fun onJsonParsed(
                    result: JsonValue,
                    timestamp: Long,
                    session: UUID,
                    fromCache: Boolean
                ) {
                    try {
                        val subreddits = result.getArrayAtPath("data", "children")
                        val after = result.getStringAtPath("data", "after")
                        if (subreddits.isEmpty) {
                            throw IOException("Subreddit data not found")
                        }
                        val output = ArrayList<RedditSubreddit>()
                        for (value in subreddits.get()) {
                            val redditThing = value.asObject(RedditThing::class.java)
                            val subreddit = redditThing!!.asSubreddit()
                            output.add(subreddit)
                        }
                        handler.notifySuccess(
                            SubredditListResponse(output, after)
                        )
                    } catch (e: Exception) {
                        onFailure(
                            CacheRequest.REQUEST_FAILURE_PARSE,
                            e,
                            null,
                            null,
                            Optional.of(FailedRequestBody(result))
                        )
                    }
                }

                override fun onFailure(
                    @RequestFailureType type: Int,
                    t: Throwable?,
                    httpStatus: Int?,
                    readableMessage: String?,
                    body: Optional<FailedRequestBody>
                ) {
                    handler.notifyFailure(type, t, httpStatus, readableMessage, body)
                }
            }
        ))
    }

    private fun findFailureType(response: JsonValue?): APIFailureType? {

        // TODO handle 403 forbidden
        if (response == null) {
            return null
        }
        var unknownError = false
        if (response.asObject() != null) {
            for ((key, value) in response.asObject()!!) {
                if ("success" == key && java.lang.Boolean.FALSE == value.asBoolean()) {
                    unknownError = true
                }
                val failureType = findFailureType(value)
                if (failureType == APIFailureType.UNKNOWN) {
                    unknownError = true
                } else if (failureType != null) {
                    return failureType
                }
            }
            val errors = response.getArrayAtPath("json", "errors")
            if (errors.isPresent && errors.get().size() > 0) {
                unknownError = true
            }
        } else if (response.asArray() != null) {
            for (v in response.asArray()!!) {
                val failureType = findFailureType(v)
                if (failureType == APIFailureType.UNKNOWN) {
                    unknownError = true
                } else if (failureType != null) {
                    return failureType
                }
            }
        } else if (response is JsonString) {
            val responseAsString: String = response.asString()
            if (Constants.Reddit.isApiErrorUser(responseAsString)) {
                return APIFailureType.INVALID_USER
            }
            if (Constants.Reddit.isApiErrorCaptcha(responseAsString)) {
                return APIFailureType.BAD_CAPTCHA
            }
            if (Constants.Reddit.isApiErrorNotAllowed(responseAsString)) {
                return APIFailureType.NOTALLOWED
            }
            if (Constants.Reddit.isApiErrorSubredditRequired(responseAsString)) {
                return APIFailureType.SUBREDDIT_REQUIRED
            }
            if (Constants.Reddit.isApiErrorURLRequired(responseAsString)) {
                return APIFailureType.URL_REQUIRED
            }
            if (Constants.Reddit.isApiTooFast(responseAsString)) {
                return APIFailureType.TOO_FAST
            }
            if (Constants.Reddit.isApiTooLong(responseAsString)) {
                return APIFailureType.TOO_LONG
            }
            if (Constants.Reddit.isApiAlreadySubmitted(responseAsString)) {
                return APIFailureType.ALREADY_SUBMITTED
            }
            if (Constants.Reddit.isPostFlairRequired(responseAsString)) {
                return APIFailureType.POST_FLAIR_REQUIRED
            }
            if (Constants.Reddit.isApiError(responseAsString)) {
                unknownError = true
            }
        }
        return if (unknownError) APIFailureType.UNKNOWN else null
    }

    private fun createPostRequest(
        url: URI,
        user: RedditAccount,
        postFields: List<PostField>,
        context: Context,
        handler: CacheRequestJSONParser.Listener
    ): CacheRequest {
        return createPostRequestUnprocessedResponse(
            url,
            user,
            postFields,
            context,
            CacheRequestJSONParser(context, handler)
        )
    }

    private fun createPostRequestUnprocessedResponse(
        url: URI,
        user: RedditAccount,
        postFields: List<PostField>,
        context: Context,
        callbacks: CacheRequestCallbacks
    ): CacheRequest {
        return CacheRequest(
            url,
            user,
            null,
            Priority(Constants.Priority.API_ACTION),
            DownloadStrategyAlways.INSTANCE,
            Constants.FileType.NOCACHE,
            CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
            HTTPRequestBodyPostFields(postFields),
            context,
            callbacks
        )
    }

    private fun createGetRequest(
        url: URI,
        user: RedditAccount,
        priority: Priority,
        fileType: Int,
        downloadStrategy: DownloadStrategy,
        context: Context,
        handler: CacheRequestJSONParser.Listener
    ): CacheRequest {
        return CacheRequest(
            url,
            user,
            null,
            priority,
            downloadStrategy,
            fileType,
            CacheRequest.DOWNLOAD_QUEUE_REDDIT_API,
            null,
            context,
            CacheRequestJSONParser(context, handler)
        )
    }

    @IntDef(
        ACTION_UPVOTE,
        ACTION_UNVOTE,
        ACTION_DOWNVOTE,
        ACTION_SAVE,
        ACTION_HIDE,
        ACTION_UNSAVE,
        ACTION_UNHIDE,
        ACTION_REPORT,
        ACTION_DELETE
    )
    @Retention(
        RetentionPolicy.SOURCE
    )
    annotation class RedditAction

    @IntDef(SUBSCRIPTION_ACTION_SUBSCRIBE, SUBSCRIPTION_ACTION_UNSUBSCRIBE)
    @Retention(
        RetentionPolicy.SOURCE
    )
    annotation class RedditSubredditAction
    private class GenericResponseHandler(
        private val mHandler: ActionResponseHandler
    ) : CacheRequestJSONParser.Listener {
        override fun onJsonParsed(
            result: JsonValue,
            timestamp: Long,
            session: UUID,
            fromCache: Boolean
        ) {
            try {
                val failureType = findFailureType(result)
                if (failureType != null) {
                    mHandler.notifyFailure(
                        failureType,
                        "GenericResponseHandler",
                        Optional.of(FailedRequestBody(result))
                    )
                } else {
                    mHandler.notifySuccess()
                }
            } catch (e: Exception) {
                BugReportActivity.handleGlobalError(
                    mHandler.context, RRError(
                        null,
                        null,
                        true,
                        e,
                        null,
                        null,
                        result.toString()
                    )
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
            mHandler.notifyFailure(
                type,
                t,
                httpStatus,
                readableMessage,
                body
            )
        }
    }

    interface FlairSelectorResponseHandler {
        fun onSuccess(choices: Collection<RedditFlairChoice>)
        fun onSubredditDoesNotExist()
        fun onSubredditPermissionDenied()
        fun onFailure(
            type: Int,
            t: Throwable?,
            httpStatus: Int?,
            readableMessage: String?,
            response: Optional<FailedRequestBody>
        )

        fun onFailure(
            type: APIFailureType,
            response: Optional<FailedRequestBody>
        )
    }

    private class SubmitJSONListener constructor(
        private val mResponseHandler: SubmitResponseHandler
    ) : CacheRequestJSONParser.Listener {
        override fun onJsonParsed(
            result: JsonValue,
            timestamp: Long,
            session: UUID,
            fromCache: Boolean
        ) {
            try {
                val errorsJson = result.getArrayAtPath("json", "errors")
                if (errorsJson.isPresent) {
                    val errors = ArrayList<String?>()
                    for (errorValue in errorsJson.get()) {
                        val error = errorValue.asArray()
                        if (error != null && error.getString(1) != null) {
                            errors.add(error.getString(1))
                        }
                    }
                    if (!errors.isEmpty()) {
                        mResponseHandler.onSubmitErrors(errors)
                        return
                    }
                }
                val failureType = findFailureType(result)
                if (failureType != null) {
                    mResponseHandler.notifyFailure(
                        failureType,
                        null,
                        Optional.of(FailedRequestBody(result))
                    )
                } else {
                    mResponseHandler.onSuccess(
                        result.getStringAtPath("json", "data", "things", 0, "data", "permalink")
                            .orElse(result.getStringAtPath("json", "data", "url")),
                        result.getStringAtPath("json", "data", "things", 0, "data", "name")
                    )
                }
            } catch (e: Exception) {
                BugReportActivity.handleGlobalError(
                    mResponseHandler.context,
                    RRError(
                        null,
                        null,
                        true,
                        e,
                        null,
                        null,
                        result.toString()
                    )
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
            mResponseHandler.notifyFailure(
                type,
                t,
                httpStatus,
                readableMessage,
                body
            )
        }
    }

    class SubredditListResponse(
        @JvmField val subreddits: ArrayList<RedditSubreddit>,
        val after: Optional<String>
    )
}
