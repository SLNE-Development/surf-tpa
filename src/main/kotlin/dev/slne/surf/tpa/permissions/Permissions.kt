package dev.slne.surf.tpa.permissions

import dev.slne.surf.surfapi.bukkit.api.permission.PermissionRegistry

object Permissions : PermissionRegistry() {
    private const val PREFIX = "surf.tpa"
    private const val COMMAND_PREFIX = "$PREFIX.command"

    val COMMAND_TPA_GENERIC = create("$COMMAND_PREFIX.tpa")
}