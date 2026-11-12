package com.fitifysync.app

import com.fitifysync.app.data.parser.FitifyDatabaseParser
import java.io.File
import java.nio.file.Files
import org.junit.Test

class UnsupportedLegacySchemaTest {
    @Test(expected = UnsupportedOperationException::class)
    fun unverifiedLegacyLayoutIsRejectedInsteadOfInventingWorkouts() {
        val directory = Files.createTempDirectory("fitify-unknown-layout-").toFile()
        try {
            File(directory, "workout-history.db").writeText("Unverified legacy source")
            FitifyDatabaseParser.parseWorkouts(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
