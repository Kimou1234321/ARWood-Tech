package com.algerianwood.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LocalUser(
    val firstName: String,
    val familyName: String,
    val username: String,
    val password: String
)

class LocalUserRepository(private val context: Context) {
    private val usersFile: File
        get() = File(context.filesDir, "users.json")

    @Synchronized
    fun createUser(user: LocalUser): Result<Unit> {
        val users = loadUsersMutable()
        if (users.any { it.username.equals(user.username, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Username already exists"))
        }
        users.add(user)
        saveUsers(users)
        return Result.success(Unit)
    }

    @Synchronized
    fun validateCredentials(username: String, password: String): Boolean {
        if (username.isBlank() || password.isBlank()) return false
        return loadUsersMutable().any {
            it.username.equals(username, ignoreCase = true) && it.password == password
        }
    }

    @Synchronized
    fun getByUsername(username: String): LocalUser? {
        return loadUsersMutable().firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    @Synchronized
    fun listUsers(): List<LocalUser> = loadUsersMutable()

    private fun loadUsersMutable(): MutableList<LocalUser> {
        if (!usersFile.exists()) return mutableListOf()
        val raw = usersFile.readText()
        if (raw.isBlank()) return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<LocalUser>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
                LocalUser(
                    firstName = obj.optString("firstName"),
                    familyName = obj.optString("familyName"),
                    username = obj.optString("username"),
                    password = obj.optString("password")
                )
            )
        }
        return out
    }

    private fun saveUsers(users: List<LocalUser>) {
        val arr = JSONArray()
        users.forEach { user ->
            arr.put(
                JSONObject()
                    .put("firstName", user.firstName)
                    .put("familyName", user.familyName)
                    .put("username", user.username)
                    .put("password", user.password)
            )
        }
        usersFile.writeText(arr.toString())
    }
}
