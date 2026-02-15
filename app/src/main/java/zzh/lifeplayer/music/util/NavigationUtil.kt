package zzh.lifeplayer.music.util

import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.fragment.app.FragmentActivity
import com.zmusicfx.musicfx.*
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.activities.DriveModeActivity
import zzh.lifeplayer.music.activities.LicenseActivity
import zzh.lifeplayer.music.activities.SupportDevelopmentActivity
import zzh.lifeplayer.music.activities.WhatsNewFragment
import zzh.lifeplayer.music.activities.bugreport.BugReportActivity
import zzh.lifeplayer.music.extensions.showToast
import zzh.lifeplayer.music.helper.MusicPlayerRemote.audioSessionId

object NavigationUtil {
    fun bugReport(activity: Activity) {
        activity.startActivity(Intent(activity, BugReportActivity::class.java), null)
    }

    fun goToOpenSource(activity: Activity) {
        activity.startActivity(Intent(activity, LicenseActivity::class.java), null)
    }

    fun goToSupportDevelopment(activity: Activity) {
        activity.startActivity(Intent(activity, SupportDevelopmentActivity::class.java), null)
    }

    fun gotoDriveMode(activity: Activity) {
        activity.startActivity(Intent(activity, DriveModeActivity::class.java), null)
    }

    fun gotoWhatNews(activity: FragmentActivity) {
        val changelogBottomSheet = WhatsNewFragment()
        changelogBottomSheet.show(activity.supportFragmentManager, WhatsNewFragment.TAG)
    }

    fun openEqualizer(activity: Activity) {
        val sessionId = audioSessionId
        if (sessionId !== AudioEffect.ERROR_BAD_VALUE) {
            try {
                Intent(activity, ActivityMusic::class.java).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }.also { intent ->
                    activity.startActivityForResult(intent, 0)
                    activity.overridePendingTransition(R.anim.retro_fragment_open_enter, R.anim.retro_fragment_close_exit)
                }
            } catch (e: Exception) {
                activity.showToast(R.string.no_equalizer)
            }
        } else {
            activity.showToast(R.string.no_equalizer)
        }
    }
}
