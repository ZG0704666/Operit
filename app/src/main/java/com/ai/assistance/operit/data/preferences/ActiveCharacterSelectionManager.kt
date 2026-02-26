package com.ai.assistance.operit.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.first

class ActiveCharacterSelectionManager private constructor(context: Context) {

    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: ActiveCharacterSelectionManager? = null

        fun getInstance(context: Context): ActiveCharacterSelectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActiveCharacterSelectionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    suspend fun activateCharacterCard(cardId: String) {
        if (cardId.isBlank()) return

        val currentCardId = characterCardManager.activeCharacterCardIdFlow.first()
        val currentGroupId = characterGroupCardManager.activeCharacterGroupCardIdFlow.first()
        val shouldActivateCard = currentCardId != cardId || !currentGroupId.isNullOrBlank()
        if (shouldActivateCard) {
            characterCardManager.setActiveCharacterCard(cardId)
        }

        if (!currentGroupId.isNullOrBlank()) {
            characterGroupCardManager.setActiveCharacterGroupCard(null)
        }
    }

    suspend fun activateCharacterGroup(groupId: String?) {
        val normalizedGroupId = groupId?.takeIf { it.isNotBlank() }
        val currentGroupId = characterGroupCardManager.activeCharacterGroupCardIdFlow.first()
        if (currentGroupId != normalizedGroupId) {
            characterGroupCardManager.setActiveCharacterGroupCard(normalizedGroupId)
        }
    }

    suspend fun activateForChatBinding(characterCardName: String?, characterGroupId: String?) {
        val normalizedGroupId = characterGroupId?.takeIf { it.isNotBlank() }
        if (!normalizedGroupId.isNullOrBlank()) {
            activateCharacterGroup(normalizedGroupId)
            return
        }

        activateCharacterGroup(null)

        val normalizedCardName = characterCardName?.trim()?.takeIf { it.isNotBlank() } ?: return
        val targetCard = characterCardManager.findCharacterCardByName(normalizedCardName) ?: return
        activateCharacterCard(targetCard.id)
    }
}
