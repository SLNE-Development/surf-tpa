package dev.slne.surf.tpa.commands.subcommands

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.surfapi.bukkit.api.extensions.server
import dev.slne.surf.surfapi.core.api.font.toSmallCaps
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import dev.slne.surf.surfapi.core.api.messages.adventure.clickRunsCommand
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import dev.slne.surf.surfapi.core.api.messages.pagination.Pagination
import dev.slne.surf.surfapi.core.api.util.dateTimeFormatter
import dev.slne.surf.tpa.service.TeleportRequest
import dev.slne.surf.tpa.service.TeleportService
import dev.slne.surf.tpa.utils.Messages
import net.kyori.adventure.text.format.TextDecoration

private val pagination = Pagination<TeleportRequest> {
    title {
        primary("Offene Teleportanfragen".toSmallCaps(), TextDecoration.BOLD)
    }

    rowRenderer { request, _ ->
        val sender = server.getPlayer(request.senderUuid)

        listOf(
            buildText {
                spacer(">")
                appendSpace()
                variableValue(sender?.name ?: request.senderUuid.toString())
                appendSpace()
                info("möchte sich zu dir teleportieren.")
                appendSpace()
                spacer("( ${dateTimeFormatter.format(request.sentAt)} )")
                hoverEvent(buildText {
                    success("Klicke, um die Anfrage von")
                    appendSpace()
                    variableValue(sender?.name ?: request.senderUuid.toString())
                    appendSpace()
                    success("anzunehmen.")
                })
                clickRunsCommand("/tpa accept ${sender?.name ?: request.senderUuid.toString()}")
            }
        )
    }
}

fun CommandAPICommand.tpaListCommand() = subcommand("list") {
    playerExecutor { player, _ ->
        val requests = TeleportService.getPendingRequestsForTarget(player.uniqueId)

        if (requests.isEmpty()) {
            player.sendMessage(Messages.noPendingRequests())
            return@playerExecutor
        }

        player.sendText {
            appendNewline()
            append(pagination.renderComponent(requests))
        }
    }
}
