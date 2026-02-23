package dev.slne.surf.tpa.commands

import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.entitySelectorArgumentOnePlayer
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.slne.surf.tpa.commands.subcommands.tpaAcceptCommand
import dev.slne.surf.tpa.commands.subcommands.tpaDenyCommand
import dev.slne.surf.tpa.commands.subcommands.tpaListCommand
import dev.slne.surf.tpa.permissions.Permissions
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import org.bukkit.entity.Player

fun tpaCommand() = commandAPICommand("tpa") {
    withPermission(Permissions.COMMAND_TPA_GENERIC)

    tpaAcceptCommand()
    tpaDenyCommand()
    tpaListCommand()

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