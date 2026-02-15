package zzh.lifeplayer.music.fragments.home

// import androidx.core.text.parseAsHtml
import android.os.Bundle
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import zzh.lifeplayer.appthemehelper.common.ATHToolbarActivity
import zzh.lifeplayer.appthemehelper.util.ColorUtil
import zzh.lifeplayer.appthemehelper.util.ToolbarContentTintHelper
import zzh.lifeplayer.music.*
import zzh.lifeplayer.music.adapter.HomeAdapter
import zzh.lifeplayer.music.databinding.FragmentHomeBinding
import zzh.lifeplayer.music.dialogs.CreatePlaylistDialog
import zzh.lifeplayer.music.dialogs.ImportPlaylistDialog
import zzh.lifeplayer.music.extensions.accentColor
import zzh.lifeplayer.music.extensions.dip
import zzh.lifeplayer.music.extensions.elevatedAccentColor
import zzh.lifeplayer.music.fragments.ReloadType
import zzh.lifeplayer.music.fragments.base.AbsMainActivityFragment
import zzh.lifeplayer.music.glide.LifeGlideExtension
import zzh.lifeplayer.music.glide.LifeGlideExtension.profileBannerOptions
import zzh.lifeplayer.music.glide.LifeGlideExtension.songCoverOptions
import zzh.lifeplayer.music.glide.LifeGlideExtension.userProfileOptions
import zzh.lifeplayer.music.helper.MusicPlayerRemote
import zzh.lifeplayer.music.interfaces.IScrollHelper
import zzh.lifeplayer.music.model.Song
import zzh.lifeplayer.music.util.PreferenceUtil
import zzh.lifeplayer.music.util.PreferenceUtil.userName

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: HomeBinding? = null
    private val binding
        get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val homeBinding = FragmentHomeBinding.bind(view)
        _binding = HomeBinding(homeBinding)
        mainActivity.setSupportActionBar(binding.toolbar)
        mainActivity.supportActionBar?.title = null
        setupListeners()
        binding.titleWelcome.text = String.format("%s", userName)
        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        reenterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)

        checkForMargins()

        val homeAdapter = HomeAdapter(mainActivity)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(mainActivity)
            adapter = homeAdapter
        }
        libraryViewModel.getSuggestions().observe(viewLifecycleOwner) { loadSuggestions(it) }
        libraryViewModel.getHome().observe(viewLifecycleOwner) { homeAdapter.swapData(it) }

        loadProfile()
        setupTitle()
        colorButtons()
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout { adjustPlaylistButtons() }
    }

    private fun adjustPlaylistButtons() {
        val buttons =
            listOf(binding.history, binding.lastAdded, binding.topPlayed, binding.actionShuffle)
        buttons
            .maxOf { it.lineCount }
            .let { maxLineCount ->
                buttons.forEach { button ->
                    // Set the highest line count to every button for consistency
                    button.setLines(maxLineCount)
                }
            }
    }

    private fun setupListeners() {
        binding.lastAdded.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.detailListFragment,
                    bundleOf(EXTRA_PLAYLIST_TYPE to LAST_ADDED_PLAYLIST),
                )
            setSharedAxisYTransitions()
        }

        binding.topPlayed.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.detailListFragment,
                    bundleOf(EXTRA_PLAYLIST_TYPE to TOP_PLAYED_PLAYLIST),
                )
            setSharedAxisYTransitions()
        }

        binding.actionShuffle.setOnClickListener { libraryViewModel.shuffleSongs() }

        binding.history.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.detailListFragment,
                    bundleOf(EXTRA_PLAYLIST_TYPE to HISTORY_PLAYLIST),
                )
            setSharedAxisYTransitions()
        }

        binding.userImage.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.user_info_fragment,
                    null,
                    null,
                    FragmentNavigatorExtras(binding.userImage to "user_image"),
                )
        }
        // Reload suggestions
        binding.suggestions.refreshButton.setOnClickListener {
            libraryViewModel.forceReload(ReloadType.Suggestions)
        }
    }

    private fun setupTitle() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.action_search, null, navOptions)
        }
        val appName = "PixelMusic"
        binding.appBarLayout.title = appName
    }

    private fun loadProfile() {
        binding.bannerImagelarge?.let {
            Glide.with(this)
                .load(LifeGlideExtension.getBannerModel())
                .profileBannerOptions(LifeGlideExtension.getBannerModel())
                .into(it)
        }

        Glide.with(requireActivity())
            .load(LifeGlideExtension.getUserModel())
            .userProfileOptions(LifeGlideExtension.getUserModel(), requireContext())
            .into(binding.userImage)
    }

    fun colorButtons() {
        binding.history.elevatedAccentColor()
        binding.lastAdded.elevatedAccentColor()
        binding.topPlayed.elevatedAccentColor()
        binding.actionShuffle.elevatedAccentColor()
    }

    private fun checkForMargins() {
        if (mainActivity.isBottomNavVisible) {
            binding.recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dip(R.dimen.bottom_nav_height)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_layout_type)
        menu.removeItem(R.id.action_sort_order)
        menu.findItem(R.id.action_settings).setShowAsAction(SHOW_AS_ACTION_IF_ROOM)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            binding.toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar),
        )
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    fun setSharedAxisXTransitions() {
        exitTransition =
            MaterialSharedAxis(MaterialSharedAxis.X, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    private fun setSharedAxisYTransitions() {
        exitTransition =
            MaterialSharedAxis(MaterialSharedAxis.Y, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    private fun loadSuggestions(songs: List<Song>) {
        if (!PreferenceUtil.homeSuggestions) {
            binding.suggestions.root.isVisible = false
            return
        }

        if (songs.isEmpty()) {
            showEmptySuggestionsState()
            return
        }
        // 正常加载有歌曲的情况
        loadSuggestionsWithSongs(songs)
    }

    private fun showEmptySuggestionsState() {
        val images =
            listOf(
                binding.suggestions.image1,
                binding.suggestions.image2,
                binding.suggestions.image3,
                binding.suggestions.image4,
                binding.suggestions.image5,
                binding.suggestions.image6,
                binding.suggestions.image7,
                binding.suggestions.image8,
            )
        val color = accentColor()
        // 隐藏所有图片或显示占位符
        images.forEach { imageView ->
            Glide.with(this).load(R.drawable.default_audio_art).into(imageView)
        }
        binding.suggestions.message.apply {
            setTextColor(color)
            setOnClickListener(null)
            text = "Nothing"
        }
        //    binding.suggestions.message.text = "Nothing"
        //    binding.suggestions.message.setOnClickListener(null)
    }

    private fun loadSuggestionsWithSongs(songs: List<Song>) {
        val images =
            listOf(
                binding.suggestions.image1,
                binding.suggestions.image2,
                binding.suggestions.image3,
                binding.suggestions.image4,
                binding.suggestions.image5,
                binding.suggestions.image6,
                binding.suggestions.image7,
                binding.suggestions.image8,
            )

        val color = accentColor()

        // 设置消息区域
        binding.suggestions.message.apply {
            setTextColor(color)
            setOnClickListener {
                it.isClickable = false
                it.postDelayed({ it.isClickable = true }, 500)
                val maxItems = minOf(songs.size, 8)
                MusicPlayerRemote.playNext(songs.subList(0, maxItems))
                if (!MusicPlayerRemote.isPlaying) {
                    MusicPlayerRemote.playNextSong()
                }
            }
        }

        binding.suggestions.card6.setCardBackgroundColor(ColorUtil.withAlpha(color, 0.12f))

        // 安全地加载歌曲（带边界检查）
        val maxItems = minOf(songs.size, images.size)

        for (index in 0 until maxItems) {
            val song = songs[index]
            val imageView = images[index]

            imageView.isVisible = true
            imageView.setOnClickListener {
                it.isClickable = false
                it.postDelayed({ it.isClickable = true }, 500)
                MusicPlayerRemote.playNext(song)
                if (!MusicPlayerRemote.isPlaying) {
                    MusicPlayerRemote.playNextSong()
                }
            }

            Glide.with(this)
                .load(LifeGlideExtension.getSongModel(song))
                .songCoverOptions(song)
                .into(imageView)
        }

        // 隐藏未使用的imageView
        for (i in maxItems until images.size) {
            images[i].isVisible = false
        }
    }

    companion object {

        const val TAG: String = "BannerHomeFragment"

        @JvmStatic
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings ->
                findNavController().navigate(R.id.settings_fragment, null, navOptions)

            R.id.action_import_playlist ->
                ImportPlaylistDialog().show(childFragmentManager, "ImportPlaylist")

            R.id.action_add_to_playlist ->
                CreatePlaylistDialog.create(emptyList())
                    .show(childFragmentManager, "ShowCreatePlaylistDialog")
        }
        return false
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.toolbar)
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
        libraryViewModel.forceReload(ReloadType.HomeSections)
        exitTransition = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
