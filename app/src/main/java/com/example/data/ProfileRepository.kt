package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfileRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("macric_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILES = "saved_profiles"
        private const val KEY_CONNECTED_GAME_ID = "connected_game_id"
        private const val KEY_FAIR_PLAY_ACCEPTED = "fair_play_accepted"
        private const val KEY_CUSTOM_GAMES = "custom_games"
    }

    private val defaultGames = listOf(
        GameModel("game_ff", "Free Fire MAX", "com.dts.freefiremax", "Battle Royale", iconEmoji = "🔥"),
        GameModel("game_pubg", "PUBG Mobile / BGMI", "com.tencent.ig", "Tactical Shooter", iconEmoji = "🎯"),
        GameModel("game_codm", "Call of Duty: Mobile", "com.activision.callofduty.shooter", "FPS / Action", iconEmoji = "⚡"),
        GameModel("game_standoff", "Standoff 2", "com.axlebolt.standoff2", "Competitive FPS", iconEmoji = "💥"),
        GameModel("game_arena", "Arena Breakout", "com.proximabeta.mf.uamo", "Tactical Extraction", iconEmoji = "🛡️"),
        GameModel("game_apex", "Apex Legends Mobile (Custom)", "com.ea.gp.apexlegendsmobilefps", "Hero Shooter", iconEmoji = "🚀")
    )

    fun isFairPlayAccepted(): Boolean = prefs.getBoolean(KEY_FAIR_PLAY_ACCEPTED, false)

    fun setFairPlayAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_FAIR_PLAY_ACCEPTED, accepted).apply()
    }

    fun getConnectedGameId(): String? = prefs.getString(KEY_CONNECTED_GAME_ID, null)

    fun setConnectedGameId(gameId: String?) {
        prefs.edit().putString(KEY_CONNECTED_GAME_ID, gameId).apply()
    }

    fun getAllGames(): List<GameModel> {
        val customGamesJson = prefs.getString(KEY_CUSTOM_GAMES, null)
        val customGames = mutableListOf<GameModel>()
        if (!customGamesJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(customGamesJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    customGames.add(
                        GameModel(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            packageName = obj.getString("packageName"),
                            category = obj.optString("category", "Custom App"),
                            isCustom = true,
                            iconEmoji = obj.optString("iconEmoji", "🕹️")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val connectedId = getConnectedGameId()
        return (defaultGames + customGames).map { game ->
            game.copy(isConnected = game.id == connectedId)
        }
    }

    fun addCustomGame(name: String, packageName: String, emoji: String): GameModel {
        val newGame = GameModel(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = name,
            packageName = packageName,
            category = "Custom App",
            isCustom = true,
            iconEmoji = emoji.ifBlank { "🕹️" }
        )
        val current = getAllGames().filter { it.isCustom }.toMutableList()
        current.add(newGame)

        val array = JSONArray()
        current.forEach { game ->
            val obj = JSONObject().apply {
                put("id", game.id)
                put("name", game.name)
                put("packageName", game.packageName)
                put("category", game.category)
                put("iconEmoji", game.iconEmoji)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_GAMES, array.toString()).apply()
        return newGame
    }

    fun getProfiles(): List<GameProfile> {
        val json = prefs.getString(KEY_PROFILES, null)
        if (json.isNullOrEmpty()) {
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            return defaults
        }
        return try {
            parseProfilesJson(json)
        } catch (e: Exception) {
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            defaults
        }
    }

    fun saveProfiles(profiles: List<GameProfile>) {
        val json = serializeProfilesToJson(profiles)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun saveProfile(profile: GameProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile.copy(updatedAt = System.currentTimeMillis())
        } else {
            current.add(profile.copy(updatedAt = System.currentTimeMillis()))
        }
        saveProfiles(current)
    }

    fun deleteProfile(profileId: String) {
        val current = getProfiles().filter { it.id != profileId }
        saveProfiles(current)
    }

    fun duplicateProfile(profileId: String): GameProfile? {
        val target = getProfiles().find { it.id == profileId } ?: return null
        val copy = target.copy(
            id = "prof_" + UUID.randomUUID().toString().take(8),
            name = "${target.name} (Copy)",
            updatedAt = System.currentTimeMillis()
        )
        saveProfile(copy)
        return copy
    }

    fun exportProfileJson(profile: GameProfile): String {
        return serializeProfile(profile).toString(2)
    }

    fun importProfileFromJson(jsonStr: String): GameProfile {
        val obj = JSONObject(jsonStr)
        val imported = parseSingleProfile(obj).copy(
            id = "imported_" + UUID.randomUUID().toString().take(8),
            name = obj.optString("name", "Imported Profile") + " [Imported]",
            updatedAt = System.currentTimeMillis()
        )
        saveProfile(imported)
        return imported
    }

    private fun createDefaultProfiles(): List<GameProfile> {
        return listOf(
            GameProfile(
                id = "prof_default_ff",
                name = "Free Fire Pro Setup",
                gameId = "game_ff",
                crosshair = CrosshairConfig(
                    style = CrosshairStyle.CROSS_GAP,
                    size = 32f,
                    thickness = 2.5f,
                    gap = 8f,
                    colorHex = "#9312FF"
                ),
                sensitivity = SensitivityConfig(
                    general = 95,
                    cameraLook = 90,
                    scope2x = 85,
                    scope4x = 75,
                    sniperScope = 60,
                    presetName = "Pro-Gamer"
                )
            ),
            GameProfile(
                id = "prof_default_pubg",
                name = "PUBG Precision ADS",
                gameId = "game_pubg",
                crosshair = CrosshairConfig(
                    style = CrosshairStyle.DOT,
                    size = 18f,
                    thickness = 3f,
                    gap = 0f,
                    colorHex = "#45B558"
                ),
                sensitivity = SensitivityConfig(
                    general = 60,
                    cameraLook = 55,
                    scope2x = 48,
                    scope4x = 35,
                    sniperScope = 25,
                    presetName = "Medium"
                )
            ),
            GameProfile(
                id = "prof_default_codm",
                name = "CODM Ranked Aggressive",
                gameId = "game_codm",
                crosshair = CrosshairConfig(
                    style = CrosshairStyle.CIRCLE_DOT,
                    size = 36f,
                    thickness = 2f,
                    gap = 12f,
                    colorHex = "#00E5FF"
                ),
                sensitivity = SensitivityConfig(
                    general = 80,
                    cameraLook = 75,
                    scope2x = 65,
                    scope4x = 55,
                    sniperScope = 45,
                    presetName = "High"
                )
            )
        )
    }

    private fun serializeProfilesToJson(profiles: List<GameProfile>): String {
        val array = JSONArray()
        profiles.forEach { array.put(serializeProfile(it)) }
        return array.toString()
    }

    private fun serializeProfile(p: GameProfile): JSONObject {
        val root = JSONObject()
        root.put("id", p.id)
        root.put("name", p.name)
        root.put("gameId", p.gameId)
        root.put("updatedAt", p.updatedAt)

        val ch = JSONObject().apply {
            put("enabled", p.crosshair.enabled)
            put("style", p.crosshair.style.name)
            put("size", p.crosshair.size.toDouble())
            put("thickness", p.crosshair.thickness.toDouble())
            put("gap", p.crosshair.gap.toDouble())
            put("opacity", p.crosshair.opacity.toDouble())
            put("colorHex", p.crosshair.colorHex)
            put("offsetX", p.crosshair.offsetX.toDouble())
            put("offsetY", p.crosshair.offsetY.toDouble())
            put("hotkey", p.crosshair.hotkey)
        }
        root.put("crosshair", ch)

        val buttons = JSONArray()
        p.fireButtons.forEach { fb ->
            val b = JSONObject().apply {
                put("id", fb.id)
                put("label", fb.label)
                put("xPercent", fb.xPercent.toDouble())
                put("yPercent", fb.yPercent.toDouble())
                put("size", fb.size.toDouble())
                put("opacity", fb.opacity.toDouble())
                put("colorHex", fb.colorHex)
                put("shape", fb.shape.name)
                put("mode", fb.mode.name)
                put("keyBinding", fb.keyBinding)
            }
            buttons.put(b)
        }
        root.put("fireButtons", buttons)

        val sens = JSONObject().apply {
            put("general", p.sensitivity.general)
            put("cameraLook", p.sensitivity.cameraLook)
            put("scope2x", p.sensitivity.scope2x)
            put("scope4x", p.sensitivity.scope4x)
            put("sniperScope", p.sensitivity.sniperScope)
            put("presetName", p.sensitivity.presetName)
        }
        root.put("sensitivity", sens)

        return root
    }

    private fun parseProfilesJson(jsonStr: String): List<GameProfile> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<GameProfile>()
        for (i in 0 until array.length()) {
            list.add(parseSingleProfile(array.getJSONObject(i)))
        }
        return list
    }

    private fun parseSingleProfile(obj: JSONObject): GameProfile {
        val id = obj.getString("id")
        val name = obj.getString("name")
        val gameId = obj.optString("gameId", "game_generic")
        val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

        val chObj = obj.optJSONObject("crosshair")
        val crosshair = if (chObj != null) {
            val styleStr = chObj.optString("style", "CROSS_GAP")
            val style = try { CrosshairStyle.valueOf(styleStr) } catch (e: Exception) { CrosshairStyle.CROSS_GAP }
            CrosshairConfig(
                enabled = chObj.optBoolean("enabled", true),
                style = style,
                size = chObj.optDouble("size", 32.0).toFloat(),
                thickness = chObj.optDouble("thickness", 3.0).toFloat(),
                gap = chObj.optDouble("gap", 8.0).toFloat(),
                opacity = chObj.optDouble("opacity", 0.95).toFloat(),
                colorHex = chObj.optString("colorHex", "#9312FF"),
                offsetX = chObj.optDouble("offsetX", 0.0).toFloat(),
                offsetY = chObj.optDouble("offsetY", 0.0).toFloat(),
                hotkey = chObj.optString("hotkey", "H")
            )
        } else {
            CrosshairConfig()
        }

        val fbArray = obj.optJSONArray("fireButtons")
        val fireButtons = mutableListOf<FireButtonConfig>()
        if (fbArray != null) {
            for (i in 0 until fbArray.length()) {
                val b = fbArray.getJSONObject(i)
                val shapeStr = b.optString("shape", "CIRCLE")
                val shape = try { FireButtonShape.valueOf(shapeStr) } catch (e: Exception) { FireButtonShape.CIRCLE }
                val modeStr = b.optString("mode", "TAP")
                val mode = try { FireMode.valueOf(modeStr) } catch (e: Exception) { FireMode.TAP }
                fireButtons.add(
                    FireButtonConfig(
                        id = b.optString("id", UUID.randomUUID().toString()),
                        label = b.optString("label", "FIRE"),
                        xPercent = b.optDouble("xPercent", 50.0).toFloat(),
                        yPercent = b.optDouble("yPercent", 50.0).toFloat(),
                        size = b.optDouble("size", 64.0).toFloat(),
                        opacity = b.optDouble("opacity", 0.85).toFloat(),
                        colorHex = b.optString("colorHex", "#9312FF"),
                        shape = shape,
                        mode = mode,
                        keyBinding = b.optString("keyBinding", "Left Click")
                    )
                )
            }
        }

        val sensObj = obj.optJSONObject("sensitivity")
        val sensitivity = if (sensObj != null) {
            SensitivityConfig(
                general = sensObj.optInt("general", 68),
                cameraLook = sensObj.optInt("cameraLook", 72),
                scope2x = sensObj.optInt("scope2x", 54),
                scope4x = sensObj.optInt("scope4x", 42),
                sniperScope = sensObj.optInt("sniperScope", 30),
                presetName = sensObj.optString("presetName", "Custom")
            )
        } else {
            SensitivityConfig()
        }

        return GameProfile(
            id = id,
            name = name,
            gameId = gameId,
            crosshair = crosshair,
            fireButtons = if (fireButtons.isEmpty()) GameProfile(id = "", name = "", gameId = "").fireButtons else fireButtons,
            sensitivity = sensitivity,
            updatedAt = updatedAt
        )
    }
}
