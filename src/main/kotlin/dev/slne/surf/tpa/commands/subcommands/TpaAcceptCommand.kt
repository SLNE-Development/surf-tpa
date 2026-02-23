package dev.slne.surf.tpa.commands.subcommands

import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.tpa.commands.arguments.teleportRequestArgument
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import org.bukkit.entity.Player

fun tpaAcceptCommand() = subcommand("accept") {
    teleportRequestArgument()

    playerExecutor { target, arguments ->
        val sender: Player? by arguments

        if (sender == null) {
            target.sendMessage(Messages.noMatchingRequest())
            return@playerExecutor
        }

        TeleportService.accept(sender!!.uniqueId, target.uniqueId)
    }
}