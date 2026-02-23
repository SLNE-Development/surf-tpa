package dev.slne.surf.tpa.commands.subcommands

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.CommandAPIPaper
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.surfapi.bukkit.api.command.executors.playerExecutorSuspend
import dev.slne.surf.tpa.commands.arguments.teleportRequestArgument
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import org.bukkit.entity.Player

fun CommandAPICommand.tpaAcceptCommand() = subcommand("accept") {
    teleportRequestArgument()

    playerExecutorSuspend { executor, arguments ->
        val requestSender: Player? by arguments

        if (requestSender == null) {
            throw CommandAPIPaper.failWithAdventureComponent(Messages.noMatchingRequest())
        }

        TeleportService.accept(executor, requestSender!!)
    }
}