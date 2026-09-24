package ca.urbanlight.imagescale.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class JobProgress(
    val total: Int,
    val currentIndex: Int = 0,        // 1-based iC once the first image starts
    val currentFileName: String = "",
    val filePercent: Int = 0,         // stage-based 0..100 for the current file
) {
    /** Whole-batch 0..100: finished images plus the current one's stage progress. */
    val overallPercent: Int
        get() {
            if (total <= 0 || currentIndex <= 0) return 0
            val done = (currentIndex - 1).coerceIn(0, total) * 100 + filePercent
            return (done / total).coerceIn(0, 100)
        }
}

/**
 * Pure job state machine. The processing loop calls [awaitRunnable] between stages;
 * it suspends while paused or awaiting a cancel confirmation and reports whether
 * the job may continue.
 */
class JobController(val total: Int) {

    enum class State { Running, Paused, AwaitingCancelConfirm, Cancelled, Done }

    private val _state = MutableStateFlow(State.Running)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _progress = MutableStateFlow(JobProgress(total))
    val progress: StateFlow<JobProgress> = _progress.asStateFlow()

    /** State to restore when the user chooses "Continue Conversion". */
    private var stateBeforeConfirm: State = State.Running

    fun pause() {
        _state.update { if (it == State.Running) State.Paused else it }
    }

    fun resume() {
        _state.update { if (it == State.Paused) State.Running else it }
    }

    /** Stop pressed or notification dismissed: hold the job while the user decides. */
    fun requestCancel() {
        _state.update {
            if (it == State.Running || it == State.Paused) {
                stateBeforeConfirm = it
                State.AwaitingCancelConfirm
            } else it
        }
    }

    /** "Continue Conversion": back to whatever the job was doing before. */
    fun continueConversion() {
        _state.update { if (it == State.AwaitingCancelConfirm) stateBeforeConfirm else it }
    }

    /** "Stop Image Conversion": terminal. */
    fun confirmCancel() {
        _state.update { if (it == State.Done) it else State.Cancelled }
    }

    fun markDone() {
        _state.update { if (it == State.Cancelled) it else State.Done }
    }

    fun startImage(index: Int, fileName: String) {
        _progress.update { it.copy(currentIndex = index, currentFileName = fileName, filePercent = 0) }
    }

    fun fileProgress(percent: Int) {
        _progress.update { it.copy(filePercent = percent.coerceIn(0, 100)) }
    }

    /** Suspends while the job is held; true = keep going, false = cancelled. */
    suspend fun awaitRunnable(): Boolean {
        val settled = state.first {
            it == State.Running || it == State.Cancelled || it == State.Done
        }
        return settled == State.Running
    }
}
