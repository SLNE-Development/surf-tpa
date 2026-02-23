package dev.slne.surf.tpa

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.tpa.commands.tpaCommand
import org.bukkit.plugin.java.JavaPlugin

class PaperMain : SuspendingJavaPlugin() {

    override suspend fun onEnableAsync() {
        tpaCommand()

//        TeleportService.init()
    }

    override fun onDisable() {
//        TeleportService.shutdown()
    }
}

val plugin = JavaPlugin.getPlugin(PaperMain::class.java)