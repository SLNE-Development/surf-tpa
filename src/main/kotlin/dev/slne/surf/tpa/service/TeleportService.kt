package dev.slne.surf.tpa.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import com.sksamuel.aedile.core.expireAfterWrite
import dev.slne.surf.surfapi.bukkit.api.extensions.server
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import dev.slne.surf.surfapi.core.api.messages.adventure.playSound
import dev.slne.surf.tpa.plugin
import dev.slne.surf.tpa.utils.Messages
import kotlinx.coroutines.*
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.bukkit.Sound as BukkitSound

object TeleportService {
    private val EXPIRATION = 3.minutes
    private val WAIT_TIME = 5.seconds

    private val pending = Caffeine.newBuilder()
        .expireAfterWrite(EXPIRATION)
        .evictionListener<TeleportRequest, Unit> { request, _, cause ->
            if (cause.wasEvicted() && request != null) {
                handleExpiration(request)
            }
        }
        .build<TeleportRequest, Unit>()


    private val executions = Caffeine.newBuilder()
        .expireAfterWrite(WAIT_TIME)
        .removalListener<TeleportRequest, Job> { request, countDownJob, cause ->
            if (request == null || countDownJob == null) return@removalListener
            if (cause == RemovalCause.EXPIRED) {
                handleExecution(request)
            } else if (cause == RemovalCause.EXPLICIT) {
                handlePlayerMovedDuringExecution(request, countDownJob)
            }
        }
        .build<TeleportRequest, Job>()

    private fun handleExpiration(request: TeleportRequest) {
        request.sender?.sendMessage(Messages.requestExpiredForSender(request.targetName))
        request.target?.sendMessage(Messages.requestExpiredForTarget(request.senderName))
    }

    private fun handlePlayerMovedDuringExecution(request: TeleportRequest, countDownJob: Job) {
        countDownJob.cancel("Teleportation cancelled due to player movement.")

        request.sender?.sendMessage(Messages.executionCancelled(request.senderName))
    }

    private fun handleExecution(request: TeleportRequest) {
        val sender = request.sender
        val target = request.target

        if (sender == null || target == null) {
            sender?.sendMessage(Messages.teleportFailedPlayerOffline(request.targetName))
            target?.sendMessage(Messages.teleportFailedPlayerOffline(request.senderName))
            return
        }

        plugin.launch(plugin.entityDispatcher(sender)) {
            target.teleportAsync(sender.location)
        }

        target.playTeleportSound(true)
        sender.playTeleportSound(true)

        target.sendMessage(Messages.senderTeleportedTarget(sender.displayName()))
        sender.sendMessage(Messages.targetTeleportedSender(target.displayName()))
    }

    fun hasTeleportRequest(sender: UUID, target: UUID): Boolean {
        return pending.asMap().keys.any { it.senderUuid == sender && it.targetUuid == target }
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
        val iterator = executions.asMap().keys.iterator()
        for (request in iterator) {
            if (request.senderUuid == senderUuid) {
                iterator.remove()
            }
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

        pending.put(request, Unit)

        target.sendMessage(Messages.requestReceivedComponent(sender.uniqueId, sender.displayName()))
        sender.sendMessage(Messages.requestSentToTarget(target.displayName()))
    }

    suspend fun accept(acceptor: Player, accepted: Player) {
        val request = removeTeleportRequest(accepted.uniqueId, acceptor.uniqueId)

        if (request == null) {
            acceptor.sendMessage(Messages.noPendingRequestFromPlayer(accepted.displayName()))
            return
        }


        acceptor.sendMessage(Messages.requestAcceptedForTarget(accepted.displayName()))
        accepted.sendMessage(Messages.requestAcceptedForSender(acceptor.displayName()))

        val acceptedStartPosition = withContext(plugin.entityDispatcher(accepted)) {
            accepted.location
        }

        executions.put(request, plugin.launch {
            while (isActive) {
                delay(1.seconds)

                val now = OffsetDateTime.now()
                val executesAt = request.sentAt.plusNanos(WAIT_TIME.inWholeNanoseconds)
                val remainingTime = Duration.between(now, executesAt)

                val sender = request.sender ?: continue
                val target = request.target ?: continue

                val senderPosition = withContext(plugin.entityDispatcher(sender)) {
                    sender.location
                }

                if (senderPosition.toVector().distanceSquared(acceptedStartPosition.toVector()) > 0.1) {
//                    sender.sendMessage(Messages.executionCancelled(request.senderName))
//                    target.sendMessage(Messages.executionCancelled(request.targetName))
                    executions.invalidate(request)
                    break
                }

                target.sendRemainingExecutionTimeTarget(sender, remainingTime)
                sender.sendRemainingExecutionTimeSender(target, remainingTime)

                target.playTeleportSound(false)
                sender.playTeleportSound(false)
            }
        })
    }

    fun deny(senderUuid: UUID, targetUuid: UUID) {
        removeTeleportRequest(senderUuid, targetUuid)

        val target = server.getPlayer(targetUuid) ?: return
        val sender = server.getPlayer(senderUuid)

        sender?.sendMessage(Messages.requestDeniedForSender(target.displayName()))
        sender?.let { target.sendMessage(Messages.requestDeniedForTarget(it.displayName())) }
    }

    private fun removeTeleportRequest(senderUuid: UUID, targetUuid: UUID): TeleportRequest? {
        val request = pending.asMap().keys.firstOrNull { it.senderUuid == senderUuid && it.targetUuid == targetUuid }

        if (request != null) {
            pending.invalidate(request)
        }

        return request
    }

    fun getPendingRequestsForTarget(target: UUID): List<TeleportRequest> {
        return pending.asMap().keys.filter { it.targetUuid == target }
    }
}