package zzh.lifeplayer.music.activities

// import com.google.android.material.navigationrail
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.contains
import androidx.navigation.ui.setupWithNavController
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.activities.base.AbsSlidingMusicPanelActivity
import zzh.lifeplayer.music.extensions.*
import zzh.lifeplayer.music.helper.MusicPlayerRemote
import zzh.lifeplayer.music.helper.SearchQueryHelper.getSongs
import zzh.lifeplayer.music.interfaces.IScrollHelper
import zzh.lifeplayer.music.model.CategoryInfo
import zzh.lifeplayer.music.model.Song
import zzh.lifeplayer.music.repository.PlaylistSongsLoader
import zzh.lifeplayer.music.service.MusicService
import zzh.lifeplayer.music.util.PreferenceUtil
import zzh.lifeplayer.music.util.logE

class MainActivity : AbsSlidingMusicPanelActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val EXPAND_PANEL = "expand_panel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTaskDescriptionColorAuto()
        hideStatusBar()
        updateTabs()
        // AppRater.appLaunched(this)
        setupNavigationController()
    }

    private fun setupNavigationController() {
        val navController = findNavController(R.id.fragment_container)
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.main_graph)

        val categoryInfo: CategoryInfo = PreferenceUtil.libraryCategory.first { it.visible }
        if (categoryInfo.visible) {
            if (!navGraph.contains(PreferenceUtil.lastTab))
                PreferenceUtil.lastTab = categoryInfo.category.id
            navGraph.setStartDestination(
                if (PreferenceUtil.rememberLastTab) {
                    PreferenceUtil.lastTab.let {
                        if (it == 0) {
                            categoryInfo.category.id
                        } else {
                            it
                        }
                    }
                } else categoryInfo.category.id
            )
        }
        navController.graph = navGraph
        navigationView.setupWithNavController(navController)
        val startDestinationId = navGraph.startDestinationId
        // Scroll Fragment to top
        navigationView.setOnItemReselectedListener {
            currentFragment(navHostId = R.id.fragment_container).apply {
                if (this is IScrollHelper) {
                    scrollToTop()
                }
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == startDestinationId) {
                currentFragment(navHostId = R.id.fragment_container)?.enterTransition = null
            }
            when (destination.id) {
                R.id.action_home,
                R.id.action_song,
                R.id.action_album,
                R.id.action_artist,
                R.id.action_folder,
                R.id.action_playlist,
                R.id.action_genre,                
                R.id.action_search -> {
                    setBottomNavVisibility(visible = true, animate = true)
                }
                
                R.id.playing_queue_fragment,
                R.id.action_go_to_lyrics,
                R.id.user_info_fragment,
                R.id.albumDetailsFragment -> {            
                    setBottomNavVisibility(visible = false, animate = true)
                } 
                else -> setBottomNavVisibility(visible = false, animate = true)
            }
        }
    }

    private fun saveTab(id: Int) {
        if (PreferenceUtil.libraryCategory.firstOrNull<CategoryInfo> { it.category.id == id }?.visible == true) {
            PreferenceUtil.lastTab = id
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val expand = intent?.extra<Boolean>(EXPAND_PANEL)?.value ?: false
        if (expand && PreferenceUtil.isExpandPanel) {
            fromNotification = true
            slidingPanel.bringToFront()
            expandPanel()
            intent?.removeExtra(EXPAND_PANEL)
        }
    }

    private fun handlePlaybackIntent(intent: Intent) {
        lifecycleScope.launch(IO) {
            val uri: Uri? = intent.data
            val mimeType: String? = intent.type
            var handled = false
            if (
                intent.action != null &&
                    intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
            ) {
                val songs: List<Song> = getSongs(intent.extras!!)
                if (MusicPlayerRemote.shuffleMode == MusicService.SHUFFLE_MODE_SHUFFLE) {
                    MusicPlayerRemote.openAndShuffleQueue(songs, true)
                } else {
                    MusicPlayerRemote.openQueue(songs, 0, true)
                }
                handled = true
            }
            if (uri != null && uri.toString().isNotEmpty()) {
                MusicPlayerRemote.playFromUri(this@MainActivity, uri)
                handled = true
            } else if ("vnd.android.cursor.dir/playlist" == mimeType) {
                val id = parseLongFromIntent(intent, "playlistId", "playlist")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs: List<Song> = PlaylistSongsLoader.getPlaylistSongList(get(), id)
                    MusicPlayerRemote.openQueue(songs, position, true)
                    handled = true
                }
            } else if ("vnd.android.cursor.dir/album" == mimeType) {
                val id = parseLongFromIntent(intent, "albumId", "album")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs = libraryViewModel.albumById(id).songs
                    MusicPlayerRemote.openQueue(songs, position, true)
                    handled = true
                }
            } else if ("vnd.android.cursor.dir/artist" == mimeType) {
                val id = parseLongFromIntent(intent, "artistId", "artist")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs: List<Song> = libraryViewModel.artistById(id).songs
                    MusicPlayerRemote.openQueue(songs, position, true)
                    handled = true
                }
            }

            if (handled) {
                setIntent(Intent())
            }
        }
    }

    private fun parseLongFromIntent(intent: Intent, longKey: String, stringKey: String): Long {
        var id = intent.getLongExtra(longKey, -1)
        if (id < 0) {
            val idString = intent.getStringExtra(stringKey)
            if (idString != null) {
                try {
                    id = idString.toLong()
                } catch (e: NumberFormatException) {
                    logE(e)
                }
            }
        }
        return id
    }

    override fun onResume() {
        super.onResume()
        setupNavigationController()
    }
}
