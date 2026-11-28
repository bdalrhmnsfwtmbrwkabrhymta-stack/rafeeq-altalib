package com.app.games

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gamesHubDataStore: DataStore<Preferences> by preferencesDataStore(name = "educational_games_hub_prefs")

class GameShopRepository(private val context: Context) {

    private object Keys {
        val COINS = intPreferencesKey("games_coins")
        val UNLOCKED_STAGE = intPreferencesKey("games_unlocked_stage")
        val COMPLETED_STAGES = stringSetPreferencesKey("games_completed_stages")
        val OWNED_CHARACTERS = stringSetPreferencesKey("games_owned_characters")
        val EQUIPPED_CHARACTER = stringPreferencesKey("games_equipped_character")
        val MAGNET_LEVEL = intPreferencesKey("games_magnet_level")
        val SHIELD_LEVEL = intPreferencesKey("games_shield_level")
        val DETECTOR_LEVEL = intPreferencesKey("games_detector_level")
        val HIGH_SCORE = intPreferencesKey("games_high_score")
        val BEST_COMBO = intPreferencesKey("games_best_combo")
        val TOTAL_CORRECT = intPreferencesKey("games_total_correct")
        val TOTAL_BOSSES = intPreferencesKey("games_total_bosses")
    }

    val shopStateFlow: Flow<GameShopState> = context.gamesHubDataStore.data
        .catch { e ->
            if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val ownedIds = prefs[Keys.OWNED_CHARACTERS]
                ?.mapNotNull { id -> GameCharacterId.entries.firstOrNull { it.id == id } }
                ?.toSet()
                .orEmpty() + GameCharacterId.FAHIM_SMART

            val equipped = GameCharacterId.fromId(prefs[Keys.EQUIPPED_CHARACTER])
                .let { if (it in ownedIds) it else GameCharacterId.FAHIM_SMART }

            val completed = prefs[Keys.COMPLETED_STAGES]
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                .orEmpty()

            GameShopState(
                coins = prefs[Keys.COINS] ?: 350,
                unlockedStage = (prefs[Keys.UNLOCKED_STAGE] ?: 1).coerceIn(1, TOTAL_GAME_STAGES),
                completedStages = completed,
                ownedCharacters = ownedIds,
                equippedCharacter = equipped,
                magnetLevel = (prefs[Keys.MAGNET_LEVEL] ?: 1).coerceIn(1, PowerUpType.COIN_MAGNET.maxLevel),
                shieldLevel = (prefs[Keys.SHIELD_LEVEL] ?: 1).coerceIn(1, PowerUpType.KNOWLEDGE_SHIELD.maxLevel),
                detectorLevel = (prefs[Keys.DETECTOR_LEVEL] ?: 1).coerceIn(1, PowerUpType.GATE_DETECTOR.maxLevel),
                highScore = prefs[Keys.HIGH_SCORE] ?: 0,
                bestCombo = (prefs[Keys.BEST_COMBO] ?: 1).coerceAtLeast(1),
                totalCorrectAnswers = prefs[Keys.TOTAL_CORRECT] ?: 0,
                totalBossesDefeated = prefs[Keys.TOTAL_BOSSES] ?: 0
            )
        }

    suspend fun purchaseOrEquipCharacter(character: GameCharacterId): Pair<Boolean, String> {
        val current = shopStateFlow.first()
        if (character in current.ownedCharacters) {
            context.gamesHubDataStore.edit { prefs ->
                prefs[Keys.EQUIPPED_CHARACTER] = character.id
            }
            return true to "تم تفعيل شخصية «${character.titleAr}» بنجاح!"
        }
        if (current.coins < character.price) {
            val missing = character.price - current.coins
            return false to "تحتاج إلى $missing عملة إضافية لفتح «${character.titleAr}»"
        }
        context.gamesHubDataStore.edit { prefs ->
            val currentCoins = prefs[Keys.COINS] ?: 100
            val updatedOwned = (prefs[Keys.OWNED_CHARACTERS] ?: setOf(GameCharacterId.FAHIM_SMART.id)) + character.id
            prefs[Keys.COINS] = (currentCoins - character.price).coerceAtLeast(0)
            prefs[Keys.OWNED_CHARACTERS] = updatedOwned
            prefs[Keys.EQUIPPED_CHARACTER] = character.id
        }
        return true to "مبارك! تم شراء وتفعيل «${character.titleAr}» 🎉"
    }

    suspend fun upgradePowerUp(type: PowerUpType): Pair<Boolean, String> {
        val current = shopStateFlow.first()
        val level = current.powerUpLevel(type)
        if (level >= type.maxLevel) {
            return false to "وصلت قدرة «${type.titleAr}» إلى الحد الأقصى (المستوى ${type.maxLevel})!"
        }
        val cost = type.upgradePrice(level)
        if (current.coins < cost) {
            val missing = cost - current.coins
            return false to "تحتاج إلى $missing عملة إضافية لترقية «${type.titleAr}»"
        }
        context.gamesHubDataStore.edit { prefs ->
            val currentCoins = prefs[Keys.COINS] ?: 100
            prefs[Keys.COINS] = (currentCoins - cost).coerceAtLeast(0)
            val nextLevel = (level + 1).coerceAtMost(type.maxLevel)
            when (type) {
                PowerUpType.COIN_MAGNET -> prefs[Keys.MAGNET_LEVEL] = nextLevel
                PowerUpType.KNOWLEDGE_SHIELD -> prefs[Keys.SHIELD_LEVEL] = nextLevel
                PowerUpType.GATE_DETECTOR -> prefs[Keys.DETECTOR_LEVEL] = nextLevel
            }
        }
        return true to "تمت ترقية «${type.titleAr}» إلى المستوى ${level + 1} ⚡"
    }

    suspend fun recordRunOutcome(
        stageNumber: Int,
        victory: Boolean,
        totalCoinsAdded: Int,
        score: Int,
        correctAnswers: Int,
        maxCombo: Int
    ): Int? {
        var newlyUnlocked: Int? = null
        context.gamesHubDataStore.edit { prefs ->
            val oldCoins = prefs[Keys.COINS] ?: 100
            prefs[Keys.COINS] = (oldCoins + totalCoinsAdded.coerceAtLeast(0))

            val oldHigh = prefs[Keys.HIGH_SCORE] ?: 0
            if (score > oldHigh) prefs[Keys.HIGH_SCORE] = score

            val oldBestCombo = prefs[Keys.BEST_COMBO] ?: 1
            if (maxCombo > oldBestCombo) prefs[Keys.BEST_COMBO] = maxCombo

            val oldCorrect = prefs[Keys.TOTAL_CORRECT] ?: 0
            prefs[Keys.TOTAL_CORRECT] = oldCorrect + correctAnswers.coerceAtLeast(0)

            if (victory) {
                val oldBosses = prefs[Keys.TOTAL_BOSSES] ?: 0
                prefs[Keys.TOTAL_BOSSES] = oldBosses + 1

                val completed = (prefs[Keys.COMPLETED_STAGES] ?: emptySet()) + stageNumber.toString()
                prefs[Keys.COMPLETED_STAGES] = completed

                val oldUnlocked = (prefs[Keys.UNLOCKED_STAGE] ?: 1).coerceIn(1, TOTAL_GAME_STAGES)
                val targetUnlock = (stageNumber + 1).coerceAtMost(TOTAL_GAME_STAGES)
                if (targetUnlock > oldUnlocked) {
                    prefs[Keys.UNLOCKED_STAGE] = targetUnlock
                    newlyUnlocked = targetUnlock
                }
            }
        }
        return newlyUnlocked
    }
}
