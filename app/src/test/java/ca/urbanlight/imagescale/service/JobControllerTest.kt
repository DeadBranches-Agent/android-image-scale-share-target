package ca.urbanlight.imagescale.service

import ca.urbanlight.imagescale.service.JobController.State
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobControllerTest {

    @Test
    fun `gate passes immediately while running`() = runTest {
        assertTrue(JobController(3).awaitRunnable())
    }

    @Test
    fun `pause suspends the gate until resume`() = runTest {
        val controller = JobController(3)
        controller.pause()
        val gate: Deferred<Boolean> = async { controller.awaitRunnable() }
        testScheduler.runCurrent()
        assertFalse("gate must suspend while paused", gate.isCompleted)
        controller.resume()
        assertTrue(gate.await())
    }

    @Test
    fun `stop request holds the job and continue restores the prior state`() = runTest {
        val controller = JobController(3)
        controller.requestCancel()
        assertEquals(State.AwaitingCancelConfirm, controller.state.value)
        val gate = async { controller.awaitRunnable() }
        testScheduler.runCurrent()
        assertFalse(gate.isCompleted)
        controller.continueConversion()
        assertEquals(State.Running, controller.state.value)
        assertTrue(gate.await())
    }

    @Test
    fun `continue after stop while paused returns to paused`() = runTest {
        val controller = JobController(3)
        controller.pause()
        controller.requestCancel()
        controller.continueConversion()
        assertEquals(State.Paused, controller.state.value)
    }

    @Test
    fun `confirmed cancel releases the gate with false and is terminal`() = runTest {
        val controller = JobController(3)
        controller.requestCancel()
        val gate = async { controller.awaitRunnable() }
        controller.confirmCancel()
        assertFalse(gate.await())
        controller.resume()
        controller.continueConversion()
        assertEquals(State.Cancelled, controller.state.value)
        controller.markDone()
        assertEquals("done must not overwrite cancelled", State.Cancelled, controller.state.value)
    }

    @Test
    fun `progress tracks current index and file`() = runTest {
        val controller = JobController(5)
        controller.startImage(2, "b.png")
        controller.fileProgress(150)
        val progress = controller.progress.value
        assertEquals(5, progress.total)
        assertEquals(2, progress.currentIndex)
        assertEquals("b.png", progress.currentFileName)
        assertEquals("file percent clamped", 100, progress.filePercent)
    }
}
