package dev.slne.surf.tpa.commands.arguments

import com.github.shynixn.mccoroutine.folia.scope
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.CustomArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.slne.surf.surfapi.bukkit.api.extensions.server
import dev.slne.surf.surfapi.core.api.messages.adventure.uuid
import dev.slne.surf.tpa.plugin
import dev.slne.surf.tpa.service.TeleportService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.future.future
import org.bukkit.entity.Player

const val TPA_SENDER_ARGUMENT_NODE_NAME = "tpaSender"

@OptIn(DelicateCoroutinesApi::class)
class TeleportRequestArgument(
    nodeName: String = TPA_SENDER_ARGUMENT_NODE_NAME
) : CustomArgument<Player?, String>(
    StringArgument(nodeName),
    { info ->
        val raw = info.currentInput
        val requests = TeleportService.getPendingRequestsForTarget(info.sender.uuid())

        val request = requests.mapNotNull {
            val senderPlayer = server.getPlayer(it.senderUuid) ?: return@mapNotNull null

            senderPlayer to it
        }.firstOrNull { (sender, _) ->
            sender.name.equals(raw, ignoreCase = true)
        }

        request?.first
    }
) {
    init {
        replaceSuggestions(ArgumentSuggestions.stringCollectionAsync { info ->
            plugin.scope.future {
                val player = info.sender as? Player ?: return@future emptyList()
                val requests = TeleportService.getPendingRequestsForTarget(player.uniqueId)

                if (requests.isEmpty()) return@future emptyList()

                return@future requests.map { req ->
                    player.server.getPlayer(req.senderUuid)?.name
                        ?: req.senderUuid.toString()
                }.toList()
            }
        })
    }
}

fun CommandAPICommand.teleportRequestArgument(
    nodeName: String = TPA_SENDER_ARGUMENT_NODE_NAME,
    optional: Boolean = false,
    block: Argument<*>.() -> Unit = {}
): CommandAPICommand = withArguments(
    TeleportRequestArgument(nodeName).setOptional(optional).apply(block)
)