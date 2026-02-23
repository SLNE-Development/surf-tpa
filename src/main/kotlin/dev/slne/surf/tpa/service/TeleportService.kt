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
                countDownJob.cancel()
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

        request.sender?.sendMessage(Messages.executionCancelled(request.targetName))
    }

    private fun handleExecution(request: TeleportRequest) {
        val sender = request.sender
        val target = request.target

        if (sender == null || target == null) {
            sender?.sendMessage(Messages.teleportFailedPlayerOffline(request.targetName))
            target?.sendMessage(Messages.teleportFailedPlayerOffline(request.senderName))
            return
        }

        plugin.launch(plugin.entityDispatcher(target)) {
            sender.teleportAsync(target.location)
        }

        target.playTeleportSound(true)
        sender.playTeleportSound(true)

        sender.sendMessage(Messages.senderTeleportedToTarget(target.displayName()))
        target.sendMessage(Messages.senderArrivedAtTarget(sender.displayName()))
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

    suspend fun accept(target: Player, sender: Player) {
        val request = removeTeleportRequest(sender.uniqueId, target.uniqueId)

        if (request == null) {
            target.sendMessage(Messages.noPendingRequestFromPlayer(sender.displayName()))
            return
        }


        target.sendMessage(Messages.requestAcceptedForTarget(sender.displayName()))
        sender.sendMessage(Messages.requestAcceptedForSender(target.displayName()))

        val senderStartPosition = withContext(plugin.entityDispatcher(sender)) {
            sender.location
        }

        executions.put(request, plugin.launch {
            val acceptedAt = OffsetDateTime.now()

            while (isActive) {
                val now = OffsetDateTime.now()
                val remainingTime = Duration.between(now, acceptedAt.plusSeconds(WAIT_TIME.inWholeSeconds))

                if (remainingTime.isNegative) {
                    executions.invalidate(request)
                    break
                }

                val requestSender = request.sender ?: continue
                val requestTarget = request.target ?: continue

                val senderCurrentPosition = withContext(plugin.entityDispatcher(requestSender)) {
                    requestSender.location
                }

                if (senderCurrentPosition.toVector().distanceSquared(senderStartPosition.toVector()) > 0.1) {
                    executions.invalidate(request)
                    break
                }

                requestSender.sendRemainingExecutionTimeTarget(requestTarget, remainingTime)
                requestTarget.sendRemainingExecutionTimeSender(requestSender, remainingTime)


                requestSender.playTeleportSound(false)
                requestTarget.playTeleportSound(false)

                delay(1.seconds)
            }
        })
    }

    fun deny(senderUuid: UUID, targetUuid: UUID) {
        removeTeleportRequest(senderUuid, targetUuid)

        val denier = server.getPlayer(targetUuid) ?: return
        val requestSender = server.getPlayer(senderUuid)

        requestSender?.sendMessage(Messages.requestDeniedForSender(denier.displayName()))
        requestSender?.let { denier.sendMessage(Messages.requestDeniedForTarget(it.displayName())) }
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