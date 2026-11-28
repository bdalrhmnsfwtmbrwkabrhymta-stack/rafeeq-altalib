package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.download.HawasApplication
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HawasApplication::class)
class StartupCrashDiagnosticTest {

    @Test
    fun coldStartMainActivityDoesNotCrash() {
        val app = ApplicationProvider.getApplicationContext<HawasApplication>()
        assertNotNull(app)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()

        val activity = controller.get()
        assertNotNull(activity)

        // Advance main looper through SplashScreen (1800ms) and into RafeeqSetupFlowScreen
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        controller.pause().stop().destroy()
    }
}
