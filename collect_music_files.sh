#!/data/data/com.termux/files/usr/bin/bash

set -e

OUT="$HOME/storage/downloads/music_playback_dump.txt"

echo "=== ArchiveTune_S Music Playback Source Dump ===" > "$OUT"
echo "Generated: $(date)" >> "$OUT"
echo "Project: $(pwd)" >> "$OUT"
echo "" >> "$OUT"

echo "=== PROJECT STRUCTURE (relevant files) ===" >> "$OUT"

find . \
  -type f \
  ! -path './.git/*' \
  ! -path './build/*' \
  ! -path './.gradle/*' \
  ! -path './app/build/*' \
  ! -path './node_modules/*' \
  \( \
    -iname '*music*' -o \
    -iname '*player*' -o \
    -iname '*playback*' -o \
    -iname '*audio*' -o \
    -iname '*media*' -o \
    -iname '*session*' -o \
    -iname '*queue*' -o \
    -iname '*service*' -o \
    -iname '*notification*' -o \
    -iname '*exo*' -o \
    -iname '*download*' \
  \) \
  | sort >> "$OUT"

echo "" >> "$OUT"
echo "==============================================" >> "$OUT"
echo "=== FILE CONTENTS ===" >> "$OUT"
echo "==============================================" >> "$OUT"

find . \
  -type f \
  ! -path './.git/*' \
  ! -path './build/*' \
  ! -path './.gradle/*' \
  ! -path './app/build/*' \
  ! -path './node_modules/*' \
  \( \
    -iname '*music*' -o \
    -iname '*player*' -o \
    -iname '*playback*' -o \
    -iname '*audio*' -o \
    -iname '*media*' -o \
    -iname '*session*' -o \
    -iname '*queue*' -o \
    -iname '*service*' -o \
    -iname '*notification*' -o \
    -iname '*exo*' -o \
    -iname '*download*' \
  \) \
  | sort | while IFS= read -r file; do

    echo "" >> "$OUT"
    echo "############################################################" >> "$OUT"
    echo "FILE: $file" >> "$OUT"
    echo "############################################################" >> "$OUT"

    if file "$file" | grep -qiE 'text|source|xml|json|kotlin|java|script'; then
        cat "$file" >> "$OUT"
    else
        echo "[Binary/non-text file - content omitted]" >> "$OUT"
    fi

    echo "" >> "$OUT"
done

echo "" >> "$OUT"
echo "==============================================" >> "$OUT"
echo "=== REFERENCES TO PLAYBACK APIs ===" >> "$OUT"
echo "==============================================" >> "$OUT"

grep -RInE \
  --exclude-dir=.git \
  --exclude-dir=build \
  --exclude-dir=.gradle \
  --exclude-dir=node_modules \
  --exclude='music_playback_dump.txt' \
  'ExoPlayer|MediaPlayer|AudioTrack|MediaSession|MediaController|play\(|pause\(|seekTo\(|seekToNext|seekToPrevious|setMediaItem|setMediaItems|prepare\(|PlayerConnection|MusicService|Playback|playback|audioFocus|AudioManager|QUEUE|queue' \
  . >> "$OUT" 2>/dev/null || true

echo ""
echo "=========================================="
echo "تم الانتهاء."
echo "الملف موجود هنا:"
echo "$OUT"
echo "=========================================="
echo ""
echo "الحجم:"
du -h "$OUT"

echo ""
echo "عدد الأسطر:"
wc -l "$OUT"
