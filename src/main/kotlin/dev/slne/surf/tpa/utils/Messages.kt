package dev.slne.surf.tpa.utils

import dev.slne.surf.surfapi.core.api.messages.Colors
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import dev.slne.surf.surfapi.core.api.messages.builder.SurfComponentBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

fun OfflinePlayer.formattedDisplayName() = Messages.getDisplayName(this)

object Messages {
    private val clickableComponent: (Component) -> Component = {
        buildText {
            text("HIER", Colors.VARIABLE_VALUE, TextDecoration.UNDERLINED)
            hoverEvent(HoverEvent.showText(buildText {
                info("Klicke hier, um die Teleportanfrage von")
                appendSpace()
                appendDisplayName(it)
                appendSpace()
                success("anzunehmen.")
            }))
            clickEvent(ClickEvent.runCommand("/tpa accept $it"))
        }
    }

    private fun SurfComponentBuilder.appendDisplayName(displayName: Component) =
        append(displayName).colorIfAbsent(Colors.VARIABLE_VALUE)

    fun getDisplayName(player: OfflinePlayer) = if (player is Player) player.displayName() else buildText {
        variableValue(player.name ?: player.uniqueId.toString())
    }

    fun requestAlreadyExists(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Du hast bereits eine offene Teleportanfrage an")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("!")
    }

    fun teleportFailedPlayerOffline(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Der Spieler")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("ist nicht mehr online.")
    }

    fun requestSentToTarget(displayName: Component) = buildText {
        appendSuccessPrefix()
        success("Du hast eine Teleportanfrage an")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        success("gesendet!")
    }

    fun requestExpiredForSender(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Die Teleportanfrage an")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("ist abgelaufen!")
    }

    fun requestExpiredForTarget(senderDisplayName: Component) = buildText {
        appendErrorPrefix()
        error("Die Teleportanfrage von")
        appendSpace()
        appendDisplayName(senderDisplayName)
        appendSpace()
        error("ist abgelaufen!")
    }

    fun noPendingRequests() = buildText {
        appendErrorPrefix()
        error("Du hast keine offenen Teleportanfragen.")
    }

    fun noPendingRequestFromPlayer(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Du hast keine offene Teleportanfrage von")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("!")
    }

    fun requestAcceptedForSender(displayName: Component) = buildText {
        appendSuccessPrefix()
        appendDisplayName(displayName)
        appendSpace()
        success("hat deine Teleportanfrage akzeptiert!")
    }

    fun requestAcceptedForTarget(displayName: Component) = buildText {
        appendSuccessPrefix()
        success("Du hast die Teleportanfrage von")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        success("akzeptiert!")
    }

    fun requestDeniedForSender(displayName: Component) = buildText {
        appendErrorPrefix()
        appendDisplayName(displayName)
        appendSpace()
        error("hat deine Teleportanfrage abgelehnt.")
    }

    fun requestDeniedForTarget(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Du hast die Teleportanfrage von")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("abgelehnt.")
    }

    fun requestReceivedComponent(displayName: Component) = buildText {
        appendSuccessPrefix()
        success("Du hast eine Teleportanfrage von")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        success("erhalten.")
        appendSpace()
        success("Klicke")
        appendSpace()
        append(clickableComponent(displayName))
        appendSpace()
        success("um die Anfrage anzunehmen.")
    }

    fun noMatchingRequest() = buildText {
        appendErrorPrefix()
        error("Es gibt keine passende Teleportanfrage.")
    }

    fun cantSendRequestToSelf() = buildText {
        appendErrorPrefix()
        error("Du kannst dir selbst keine Teleportanfrage senden.")
    }

    fun executionCancelled(displayName: Component) = buildText {
        appendErrorPrefix()
        error("Die Teleportation zu")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        error("wurde abgebrochen, da du dich bewegt hast.")
    }

    fun senderTeleportedTarget(displayName: Component) = buildText {
        appendSuccessPrefix()
        success("Du wurdest zu")
        appendSpace()
        appendDisplayName(displayName)
        appendSpace()
        success("teleportiert!")
    }

    fun targetTeleportedSender(displayName: Component) = buildText {
        appendSuccessPrefix()
        appendDisplayName(displayName)
        appendSpace()
        success("hat sich zu dir teleportiert!")
    }
}
