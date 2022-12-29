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

import androidx.annotation.StringRes
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.os.Build
import android.view.MenuItem
import org.quantumbadger.redreader.R
import org.quantumbadger.redreader.reddit.PostSort
import org.quantumbadger.redreader.reddit.PostCommentSort
import org.quantumbadger.redreader.reddit.UserCommentSort
import kotlin.jvm.JvmOverloads
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost
import org.quantumbadger.redreader.adapters.MainMenuListingManager.SubredditAction
import org.quantumbadger.redreader.adapters.MainMenuListingManager
import org.quantumbadger.redreader.fragments.MainMenuFragment.MainMenuUserItems
import org.quantumbadger.redreader.fragments.MainMenuFragment
import org.quantumbadger.redreader.fragments.MainMenuFragment.MainMenuShortcutItems
import org.quantumbadger.redreader.activities.OptionsMenuUtility.AppbarItemsPref
import org.quantumbadger.redreader.common.PrefsUtility.AppbarItemInfo
import org.quantumbadger.redreader.activities.OptionsMenuUtility
import org.quantumbadger.redreader.reddit.api.RedditAPICommentAction.RedditCommentAction
import org.quantumbadger.redreader.reddit.api.RedditAPICommentAction
import org.quantumbadger.redreader.reddit.things.SubredditCanonicalId
import org.quantumbadger.redreader.io.WritableHashSet
import org.quantumbadger.redreader.reddit.things.InvalidSubredditNameException
import org.quantumbadger.redreader.common.PrefsUtility.BehaviourCollapseStickyComments
import java.lang.IllegalArgumentException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object PrefsUtility {
    private var sharedPrefs: SharedPrefsWrapper? = null
    private var mRes: Resources? = null
    fun init(context: Context) {
        sharedPrefs = General.getSharedPrefs(context)
        mRes = Objects.requireNonNull(context.resources)
    }

    private fun getPrefKey(@StringRes prefKey: Int): String {
        return mRes!!.getString(prefKey)
    }

    private val mDefaultLocale = AtomicReference<Locale>()
    @JvmStatic
	fun getString(
        id: Int,
        defaultValue: String?
    ): String? {
        return sharedPrefs!!.getString(getPrefKey(id), defaultValue)
    }

    @JvmStatic
	fun getStringSet(
        id: Int,
        defaultArrayRes: Int
    ): Set<String>? {
        return sharedPrefs!!.getStringSet(
            getPrefKey(id),
            General.hashsetFromArray(*mRes!!.getStringArray(defaultArrayRes))
        )
    }

    private fun getBoolean(
        id: Int,
        defaultValue: Boolean
    ): Boolean {
        return sharedPrefs!!.getBoolean(getPrefKey(id), defaultValue)
    }

    private fun getLong(
        id: Int,
        defaultValue: Long
    ): Long {
        return sharedPrefs!!.getLong(getPrefKey(id), defaultValue)
    }

    @JvmStatic
	fun isReLayoutRequired(context: Context, key: String): Boolean {
        return context.getString(R.string.pref_appearance_theme_key) == key || (context.getString(R.string.pref_menus_mainmenu_useritems_key)
                == key) || (context.getString(R.string.pref_menus_mainmenu_shortcutitems_key)
                == key)
    }

    @JvmStatic
	fun isRefreshRequired(context: Context, key: String): Boolean {
        return (key.startsWith("pref_appearance")
                || key == context.getString(R.string.pref_behaviour_fling_post_left_key) || key == context.getString(
            R.string.pref_behaviour_fling_post_right_key
        ) || key == context.getString(R.string.pref_behaviour_nsfw_key) || key == context.getString(
            R.string.pref_behaviour_postcount_key
        ) || key == context.getString(R.string.pref_behaviour_comment_min_key) || key == context.getString(
            R.string.pref_behaviour_pinned_subredditsort_key
        ) || key == context.getString(
            R.string.pref_behaviour_blocked_subredditsort_key
        ) || key == context.getString(
            R.string.pref_appearance_hide_headertoolbar_commentlist_key
        ) || key == context.getString(
            R.string.pref_appearance_hide_headertoolbar_postlist_key
        ) || key == context.getString(R.string.pref_images_thumbnail_size_key) || key == context.getString(
            R.string.pref_images_inline_image_previews_key
        ) || key == context.getString(
            R.string.pref_images_inline_image_previews_nsfw_key
        ) || key == context.getString(
            R.string.pref_images_inline_image_previews_spoiler_key
        ) || key == context.getString(R.string.pref_images_high_res_thumbnails_key) || key == context.getString(
            R.string.pref_accessibility_separate_body_text_lines_key
        ) || key == context.getString(
            R.string.pref_accessibility_min_comment_height_key
        ) || key == context.getString(
            R.string.pref_behaviour_post_title_opens_comments_key
        ) || key == context.getString(
            R.string.pref_accessibility_say_comment_indent_level_key
        ) || key == context.getString(
            R.string.pref_behaviour_collapse_sticky_comments_key
        ) || key == context.getString(
            R.string.pref_accessibility_concise_mode_key
        ) || key == context.getString(
            R.string.pref_appearance_post_hide_subreddit_header_key
        ))
    }

    @JvmStatic
	fun isRestartRequired(context: Context, key: String): Boolean {
        return context.getString(R.string.pref_appearance_twopane_key) == key || context.getString(R.string.pref_appearance_theme_key) == key || context.getString(
            R.string.pref_appearance_navbar_color_key
        ) == key || context.getString(R.string.pref_appearance_langforce_key) == key || (context.getString(
            R.string.pref_behaviour_bezel_toolbar_swipezone_key
        )
                == key) || (context.getString(R.string.pref_appearance_hide_username_main_menu_key)
                == key) || context.getString(R.string.pref_appearance_android_status_key) == key || (context.getString(
            R.string.pref_appearance_comments_show_floating_toolbar_key
        )
                == key) || context.getString(R.string.pref_behaviour_enable_swipe_refresh_key) == key || context.getString(
            R.string.pref_menus_show_multireddit_main_menu_key
        ) == key || (context.getString(R.string.pref_menus_show_subscribed_subreddits_main_menu_key)
                == key) || context.getString(R.string.pref_menus_mainmenu_dev_announcements_key) == key || context.getString(
            R.string.pref_appearance_bottom_toolbar_key
        ) == key || (context.getString(R.string.pref_appearance_hide_toolbar_on_scroll_key)
                == key) || context.getString(R.string.pref_behaviour_block_screenshots_key) == key || context.getString(
            R.string.pref_behaviour_keep_screen_awake_key
        ) == key
    }

    @JvmStatic
	fun appearance_twopane(): AppearanceTwopane {
        return AppearanceTwopane.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_twopane_key,
                    "auto"
                )!!
            )
        )
    }

    @JvmStatic val isNightMode: Boolean
        get() {
            val theme = appearance_theme()
            return theme == AppearanceTheme.NIGHT || theme == AppearanceTheme.NIGHT_LOWCONTRAST || theme == AppearanceTheme.ULTRABLACK
        }

    @JvmStatic
	fun appearance_theme(): AppearanceTheme {
        return AppearanceTheme.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_theme_key,
                    "red"
                )!!
            )
        )
    }

    @JvmStatic
	fun appearance_navbar_colour(): AppearanceNavbarColour {
        return AppearanceNavbarColour.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_navbar_color_key,
                    "black"
                )!!
            )
        )
    }

    @JvmStatic
	fun applyTheme(activity: Activity) {
        val theme = appearance_theme()
        when (theme) {
            AppearanceTheme.RED -> activity.setTheme(R.style.RR_Light_Red)
            AppearanceTheme.GREEN -> activity.setTheme(R.style.RR_Light_Green)
            AppearanceTheme.BLUE -> activity.setTheme(R.style.RR_Light_Blue)
            AppearanceTheme.LTBLUE -> activity.setTheme(R.style.RR_Light_LtBlue)
            AppearanceTheme.ORANGE -> activity.setTheme(R.style.RR_Light_Orange)
            AppearanceTheme.GRAY -> activity.setTheme(R.style.RR_Light_Gray)
            AppearanceTheme.NIGHT -> activity.setTheme(R.style.RR_Dark)
            AppearanceTheme.NIGHT_LOWCONTRAST -> activity.setTheme(R.style.RR_Dark_LowContrast)
            AppearanceTheme.ULTRABLACK -> activity.setTheme(R.style.RR_Dark_UltraBlack)
        }
        applyLanguage(activity)
    }

    @JvmStatic
	fun applySettingsTheme(activity: Activity) {
        activity.setTheme(R.style.RR_Settings)
        applyLanguage(activity)
    }

    private fun applyLanguage(activity: Activity) {
        synchronized(mDefaultLocale) {
            if (mDefaultLocale.get() == null) {
                mDefaultLocale.set(Locale.getDefault())
            }
        }
        val lang = getString(
            R.string.pref_appearance_langforce_key,
            "auto"
        )
        for (res in arrayOf(
            activity.resources,
            activity.application.resources
        )) {
            val dm = res.displayMetrics
            val conf = res.configuration
            if (lang != "auto") {
                if (lang!!.contains("-r")) {
                    val split = lang.split("-r").toTypedArray()
                    setLocaleOnConfiguration(conf, Locale(split[0], split[1]))
                } else {
                    setLocaleOnConfiguration(conf, Locale(lang))
                }
            } else {
                setLocaleOnConfiguration(conf, mDefaultLocale.get())
            }
            res.updateConfiguration(conf, dm)
        }
    }

    private fun setLocaleOnConfiguration(
        conf: Configuration,
        locale: Locale
    ) {
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= 17) {
            conf.setLocale(locale)
        } else {
            conf.locale = locale
        }
    }

    @JvmStatic
	fun appearance_thumbnails_show(): NeverAlwaysOrWifiOnly {
        return NeverAlwaysOrWifiOnly.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_thumbnails_show_list_key,
                    "always"
                )!!
            )
        )
    }

    @JvmStatic
	fun appearance_thumbnails_show_old(): NeverAlwaysOrWifiOnly {
        return if (!getBoolean(
                R.string.pref_appearance_thumbnails_show_key,
                true
            )
        ) {
            NeverAlwaysOrWifiOnly.NEVER
        } else if (getBoolean(
                R.string.pref_appearance_thumbnails_wifionly_key,
                false
            )
        ) {
            NeverAlwaysOrWifiOnly.WIFIONLY
        } else {
            NeverAlwaysOrWifiOnly.ALWAYS
        }
    }

    @JvmStatic
	fun appearance_thumbnails_nsfw_show(): Boolean {
        return getBoolean(
            R.string.pref_appearance_thumbnails_nsfw_show_key,
            false
        )
    }

    @JvmStatic
	fun appearance_thumbnails_spoiler_show(): Boolean {
        return getBoolean(
            R.string.pref_appearance_thumbnails_spoiler_show_key,
            false
        )
    }

    fun appearance_fontscale_global(): Float {
        return getString(
            R.string.pref_appearance_fontscale_global_key,
            "1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_bodytext(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_bodytext_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_bodytext_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_comment_headers(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_comment_headers_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_comment_headers_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_linkbuttons(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_linkbuttons_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_linkbuttons_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_posts(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_posts_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_posts_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_post_subtitles(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_post_subtitles_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_post_subtitles_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_post_header_titles(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_post_header_titles_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_post_header_titles_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun appearance_fontscale_post_header_subtitles(): Float {
        return if (getString(
                R.string.pref_appearance_fontscale_post_header_subtitles_key,
                "-1"
            ) == "-1"
        ) {
            appearance_fontscale_global()
        } else getString(
            R.string.pref_appearance_fontscale_post_header_subtitles_key,
            "-1"
        )!!.toFloat()
    }

    @JvmStatic
	fun pref_appearance_hide_username_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_appearance_hide_username_main_menu_key,
            false
        )
    }

    @JvmStatic
	fun pref_show_popular_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_menus_show_popular_main_menu_key,
            false
        )
    }

    @JvmStatic
	fun pref_show_random_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_menus_show_random_main_menu_key,
            false
        )
    }

    @JvmStatic
	fun pref_show_multireddit_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_menus_show_multireddit_main_menu_key,
            true
        )
    }

    @JvmStatic
	fun pref_show_subscribed_subreddits_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_menus_show_subscribed_subreddits_main_menu_key,
            true
        )
    }

    @JvmStatic
	fun pref_menus_mainmenu_dev_announcements(): Boolean {
        return getBoolean(
            R.string.pref_menus_mainmenu_dev_announcements_key,
            true
        )
    }

    @JvmStatic
	fun pref_appearance_show_blocked_subreddits_main_menu(): Boolean {
        return getBoolean(
            R.string.pref_appearance_show_blocked_subreddits_main_menu_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_linkbuttons(): Boolean {
        return getBoolean(
            R.string.pref_appearance_linkbuttons_key,
            true
        )
    }

    @JvmStatic
	fun pref_appearance_android_status(): AppearanceStatusBarMode {
        return AppearanceStatusBarMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_android_status_key,
                    "never_hide"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_appearance_link_text_clickable(): Boolean {
        return getBoolean(
            R.string.pref_appearance_link_text_clickable_key,
            true
        )
    }

    fun pref_appearance_image_viewer_show_floating_toolbar(): Boolean {
        return getBoolean(
            R.string.pref_appearance_image_viewer_show_floating_toolbar_key,
            true
        )
    }

    fun pref_appearance_show_aspect_ratio_indicator(): Boolean {
        return getBoolean(
            R.string.pref_appearance_show_aspect_ratio_indicator_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_comments_show_floating_toolbar(): Boolean {
        return getBoolean(
            R.string.pref_appearance_comments_show_floating_toolbar_key,
            true
        )
    }

    @JvmStatic
	fun pref_appearance_indentlines(): Boolean {
        return getBoolean(
            R.string.pref_appearance_indentlines_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_left_handed(): Boolean {
        return getBoolean(
            R.string.pref_appearance_left_handed_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_bottom_toolbar(): Boolean {
        return getBoolean(
            R.string.pref_appearance_bottom_toolbar_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_hide_toolbar_on_scroll(): Boolean {
        return getBoolean(
            R.string.pref_appearance_hide_toolbar_on_scroll_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_post_hide_subreddit_header(): Boolean {
        return getBoolean(
            R.string.pref_appearance_post_hide_subreddit_header_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_hide_headertoolbar_postlist(): Boolean {
        return getBoolean(
            R.string.pref_appearance_hide_headertoolbar_postlist_key,
            false
        )
    }

    @JvmStatic
	fun pref_appearance_hide_headertoolbar_commentlist(): Boolean {
        return getBoolean(
            R.string.pref_appearance_hide_headertoolbar_commentlist_key,
            false
        )
    }

    fun appearance_post_subtitle_items(): EnumSet<AppearancePostSubtitleItem> {
        val strings = getStringSet(
            R.string.pref_appearance_post_subtitle_items_key,
            R.array.pref_appearance_post_subtitle_items_default
        )
        val result = EnumSet.noneOf(
            AppearancePostSubtitleItem::class.java
        )
        for (s in strings!!) {
            result.add(
                AppearancePostSubtitleItem.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    fun appearance_post_age_units(): Int {
        return try {
            getString(
                R.string.pref_appearance_post_age_units_key,
                "2"
            )!!.toInt()
        } catch (e: Throwable) {
            2
        }
    }

    fun appearance_post_subtitle_items_use_different_settings(): Boolean {
        return getBoolean(
            R.string.pref_appearance_post_subtitle_items_use_different_settings_key,
            false
        )
    }

    fun appearance_post_header_subtitle_items(): EnumSet<AppearancePostSubtitleItem> {
        val strings = getStringSet(
            R.string.pref_appearance_post_header_subtitle_items_key,
            R.array.pref_appearance_post_subtitle_items_default
        )
        val result = EnumSet.noneOf(
            AppearancePostSubtitleItem::class.java
        )
        for (s in strings!!) {
            result.add(
                AppearancePostSubtitleItem.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    fun appearance_post_header_age_units(): Int {
        return try {
            getString(
                R.string.pref_appearance_post_header_age_units_key,
                "2"
            )!!.toInt()
        } catch (e: Throwable) {
            2
        }
    }

    @JvmStatic
	fun appearance_post_show_comments_button(): Boolean {
        return getBoolean(
            R.string.pref_appearance_post_show_comments_button_key,
            true
        )
    }

    @JvmStatic
	fun appearance_comment_header_items(): EnumSet<AppearanceCommentHeaderItem> {
        val strings = getStringSet(
            R.string.pref_appearance_comment_header_items_key,
            R.array.pref_appearance_comment_header_items_default
        )
        val result = EnumSet.noneOf(
            AppearanceCommentHeaderItem::class.java
        )
        for (s in strings!!) {
            if (s.equals("ups_downs", ignoreCase = true)) {
                continue
            }
            try {
                result.add(AppearanceCommentHeaderItem.valueOf(StringUtils.asciiUppercase(s)))
            } catch (e: IllegalArgumentException) {
                // Ignore -- this option no longer exists
            }
        }
        return result
    }

    @JvmStatic
	fun appearance_comment_age_units(): Int {
        return try {
            getString(
                R.string.pref_appearance_comment_age_units_key,
                "2"
            )!!.toInt()
        } catch (e: Throwable) {
            2
        }
    }

    @JvmStatic
	fun appearance_comment_age_mode(): CommentAgeMode {
        return CommentAgeMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_appearance_comment_age_mode_key,
                    "absolute"
                )!!
            )
        )
    }

    @JvmStatic
	fun appearance_inbox_age_units(): Int {
        return try {
            getString(
                R.string.pref_appearance_inbox_age_units_key,
                "2"
            )!!.toInt()
        } catch (e: Throwable) {
            2
        }
    }

    @JvmStatic
	fun images_thumbnail_size_dp(): Int {
        return try {
            getString(
                R.string.pref_images_thumbnail_size_key,
                "64"
            )!!.toInt()
        } catch (e: Throwable) {
            64
        }
    }

    @JvmStatic
	fun images_inline_image_previews(): NeverAlwaysOrWifiOnly {
        return NeverAlwaysOrWifiOnly.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_images_inline_image_previews_key,
                    "always"
                )!!
            )
        )
    }

    @JvmStatic
	fun images_inline_image_previews_nsfw(): Boolean {
        return getBoolean(
            R.string.pref_images_inline_image_previews_nsfw_key,
            false
        )
    }

    @JvmStatic
	fun images_inline_image_previews_spoiler(): Boolean {
        return getBoolean(
            R.string.pref_images_inline_image_previews_spoiler_key,
            false
        )
    }

    @JvmStatic
	fun images_high_res_thumbnails(): NeverAlwaysOrWifiOnly {
        return NeverAlwaysOrWifiOnly.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_images_high_res_thumbnails_key,
                    "wifionly"
                )!!
            )
        )
    }

    ///////////////////////////////
    // pref_behaviour
    ///////////////////////////////
	@JvmStatic
	fun pref_behaviour_skiptofrontpage(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_skiptofrontpage_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_useinternalbrowser(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_useinternalbrowser_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_usecustomtabs(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_usecustomtabs_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_notifications(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_notifications_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_enable_swipe_refresh(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_enable_swipe_refresh_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_video_playback_controls(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_video_playback_controls_key,
            false
        )
    }

    fun pref_behaviour_video_mute_default(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_video_mute_default_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_video_zoom_default(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_video_zoom_default_key,
            false
        )
    }

    fun pref_videos_download_before_playing(): Boolean {
        return getBoolean(
            R.string.pref_videos_download_before_playing_key,
            false
        )
    }

    fun pref_behaviour_imagevideo_tap_close(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_imagevideo_tap_close_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_bezel_toolbar_swipezone_dp(): Int {
        return try {
            getString(
                R.string.pref_behaviour_bezel_toolbar_swipezone_key,
                "10"
            )!!.toInt()
        } catch (e: Throwable) {
            10
        }
    }

    @JvmStatic
	fun pref_behaviour_back_again(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_postlist_back_again_key,
            false
        )
    }

    fun pref_behaviour_gallery_swipe_length_dp(): Int {
        return try {
            getString(
                R.string.pref_behaviour_gallery_swipe_length_key,
                "150"
            )!!.toInt()
        } catch (e: Throwable) {
            150
        }
    }

    @JvmStatic
	fun pref_behaviour_comment_min(): Int? {
        val defaultValue = -4
        val value = getString(
            R.string.pref_behaviour_comment_min_key,
            defaultValue.toString()
        )
        return if (value == null || value.trim { it <= ' ' }.isEmpty()) {
            null
        } else try {
            value.toInt()
        } catch (e: Throwable) {
            defaultValue
        }
    }

    @JvmStatic
	fun pref_behaviour_post_title_opens_comments(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_post_title_opens_comments_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_imageview_mode(): ImageViewMode {
        return ImageViewMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_imageview_mode_key,
                    "internal_opengl"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_albumview_mode(): AlbumViewMode {
        return AlbumViewMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_albumview_mode_key,
                    "internal_list"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_gifview_mode(): GifViewMode {
        return GifViewMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_gifview_mode_key,
                    "internal_movie"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_videoview_mode(): VideoViewMode {
        return VideoViewMode.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_videoview_mode_key,
                    "internal_videoview"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_fling_post_left(): PostFlingAction {
        return PostFlingAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_fling_post_left_key,
                    "downvote"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_fling_post_right(): PostFlingAction {
        return PostFlingAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_fling_post_right_key,
                    "upvote"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_self_post_tap_actions(): SelfpostAction {
        return SelfpostAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_self_post_tap_actions_key,
                    "collapse"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_fling_comment_left(): CommentFlingAction {
        return CommentFlingAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_fling_comment_left_key,
                    "downvote"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_fling_comment_right(): CommentFlingAction {
        return CommentFlingAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_fling_comment_right_key,
                    "upvote"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_actions_comment_tap(): CommentAction {
        return CommentAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_actions_comment_tap_key,
                    "collapse"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_actions_comment_longclick(): CommentAction {
        return CommentAction.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_actions_comment_longclick_key,
                    "action_menu"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_sharing_share_text(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_sharing_share_text_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_sharing_include_desc(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_sharing_include_desc_key,
            true
        )
    }

    @JvmStatic
	fun pref_behaviour_sharing_dialog(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_sharing_share_dialog_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_sharing_dialog_data_get(): String? {
        return getString(
            R.string.pref_behaviour_sharing_share_dialog_data,
            ""
        )
    }

    @JvmStatic
	fun pref_behaviour_sharing_dialog_data_set(
        context: Context,
        appNames: String?
    ) {
        sharedPrefs!!.edit()
            .putString(
                context.getString(R.string.pref_behaviour_sharing_share_dialog_data),
                appNames
            )
            .apply()
    }

    @JvmStatic
	fun pref_behaviour_postsort(): PostSort {
        return PostSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_postsort_key,
                    "hot"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_user_postsort(): PostSort {
        return PostSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_user_postsort_key,
                    "new"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_multi_postsort(): PostSort {
        return PostSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_multi_postsort_key,
                    "hot"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_commentsort(): PostCommentSort {
        return PostCommentSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_commentsort_key,
                    "best"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_user_commentsort(): UserCommentSort {
        return UserCommentSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_user_commentsort_key,
                    "new"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_pinned_subredditsort(): PinnedSubredditSort {
        return PinnedSubredditSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_pinned_subredditsort_key,
                    "name"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_blocked_subredditsort(): BlockedSubredditSort {
        return BlockedSubredditSort.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_blocked_subredditsort_key,
                    "name"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_behaviour_nsfw(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_nsfw_key,
            false
        )
    }

    //Show Visited Posts? True hides them.
    // See strings.xml, prefs_behaviour.xml, PostListingFragment.java
	@JvmStatic
	fun pref_behaviour_hide_read_posts(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_hide_read_posts_key,
            false
        )
    }

    fun pref_behaviour_share_permalink(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_share_permalink_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_post_count(): PostCount {
        return PostCount.valueOf(
            getString(
                R.string.pref_behaviour_postcount_key,
                "ALL"
            )!!
        )
    }

    @JvmStatic
	fun pref_behaviour_screen_orientation(): ScreenOrientation {
        return ScreenOrientation.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_screenorientation_key,
                    StringUtils.asciiLowercase(ScreenOrientation.AUTO.name)
                )!!
            )
        )
    }

    fun pref_behaviour_save_location(): SaveLocation {
        return SaveLocation.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_save_location_key,
                    StringUtils.asciiLowercase(SaveLocation.PROMPT_EVERY_TIME.name)
                )!!
            )
        )
    }

    @JvmStatic
	fun behaviour_block_screenshots(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_block_screenshots_key,
            false
        )
    }

    ///////////////////////////////
    // pref_cache
    ///////////////////////////////
    // pref_cache_location
    @JvmStatic
	fun pref_cache_location(
        context: Context
    ): String? {
        var defaultCacheDir = context.externalCacheDir
        if (defaultCacheDir == null) {
            defaultCacheDir = context.cacheDir
        }
        return getString(
            R.string.pref_cache_location_key,
            defaultCacheDir!!.absolutePath
        )
    }

    @JvmStatic
	fun pref_cache_location(
        context: Context,
        path: String?
    ) {
        sharedPrefs!!.edit()
            .putString(context.getString(R.string.pref_cache_location_key), path)
            .apply()
    }

    @JvmStatic
	fun pref_cache_rerequest_postlist_age_ms(): Long {
        return try {
            val hours =
                getString(
                    R.string.pref_cache_rerequest_postlist_age_key,
                    "1"
                )!!.toInt()
            General.hoursToMs(hours.toLong())
        } catch (e: Throwable) {
            1
        }
    }

    // pref_cache_maxage
    @JvmStatic
	@JvmOverloads
    fun createFileTypeToLongMap(
        listings: Long = 0,
        thumbnails: Long = 0,
        images: Long = 0
    ): HashMap<Int, Long> {
        val maxAgeMap = HashMap<Int, Long>(10)
        maxAgeMap[Constants.FileType.POST_LIST] = listings
        maxAgeMap[Constants.FileType.COMMENT_LIST] = listings
        maxAgeMap[Constants.FileType.SUBREDDIT_LIST] = listings
        maxAgeMap[Constants.FileType.SUBREDDIT_ABOUT] = listings
        maxAgeMap[Constants.FileType.USER_ABOUT] = listings
        maxAgeMap[Constants.FileType.INBOX_LIST] = listings
        maxAgeMap[Constants.FileType.THUMBNAIL] = thumbnails
        maxAgeMap[Constants.FileType.IMAGE] = images
        maxAgeMap[Constants.FileType.IMAGE_INFO] = images
        maxAgeMap[Constants.FileType.CAPTCHA] = images
        maxAgeMap[Constants.FileType.INLINE_IMAGE_PREVIEW] = images
        return maxAgeMap
    }

    @JvmStatic
	fun pref_cache_maxage(): HashMap<Int, Long> {
        val maxAgeListing = (1000L
                * 60L
                * 60L
                * getString(
            R.string.pref_cache_maxage_listing_key,
            "168"
        )!!.toLong())
        val maxAgeThumb = (1000L
                * 60L
                * 60L
                * getString(
            R.string.pref_cache_maxage_thumb_key,
            "168"
        )!!.toLong())
        val maxAgeImage = (1000L
                * 60L
                * 60L
                * getString(
            R.string.pref_cache_maxage_image_key,
            "72"
        )!!.toLong())
        return createFileTypeToLongMap(maxAgeListing, maxAgeThumb, maxAgeImage)
    }

    @JvmStatic
	fun pref_cache_maxage_entry(): Long {
        return (1000L
                * 60L
                * 60L
                * getString(
            R.string.pref_cache_maxage_entry_key,
            "168"
        )!!.toLong())
    }

    // pref_cache_precache_images
	@JvmStatic
	fun cache_precache_images(): NeverAlwaysOrWifiOnly {
        return NeverAlwaysOrWifiOnly.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_cache_precache_images_list_key,
                    "wifionly"
                )!!
            )
        )
    }

    @JvmStatic
	fun cache_precache_images_old(): NeverAlwaysOrWifiOnly {
        if (network_tor()) {
            return NeverAlwaysOrWifiOnly.NEVER
        }
        return if (!getBoolean(
                R.string.pref_cache_precache_images_key,
                true
            )
        ) {
            NeverAlwaysOrWifiOnly.NEVER
        } else if (getBoolean(
                R.string.pref_cache_precache_images_wifionly_key,
                true
            )
        ) {
            NeverAlwaysOrWifiOnly.WIFIONLY
        } else {
            NeverAlwaysOrWifiOnly.ALWAYS
        }
    }

    // pref_cache_precache_comments
	@JvmStatic
	fun cache_precache_comments(): NeverAlwaysOrWifiOnly {
        return NeverAlwaysOrWifiOnly.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_cache_precache_comments_list_key,
                    "always"
                )!!
            )
        )
    }

    @JvmStatic
	fun cache_precache_comments_old(): NeverAlwaysOrWifiOnly {
        return if (!getBoolean(
                R.string.pref_cache_precache_comments_key,
                true
            )
        ) {
            NeverAlwaysOrWifiOnly.NEVER
        } else if (getBoolean(
                R.string.pref_cache_precache_comments_wifionly_key,
                false
            )
        ) {
            NeverAlwaysOrWifiOnly.WIFIONLY
        } else {
            NeverAlwaysOrWifiOnly.ALWAYS
        }
    }

    ///////////////////////////////
    // pref_network
    ///////////////////////////////
	@JvmStatic
	fun network_tor(): Boolean {
        return getBoolean(
            R.string.pref_network_tor_key,
            false
        )
    }

    ///////////////////////////////
    // pref_menus
    ///////////////////////////////
    fun pref_menus_post_context_items(): EnumSet<RedditPreparedPost.Action> {
        val strings = getStringSet(
            R.string.pref_menus_post_context_items_key,
            R.array.pref_menus_post_context_items_return
        )
        val result = EnumSet.noneOf(
            RedditPreparedPost.Action::class.java
        )
        for (s in strings!!) {
            result.add(
                RedditPreparedPost.Action.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    fun pref_menus_post_toolbar_items(): EnumSet<RedditPreparedPost.Action> {
        val strings = getStringSet(
            R.string.pref_menus_post_toolbar_items_key,
            R.array.pref_menus_post_toolbar_items_return
        )
        val result = EnumSet.noneOf(
            RedditPreparedPost.Action::class.java
        )
        for (s in strings!!) {
            result.add(
                RedditPreparedPost.Action.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    @JvmStatic
	fun pref_menus_link_context_items(): EnumSet<LinkHandler.LinkAction> {
        val strings = getStringSet(
            R.string.pref_menus_link_context_items_key,
            R.array.pref_menus_link_context_items_return
        )
        val result = EnumSet.noneOf(
            LinkHandler.LinkAction::class.java
        )
        for (s in strings!!) {
            result.add(
                LinkHandler.LinkAction.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    @JvmStatic
	fun pref_menus_subreddit_context_items(): EnumSet<SubredditAction> {
        val strings = getStringSet(
            R.string.pref_menus_subreddit_context_items_key,
            R.array.pref_menus_subreddit_context_items_return
        )
        val result = EnumSet.noneOf(
            SubredditAction::class.java
        )
        for (s in strings!!) {
            result.add(
                SubredditAction.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    @JvmStatic
	fun pref_menus_mainmenu_useritems(): EnumSet<MainMenuUserItems> {
        val strings = getStringSet(
            R.string.pref_menus_mainmenu_useritems_key,
            R.array.pref_menus_mainmenu_useritems_items_default
        )
        val result = EnumSet.noneOf(
            MainMenuUserItems::class.java
        )
        for (s in strings!!) {
            result.add(
                MainMenuUserItems.valueOf(
                    StringUtils.asciiUppercase(
                        s
                    )
                )
            )
        }
        return result
    }

    @JvmStatic
	fun pref_menus_mainmenu_shortcutitems(): EnumSet<MainMenuShortcutItems> {
        val strings = getStringSet(
            R.string.pref_menus_mainmenu_shortcutitems_key,
            R.array.pref_menus_mainmenu_shortcutitems_items_default
        )
        val result = EnumSet.noneOf(
            MainMenuShortcutItems::class.java
        )
        for (s in strings!!) {
            result.add(
                MainMenuShortcutItems.valueOf(
                    StringUtils.asciiUppercase(s)
                )
            )
        }
        return result
    }

    @JvmStatic
	fun pref_menus_appbar_items(): EnumMap<AppbarItemsPref, Int> {
        val appbarItemsInfo = arrayOf(
            AppbarItemInfo(
                AppbarItemsPref.SORT,
                R.string.pref_menus_appbar_sort_key,
                MenuItem.SHOW_AS_ACTION_ALWAYS
            ),
            AppbarItemInfo(
                AppbarItemsPref.REFRESH,
                R.string.pref_menus_appbar_refresh_key,
                MenuItem.SHOW_AS_ACTION_ALWAYS
            ),
            AppbarItemInfo(
                AppbarItemsPref.PAST,
                R.string.pref_menus_appbar_past_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.SUBMIT_POST,
                R.string.pref_menus_appbar_submit_post_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.PIN,
                R.string.pref_menus_appbar_pin_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.SUBSCRIBE,
                R.string.pref_menus_appbar_subscribe_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.BLOCK,
                R.string.pref_menus_appbar_block_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.SIDEBAR,
                R.string.pref_menus_appbar_sidebar_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.ACCOUNTS,
                R.string.pref_menus_appbar_accounts_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.THEME,
                R.string.pref_menus_appbar_theme_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.SETTINGS,
                R.string.pref_menus_appbar_settings_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.CLOSE_ALL,
                R.string.pref_menus_appbar_close_all_key,
                OptionsMenuUtility.DO_NOT_SHOW
            ),
            AppbarItemInfo(
                AppbarItemsPref.REPLY,
                R.string.pref_menus_appbar_reply_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            ),
            AppbarItemInfo(
                AppbarItemsPref.SEARCH,
                R.string.pref_menus_appbar_search_key,
                MenuItem.SHOW_AS_ACTION_NEVER
            )
        )
        val appbarItemsPrefs = EnumMap<AppbarItemsPref, Int>(
            AppbarItemsPref::class.java
        )
        for (item in appbarItemsInfo) {
            try {
                appbarItemsPrefs[item.itemPref] = getString(
                    item.stringRes,
                    Integer.toString(item.defaultValue)
                )!!.toInt()
            } catch (e: NumberFormatException) {
                appbarItemsPrefs[item.itemPref] = item.defaultValue
            } catch (e: NullPointerException) {
                appbarItemsPrefs[item.itemPref] = item.defaultValue
            }
        }
        return appbarItemsPrefs
    }

    @JvmStatic
	fun pref_menus_quick_account_switcher(): Boolean {
        return getBoolean(
            R.string.pref_menus_quick_account_switcher_key,
            true
        )
    }

    @JvmStatic
	fun pref_menus_comment_context_items(): EnumSet<RedditCommentAction> {
        val strings = getStringSet(
            R.string.pref_menus_comment_context_items_key,
            R.array.pref_menus_comment_context_items_return
        )
        val result = EnumSet.noneOf(
            RedditCommentAction::class.java
        )
        for (s in strings!!) {
            result.add(
                RedditCommentAction.valueOf(
                    StringUtils.asciiUppercase(s)
                )
            )
        }
        return result
    }

    ///////////////////////////////
    // pref_pinned_subreddits
    ///////////////////////////////
	@JvmStatic
	fun pref_pinned_subreddits(): List<SubredditCanonicalId> {
        return pref_subreddits_list(R.string.pref_pinned_subreddits_key)
    }

    @JvmStatic
	fun pref_pinned_subreddits_add(
        context: Context,
        subreddit: SubredditCanonicalId
    ) {
        pref_subreddits_add(
            context,
            subreddit,
            R.string.pref_pinned_subreddits_key
        )
        General.quickToast(
            context, context.applicationContext.getString(
                R.string.pin_successful,
                subreddit.toString()
            )
        )
    }

    @JvmStatic
	fun pref_pinned_subreddits_remove(
        context: Context,
        subreddit: SubredditCanonicalId
    ) {
        pref_subreddits_remove(
            context,
            subreddit,
            R.string.pref_pinned_subreddits_key
        )
        General.quickToast(
            context, context.applicationContext.getString(
                R.string.unpin_successful,
                subreddit.toString()
            )
        )
    }

    @JvmStatic
	fun pref_pinned_subreddits_check(id: SubredditCanonicalId): Boolean {
        return pref_pinned_subreddits().contains(id)
    }

    ///////////////////////////////
    // pref_blocked_subreddits
    ///////////////////////////////
	@JvmStatic
	fun pref_blocked_subreddits(): List<SubredditCanonicalId> {
        return pref_subreddits_list(R.string.pref_blocked_subreddits_key)
    }

    @JvmStatic
	fun pref_blocked_subreddits_add(
        context: Context,
        subreddit: SubredditCanonicalId
    ) {
        pref_subreddits_add(
            context,
            subreddit,
            R.string.pref_blocked_subreddits_key
        )
        General.quickToast(context, R.string.block_done)
    }

    @JvmStatic
	fun pref_blocked_subreddits_remove(
        context: Context,
        subreddit: SubredditCanonicalId
    ) {
        pref_subreddits_remove(
            context,
            subreddit,
            R.string.pref_blocked_subreddits_key
        )
        General.quickToast(context, R.string.unblock_done)
    }

    @JvmStatic
	fun pref_blocked_subreddits_check(subreddit: SubredditCanonicalId): Boolean {
        return pref_blocked_subreddits().contains(subreddit)
    }

    ///////////////////////////////
    // Shared pref_subreddits methods
    ///////////////////////////////
    private fun pref_subreddits_add(
        context: Context,
        subreddit: SubredditCanonicalId,
        prefId: Int
    ) {
        val value = getString(prefId, "")
        val list = WritableHashSet.escapedStringToList(value)
        if (!list.contains(subreddit.toString())) {
            list.add(subreddit.toString())
            val result = WritableHashSet.listToEscapedString(list)
            sharedPrefs!!.edit().putString(context.getString(prefId), result).apply()
        }
    }

    private fun pref_subreddits_remove(
        context: Context,
        subreddit: SubredditCanonicalId,
        prefId: Int
    ) {
        val value = getString(prefId, "")
        val list = WritableHashSet.escapedStringToList(value)
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id == subreddit.toString()) {
                iterator.remove()
                break
            }
        }
        val resultStr = WritableHashSet.listToEscapedString(list)
        sharedPrefs!!.edit().putString(context.getString(prefId), resultStr).apply()
    }

    fun pref_subreddits_list(prefId: Int): List<SubredditCanonicalId> {
        val value = getString(prefId, "")
        val list = WritableHashSet.escapedStringToList(value)
        val result = ArrayList<SubredditCanonicalId>(list.size)
        try {
            for (str in list) {
                result.add(SubredditCanonicalId(str!!))
            }
        } catch (e: InvalidSubredditNameException) {
            throw RuntimeException(e)
        }
        return result
    }

    @JvmStatic
	fun pref_accessibility_separate_body_text_lines(): Boolean {
        return getBoolean(
            R.string.pref_accessibility_separate_body_text_lines_key,
            true
        )
    }

    @JvmStatic
	fun pref_accessibility_min_comment_height(): Int {
        return try {
            getString(
                R.string.pref_accessibility_min_comment_height_key,
                "0"
            )!!.toInt()
        } catch (e: Throwable) {
            0
        }
    }

    @JvmStatic
	fun pref_accessibility_say_comment_indent_level(): Boolean {
        return getBoolean(
            R.string.pref_accessibility_say_comment_indent_level_key,
            true
        )
    }

    @JvmStatic
	fun behaviour_collapse_sticky_comments(): BehaviourCollapseStickyComments {
        return BehaviourCollapseStickyComments.valueOf(
            StringUtils.asciiUppercase(
                getString(
                    R.string.pref_behaviour_collapse_sticky_comments_key,
                    "ONLY_BOTS"
                )!!
            )
        )
    }

    @JvmStatic
	fun pref_accessibility_concise_mode(): Boolean {
        return getBoolean(
            R.string.pref_accessibility_concise_mode_key,
            false
        )
    }

    @JvmStatic
	fun pref_behaviour_keep_screen_awake(): Boolean {
        return getBoolean(
            R.string.pref_behaviour_keep_screen_awake_key,
            false
        )
    }

    ///////////////////////////////
    // pref_appearance
    ///////////////////////////////
    // pref_appearance_twopane
    enum class AppearanceTwopane {
        NEVER, AUTO, FORCE
    }

    enum class AppearanceTheme {
        RED, GREEN, BLUE, LTBLUE, ORANGE, GRAY, NIGHT, NIGHT_LOWCONTRAST, ULTRABLACK
    }

    enum class AppearanceNavbarColour {
        BLACK, WHITE, PRIMARY, PRIMARYDARK
    }

    enum class AppearanceStatusBarMode {
        ALWAYS_HIDE, HIDE_ON_MEDIA, NEVER_HIDE
    }

    enum class AppearancePostSubtitleItem {
        AUTHOR, FLAIR, SCORE, AGE, GOLD, SUBREDDIT, DOMAIN, STICKY, SPOILER, NSFW, UPVOTE_RATIO, COMMENTS
    }

    enum class AppearanceCommentHeaderItem {
        AUTHOR, FLAIR, SCORE, CONTROVERSIALITY, AGE, GOLD, SUBREDDIT
    }

    enum class CommentAgeMode {
        ABSOLUTE, RELATIVE_POST, RELATIVE_PARENT
    }

    // pref_behaviour_imageview_mode
    enum class ImageViewMode(val downloadInApp: Boolean) {
        INTERNAL_OPENGL(true), INTERNAL_BROWSER(false), EXTERNAL_BROWSER(false);
    }

    // pref_behaviour_albumview_mode
    enum class AlbumViewMode {
        INTERNAL_LIST, INTERNAL_BROWSER, EXTERNAL_BROWSER
    }

    // pref_behaviour_gifview_mode
    enum class GifViewMode(val downloadInApp: Boolean) {
        INTERNAL_MOVIE(true), INTERNAL_LEGACY(true), INTERNAL_BROWSER(false), EXTERNAL_BROWSER(false);
    }

    // pref_behaviour_videoview_mode
    enum class VideoViewMode(val downloadInApp: Boolean) {
        INTERNAL_VIDEOVIEW(true), INTERNAL_BROWSER(false), EXTERNAL_BROWSER(false), EXTERNAL_APP_VLC(
            true
        );
    }

    // pref_behaviour_fling_post
    enum class PostFlingAction {
        UPVOTE, DOWNVOTE, SAVE, HIDE, COMMENTS, LINK, ACTION_MENU, BROWSER, BACK, REPORT, SAVE_IMAGE, GOTO_SUBREDDIT, SHARE, SHARE_COMMENTS, SHARE_IMAGE, COPY, USER_PROFILE, PROPERTIES, DISABLED
    }

    enum class SelfpostAction {
        COLLAPSE, NOTHING
    }

    // pref_behaviour_fling_comment
    enum class CommentFlingAction {
        UPVOTE, DOWNVOTE, SAVE, REPORT, REPLY, CONTEXT, GO_TO_COMMENT, COMMENT_LINKS, SHARE, COPY_TEXT, COPY_URL, USER_PROFILE, COLLAPSE, ACTION_MENU, PROPERTIES, BACK, DISABLED
    }

    enum class CommentAction {
        COLLAPSE, ACTION_MENU, NOTHING
    }

    enum class PinnedSubredditSort {
        NAME, DATE
    }

    enum class BlockedSubredditSort {
        NAME, DATE
    }

    enum class PostCount {
        R25, R50, R100, ALL
    }

    enum class ScreenOrientation {
        AUTO, PORTRAIT, LANDSCAPE
    }

    enum class SaveLocation {
        PROMPT_EVERY_TIME, SYSTEM_DEFAULT
    }

    private class AppbarItemInfo internal constructor(
        val itemPref: AppbarItemsPref,
        val stringRes: Int,
        val defaultValue: Int
    )

    enum class BehaviourCollapseStickyComments {
        ALWAYS, ONLY_BOTS, NEVER
    }
}
