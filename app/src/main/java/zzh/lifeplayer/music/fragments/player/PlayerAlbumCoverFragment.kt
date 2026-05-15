package zzh.lifeplayer.music.fragments.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zzh.lifeplayer.appthemehelper.util.ColorUtil
import zzh.lifeplayer.appthemehelper.util.MaterialValueHelper
import zzh.lifeplayer.music.LYRICS_FONT_SIZE
import zzh.lifeplayer.music.LYRICS_TYPE
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.SHOW_LYRICS
import zzh.lifeplayer.music.adapter.album.AlbumCoverPagerAdapter
import zzh.lifeplayer.music.adapter.album.AlbumCoverPagerAdapter.AlbumCoverFragment
import zzh.lifeplayer.music.databinding.FragmentPlayerAlbumCoverBinding
import zzh.lifeplayer.music.extensions.isColorLight
import zzh.lifeplayer.music.extensions.surfaceColor
import zzh.lifeplayer.music.fragments.NowPlayingScreen.*
import zzh.lifeplayer.music.fragments.base.AbsMusicServiceFragment
import zzh.lifeplayer.music.helper.MusicPlayerRemote
import zzh.lifeplayer.music.helper.MusicProgressViewUpdateHelper
import zzh.lifeplayer.music.lyrics.CoverLrcView
import zzh.lifeplayer.music.model.lyrics.Lyrics
import zzh.lifeplayer.music.transform.CarousalPagerTransformer
import zzh.lifeplayer.music.transform.ParallaxPagerTransformer
import zzh.lifeplayer.music.util.CoverLyricsType
import zzh.lifeplayer.music.util.LyricUtil
import zzh.lifeplayer.music.util.PreferenceUtil
import zzh.lifeplayer.music.util.PreferenceUtil.lyricsfontsize
import zzh.lifeplayer.music.util.color.MediaNotificationProcessor

class PlayerAlbumCoverFragment :
    AbsMusicServiceFragment(R.layout.fragment_player_album_cover),
    ViewPager.OnPageChangeListener,
    MusicProgressViewUpdateHelper.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentPlayerAlbumCoverBinding? = null
    private val binding
        get() = _binding!!

    private var callbacks: Callbacks? = null
    private var currentPosition: Int = 0
    val viewPager
        get() = binding.viewPager

    private val colorReceiver =
        object : AlbumCoverFragment.ColorReceiver {
            override fun onColorReady(color: MediaNotificationProcessor, request: Int) {
                if (currentPosition == request) {
                    notifyColorChange(color)
                }
            }
        }
    private var progressViewUpdateHelper: MusicProgressViewUpdateHelper? = null

    private val lrcView: CoverLrcView
        get() = binding.lyricsView

    var lyrics: Lyrics? = null

    fun removeSlideEffect() {
        val transformer = ParallaxPagerTransformer(R.id.player_image)
        transformer.setSpeed(0.3f)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewPager.setPageTransformer(false, transformer)
            }
        }
    }

    private fun updateLyrics() {
        val song = MusicPlayerRemote.currentSong
        lifecycleScope.launch(Dispatchers.IO) {
            val lrcFile = LyricUtil.getSyncedLyricsFile(song)
            if (lrcFile != null) {
                binding.lyricsView.loadLrc(lrcFile)
            } else {
                val embeddedLyrics = LyricUtil.getEmbeddedSyncedLyrics(song.data)
                if (embeddedLyrics != null) {
                    binding.lyricsView.loadLrc(embeddedLyrics)
                } else {
                    withContext(Dispatchers.Main) {
                        binding.lyricsView.reset()
                        binding.lyricsView.setLabel(context?.getString(R.string.no_lyrics_found))
                    }
                }
            }
        }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.lyricsView.updateTime(progress.toLong())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerAlbumCoverBinding.bind(view)
        setupViewPager()
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        maybeInitLyrics()
        lrcView.apply {
            setDraggable(true) { time ->
                MusicPlayerRemote.seekTo(time.toInt())
                MusicPlayerRemote.resumePlaying()
                true
            }
            applyfontsize()
        }
    }

    private fun setupViewPager() {
        binding.viewPager.addOnPageChangeListener(this)
        val nps = PreferenceUtil.nowPlayingScreen

        if (nps == Full || nps == Classic || nps == Fit || nps == Gradient) {
            binding.viewPager.offscreenPageLimit = 2
        } else if (PreferenceUtil.isCarouselEffect) {
            val metrics = resources.displayMetrics
            val ratio = metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()
            binding.viewPager.clipToPadding = false
            val padding =
                if (ratio >= 1.777f) {
                    40
                } else {
                    100
                }
            binding.viewPager.setPadding(padding, 0, padding, 0)
            binding.viewPager.pageMargin = 0
            binding.viewPager.setPageTransformer(false, CarousalPagerTransformer(requireContext()))
        } else {
            binding.viewPager.offscreenPageLimit = 2
            binding.viewPager.setPageTransformer(true, PreferenceUtil.albumCoverTransform)
        }
    }

    override fun onResume() {
        super.onResume()
        maybeInitLyrics()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        binding.viewPager.removeOnPageChangeListener(this)
        progressViewUpdateHelper?.stop()
        _binding = null
    }

    override fun onServiceConnected() {
        updatePlayingQueue()
        updateLyrics()
    }

    override fun onPlayingMetaChanged() {
        if (viewPager.currentItem != MusicPlayerRemote.position) {
            viewPager.setCurrentItem(MusicPlayerRemote.position, true)
        }
        updateLyrics()
    }

    override fun onQueueChanged() {
        updatePlayingQueue()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            SHOW_LYRICS -> {
                if (PreferenceUtil.showLyrics) {
                    maybeInitLyrics()
                } else {
                    showLyrics(false)
                    progressViewUpdateHelper?.stop()
                }
            }

            LYRICS_TYPE -> {
                maybeInitLyrics()
            }

            LYRICS_FONT_SIZE -> {
                applyfontsize()
            }
        }
    }

    private fun applyfontsize() {
        binding.lyricsView?.setLyricFontSize(lyricsfontsize)
    }

    private fun setLRCViewColors(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int) {
        lrcView.apply {
            setCurrentColor(primaryColor)
            setTimeTextColor(primaryColor)
            setTimelineColor(primaryColor)
            setNormalColor(secondaryColor)
            setTimelineTextColor(primaryColor)
        }
    }

    private fun showLyrics(visible: Boolean) {
        binding.coverLyrics.isVisible = false
        binding.lyricsView.isVisible = false
        binding.viewPager.isVisible = true
        val lyrics: View =
            if (PreferenceUtil.lyricsType == CoverLyricsType.REPLACE_COVER) {
                ObjectAnimator.ofFloat(viewPager, View.ALPHA, if (visible) 0F else 1F).start()
                lrcView
            } else {
                ObjectAnimator.ofFloat(viewPager, View.ALPHA, 1F).start()
                binding.coverLyrics
            }
        ObjectAnimator.ofFloat(lyrics, View.ALPHA, if (visible) 1F else 0F).apply {
            doOnEnd { lyrics.isVisible = visible }
            start()
        }
    }

    private fun maybeInitLyrics() {
        val nps = PreferenceUtil.nowPlayingScreen
        // Don't show lyrics container for below conditions
        if (lyricViewNpsList.contains(nps) && PreferenceUtil.showLyrics) {
            showLyrics(true)
            if (PreferenceUtil.lyricsType == CoverLyricsType.REPLACE_COVER) {
                progressViewUpdateHelper?.start()
            }
        } else {
            showLyrics(false)
            progressViewUpdateHelper?.stop()
        }
    }

    private fun updatePlayingQueue() {
        val adapter = binding.viewPager.adapter
        if (adapter is AlbumCoverPagerAdapter) {
            adapter.updateData(MusicPlayerRemote.playingQueue)
        } else {
            binding.viewPager.adapter =
                AlbumCoverPagerAdapter(parentFragmentManager, MusicPlayerRemote.playingQueue)
        }
        binding.viewPager.setCurrentItem(MusicPlayerRemote.position, true)
        onPageSelected(MusicPlayerRemote.position)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        currentPosition = position
        if (binding.viewPager.adapter != null) {
            (binding.viewPager.adapter as AlbumCoverPagerAdapter).receiveColor(
                colorReceiver,
                position,
            )
        }
        if (position != MusicPlayerRemote.position) {
            MusicPlayerRemote.playSongAt(position)
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}

    private fun notifyColorChange(color: MediaNotificationProcessor) {
        callbacks?.onColorChanged(color)
        val primaryColor =
            MaterialValueHelper.getPrimaryTextColor(requireContext(), surfaceColor().isColorLight)
        val secondaryColor =
            MaterialValueHelper.getSecondaryDisabledTextColor(
                requireContext(),
                surfaceColor().isColorLight,
            )

        when (PreferenceUtil.nowPlayingScreen) {
            Flat,
            Normal,
            Material ->
                if (PreferenceUtil.isAdaptiveColor) {
                    setLRCViewColors(color.primaryTextColor, color.secondaryTextColor)
                } else {
                    setLRCViewColors(primaryColor, secondaryColor)
                }
            Color,
            Classic -> setLRCViewColors(color.primaryTextColor, color.secondaryTextColor)
            Blur ->
                setLRCViewColors(
                    android.graphics.Color.WHITE,
                    ColorUtil.withAlpha(android.graphics.Color.WHITE, 0.5f),
                )
            else -> setLRCViewColors(primaryColor, secondaryColor)
        }
    }

    fun setCallbacks(listener: Callbacks) {
        callbacks = listener
    }

    interface Callbacks {

        fun onColorChanged(color: MediaNotificationProcessor)

        fun onFavoriteToggled()
    }

    companion object {
        val TAG: String = PlayerAlbumCoverFragment::class.java.simpleName
    }

    private val lyricViewNpsList =
        listOf(Blur, Classic, Color, Flat, Material, MD3, Normal, Plain, Simple)
}
