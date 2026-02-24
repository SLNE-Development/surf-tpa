package dev.slne.surf.tpa

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.surfapi.bukkit.api.event.register
import dev.slne.surf.tpa.commands.tpaCommand
import dev.slne.surf.tpa.listeners.PlayerConnectionListener
import org.bukkit.plugin.java.JavaPlugin

class PaperMain : SuspendingJavaPlugin() {

    override suspend fun onEnableAsync() {
        tpaCommand()
        PlayerConnectionListener.register()
    }

    override fun onDisable() {
    }
}

val plugin = JavaPlugin.getPlugin(PaperMain::class.java)