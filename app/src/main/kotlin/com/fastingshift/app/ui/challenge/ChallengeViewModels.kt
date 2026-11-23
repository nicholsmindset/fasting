package com.fastingshift.app.ui.challenge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.data.repo.ChallengeRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.challenge.ChallengeProgress
import com.fastingshift.domain.challenge.ChallengeStatus
import com.fastingshift.domain.challenge.ChallengeTemplate
import com.fastingshift.domain.challenge.Challenges
import com.fastingshift.domain.challenge.InviteResult
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A challenge with its locally computed progress. */
data class ChallengeItem(val challenge: Challenge, val progress: ChallengeProgress) {
    val left: Boolean get() = challenge.leftAt != null
    val current: Boolean get() = !left && progress.status != ChallengeStatus.FINISHED
}

/** Ticks every minute so day states roll over at midnight without other changes. */
private fun minutes(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(60_000)
    }
}

/** Challenges are for people the safety screening allows to fast (never education-only, SR-1). */
private fun ScreeningRepository.allowsChallenges(): Flow<Boolean> =
    observeLatest().map { it != null && it.gateResult != GateResult.BLOCK }

private fun itemsFlow(
    challenges: Flow<List<Challenge>>,
    fasts: FastRepository,
    settings: SettingsRepository,
    clock: Clock,
): Flow<List<ChallengeItem>> = combine(challenges, fasts.observeAll(), settings.settings, minutes(clock)) { list, all, s, now ->
    val zone = ZoneId.systemDefault()
    list.map { c -> ChallengeItem(c, Challenges.progress(c, all, zone, now, s.hijriOffsetDays)) }
}

data class ChallengeListState(val current: List<ChallengeItem>, val past: List<ChallengeItem>, val allowed: Boolean)

@HiltViewModel
class ChallengeListViewModel @Inject constructor(
    challenges: ChallengeRepository,
    fasts: FastRepository,
    settings: SettingsRepository,
    screening: ScreeningRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<ChallengeListState?> =
        combine(itemsFlow(challenges.observeAll(), fasts, settings, clock), screening.allowsChallenges()) { items, allowed ->
            ChallengeListState(
                current = items.filter { it.current }.sortedBy { it.challenge.startDate },
                past = items.filterNot { it.current },
                allowed = allowed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** When a new challenge starts. */
enum class StartOption { TODAY, TOMORROW, NEXT_MONDAY }

fun StartOption.date(today: LocalDate): LocalDate = when (this) {
    StartOption.TODAY -> today
    StartOption.TOMORROW -> today.plusDays(1)
    StartOption.NEXT_MONDAY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
}

@HiltViewModel
class CreateChallengeViewModel @Inject constructor(
    private val challenges: ChallengeRepository,
    screening: ScreeningRepository,
    private val clock: Clock,
) : ViewModel() {
    val allowed: StateFlow<Boolean?> = screening.allowsChallenges().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    private var creating = false

    /** One challenge per tap: a quick double tap must not create two. */
    fun create(template: ChallengeTemplate, start: StartOption, name: String, onCreated: (Long) -> Unit) {
        if (creating || allowed.value != true) return
        creating = true
        viewModelScope.launch {
            try {
                val id = challenges.join(template.create(start.date(today()), clock.instant(), name))
                onCreated(id)
            } finally {
                creating = false
            }
        }
    }
}

@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    private val challenges: ChallengeRepository,
    fasts: FastRepository,
    settings: SettingsRepository,
    savedState: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("challengeId") ?: 0L

    /** null while loading; a missing challenge (deleted) closes the screen. */
    val item: StateFlow<ChallengeItem?> =
        itemsFlow(challenges.observeAll().map { l -> l.filter { it.id == id } }, fasts, settings, clock)
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun leave() = viewModelScope.launch {
        challenges.get(id)?.let { challenges.update(it.copy(leftAt = clock.instant())) }
    }

    fun remove(then: () -> Unit) = viewModelScope.launch {
        challenges.delete(id)
        then()
    }
}

sealed interface JoinState {
    data object Loading : JoinState
    data object Invalid : JoinState
    data object NewerVersion : JoinState
    data object NotAllowed : JoinState
    data class Ready(val invite: ChallengeInvite, val alreadyJoinedId: Long?) : JoinState
}

@HiltViewModel
class JoinChallengeViewModel @Inject constructor(
    private val challenges: ChallengeRepository,
    screening: ScreeningRepository,
    savedState: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {
    private val code: String = savedState.get<String>("code").orEmpty()
    private val joining = MutableStateFlow(false)

    val state: StateFlow<JoinState> = combine(challenges.observeAll(), screening.allowsChallenges()) { list, allowed ->
        when (val r = ChallengeLinks.decode(code)) {
            InviteResult.Invalid -> JoinState.Invalid
            InviteResult.NewerVersion -> JoinState.NewerVersion
            is InviteResult.Valid -> if (!allowed) {
                JoinState.NotAllowed
            } else {
                val same = list.firstOrNull { it.leftAt == null && ChallengeInvite.of(it) == r.invite }
                JoinState.Ready(r.invite, same?.id)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JoinState.Loading)

    fun join(invite: ChallengeInvite, onJoined: (Long) -> Unit) {
        if (joining.value) return
        joining.value = true
        viewModelScope.launch { onJoined(challenges.join(invite.toChallenge(clock.instant()))) }
    }
}
