package dev.slne.surf.tpa

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.surfapi.bukkit.api.event.register
import dev.slne.surf.tpa.commands.tpaCommand
import dev.slne.surf.tpa.listeners.PlayerMoveListener
import dev.slne.surf.tpa.service.TeleportService
import org.bukkit.plugin.java.JavaPlugin

class PaperMain : SuspendingJavaPlugin() {

    override suspend fun onEnableAsync() {
        tpaCommand()

        PlayerMoveListener.register()
        TeleportService.init()
    }

    override fun onDisable() {
        TeleportService.shutdown()
    }
}

val plugin = JavaPlugin.getPlugin(PaperMain::class.java)