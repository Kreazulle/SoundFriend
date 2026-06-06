package com.soundffriend.core

/**
 * Portable data models for SoundFriend.
 */

data class WingMixer(val name: String, val ip: String)

data class FxSlot(val id: Int, val model: String, val hasTempo: Boolean)

enum class NotificationType { ALERT, INFO }

data class Notification(val text: String, val type: NotificationType)
