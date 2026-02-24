package dev.slne.surf.tpa.listeners

import dev.slne.surf.tpa.service.TeleportService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

object PlayerConnectionListener : Listener {

    @EventHandler
    fun onPlayerQuitEvent(event: PlayerQuitEvent) {
        val player = event.player

        TeleportService.removeAllForPlayer(player.uniqueId)
    }
}