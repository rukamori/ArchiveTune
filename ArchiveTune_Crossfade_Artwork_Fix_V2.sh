#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT="${1:-$HOME/ArchiveTune_S}"

SERVICE="$PROJECT/app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt"
PLAYER="$PROJECT/app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Player.kt"
THUMB="$PROJECT/app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Thumbnail.kt"
MEDIA_EXT="$PROJECT/app/src/main/kotlin/moe/rukamori/archivetune/extensions/MediaItemExt.kt"
COIL="$PROJECT/app/src/main/kotlin/moe/rukamori/archivetune/utils/CoilBitmapLoader.kt"

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="$PROJECT/_backups/crossfade_artwork_fix_v2_$STAMP"

echo "=============================================="
echo " ArchiveTune_S"
echo " Crossfade + Artwork Fix V2"
echo "=============================================="
echo

if [ ! -d "$PROJECT/.git" ]; then
    echo "ERROR: Git project not found:"
    echo "$PROJECT"
    exit 1
fi

for f in "$SERVICE" "$PLAYER" "$THUMB" "$MEDIA_EXT" "$COIL"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: Required file not found:"
        echo "$f"
        exit 1
    fi
done

echo "[1/7] Checking current source structure..."

for marker in \
    'private suspend fun runCrossfade2ManualSelection(queue: Queue): Boolean' \
    'private fun prepareCrossfade2Incoming(mediaItem: MediaItem): ExoPlayer?' \
    'private suspend fun performCrossfadeHandoff(' \
    'private fun applyCrossfadeVolumes('
do
    if ! grep -Fq "$marker" "$SERVICE"; then
        echo "ERROR: Expected MusicService marker missing:"
        echo "$marker"
        echo
        echo "Refusing to modify the project."
        exit 1
    fi
done

if ! grep -Fq \
    'ytmUrl = mediaMetadata?.thumbnailUrl?.highRes(),' \
    "$PLAYER"
then
    echo "ERROR: Expected current Player artwork expression was not found."
    echo "Refusing to modify the project."
    exit 1
fi

echo "Source structure OK."

echo
echo "[2/7] Creating backup..."

mkdir -p "$BACKUP_DIR"

cp -p "$SERVICE" "$BACKUP_DIR/MusicService.kt"
cp -p "$PLAYER" "$BACKUP_DIR/Player.kt"
cp -p "$THUMB" "$BACKUP_DIR/Thumbnail.kt"
cp -p "$MEDIA_EXT" "$BACKUP_DIR/MediaItemExt.kt"
cp -p "$COIL" "$BACKUP_DIR/CoilBitmapLoader.kt"

echo "Backup:"
echo "$BACKUP_DIR"

export SERVICE PLAYER THUMB MEDIA_EXT COIL

echo
echo "[3/7] Applying controlled source changes..."

python3 <<'PY'
from pathlib import Path
import os

files = {
    "SERVICE": Path(os.environ["SERVICE"]),
    "PLAYER": Path(os.environ["PLAYER"]),
    "THUMB": Path(os.environ["THUMB"]),
    "MEDIA_EXT": Path(os.environ["MEDIA_EXT"]),
    "COIL": Path(os.environ["COIL"]),
}

def replace_once(text, old, new, label):
    count = text.count(old)

    if count != 1:
        raise SystemExit(
            f"{label}: expected exactly 1 match, found {count}"
        )

    return text.replace(old, new, 1)


# ============================================================
# 1. MusicService.kt
# ============================================================

p = files["SERVICE"]
s = p.read_text(encoding="utf-8")

# ------------------------------------------------------------
# Manual-specific readiness helper.
#
# Important:
# The secondary player must remain audible while the new
# primary player is preparing.
#
# We do NOT pause the secondary simply because the stricter
# natural-crossfade readiness condition failed.
# ------------------------------------------------------------

if "private suspend fun awaitManualPrimaryCrossfadeHandoffReady(" not in s:

    anchor = """    private suspend fun awaitPrimaryCrossfadeHandoffReady(incomingPlayer: ExoPlayer): Boolean {
"""

    helper = """    private suspend fun awaitManualPrimaryCrossfadeHandoffReady(
        targetIndex: Int,
        incomingPlayer: ExoPlayer,
    ): Boolean {
        val deadlineMs =
            android.os.SystemClock.elapsedRealtime() +
                CROSSFADE2_READY_TIMEOUT_MS.coerceAtMost(15_000L)

        while (
            kotlinx.coroutines.currentCoroutineContext().isActive &&
                android.os.SystemClock.elapsedRealtime() < deadlineMs
        ) {
            if (player.currentMediaItemIndex != targetIndex) {
                return false
            }

            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }

            if (
                player.playbackState == Player.STATE_READY &&
                    player.playWhenReady
            ) {
                if (!player.isPlaying) {
                    player.play()
                }

                if (player.isPlaying) {
                    val secondaryPosition =
                        incomingPlayer.currentPosition.coerceAtLeast(0L)

                    val primaryPosition =
                        player.currentPosition.coerceAtLeast(0L)

                    val drift =
                        kotlin.math.abs(
                            primaryPosition - secondaryPosition,
                        )

                    if (drift <= CROSSFADE_HANDOFF_MAX_DRIFT_MS) {
                        return true
                    }

                    /*
                     * Synchronize once when the drift is meaningful.
                     *
                     * This is intentionally NOT used as a fallback after
                     * performCrossfadeHandoff() fails.
                     */
                    player.seekTo(
                        targetIndex,
                        secondaryPosition,
                    )
                }
            }

            delay(25L)
        }

        return player.currentMediaItemIndex == targetIndex &&
            player.playbackState == Player.STATE_READY &&
            player.playWhenReady &&
            player.isPlaying
    }

"""

    s = replace_once(
        s,
        anchor,
        helper + anchor,
        "manual handoff helper anchor",
    )

# ------------------------------------------------------------
# Replace the old manual handoff logic.
#
# IMPORTANT:
# No:
#
#     player.volume = ...
#     incomingPlayer.pause()
#
# when readiness fails.
#
# This was the source of the audible hole.
# ------------------------------------------------------------

old = """            if (player.playbackState == Player.STATE_READY) {
                val syncPosition = incomingPlayer.currentPosition.coerceAtLeast(0L)
                player.seekTo(targetIndex, syncPosition)
                player.playWhenReady = true
                crossfadeHandoffInProgress = true
                crossfadeHandoffProgress = 0f

                if (!awaitPrimaryCrossfadeHandoffReady(incomingPlayer)) {
                    Timber.tag(TAG).w("Crossfade2 primary handoff readiness check failed; keeping primary active")
                    player.volume = crossfadeIncomingBaseVolume
                    incomingPlayer.pause()
                } else {
                    if (!performCrossfadeHandoff(targetIndex, incomingPlayer)) {
                        Timber.tag(TAG).w("Crossfade2 primary handoff animation did not complete; keeping primary active")
                        player.volume = crossfadeIncomingBaseVolume
                        incomingPlayer.pause()
                    }
                }
            }
"""

new = """            if (player.playbackState == Player.STATE_READY) {
                val syncPosition =
                    incomingPlayer.currentPosition.coerceAtLeast(0L)

                player.seekTo(
                    targetIndex,
                    syncPosition,
                )

                player.playWhenReady = true
                crossfadeHandoffInProgress = true
                crossfadeHandoffProgress = 0f

                /*
                 * Manual-selection rule:
                 *
                 * B (secondary) stays audible while primary B prepares.
                 *
                 * We do NOT pause B just because the natural-crossfade
                 * readiness guard fails.
                 */
                if (
                    !awaitManualPrimaryCrossfadeHandoffReady(
                        targetIndex,
                        incomingPlayer,
                    )
                ) {
                    Timber.tag(TAG).w(
                        "Crossfade2 manual primary did not become playable before timeout; keeping secondary active until cleanup",
                    )
                } else if (
                    !performCrossfadeHandoff(
                        targetIndex,
                        incomingPlayer,
                    )
                ) {
                    Timber.tag(TAG).w(
                        "Crossfade2 manual primary handoff animation did not complete",
                    )
                }
            }
"""

s = replace_once(
    s,
    old,
    new,
    "manual Crossfade2 handoff block",
)

p.write_text(s, encoding="utf-8")


# ============================================================
# 2. Player.kt
# ============================================================

p = files["PLAYER"]
s = p.read_text(encoding="utf-8")

old = """            videoId = mediaMetadata?.id,
            ytmUrl = mediaMetadata?.thumbnailUrl?.highRes(),
            lowDataMode = lowDataModeActive,
            isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
"""

new = """            videoId = mediaMetadata?.id,
            ytmUrl = mediaMetadata?.thumbnailUrl,
            lowDataMode = lowDataModeActive,
            isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
"""

s = replace_once(
    s,
    old,
    new,
    "Player artwork URL",
)

p.write_text(s, encoding="utf-8")


# ============================================================
# 3. Thumbnail.kt
# ============================================================

p = files["THUMB"]
s = p.read_text(encoding="utf-8")

old = """    val shouldAttemptYT = videoId != null && !lowDataMode && isMusicVideo
"""

new = """    val shouldAttemptYT =
        videoId != null &&
            !lowDataMode &&
            isMusicVideo &&
            ytmUrl.isNullOrBlank()
"""

s = replace_once(
    s,
    old,
    new,
    "Thumbnail YT probe gate",
)

p.write_text(s, encoding="utf-8")


# ============================================================
# 4. MediaItemExt.kt
# ============================================================

p = files["MEDIA_EXT"]
s = p.read_text(encoding="utf-8")

old = """private fun String?.toNotificationArtworkUri() =
    this
        ?.resize(
            width = NotificationArtworkSizePx,
            height = NotificationArtworkSizePx,
            ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
        )?.toUri()
"""

new = """private fun String?.toNotificationArtworkUri() =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.toUri()
"""

s = replace_once(
    s,
    old,
    new,
    "notification artwork URI",
)


# Normal Song artwork:
# Prefer the actual persisted thumbnail URL first.

old = """                .setArtworkUri(
                    if (song.isMusicVideo) {
                        buildYTThumbnailUrl(song.id, YTThumbQuality.HQ).toUri()
                    } else {
                        song.thumbnailUrl.toNotificationArtworkUri()
                    },
                )
"""

new = """                .setArtworkUri(
                    song.thumbnailUrl.toNotificationArtworkUri()
                        ?: if (song.isMusicVideo) {
                            buildYTThumbnailUrl(
                                song.id,
                                YTThumbQuality.HQ,
                            ).toUri()
                        } else {
                            null
                        },
                )
"""

s = replace_once(
    s,
    old,
    new,
    "Song notification artwork",
)


# Online SongItem artwork.

old = """                .setArtworkUri(
                    if (isMusicVideo()) {
                        buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()
                    } else {
                        thumbnail.toNotificationArtworkUri()
                    },
                ).setAlbumTitle(album?.name)
"""

new = """                .setArtworkUri(
                    thumbnail.toNotificationArtworkUri()
                        ?: if (isMusicVideo()) {
                            buildYTThumbnailUrl(
                                id,
                                YTThumbQuality.HQ,
                            ).toUri()
                        } else {
                            null
                        },
                ).setAlbumTitle(album?.name)
"""

s = replace_once(
    s,
    old,
    new,
    "SongItem notification artwork",
)

p.write_text(s, encoding="utf-8")


# ============================================================
# 5. CoilBitmapLoader.kt
# ============================================================

p = files["COIL"]
s = p.read_text(encoding="utf-8")

if "import coil3.request.CachePolicy" not in s:

    anchor = "import coil3.request.ErrorResult\n"

    s = replace_once(
        s,
        anchor,
        "import coil3.request.CachePolicy\n" + anchor,
        "Coil CachePolicy import",
    )

old = """                ImageRequest
                    .Builder(applicationContext)
                    .data(uri)
                    .allowHardware(false)
                    .size(maximumArtworkDimensionPx, maximumArtworkDimensionPx)
                    .build()
"""

new = """                val cacheKey = uri.toString()

                ImageRequest
                    .Builder(applicationContext)
                    .data(uri)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(false)
                    .size(
                        maximumArtworkDimensionPx,
                        maximumArtworkDimensionPx,
                    )
                    .build()
"""

s = replace_once(
    s,
    old,
    new,
    "Coil artwork request",
)

p.write_text(s, encoding="utf-8")


print("All controlled transformations completed.")
PY

echo
echo "[4/7] Running structural checks..."

CHECK_FILES=(
    "$SERVICE"
    "$PLAYER"
    "$THUMB"
    "$MEDIA_EXT"
    "$COIL"
)

check_marker() {
    local marker="$1"

    for f in "${CHECK_FILES[@]}"; do
        if grep -Fq "$marker" "$f"; then
            return 0
        fi
    done

    echo "ERROR: Missing marker:"
    echo "$marker"
    return 1
}

check_marker \
    "private suspend fun awaitManualPrimaryCrossfadeHandoffReady("

check_marker \
    "Crossfade2 manual primary did not become playable"

check_marker \
    "ytmUrl = mediaMetadata?.thumbnailUrl,"

check_marker \
    "ytmUrl.isNullOrBlank()"

check_marker \
    "memoryCacheKey(cacheKey)"

check_marker \
    "diskCacheKey(cacheKey)"

check_marker \
    "memoryCachePolicy(CachePolicy.ENABLED)"

check_marker \
    "diskCachePolicy(CachePolicy.ENABLED)"

check_marker \
    "networkCachePolicy(CachePolicy.ENABLED)"

echo "Structural markers OK."

echo
echo "[5/7] Checking dangerous old manual fallback..."

if grep -nF \
    'incomingPlayer.pause()' \
    "$SERVICE" | grep -q .
then
    echo "WARNING: incomingPlayer.pause() still exists somewhere in MusicService."
    echo "That may belong to another legitimate path."
    echo "We will NOT delete it automatically."
fi

echo
echo "[6/7] git diff --check..."

if ! git -C "$PROJECT" diff --check; then
    echo
    echo "ERROR: git diff --check failed."
    echo "Restoring files from backup..."

    cp -p "$BACKUP_DIR/MusicService.kt" "$SERVICE"
    cp -p "$BACKUP_DIR/Player.kt" "$PLAYER"
    cp -p "$BACKUP_DIR/Thumbnail.kt" "$THUMB"
    cp -p "$BACKUP_DIR/MediaItemExt.kt" "$MEDIA_EXT"
    cp -p "$BACKUP_DIR/CoilBitmapLoader.kt" "$COIL"

    exit 1
fi

echo "git diff --check: OK."

echo
echo "[7/7] Final diff..."

echo
echo "========== STATUS =========="

git -C "$PROJECT" status --short -- \
    "$SERVICE" \
    "$PLAYER" \
    "$THUMB" \
    "$MEDIA_EXT" \
    "$COIL"

echo
echo "========== DIFF STAT =========="

git -C "$PROJECT" diff --stat -- \
    "$SERVICE" \
    "$PLAYER" \
    "$THUMB" \
    "$MEDIA_EXT" \
    "$COIL"

echo
echo "========== BACKUP =========="

echo "$BACKUP_DIR"

echo
echo "=============================================="
echo " DONE"
echo "=============================================="
echo
echo "No Gradle."
echo "No APK."
echo "No Logcat."
echo "No commit."
echo "No push."
echo
echo "راجع الـ diff الآن."
echo
echo "git -C \"$PROJECT\" diff --check"
echo
echo "git -C \"$PROJECT\" diff -- \\"
echo "  \"$SERVICE\" \\"
echo "  \"$PLAYER\" \\"
echo "  \"$THUMB\" \\"
echo "  \"$MEDIA_EXT\" \\"
echo "  \"$COIL\""
echo
echo "Backup:"
echo "$BACKUP_DIR"
