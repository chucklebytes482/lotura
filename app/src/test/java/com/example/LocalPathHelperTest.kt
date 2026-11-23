package com.example

import com.example.data.LocalPathHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPathHelperTest {

    @Test
    fun driveRoot_usesEmailUnderGoogleDriveFolder() {
        assertEquals(
            "/storage/emulated/0/google drive/user@gmail.com",
            LocalPathHelper.driveRoot("user@gmail.com").path
        )
    }

    @Test
    fun githubRoot_usesUsernameAndRepo() {
        assertEquals(
            "/storage/emulated/0/github/chucklebytes482/lotura",
            LocalPathHelper.githubRoot("chucklebytes482", "lotura").path
        )
    }

    @Test
    fun illegalCharactersAreReplaced() {
        assertEquals("a_b_c", LocalPathHelper.sanitize("a/b:c"))
        assertEquals("_", LocalPathHelper.sanitize("   "))
    }
}
