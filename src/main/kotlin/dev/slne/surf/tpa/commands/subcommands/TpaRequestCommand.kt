package dev.slne.surf.tpa.commands.subcommands

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.entitySelectorArgumentOnePlayer
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import org.bukkit.entity.Player

fun CommandAPICommand.tpaRequestCommand() = subcommand("request") {

    entitySelectorArgumentOnePlayer("targetPlayer")

    playerExecutor { player, arguments ->
        val targetPlayer: Player by arguments

        if (targetPlayer == player) {
            player.sendMessage(Messages.cantSendRequestToSelf())
            return@playerExecutor
        }

        TeleportService.sendRequest(player, targetPlayer)
    }
}