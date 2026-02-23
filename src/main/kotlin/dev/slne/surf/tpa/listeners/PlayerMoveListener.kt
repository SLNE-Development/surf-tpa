package dev.slne.surf.tpa.listeners

import dev.slne.surf.tpa.service.TeleportService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

object PlayerMoveListener : Listener {
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!event.hasExplicitlyChangedPosition()) return

        TeleportService.removeExecutionForSender(player.uniqueId)
    }
}