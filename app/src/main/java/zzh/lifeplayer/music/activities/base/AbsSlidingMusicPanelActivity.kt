package zzh.lifeplayer.music.activities.base

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING
import com.google.android.material.bottomsheet.BottomSheetBehavior.from
import com.google.android.material.navigationrail.NavigationRailView
import org.koin.androidx.viewmodel.ext.android.viewModel
import zzh.lifeplayer.appthemehelper.util.VersionUtils
import zzh.lifeplayer.music.ADAPTIVE_COLOR_APP
import zzh.lifeplayer.music.ALBUM_COVER_STYLE
import zzh.lifeplayer.music.ALBUM_COVER_TRANSFORM
import zzh.lifeplayer.music.CAROUSEL_EFFECT
import zzh.lifeplayer.music.CIRCLE_PLAY_BUTTON
import zzh.lifeplayer.music.EXTRA_SONG_INFO
import zzh.lifeplayer.music.KEEP_SCREEN_ON
import zzh.lifeplayer.music.LIBRARY_CATEGORIES
import zzh.lifeplayer.music.NOW_PLAYING_SCREEN_ID
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.SCREEN_ON_LYRICS
import zzh.lifeplayer.music.SWIPE_ANYWHERE_NOW_PLAYING
import zzh.lifeplayer.music.SWIPE_DOWN_DISMISS
import zzh.lifeplayer.music.TAB_TEXT_MODE
import zzh.lifeplayer.music.TOGGLE_ADD_CONTROLS
import zzh.lifeplayer.music.TOGGLE_FULL_SCREEN
import zzh.lifeplayer.music.TOGGLE_VOLUME
import zzh.lifeplayer.music.activities.PermissionActivity
import zzh.lifeplayer.music.databinding.SlidingMusicPanelLayoutBinding
import zzh.lifeplayer.music.extensions.currentFragment
import zzh.lifeplayer.music.extensions.darkAccentColor
import zzh.lifeplayer.music.extensions.dip
import zzh.lifeplayer.music.extensions.getBottomInsets
import zzh.lifeplayer.music.extensions.hide
import zzh.lifeplayer.music.extensions.isColorLight
import zzh.lifeplayer.music.extensions.isLandscape
import zzh.lifeplayer.music.extensions.keepScreenOn
import zzh.lifeplayer.music.extensions.maybeSetScreenOn
import zzh.lifeplayer.music.extensions.peekHeightAnimate
import zzh.lifeplayer.music.extensions.setLightNavigationBar
import zzh.lifeplayer.music.extensions.setLightNavigationBarAuto
import zzh.lifeplayer.music.extensions.setLightStatusBar
import zzh.lifeplayer.music.extensions.setLightStatusBarAuto
import zzh.lifeplayer.music.extensions.setNavigationBarColorPreOreo
import zzh.lifeplayer.music.extensions.setTaskDescriptionColor
import zzh.lifeplayer.music.extensions.show
import zzh.lifeplayer.music.extensions.surfaceColor
import zzh.lifeplayer.music.extensions.whichFragment
import zzh.lifeplayer.music.fragments.LibraryViewModel
import zzh.lifeplayer.music.fragments.NowPlayingScreen
import zzh.lifeplayer.music.fragments.NowPlayingScreen.*
import zzh.lifeplayer.music.fragments.base.AbsPlayerFragment
import zzh.lifeplayer.music.fragments.other.MiniPlayerFragment
import zzh.lifeplayer.music.fragments.player.adaptive.AdaptiveFragment
import zzh.lifeplayer.music.fragments.player.blur.BlurPlayerFragment
import zzh.lifeplayer.music.fragments.player.card.CardFragment
import zzh.lifeplayer.music.fragments.player.cardblur.CardBlurFragment
import zzh.lifeplayer.music.fragments.player.circle.CirclePlayerFragment
import zzh.lifeplayer.music.fragments.player.classic.ClassicPlayerFragment
import zzh.lifeplayer.music.fragments.player.color.ColorFragment
import zzh.lifeplayer.music.fragments.player.fit.FitFragment
import zzh.lifeplayer.music.fragments.player.flat.FlatPlayerFragment
import zzh.lifeplayer.music.fragments.player.full.FullPlayerFragment
import zzh.lifeplayer.music.fragments.player.gradient.GradientPlayerFragment
import zzh.lifeplayer.music.fragments.player.material.MaterialFragment
import zzh.lifeplayer.music.fragments.player.md3.MD3PlayerFragment
import zzh.lifeplayer.music.fragments.player.normal.PlayerFragment
import zzh.lifeplayer.music.fragments.player.peek.PeekPlayerFragment
import zzh.lifeplayer.music.fragments.player.plain.PlainPlayerFragment
import zzh.lifeplayer.music.fragments.player.simple.SimplePlayerFragment
import zzh.lifeplayer.music.fragments.player.tiny.TinyPlayerFragment
import zzh.lifeplayer.music.fragments.queue.PlayingQueueFragment
import zzh.lifeplayer.music.helper.MusicPlayerRemote
import zzh.lifeplayer.music.model.CategoryInfo
import zzh.lifeplayer.music.util.PreferenceUtil
import zzh.lifeplayer.music.util.ViewUtil
import zzh.lifeplayer.music.util.logD

abstract class AbsSlidingMusicPanelActivity :
    AbsMusicServiceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        val TAG: String = AbsSlidingMusicPanelActivity::class.java.simpleName
    }

    var fromNotification = false
    private var windowInsets: WindowInsetsCompat? = null
    protected val libraryViewModel by viewModel<LibraryViewModel>()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var playerFragment: AbsPlayerFragment
    private var miniPlayerFragment: MiniPlayerFragment? = null
    private var nowPlayingScreen: NowPlayingScreen? = null
    private var taskColor: Int = 0
    private var paletteColor: Int = 0xFFFFFFFF.toInt()
    private var navigationBarColor = 0

    private val panelState: Int
        get() = bottomSheetBehavior.state

    private var panelStateBefore: Int? = null
    private var panelStateCurrent: Int? = null
    private lateinit var binding: SlidingMusicPanelLayoutBinding
    private var isInOneTabMode = false

    private var navigationBarColorAnimator: ValueAnimator? = null
    private val argbEvaluator: ArgbEvaluator = ArgbEvaluator()

    private val onBackPressedCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (handleBackPress()) {
                    return
                }
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.fragment_container)
                        as NavHostFragment
                if (!navHostFragment.navController.navigateUp()) {
                    finish()
                }
            }
        }

    private val bottomSheetCallbackList by lazy {
        object : BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                setMiniPlayerAlphaProgress(slideOffset)
                navigationBarColorAnimator?.cancel()
                setNavigationBarColorPreOreo(
                    argbEvaluator.evaluate(slideOffset, surfaceColor(), navigationBarColor) as Int
                )
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (panelStateCurrent != null) {
                    panelStateBefore = panelStateCurrent
                }
                panelStateCurrent = newState
                when (newState) {
                    STATE_EXPANDED -> {
                        onPanelExpanded()
                        if (PreferenceUtil.lyricsScreenOn && PreferenceUtil.showLyrics) {
                            keepScreenOn(true)
                        }
                    }

                    STATE_COLLAPSED -> {
                        onPanelCollapsed()
                        if (
                            (PreferenceUtil.lyricsScreenOn && PreferenceUtil.showLyrics) ||
                                !PreferenceUtil.isScreenOnEnabled
                        ) {
                            keepScreenOn(false)
                        }
                    }

                    STATE_SETTLING,
                    STATE_DRAGGING -> {
                        if (fromNotification) {
                            
                            fromNotification = false
                        }
                    }

                    STATE_HIDDEN -> {
                        MusicPlayerRemote.clearQueue()
                    }

                    else -> {
                        logD("Do a flip")
                    }
                }
            }
        }
    }

    fun getBottomSheetBehavior() = bottomSheetBehavior

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
        }
        binding = SlidingMusicPanelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setOnApplyWindowInsetsListener { _, insets ->
            windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
            insets
        }
        chooseFragmentForTheme()
        setupSlidingUpPanel()
        setupBottomSheet()
        updateColor()
        if (!PreferenceUtil.materialYou) {
            binding.slidingPanel.backgroundTintList = ColorStateList.valueOf(darkAccentColor())
            navigationView.backgroundTintList = ColorStateList.valueOf(darkAccentColor())
        }

        navigationBarColor = surfaceColor()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = from(binding.slidingPanel)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallbackList)
        bottomSheetBehavior.isHideable = PreferenceUtil.swipeDownToDismiss
        bottomSheetBehavior.significantVelocityThreshold = 300
        setMiniPlayerAlphaProgress(0F)
    }

    override fun onResume() {
        super.onResume()
        PreferenceUtil.registerOnSharedPreferenceChangedListener(this)
        if (nowPlayingScreen != PreferenceUtil.nowPlayingScreen) {
            postRecreate()
        }
        if (bottomSheetBehavior.state == STATE_EXPANDED) {
            setMiniPlayerAlphaProgress(1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallbackList)
        PreferenceUtil.unregisterOnSharedPreferenceChangedListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            SWIPE_DOWN_DISMISS -> {
                bottomSheetBehavior.isHideable = PreferenceUtil.swipeDownToDismiss
            }

            TOGGLE_ADD_CONTROLS -> {
                miniPlayerFragment?.setUpButtons()
            }

            NOW_PLAYING_SCREEN_ID -> {
                chooseFragmentForTheme()
                binding.slidingPanel.updateLayoutParams<ViewGroup.LayoutParams> {
                    height =
                        if (nowPlayingScreen != Peek) {
                            ViewGroup.LayoutParams.MATCH_PARENT
                        } else {
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    onServiceConnected()
                }
            }

            ALBUM_COVER_TRANSFORM,
            CAROUSEL_EFFECT,
            ALBUM_COVER_STYLE,
            TOGGLE_VOLUME,
            EXTRA_SONG_INFO,
            CIRCLE_PLAY_BUTTON -> {
                chooseFragmentForTheme()
                onServiceConnected()
            }

            SWIPE_ANYWHERE_NOW_PLAYING -> {
                playerFragment.addSwipeDetector()
            }

            ADAPTIVE_COLOR_APP -> {
                if (PreferenceUtil.nowPlayingScreen in listOf(Normal, Material, Flat)) {
                    chooseFragmentForTheme()
                    onServiceConnected()
                }
            }

            LIBRARY_CATEGORIES -> {
                updateTabs()
            }

            TAB_TEXT_MODE -> {
                navigationView.labelVisibilityMode = PreferenceUtil.tabTitleMode
            }

            TOGGLE_FULL_SCREEN -> {
                recreate()
            }

            SCREEN_ON_LYRICS -> {
                keepScreenOn(
                    bottomSheetBehavior.state == STATE_EXPANDED &&
                        PreferenceUtil.lyricsScreenOn &&
                        PreferenceUtil.showLyrics || PreferenceUtil.isScreenOnEnabled
                )
            }

            KEEP_SCREEN_ON -> {
                maybeSetScreenOn()
            }
        }
    }

    fun collapsePanel() {
        bottomSheetBehavior.state = STATE_COLLAPSED
    }

    fun expandPanel() {
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun setMiniPlayerAlphaProgress(progress: Float) {
        if (progress < 0) return
        val alpha = 1 - progress
        miniPlayerFragment?.view?.alpha = 1 - (progress / 0.2F)
        miniPlayerFragment?.view?.isGone = alpha == 0f

        binding.playerFragmentContainer.alpha = (progress - 0.2F) / 0.2F
    }

    private fun animateNavigationBarColor(color: Int) {
        if (VersionUtils.hasOreo()) return
        navigationBarColorAnimator?.cancel()
        navigationBarColorAnimator =
            ValueAnimator.ofArgb(window.navigationBarColor, color).apply {
                duration = ViewUtil.RETRO_MUSIC_ANIM_TIME.toLong()
                interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
                addUpdateListener { animation: ValueAnimator ->
                    setNavigationBarColorPreOreo(animation.animatedValue as Int)
                }
                start()
            }
    }

    open fun onPanelCollapsed() {
        setMiniPlayerAlphaProgress(0F)
        // restore values
        animateNavigationBarColor(surfaceColor())
        setLightStatusBarAuto()
        setLightNavigationBarAuto()
        setTaskDescriptionColor(taskColor)
        // playerFragment?.onHide()
    }

    open fun onPanelExpanded() {
        setMiniPlayerAlphaProgress(1F)
        onPaletteColorChanged()
        // playerFragment?.onShow()
    }

    private fun setupSlidingUpPanel() {
        binding.slidingPanel.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.slidingPanel.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (nowPlayingScreen != Peek) {
                        binding.slidingPanel.updateLayoutParams<ViewGroup.LayoutParams> {
                            height = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    }
                    when (panelState) {
                        STATE_EXPANDED -> onPanelExpanded()
                        STATE_COLLAPSED -> onPanelCollapsed()
                        else -> {
                            // playerFragment!!.onHide()
                        }
                    }
                }
            }
        )
    }

    val navigationView
        get() = binding.navigationView

    val slidingPanel
        get() = binding.slidingPanel

    val isBottomNavVisible
        get() = navigationView.isVisible && navigationView is BottomNavigationView

    override fun onServiceConnected() {
        super.onServiceConnected()
        hideBottomSheet(false)
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        // Mini player should be hidden in Playing Queue
        // it may pop up if hideBottomSheet is called
        if (currentFragment(R.id.fragment_container) !is PlayingQueueFragment) {
            hideBottomSheet(MusicPlayerRemote.playingQueue.isEmpty())
        }
    }

    private fun handleBackPress(): Boolean {
        if (
            panelState == STATE_EXPANDED ||
                (panelState == STATE_SETTLING && panelStateBefore != STATE_EXPANDED)
        ) {
            collapsePanel()
            return true
        }
        return false
    }

    private fun onPaletteColorChanged() {
        if (panelState == STATE_EXPANDED) {
            navigationBarColor = surfaceColor()
            setTaskDescColor(paletteColor)
            val isColorLight = paletteColor.isColorLight
            if (
                PreferenceUtil.isAdaptiveColor &&
                    (nowPlayingScreen == Normal ||
                        nowPlayingScreen == Flat ||
                        nowPlayingScreen == Material)
            ) {
                setLightNavigationBar(true)
                setLightStatusBar(isColorLight)
            } else if (
                nowPlayingScreen == Card || nowPlayingScreen == Blur || nowPlayingScreen == BlurCard
            ) {
                animateNavigationBarColor(android.graphics.Color.BLACK)
                navigationBarColor = android.graphics.Color.BLACK
                setLightStatusBar(false)
                setLightNavigationBar(true)
            } else if (
                nowPlayingScreen == Color ||
                    nowPlayingScreen == Tiny ||
                    nowPlayingScreen == Gradient
            ) {
                animateNavigationBarColor(paletteColor)
                navigationBarColor = paletteColor
                setLightNavigationBar(isColorLight)
                setLightStatusBar(isColorLight)
            } else if (nowPlayingScreen == Full) {
                animateNavigationBarColor(paletteColor)
                navigationBarColor = paletteColor
                setLightNavigationBar(isColorLight)
                setLightStatusBar(false)
            } else if (nowPlayingScreen == Classic) {
                setLightStatusBar(false)
            } else if (nowPlayingScreen == Fit) {
                setLightStatusBar(false)
            }
        }
    }

    private fun setTaskDescColor(color: Int) {
        taskColor = color
        if (panelState == STATE_COLLAPSED) {
            setTaskDescriptionColor(color)
        }
    }

    fun updateTabs() {
        binding.navigationView.menu.clear()
        val currentTabs: List<CategoryInfo> = PreferenceUtil.libraryCategory
        for (tab in currentTabs) {
            if (tab.visible) {
                val menu = tab.category
                binding.navigationView.menu.add(0, menu.id, 0, menu.stringRes).setIcon(menu.icon)
            }
        }
        if (binding.navigationView.menu.size() == 1) {
            isInOneTabMode = true            
        } else {
            isInOneTabMode = false
        }
    }

    private fun updateColor() {
        libraryViewModel.paletteColor.observe(this) { color ->
            this.paletteColor = color
            onPaletteColorChanged()
        }
    }

    fun setBottomNavVisibility(
        visible: Boolean,
        animate: Boolean = false,
        hideBottomSheet: Boolean = MusicPlayerRemote.playingQueue.isEmpty(),
    ) {
        val mAnimate = animate && bottomSheetBehavior.state == STATE_COLLAPSED
        
        if (visible) {
            if (!navigationView.isVisible || navigationView.alpha < 1f) {
                if (mAnimate) {
                    binding.navigationView.isEnabled = true
                    binding.navigationView.isClickable = true
                    setNavigationItemsEnabled(binding.navigationView, true)
                    binding.navigationView.show()
                    binding.navigationView
                        .animate()
                        .alpha(1f)
                        .setDuration(300)
                        .withStartAction { binding.navigationView.alpha = 0.4f }
                        .withEndAction {}
                        .start()
                } else {
                    binding.navigationView.bringToFront()
                    binding.navigationView.show()
                    binding.navigationView.isEnabled = true
                    binding.navigationView.isClickable = true
                    setNavigationItemsEnabled(binding.navigationView, true)
                    binding.navigationView.alpha = 1f
                }
            }
        } else {
            if (navigationView.isVisible || navigationView.alpha > 0f) {
                if (mAnimate) {
                    binding.navigationView.isEnabled = false
                    binding.navigationView.isClickable = false
                    setNavigationItemsEnabled(binding.navigationView, false)
                    binding.navigationView
                        .animate()
                        .alpha(0.7f)
                        .setDuration(300)
                    //  .withEndAction { binding.navigationView.hide() }
                        .start()
                } else {
                    binding.navigationView.isEnabled = false
                    binding.navigationView.isClickable = false
                    setNavigationItemsEnabled(binding.navigationView, false)
                    binding.navigationView.alpha = 0.6f
                    // binding.navigationView.hide()
                }
            }
        }
        
        hideBottomSheet(
            hide = hideBottomSheet,
            animate = animate,
            isBottomNavVisible = visible && navigationView is BottomNavigationView,
        )
    }

    fun setNavigationItemsEnabled(nvgview: View, enabled: Boolean) {
        if (binding.navigationView !is NavigationRailView) {
            val menu = binding.navigationView.menu
            for (i in 0 until menu.size()) {
                menu.getItem(i).isEnabled = enabled
            }
            return
        }
        val menu = binding.navigationView.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            menuItem.isEnabled = enabled
            // menuItem.isCheckable = enabled
        }
    }

    fun hideBottomSheet(
        hide: Boolean,
        animate: Boolean = false,
        isBottomNavVisible: Boolean =
            navigationView.isVisible && navigationView is BottomNavigationView,
    ) {
        val heightOfBar = windowInsets.getBottomInsets() + dip(R.dimen.mini_player_height)
        val heightOfBarWithTabs = heightOfBar + dip(R.dimen.bottom_nav_height)
        if (hide) {
            bottomSheetBehavior.peekHeight = (-windowInsets.getBottomInsets()).coerceAtLeast(0)
            bottomSheetBehavior.state = STATE_COLLAPSED
            libraryViewModel.setFabMargin(
                this,
                if (isBottomNavVisible) dip(R.dimen.bottom_nav_height) else 0,
            )
        } else {
            if (MusicPlayerRemote.playingQueue.isNotEmpty()) {
                binding.slidingPanel.elevation = 0F
                
                if (isBottomNavVisible) {
                    logD("List")
                    if (animate) {
                        bottomSheetBehavior.peekHeightAnimate(heightOfBarWithTabs)
                    } else {
                        bottomSheetBehavior.peekHeight = heightOfBarWithTabs
                    }
                    libraryViewModel.setFabMargin(this, dip(R.dimen.bottom_nav_mini_player_height))
                } else {
                    logD("Details")
                    if (animate) {
                        bottomSheetBehavior.peekHeightAnimate(heightOfBar).doOnEnd {
                            binding.slidingPanel.bringToFront()
                        }
                    } else {
                        bottomSheetBehavior.peekHeight = heightOfBar
                        binding.slidingPanel.bringToFront()
                    }
                    libraryViewModel.setFabMargin(this, dip(R.dimen.mini_player_height))
                }
            }
        }
    }

    fun setAllowDragging(allowDragging: Boolean) {
        bottomSheetBehavior.isDraggable = allowDragging
        hideBottomSheet(false)
    }

    private fun chooseFragmentForTheme() {
        nowPlayingScreen = PreferenceUtil.nowPlayingScreen

        val fragment: AbsPlayerFragment =
            when (nowPlayingScreen) {
                Blur -> BlurPlayerFragment()
                Adaptive -> AdaptiveFragment()
                Normal -> PlayerFragment()
                Card -> CardFragment()
                BlurCard -> CardBlurFragment()
                Fit -> FitFragment()
                Flat -> FlatPlayerFragment()
                Full -> FullPlayerFragment()
                Plain -> PlainPlayerFragment()
                Simple -> SimplePlayerFragment()
                Material -> MaterialFragment()
                Color -> ColorFragment()
                Gradient -> GradientPlayerFragment()
                Tiny -> TinyPlayerFragment()
                Peek -> PeekPlayerFragment()
                Circle -> CirclePlayerFragment()
                Classic -> ClassicPlayerFragment()
                MD3 -> MD3PlayerFragment()
                else -> BlurPlayerFragment()
            }
        supportFragmentManager.commit { replace(R.id.playerFragmentContainer, fragment) }
        supportFragmentManager.executePendingTransactions()
        playerFragment = whichFragment(R.id.playerFragmentContainer)
        miniPlayerFragment = whichFragment<MiniPlayerFragment>(R.id.miniPlayerFragment)
        miniPlayerFragment?.view?.setOnClickListener { expandPanel() }
    }
}
