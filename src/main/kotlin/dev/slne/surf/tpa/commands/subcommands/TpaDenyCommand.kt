package dev.slne.surf.tpa.commands.subcommands

import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.tpa.commands.arguments.teleportRequestArgument
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import org.bukkit.entity.Player

fun tpaDenyCommand() = subcommand("deny") {
    teleportRequestArgument()

    playerExecutor { player, arguments ->
        val requestSender: Player? by arguments

        if (requestSender == null) {
            player.sendMessage(Messages.noMatchingRequest())
            return@playerExecutor
        }

        TeleportService.deny(requestSender!!.uniqueId, player.uniqueId)
    }
}