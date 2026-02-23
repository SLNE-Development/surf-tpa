package dev.slne.surf.tpa.commands

import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.slne.surf.tpa.commands.subcommands.tpaAcceptCommand
import dev.slne.surf.tpa.commands.subcommands.tpaDenyCommand
import dev.slne.surf.tpa.commands.subcommands.tpaListCommand
import dev.slne.surf.tpa.commands.subcommands.tpaRequestCommand
import dev.slne.surf.tpa.permissions.Permissions

fun tpaCommand() = commandAPICommand("tpa") {
    withPermission(Permissions.COMMAND_TPA_GENERIC)
    tpaAcceptCommand()
    tpaDenyCommand()
    tpaListCommand()
    tpaRequestCommand()
}