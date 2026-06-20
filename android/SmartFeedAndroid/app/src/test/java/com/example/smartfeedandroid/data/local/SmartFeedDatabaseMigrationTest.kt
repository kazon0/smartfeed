package com.example.smartfeedandroid.data.local

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartFeedDatabaseMigrationTest {
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE conversations (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    status TEXT NOT NULL,
                    storedChunks INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    messagesJson TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
        connection.prepareStatement(
            """
            INSERT INTO conversations (
                id, title, url, summary, status,
                storedChunks, updatedAtMillis, messagesJson
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, "legacy-conversation")
            statement.setString(2, "旧文章")
            statement.setString(3, "https://example.com/legacy")
            statement.setString(4, "旧摘要")
            statement.setString(5, "saved")
            statement.setInt(6, 3)
            statement.setLong(7, 456L)
            statement.setString(8, "[]")
            statement.executeUpdate()
        }
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrateVersionOneToThreeCreatesMessagesAndBackfillsMetadata() {
        executeStatements(SmartFeedDatabase.MIGRATION_1_2_STATEMENTS)
        executeStatements(SmartFeedDatabase.MIGRATION_2_3_STATEMENTS)

        assertTrue(tableColumns("messages").containsAll(MESSAGE_COLUMNS))
        assertTrue(tableColumns("conversations").containsAll(VERSION_THREE_COLUMNS))

        connection.prepareStatement(
            "SELECT sourceUrl, topic, createdAtMillis, updatedAtMillis FROM conversations WHERE id = ?"
        ).use { statement ->
            statement.setString(1, "legacy-conversation")
            statement.executeQuery().use { result ->
                assertTrue(result.next())
                assertEquals("https://example.com/legacy", result.getString("sourceUrl"))
                assertEquals("", result.getString("topic"))
                assertEquals(456L, result.getLong("createdAtMillis"))
                assertEquals(456L, result.getLong("updatedAtMillis"))
            }
        }
    }

    private fun executeStatements(statements: List<String>) {
        connection.createStatement().use { statement ->
            statements.forEach(statement::execute)
        }
    }

    private fun tableColumns(table: String): Set<String> {
        return connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(`$table`)").use { result ->
                buildSet {
                    while (result.next()) {
                        add(result.getString("name"))
                    }
                }
            }
        }
    }

    private companion object {
        val MESSAGE_COLUMNS = setOf(
            "id",
            "conversationId",
            "messageIndex",
            "type",
            "text",
            "responseJson"
        )

        val VERSION_THREE_COLUMNS = setOf(
            "sourceUrl",
            "topic",
            "createdAtMillis"
        )
    }
}
