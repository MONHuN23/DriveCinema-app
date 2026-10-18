package com.drivecinema.app.core.auth

import com.drivecinema.app.MainActivity
import com.drivecinema.app.ui.player.PlayerActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Report F-11: the cached access token is written and read on different threads (IO / main / ExoPlayer loader),
 * so the field must stay volatile. A visibility race cannot be reproduced reliably in a test; this guards the fix.
 */
class CachedTokenVisibilityTest {

    private fun assertVolatile(type: Class<*>) {
        val field = type.getDeclaredField("cachedAccessToken")
        assertTrue("${type.simpleName}.cachedAccessToken must be @Volatile", Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun mainActivityTokenIsVolatile() = assertVolatile(MainActivity::class.java)

    @Test
    fun playerActivityTokenIsVolatile() = assertVolatile(PlayerActivity::class.java)
}
