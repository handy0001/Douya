/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.douya.timeline

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.douya.api.info.SizedImage
import me.zhanghai.android.douya.api.info.TimelineItem
import me.zhanghai.android.douya.api.info.VideoInfo
import me.zhanghai.android.douya.api.util.activityCompat
import me.zhanghai.android.douya.api.util.normalOrClosest
import me.zhanghai.android.douya.api.util.subtitleWithEntities
import me.zhanghai.android.douya.api.util.textWithEntities
import me.zhanghai.android.douya.api.util.textWithEntitiesAndParent
import me.zhanghai.android.douya.api.util.uriOrUrl
import me.zhanghai.android.douya.arch.EventLiveData
import me.zhanghai.android.douya.arch.ResumedLifecycleOwner
import me.zhanghai.android.douya.arch.mapDistinct
import me.zhanghai.android.douya.arch.valueCompat
import me.zhanghai.android.douya.databinding.TimelineItemLayoutBinding
import me.zhanghai.android.douya.link.UriHandler
import me.zhanghai.android.douya.ui.HorizontalImageAdapter
import me.zhanghai.android.douya.util.GutterItemDecoration
import me.zhanghai.android.douya.util.OnHorizontalScrollListener
import me.zhanghai.android.douya.util.dpToDimensionPixelSize
import me.zhanghai.android.douya.util.fadeInUnsafe
import me.zhanghai.android.douya.util.fadeOutUnsafe
import me.zhanghai.android.douya.util.layoutInflater
import me.zhanghai.android.douya.util.takeIfNotEmpty
import org.threeten.bp.ZonedDateTime

class TimelineItemLayout : ConstraintLayout {
    companion object {
        private const val IMAGE_RECYCLER_GUTTER_SIZE_DP = 2
    }

    private val lifecycleOwner = ResumedLifecycleOwner()

    private val binding = TimelineItemLayoutBinding.inflate(context.layoutInflater, this, true)

    private val imageAdapter = HorizontalImageAdapter()

    private val viewModel = ViewModel()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        binding.imageRecycler.apply {
            layoutManager = LinearLayoutManager(null, RecyclerView.HORIZONTAL, false)
            val gutterSize = context.dpToDimensionPixelSize(IMAGE_RECYCLER_GUTTER_SIZE_DP)
            addItemDecoration(GutterItemDecoration(gutterSize))
            adapter = imageAdapter
            addOnScrollListener(object : OnHorizontalScrollListener() {
                private var scrollingLeft = true

                override fun onScrolledLeft() {
                    if (imageAdapter.itemCount == 0 || scrollingLeft) {
                        return
                    }
                    scrollingLeft = true
                    binding.imageRecyclerDescriptionScrim.fadeInUnsafe()
                    binding.imageRecyclerDescriptionText.fadeInUnsafe()
                }

                override fun onScrolledRight() {
                    if (imageAdapter.itemCount == 0 || !scrollingLeft) {
                        return
                    }
                    scrollingLeft = false
                    binding.imageRecyclerDescriptionScrim.fadeOutUnsafe()
                    binding.imageRecyclerDescriptionText.fadeOutUnsafe()
                }
            })
        }

        binding.lifecycleOwner = lifecycleOwner
        binding.viewModel = viewModel
        viewModel.imageList.observe(lifecycleOwner) { imageAdapter.submitList(it) }
        viewModel.openUriEvent.observe(lifecycleOwner) { UriHandler.open(it, context) }
    }

    fun setTimelineItem(timelineItem: TimelineItem?) {
        viewModel.setTimelineItem(timelineItem)
        binding.executePendingBindings()
    }

    class ViewModel {
        data class State(
            val avatarUrl: String,
            val author: String,
            val authorUri: String,
            val time: ZonedDateTime?,
            val activity: String,
            val hasText: Boolean,
            val text: (Context) -> CharSequence,
            val hasReshared: Boolean,
            val resharedDeleted: Boolean,
            val resharedAuthor: String,
            val resharedActivity: String,
            val resharedText: CharSequence,
            val resharedUri: String,
            val hasCard: Boolean,
            val cardOwner: String,
            val cardActivity: String,
            val cardImageUrl: String,
            val cardTitle: String,
            val cardText: CharSequence,
            val cardUri: String,
            val image: SizedImage?,
            val imageList: List<SizedImage>,
            val video: VideoInfo?
        )

        private val state = MutableLiveData(
            State(
                avatarUrl = "",
                author = "",
                authorUri = "",
                time = null,
                activity = "",
                hasText = false,
                text = { "" },
                hasReshared = false,
                resharedDeleted = false,
                resharedAuthor = "",
                resharedActivity = "",
                resharedText = "",
                resharedUri = "",
                hasCard = false,
                cardOwner = "",
                cardActivity = "",
                cardImageUrl = "",
                cardTitle = "",
                cardText = "",
                cardUri = "",
                image = null,
                imageList = emptyList(),
                video = null
            )
        )
        val avatarUrl = state.mapDistinct { it.avatarUrl }
        val author = state.mapDistinct { it.author }
        val time = state.mapDistinct { it.time }
        val activity = state.mapDistinct { it.activity }
        val hasText = state.mapDistinct { it.hasText }
        val text = state.mapDistinct { it.text }
        val hasReshared = state.mapDistinct { it.hasReshared }
        val resharedDeleted = state.mapDistinct { it.resharedDeleted }
        val resharedAuthor = state.mapDistinct { it.resharedAuthor }
        val resharedActivity = state.mapDistinct { it.resharedActivity }
        val resharedText = state.mapDistinct { it.resharedText }
        val hasCard = state.mapDistinct { it.hasCard }
        val cardOwner = state.mapDistinct { it.cardOwner }
        val cardActivity = state.mapDistinct { it.cardActivity }
        val cardImageUrl = state.mapDistinct { it.cardImageUrl }
        val cardTitle = state.mapDistinct { it.cardTitle }
        val cardText = state.mapDistinct { it.cardText }
        val image = state.mapDistinct { it.image }
        val imageList = state.mapDistinct { it.imageList }
        val video = state.mapDistinct { it.video }

        private val _openUriEvent = EventLiveData<String>()
        val openUriEvent: LiveData<String> = _openUriEvent

        fun setTimelineItem(timelineItem: TimelineItem?) {
            val status = timelineItem?.content?.status
            state.value = if (status != null) {
                val contentStatus = status.resharedStatus ?: status
                val card = contentStatus.card
                val images = card?.imageBlock?.images?.takeIfNotEmpty()?.map { it.image!! }
                    ?: contentStatus.images
                val video = contentStatus.videoInfo
                State(
                    avatarUrl = status.author?.avatar ?: "",
                    author = status.author?.name ?: "",
                    authorUri = status.author?.uriOrUrl ?: "",
                    activity = status.activityCompat,
                    time = status.createTime,
                    hasText = status.text.isNotEmpty(),
                    text = status::textWithEntitiesAndParent,
                    hasReshared = status.resharedStatus != null,
                    resharedDeleted = status.resharedStatus?.deleted ?: false,
                    resharedAuthor = status.resharedStatus?.author?.name ?: "",
                    resharedActivity = status.resharedStatus?.activityCompat ?: "",
                    resharedText = status.resharedStatus?.textWithEntities ?: "",
                    resharedUri = status.resharedStatus?.uri ?: "",
                    hasCard = card != null,
                    cardOwner = card?.ownerName ?: "",
                    cardActivity = card?.activity ?: "",
                    cardImageUrl = card?.image?.normalOrClosest?.url?.takeIf { images.isEmpty() }
                        ?: "",
                    cardTitle = card?.title ?: "",
                    cardText = card?.subtitleWithEntities?.takeIfNotEmpty() ?: card?.url ?: "",
                    cardUri = card?.uriOrUrl ?: "",
                    image = images.singleOrNull()?.takeIf { video == null },
                    imageList = images.takeIf { video == null && it.size > 1 } ?: emptyList(),
                    video = video
                )
            } else {
                val images = timelineItem?.content?.photos?.ifEmpty {
                    timelineItem.content.photo?.let { listOf(it) }
                }?.map { it.image!! } ?: emptyList()
                val video = timelineItem?.content?.videoInfo
                State(
                    avatarUrl = timelineItem?.owner?.avatar ?: "",
                    author = timelineItem?.owner?.name ?: "",
                    authorUri = timelineItem?.owner?.uriOrUrl ?: "",
                    activity = timelineItem?.action ?: "",
                    time = null,
                    hasText = false,
                    text = { "" },
                    hasReshared = false,
                    resharedDeleted = false,
                    resharedAuthor = "",
                    resharedActivity = "",
                    resharedText = "",
                    resharedUri = "",
                    hasCard = true,
                    cardOwner = "",
                    cardActivity = "",
                    cardImageUrl = images.singleOrNull()?.normalOrClosest?.url ?: "",
                    cardTitle = timelineItem?.content?.title ?: "",
                    cardText = timelineItem?.content?.abstractString ?: "",
                    cardUri = timelineItem?.content?.uriOrUrl ?: "",
                    image = null,
                    imageList = images.takeIf { video == null && it.size > 1 } ?: emptyList(),
                    video = video
                )
            }
        }

        fun openAuthor() {
            val authorUri = state.valueCompat.authorUri
            if (authorUri.isNotEmpty()) {
                _openUriEvent.value = authorUri
            }
        }

        fun openReshared() {
            val resharedUri = state.valueCompat.resharedUri
            if (resharedUri.isNotEmpty()) {
                _openUriEvent.value = resharedUri
            }
        }

        fun openCard() {
            val cardUri = state.valueCompat.cardUri
            if (cardUri.isNotEmpty()) {
                _openUriEvent.value = cardUri
            }
        }
    }
}