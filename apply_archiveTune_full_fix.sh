#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/ArchiveTune_S"
cd "$ROOT"

TS="$(date +%Y%m%d_%H%M%S)"
BACKUP=".archiveTune_full_fix_backup_$TS"

echo "=============================================="
echo " ArchiveTune_S - Full Playback/Artwork Fix"
echo "=============================================="
echo

if [ ! -d ".git" ]; then
    echo "ERROR: $ROOT ليس Git repository"
    exit 1
fi

mkdir -p "$BACKUP"

echo "[1/8] Checking repository..."
git status --short

echo
echo "[2/8] Creating targeted backups..."

FILES=(
"app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/playback/PlayerConnection.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Player.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/ui/player/PlayerComponents.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/ui/player/MiniPlayerComponents.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Thumbnail.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/utils/MediaItemExt.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/utils/CoilBitmapLoader.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/constants/PreferenceKeys.kt"
"app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/PlayerSettings.kt"
)

for f in "${FILES[@]}"; do
    if [ -f "$f" ]; then
        mkdir -p "$BACKUP/$(dirname "$f")"
        cp -p "$f" "$BACKUP/$f"
        echo "  backup: $f"
    fi
done

echo
echo "[3/8] Detecting actual source structure..."

MUSIC="app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt"
CONNECTION="app/src/main/kotlin/moe/rukamori/archivetune/playback/PlayerConnection.kt"

if [ ! -f "$MUSIC" ]; then
    echo "ERROR: MusicService.kt not found"
    exit 1
fi

if grep -q "runCrossfade2ManualSelection" "$MUSIC"; then
    echo "  OK: Crossfade2 manual-selection path found"
else
    echo "  WARNING: Crossfade2 manual-selection path not found"
fi

if grep -q "performCrossfadeHandoff" "$MUSIC"; then
    echo "  OK: Crossfade handoff found"
else
    echo "  WARNING: Crossfade handoff function not found"
fi

if grep -q "secondaryCrossfadePlayer" "$MUSIC"; then
    echo "  OK: secondary crossfade player found"
else
    echo "  WARNING: secondary crossfade player not found"
fi

echo
echo "[4/8] Applying safe MusicService fixes..."

python3 - "$MUSIC" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

changed = False

# ---------------------------------------------------------
# 1. Protect playback error recovery from escaping coroutine
# ---------------------------------------------------------

old = '''scope.launch {
            handlePlaybackError(error, snapshot)
        }'''

new = '''scope.launch {
            try {
                handlePlaybackError(error, snapshot)
            } catch (recoveryError: Throwable) {
                Timber.tag(TAG).e(
                    recoveryError,
                    "Playback error recovery failed without crashing the playback service",
                )

                try {
                    player.playWhenReady = false
                } catch (_: Throwable) {
                }
            }
        }'''

if old in s:
    s = s.replace(old, new, 1)
    changed = True
    print("  applied: protected playback error recovery")
elif "Playback error recovery failed without crashing the playback service" in s:
    print("  skip: recovery protection already present")
else:
    print("  skip: recovery block structure differs")

# ---------------------------------------------------------
# 2. Prevent remote EOF from automatically being cache
#    corruption unless cached content actually exists.
# ---------------------------------------------------------

old = '''return error is EOFException ||
            error is Cache.CacheException'''

new = '''return error is Cache.CacheException ||
            (error is EOFException && hasCachedContentForPlaybackError())'''

if old in s:
    s = s.replace(old, new, 1)
    changed = True
    print("  applied: EOF/cache corruption separation")
elif "hasCachedContentForPlaybackError()" in s:
    print("  skip: EOF/cache separation already present")
else:
    print("  skip: cache corruption structure differs")

# ---------------------------------------------------------
# 3. Add helper only when it does not already exist.
# ---------------------------------------------------------

if "private fun hasCachedContentForPlaybackError()" not in s:

    anchor = "private fun isCacheCorruptionError("

    idx = s.find(anchor)

    if idx >= 0:
        helper = r'''
private fun hasCachedContentForPlaybackError(): Boolean {
    return try {
        val mediaId = player.currentMediaItem?.mediaId ?: return false

        val cache = cacheDataSourceFactory.cache
        val metadata = cache.getContentMetadata(mediaId)

        metadata.getContentLength() > 0L ||
            cache.getCachedBytes(mediaId, 0L, Long.MAX_VALUE) > 0L
    } catch (e: Throwable) {
        Timber.tag(TAG).d(e, "Unable to determine cached playback content")
        false
    }
}

'''
        s = s[:idx] + helper + s[idx:]
        changed = True
        print("  applied: cached-content helper")
    else:
        print("  skip: isCacheCorruptionError anchor not found")

p.write_text(s)
PY

echo
echo "[5/8] Fixing manual Crossfade handoff..."

python3 - "$MUSIC" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old = '''if (!awaitPrimaryCrossfadeHandoffReady(incomingPlayer)) {
                Timber.tag(TAG).w("Crossfade2 primary handoff readiness check failed; keeping primary active")
                player.volume = crossfadeIncomingBaseVolume
                incomingPlayer.pause()
            } else {
                if (!performCrossfadeHandoff(targetIndex, incomingPlayer)) {
                    Timber.tag(TAG).w("Crossfade2 primary handoff animation did not complete; keeping primary active")
                    player.volume = crossfadeIncomingBaseVolume
                    incomingPlayer.pause()
                }
            }'''

new = '''if (!awaitManualPrimaryCrossfadeHandoffReady(targetIndex, incomingPlayer)) {
                Timber.tag(TAG).w(
                    "Crossfade2 manual primary did not become playable before timeout; keeping secondary active",
                )
            } else if (!performCrossfadeHandoff(targetIndex, incomingPlayer)) {
                Timber.tag(TAG).w(
                    "Crossfade2 manual primary handoff animation did not complete",
                )
            }'''

if old in s:
    s = s.replace(old, new, 1)
    print("  applied: manual handoff readiness")
elif "awaitManualPrimaryCrossfadeHandoffReady" in s:
    print("  skip: manual handoff already patched")
else:
    print("  skip: manual handoff structure differs")

if "private suspend fun awaitManualPrimaryCrossfadeHandoffReady(" not in s:

    anchor = "private suspend fun awaitPrimaryCrossfadeHandoffReady("

    idx = s.find(anchor)

    if idx >= 0:

        helper = r'''private suspend fun awaitManualPrimaryCrossfadeHandoffReady(
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
                    kotlin.math.abs(primaryPosition - secondaryPosition)

                if (drift <= CROSSFADE_HANDOFF_MAX_DRIFT_MS) {
                    return true
                }

                // Synchronize only once per meaningful drift instead of
                // continuously seeking while the two players are running.
                player.seekTo(targetIndex, secondaryPosition)

                delay(50L)

                if (
                    player.playbackState == Player.STATE_READY &&
                        player.playWhenReady &&
                        player.isPlaying
                ) {
                    return true
                }
            }
        }

        delay(25L)
    }

    return player.currentMediaItemIndex == targetIndex &&
        player.playbackState == Player.STATE_READY &&
        player.playWhenReady &&
        player.isPlaying
}

'''

        s = s[:idx] + helper + s[idx:]
        print("  applied: manual readiness helper")
    else:
        print("  skip: readiness helper anchor not found")

p.write_text(s)
PY

echo
echo "[6/8] Fixing artwork source duplication where safely detectable..."

PLAYER="app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Player.kt"

if [ -f "$PLAYER" ]; then
python3 - "$PLAYER" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old = "ytmUrl = mediaMetadata?.thumbnailUrl?.highRes(),"
new = "ytmUrl = mediaMetadata?.thumbnailUrl,"

if old in s:
    s = s.replace(old, new, 1)
    p.write_text(s)
    print("  applied: Player artwork keeps canonical thumbnail URI")
elif "ytmUrl = mediaMetadata?.thumbnailUrl," in s:
    print("  skip: Player artwork URI already normalized")
else:
    print("  skip: Player artwork expression differs")
PY
fi

THUMB="app/src/main/kotlin/moe/rukamori/archivetune/ui/player/Thumbnail.kt"

if [ -f "$THUMB" ]; then
python3 - "$THUMB" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old = "val shouldAttemptYT = videoId != null && !lowDataMode && isMusicVideo"

new = "val shouldAttemptYT = ytmUrl.isNullOrBlank() && videoId != null && !lowDataMode && isMusicVideo"

if old in s:
    s = s.replace(old, new, 1)
    p.write_text(s)
    print("  applied: avoid unnecessary YouTube artwork probe")
elif "ytmUrl.isNullOrBlank() && videoId != null" in s:
    print("  skip: YouTube artwork probe already guarded")
else:
    print("  skip: Thumbnail structure differs")
PY
fi

echo
echo "[7/8] Fixing notification artwork URI only when exact source exists..."

MEDIA_EXT="app/src/main/kotlin/moe/rukamori/archivetune/utils/MediaItemExt.kt"

if [ -f "$MEDIA_EXT" ]; then
python3 - "$MEDIA_EXT" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old = '''fun String.toNotificationArtworkUri(): Uri {
    return resize(
        width = NotificationArtworkSizePx,
        height = NotificationArtworkSizePx,
        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
    )?.toUri()
}'''

new = '''fun String.toNotificationArtworkUri(): Uri {
    // Keep the canonical artwork URI stable so Media3/Coil can reuse
    // the same cached artwork instead of creating a second transformed URI.
    return trim().toUri()
}'''

if old in s:
    s = s.replace(old, new, 1)
    p.write_text(s)
    print("  applied: stable notification artwork URI")
elif "Keep the canonical artwork URI stable" in s:
    print("  skip: notification URI already stabilized")
else:
    print("  skip: notification artwork function differs")
PY
fi

echo
echo "[8/8] Validation..."

echo
echo "--- git diff --check ---"
if ! git diff --check; then
    echo
    echo "ERROR: whitespace/diff validation failed."
    echo "Backups are in: $BACKUP"
    exit 1
fi

echo
echo "--- changed files ---"
git status --short

echo
echo "--- diff stat ---"
git diff --stat

echo
echo "--- important Crossfade symbols ---"
grep -nE \
'runCrossfade2ManualSelection|awaitManualPrimaryCrossfadeHandoffReady|performCrossfadeHandoff|secondaryCrossfadePlayer' \
"$MUSIC" || true

echo
echo "--- artwork symbols ---"
if [ -f "$PLAYER" ]; then
    grep -n "ytmUrl =" "$PLAYER" | head -10 || true
fi

if [ -f "$THUMB" ]; then
    grep -n "shouldAttemptYT" "$THUMB" | head -10 || true
fi

if [ -f "$MEDIA_EXT" ]; then
    grep -n "toNotificationArtworkUri" "$MEDIA_EXT" | head -10 || true
fi

echo
echo "=============================================="
echo " DONE - NO COMMIT / NO PUSH"
echo "=============================================="
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "الآن لا تستخدم git add ."
echo
echo "أرسل لي ناتج:"
echo
echo "git diff --check"
echo "git diff --stat"
echo "git status --short"
echo
echo "ثم:"
echo "git diff -- app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt"
echo
