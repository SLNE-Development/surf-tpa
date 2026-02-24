package dev.slne.surf.tpa.service

import dev.slne.surf.surfapi.bukkit.api.extensions.server
import net.kyori.adventure.text.Component
import java.time.OffsetDateTime
import java.util.*

data class TeleportRequest(
    val senderUuid: UUID,
    val targetUuid: UUID,
    val sentAt: OffsetDateTime,

    val senderName: Component,
    val targetName: Component
) {
    var disconnected = false

    val sender get() = server.getPlayer(senderUuid)
    val target get() = server.getPlayer(targetUuid)
}