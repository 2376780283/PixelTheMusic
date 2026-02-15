package zzh.lifeplayer.music.activities

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore.Images.Media
import android.view.MenuItem
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.view.drawToBitmap
import com.bumptech.glide.Glide
import zzh.lifeplayer.appthemehelper.util.ColorUtil
import zzh.lifeplayer.appthemehelper.util.MaterialValueHelper
import zzh.lifeplayer.music.activities.base.AbsThemeActivity
import zzh.lifeplayer.music.databinding.ActivityShareInstagramBinding
import zzh.lifeplayer.music.extensions.accentColor
import zzh.lifeplayer.music.extensions.setStatusBarColor
import zzh.lifeplayer.music.glide.LifeGlideExtension
import zzh.lifeplayer.music.glide.LifeGlideExtension.asBitmapPalette
import zzh.lifeplayer.music.glide.LifeGlideExtension.songCoverOptions
import zzh.lifeplayer.music.glide.LifeMusicColoredTarget
import zzh.lifeplayer.music.model.Song
import zzh.lifeplayer.music.util.Share
import zzh.lifeplayer.music.util.color.MediaNotificationProcessor

/** Created by hemanths on 2020-02-02. */
@Suppress("DEPRECATION")
class ShareInstagramStory : AbsThemeActivity() {

    private lateinit var binding: ActivityShareInstagramBinding

    companion object {
        const val EXTRA_SONG = "extra_song"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareInstagramBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColor(Color.TRANSPARENT)

        binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
        setSupportActionBar(binding.toolbar)

        val song =
            intent.extras?.let { BundleCompat.getParcelable(it, EXTRA_SONG, Song::class.java) }
        song?.let { songFinal ->
            Glide.with(this)
                .asBitmapPalette()
                .songCoverOptions(songFinal)
                .load(LifeGlideExtension.getSongModel(songFinal))
                .into(
                    object : LifeMusicColoredTarget(binding.image) {
                        override fun onColorReady(colors: MediaNotificationProcessor) {
                            setColors(colors.backgroundColor)
                        }
                    }
                )

            binding.shareTitle.text = songFinal.title
            binding.shareText.text = songFinal.artistName
            binding.shareButton.setOnClickListener {
                val path: String =
                    Media.insertImage(
                        contentResolver,
                        binding.mainContent.drawToBitmap(Bitmap.Config.ARGB_8888),
                        "Design",
                        null,
                    )
                Share.shareStoryToSocial(this@ShareInstagramStory, path.toUri())
            }
        }
        binding.shareButton.setTextColor(
            MaterialValueHelper.getPrimaryTextColor(this, ColorUtil.isColorLight(accentColor()))
        )
        binding.shareButton.backgroundTintList = ColorStateList.valueOf(accentColor())
    }

    private fun setColors(color: Int) {
        binding.mainContent.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color, Color.BLACK),
            )
    }
}
