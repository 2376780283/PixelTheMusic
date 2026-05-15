package zzh.lifeplayer.music.fragments.playlists

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialSharedAxis
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.adapter.song.OrderablePlaylistSongAdapter
import zzh.lifeplayer.music.databinding.FragmentPlaylistDetailNewBinding
import zzh.lifeplayer.music.db.PlaylistWithSongs
import zzh.lifeplayer.music.db.toSongs
import zzh.lifeplayer.music.extensions.accentColor
import zzh.lifeplayer.music.extensions.elevatedAccentColor
import zzh.lifeplayer.music.extensions.surfaceColor
import zzh.lifeplayer.music.fragments.base.AbsMainActivityFragment
import zzh.lifeplayer.music.fragments.search.clearText
import zzh.lifeplayer.music.glide.LifeGlideExtension.playlistOptions
import zzh.lifeplayer.music.glide.playlistPreview.PlaylistPreview
import zzh.lifeplayer.music.helper.MusicPlayerRemote
import zzh.lifeplayer.music.helper.menu.PlaylistMenuHelper
import zzh.lifeplayer.music.model.Song
import zzh.lifeplayer.music.util.MusicUtil
import zzh.lifeplayer.music.util.PreferenceUtil
import zzh.lifeplayer.music.util.ThemedFastScroller

class PlaylistDetailsFragment : AbsMainActivityFragment(R.layout.fragment_playlist_detail_new) {
    private val arguments by navArgs<PlaylistDetailsFragmentArgs>()
    private val viewModel by
        viewModel<PlaylistDetailsViewModel> { parametersOf(arguments.extraPlaylistId) }

    private var _binding: FragmentPlaylistDetailNewBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var playlist: PlaylistWithSongs
    private lateinit var playlistSongAdapter: OrderablePlaylistSongAdapter

    private val _searchFlow = MutableSharedFlow<CharSequence?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition =
            MaterialContainerTransform(requireContext(), true).apply {
                drawingViewId = R.id.fragment_container
                scrimColor = Color.TRANSPARENT
                setAllContainerColors(surfaceColor())
                setPathMotion(MaterialArcMotion())
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlaylistDetailNewBinding.bind(view)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).addTarget(view)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = null
        //        binding.container.transitionName = playlist.playlistEntity.playlistName

        setUpRecyclerView()
        setUpSearch()
        setupButtons()
        viewModel.getPlaylist().observe(viewLifecycleOwner) { playlistWithSongs ->
            playlist = playlistWithSongs
            Glide.with(this)
                .load(PlaylistPreview(playlistWithSongs))
                .playlistOptions()
                .into(binding.image)
            binding.title.text = playlist.playlistEntity.playlistName
            binding.subtitle.text =
                MusicUtil.getPlaylistInfoString(requireContext(), playlist.songs.toSongs())
            binding.collapsingAppBarLayout.title = playlist.playlistEntity.playlistName
        }
        viewModel.getSongs().observe(viewLifecycleOwner) { songs(it.toSongs()) }
        viewModel.playlistExists().observe(viewLifecycleOwner) {
            if (!it) {
                findNavController().navigateUp()
            }
        }
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())
    }

    private fun setupButtons() {
        binding.playButton.apply {
            setOnClickListener { MusicPlayerRemote.openQueue(playlistSongAdapter.dataSet, 0, true) }
            accentColor()
        }
        binding.shuffleButton.apply {
            setOnClickListener {
                MusicPlayerRemote.openAndShuffleQueue(playlistSongAdapter.dataSet, true)
            }
            elevatedAccentColor()
        }
    }

    private fun setUpSearch() {
        if (!PreferenceUtil.enableSearchPlaylist) {
            binding.playlistSearchView.visibility = View.GONE
        } else {
            binding.playlistSearchView.visibility = View.VISIBLE
        }
        binding.playlistSearchView.addTextChangedListener { text ->
            lifecycleScope.launch {
                _searchFlow.emit(text)
                binding.clearSearch.visibility =
                    if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
        binding.clearSearch.setOnClickListener {
            lifecycleScope.launch {
                _searchFlow.emit(null)
                binding.playlistSearchView.clearText()
                binding.clearSearch.visibility = View.GONE
            }
        }
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        lifecycleScope.launch {
            _searchFlow.debounce(300).collect { text -> playlistSongAdapter.onFilter(text) }
        }
    }

    private fun setUpRecyclerView() {
        playlistSongAdapter =
            OrderablePlaylistSongAdapter(
                arguments.extraPlaylistId,
                requireActivity(),
                ArrayList(),
                R.layout.item_queue,
            )

        val dragDropManager = RecyclerViewDragDropManager()

        val wrappedAdapter: RecyclerView.Adapter<*> =
            dragDropManager.createWrappedAdapter(playlistSongAdapter)

        binding.recyclerView.apply {
            adapter = wrappedAdapter
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DraggableItemAnimator()
            dragDropManager.attachRecyclerView(this)
            ThemedFastScroller.create(this)
        }
        playlistSongAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    checkIsEmpty()
                }
            }
        )
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_playlist_detail, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return PlaylistMenuHelper.handleMenuClick(requireActivity(), playlist, item)
    }

    private fun checkIsEmpty() {
        if (_binding != null) {
            if (playlistSongAdapter.itemCount != 0) {
                binding.empty.isVisible = false
            } else {
                binding.empty.isVisible = true
                if (playlistSongAdapter.hasSongs()) {
                    binding.emptyText.text = getString(R.string.no_search_results)
                } else {
                    binding.emptyText.text = getString(R.string.no_songs)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onPause() {
        binding.playlistSearchView.clearText()
        playlistSongAdapter.saveSongs(playlist.playlistEntity)
        super.onPause()
    }

    private fun showEmptyView() {
        binding.empty.isVisible = true
        binding.emptyText.isVisible = true
    }

    fun songs(songs: List<Song>) {
        binding.progressIndicator.hide()
        if (songs.isNotEmpty()) {
            playlistSongAdapter.swapDataSet(songs)
        } else {
            showEmptyView()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
