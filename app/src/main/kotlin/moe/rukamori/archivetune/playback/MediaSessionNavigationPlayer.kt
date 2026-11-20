/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal class MediaSessionNavigationPlayer(
    player: Player,
) : ForwardingPlayer(player) {
    private val listeners = mutableListOf<NavigationCommandsListener>()

    override fun addListener(listener: Player.Listener) {
        if (listeners.any { it.listener === listener }) return
        val navigationListener = NavigationCommandsListener(listener)
        listeners += navigationListener
        super.addListener(navigationListener)
    }

    override fun removeListener(listener: Player.Listener) {
        val index = listeners.indexOfFirst { it.listener === listener }
        if (index < 0) return
        super.removeListener(listeners.removeAt(index))
    }

    override fun getAvailableCommands(): Player.Commands = withPersistentNext(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean =
        super.isCommandAvailable(command) ||
            (command == Player.COMMAND_SEEK_TO_NEXT && super.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))

    override fun seekToNext() {
        val player = wrappedPlayer
        if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
            super.seekToNext()
            return
        }
        val duration = player.duration
        if (duration != C.TIME_UNSET && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(duration)
        }
    }

    private inner class NavigationCommandsListener(
        val listener: Player.Listener,
    ) : Player.Listener by listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            listener.onAvailableCommandsChanged(withPersistentNext(availableCommands))
        }
    }

    private companion object {
        fun withPersistentNext(commands: Player.Commands): Player.Commands =
            if (commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS) && !commands.contains(Player.COMMAND_SEEK_TO_NEXT)) {
                commands.buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).build()
            } else {
                commands
            }
    }
}
