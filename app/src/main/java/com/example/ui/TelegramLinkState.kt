package com.example.ui

sealed class TelegramLinkState {
    object Idle : TelegramLinkState()
    object Checking : TelegramLinkState()
    data class Linked(val chatId: Long = 0L) : TelegramLinkState()
    data class Error(val message: String) : TelegramLinkState()
}
