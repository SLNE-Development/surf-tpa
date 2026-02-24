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
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.Duration
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
                if (countDownJob.isActive) {
                    handlePlayerMovedDuringExecution(request, countDownJob)
                } else {
                    handleExecution(request)
                }
            }
        }
        .build<TeleportRequest, Job>()

    private fun handleExpiration(request: TeleportRequest) {
        request.sender?.sendMessage(Messages.requestExpiredForSender(request.targetName))
        request.target?.sendMessage(Messages.requestExpiredForTarget(request.senderName))
    }

    private fun handlePlayerMovedDuringExecution(request: TeleportRequest, countDownJob: Job) {
        countDownJob.cancel("Teleportation cancelled due to player movement.")

        if (request.disconnected) {
            request.target?.sendMessage(Messages.teleportFailedPlayerOffline(request.senderName))
            request.sender?.sendMessage(Messages.teleportFailedPlayerOffline(request.targetName))
        } else {
            request.sender?.sendMessage(Messages.executionCancelled(request.targetName))
        }
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
        val remainingSeconds = remainingTime.inWholeSeconds

        val content = if (remainingTime <= 0.seconds) {
            buildText {
                success("Du wist zu")
                appendSpace()
                append(Messages.getDisplayName(sender))
                appendSpace()
                success("teleportiert...")
            }
        } else {
            buildText {
                success("du wirst in")
                appendSpace()
                variableValue("$remainingSeconds Sekunden")
                appendSpace()
                success("zu")
                appendSpace()
                append(Messages.getDisplayName(sender))
                appendSpace()
                success("teleportiert!")
            }
        }
        sendActionBar(content)
    }

    private fun Player.sendRemainingExecutionTimeSender(target: Player, remainingTime: Duration) {
        val remainingSeconds = remainingTime.inWholeSeconds

        val content = if (remainingTime <= 0.seconds) {
            buildText {
                append(Messages.getDisplayName(target))
                appendSpace()
                success("wird zu dir teleportiert...")
            }
        } else {
            buildText {
                append(Messages.getDisplayName(target))
                appendSpace()
                success("wird in")
                appendSpace()
                variableValue("$remainingSeconds Sekunden")
                appendSpace()
                success("zu dir teleportiert.")
            }
        }
        sendActionBar(content)
    }

    fun removeAllForPlayer(uuid: UUID) {
        val pendingItr = pending.asMap().keys.iterator()
        for (pending in pendingItr) {
            if (pending.senderUuid == uuid || pending.targetUuid == uuid) {
                pending.disconnected = true
                pendingItr.remove()
            }
        }


        val iterator = executions.asMap().keys.iterator()
        for (request in iterator) {
            if (request.senderUuid == uuid || request.targetUuid == uuid) {
                request.disconnected = true
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
        val request = pending.asMap().keys.firstOrNull {
            it.senderUuid == sender.uniqueId && it.targetUuid == target.uniqueId
        } ?: run {
            target.sendMessage(Messages.noPendingRequestFromPlayer(sender.displayName()))
            return
        }
        pending.invalidate(request)

        target.sendMessage(Messages.requestAcceptedForTarget(sender.displayName()))
        sender.sendMessage(Messages.requestAcceptedForSender(target.displayName()))

        val senderStartPosition = withContext(plugin.entityDispatcher(sender)) {
            sender.location.clone()
        }

        val teleportJob = plugin.launch {
            try {
                for (secondsLeft in WAIT_TIME.inWholeSeconds downTo 0) {
                    val currentSender = request.sender
                    val currentTarget = request.target

                    if (currentSender == null || currentTarget == null) break

                    if (currentSender.location.distanceSquared(senderStartPosition) > 0.1) {
                        executions.invalidate(request)
                        return@launch
                    }

                    val remaining = secondsLeft.seconds
                    currentSender.sendRemainingExecutionTimeTarget(currentTarget, remaining)
                    currentTarget.sendRemainingExecutionTimeSender(currentSender, remaining)

                    if (secondsLeft > 0) {
                        currentSender.playTeleportSound(false)
                        currentTarget.playTeleportSound(false)
                        delay(1.seconds)
                    }
                }

                executions.invalidate(request)

            } catch (e: CancellationException) {
            }
        }

        executions.put(request, teleportJob)
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