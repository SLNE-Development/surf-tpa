package dev.slne.surf.tpa.service

import com.github.shynixn.mccoroutine.folia.launch
import dev.slne.surf.surfapi.bukkit.api.extensions.server
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import dev.slne.surf.surfapi.core.api.messages.adventure.playSound
import dev.slne.surf.tpa.plugin
import dev.slne.surf.tpa.utils.Messages
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.bukkit.Sound as BukkitSound

object TeleportService {
    private val EXPIRATION = 3.minutes
    private val WAIT_TIME = 5.seconds

    private val pending = ConcurrentHashMap.newKeySet<TeleportRequest>()
    private val executions = ConcurrentHashMap<TeleportRequest, OffsetDateTime>()

    private lateinit var expirationJob: Job
    private lateinit var executeJob: Job

    fun init() {
        expirationJob = plugin.launch {
            while (true) {
                val now = OffsetDateTime.now()
                val iterator = pending.iterator()

                for (request in iterator) {
                    val sentAt = request.sentAt

                    if (sentAt.plusNanos(EXPIRATION.inWholeNanoseconds) <= now) {
                        iterator.remove()

                        server.getPlayer(request.senderUuid)
                            ?.sendMessage(Messages.requestExpiredForSender(request.targetName))
                        server.getPlayer(request.targetUuid)
                            ?.sendMessage(Messages.requestExpiredForTarget(request.senderName))
                    }
                }

                delay(EXPIRATION)
            }
        }

        executeJob = plugin.launch {
            while (true) {
                val now = OffsetDateTime.now()
                val iterator = executions.iterator()

                for ((request, addedAt) in iterator) {
                    val executesAt = addedAt.plusNanos(WAIT_TIME.inWholeNanoseconds)
                    val remainingTime = Duration.between(now, executesAt)

                    val sender = server.getPlayer(request.senderUuid)
                    val target = server.getPlayer(request.targetUuid)

                    if (sender == null || target == null) {
                        sender?.sendMessage(Messages.teleportFailedPlayerOffline(request.targetName))
                        target?.sendMessage(Messages.teleportFailedPlayerOffline(request.senderName))

                        continue
                    }

                    target.sendRemainingExecutionTimeTarget(sender, remainingTime)
                    sender.sendRemainingExecutionTimeSender(target, remainingTime)

                    target.playTeleportSound(false)
                    sender.playTeleportSound(false)

                    if (remainingTime <= Duration.ZERO) {
                        iterator.remove()
                        target.teleportAsync(sender.location)

                        target.playTeleportSound(true)
                        sender.playTeleportSound(true)

                        target.sendMessage(Messages.senderTeleportedTarget(sender.displayName()))
                        sender.sendMessage(Messages.targetTeleportedSender(target.displayName()))

                    }
                }

                delay(1.seconds)
            }
        }
    }

    fun shutdown() {
        expirationJob.cancel()
    }

    fun hasTeleportRequest(sender: UUID, target: UUID): Boolean {
        return pending.any { it.senderUuid == sender && it.targetUuid == target }
    }

    private fun Player.playTeleportSound(completed: Boolean) = playSound(true) {
        if (completed) {
            type(BukkitSound.ENTITY_ENDERMAN_TELEPORT)
        } else {
            type(BukkitSound.BLOCK_NOTE_BLOCK_PLING)
        }

        volume(.5f)
        source(Sound.Source.PLAYER)
    }

    private fun Player.sendRemainingExecutionTimeTarget(sender: Player, remainingTime: Duration) {
        val remainingSeconds = remainingTime.toSeconds()

        sendActionBar(buildText {
            success("Du wirst in")
            appendSpace()
            variableValue("$remainingSeconds Sekunden")
            appendSpace()
            success("zu")
            appendSpace()
            append(Messages.getDisplayName(sender))
            appendSpace()
            success("teleportiert.")
        })
    }

    private fun Player.sendRemainingExecutionTimeSender(target: Player, remainingTime: Duration) {
        val remainingSeconds = remainingTime.toSeconds()

        sendActionBar(buildText {
            append(Messages.getDisplayName(target))
            appendSpace()
            success("wird in")
            appendSpace()
            variableValue("$remainingSeconds Sekunden")
            appendSpace()
            success("zu dir teleportiert.")
        })
    }

    fun removeExecutionForSender(senderUuid: UUID) {
        val iterator = executions.iterator()

        val senderPlayer = server.getPlayer(senderUuid)
        var cancelled: TeleportRequest? = null

        for ((request, _) in iterator) {
            if (request.senderUuid == senderUuid) {
                iterator.remove()
                cancelled = request
            }
        }

        if (cancelled != null) {
            senderPlayer?.sendMessage(Messages.executionCancelled(cancelled.senderName))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sendRequest(sender: Player, target: Player) {
        val request = TeleportRequest(
            sender.uniqueId,
            target.uniqueId,
            OffsetDateTime.now(),
            sender.displayName(),
            target.displayName()
        )

        if (hasTeleportRequest(sender.uniqueId, target.uniqueId)) {
            sender.sendMessage(Messages.requestAlreadyExists(target.displayName()))
            return
        }

        pending.add(request)

        target.sendMessage(Messages.requestReceivedComponent(sender.displayName()))
        sender.sendMessage(Messages.requestSentToTarget(target.displayName()))
    }

    fun accept(senderUuid: UUID, targetUuid: UUID) {
        val request = removeTeleportRequest(senderUuid, targetUuid)
        val target = server.getPlayer(targetUuid) ?: return
        val sender = server.getPlayer(senderUuid) ?: run {
            target.sendMessage(Messages.teleportFailedPlayerOffline(target.displayName()))
            return
        }

        if (request == null) {
            target.sendMessage(Messages.noPendingRequestFromPlayer(sender.displayName()))
            return
        }

        target.sendMessage(Messages.requestAcceptedForTarget(sender.displayName()))
        sender.sendMessage(Messages.requestAcceptedForSender(target.displayName()))

        executions[request] = OffsetDateTime.now()
    }

    fun deny(senderUuid: UUID, targetUuid: UUID) {
        removeTeleportRequest(senderUuid, targetUuid)

        val target = server.getPlayer(targetUuid) ?: return
        val sender = server.getPlayer(senderUuid)

        sender?.sendMessage(Messages.requestDeniedForSender(target.displayName()))
        sender?.let { target.sendMessage(Messages.requestDeniedForTarget(it.displayName())) }
    }

    private fun removeTeleportRequest(senderUuid: UUID, targetUuid: UUID): TeleportRequest? {
        val request = pending.firstOrNull { it.senderUuid == senderUuid && it.targetUuid == targetUuid }

        if (request != null) {
            pending.remove(request)
        }

        return request
    }

    fun getPendingRequestsForTarget(target: UUID): List<TeleportRequest> {
        return pending.filter { it.targetUuid == target }
    }
}

