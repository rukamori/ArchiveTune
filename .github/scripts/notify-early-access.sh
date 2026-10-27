#!/usr/bin/env bash
set -euo pipefail

for variable in DISCORD_BOT_TOKEN DISCORD_CHANNEL_ID EARLY_ACCESS_SITE_ORIGIN GITHUB_SHA GITHUB_REF_NAME; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'Required environment variable not configured: %s\n' "$variable" >&2
    exit 1
  fi
done

if [[ ! "$DISCORD_CHANNEL_ID" =~ ^[0-9]+$ ]] || [[ ! "$EARLY_ACCESS_SITE_ORIGIN" =~ ^https://[a-zA-Z0-9.-]+$ ]]; then
  printf 'Invalid Discord channel ID or portal origin\n' >&2
  exit 1
fi

if [[ ! "$GITHUB_REF_NAME" =~ ^(main|dev)$ ]] || [[ ! "$GITHUB_SHA" =~ ^[a-f0-9]{40}$ ]]; then
  printf 'Invalid branch or commit\n' >&2
  exit 1
fi

short_sha="${GITHUB_SHA:0:7}"
portal_url="${EARLY_ACCESS_SITE_ORIGIN}/early-access"
published_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

jq -nc \
  --arg branch "$GITHUB_REF_NAME" \
  --arg commit "$short_sha" \
  --arg portal_url "$portal_url" \
  --arg published_at "$published_at" \
  '{
    embeds: [
      {
        title: "ArchiveTune | Early Access Build Available",
        url: $portal_url,
        description: "A new **signed Release build** is available for testing. All **7 APK variants** are ready in the private Early Access portal.\n\nSign in with your approved Discord account to view and download your build.",
        color: 5793266,
        fields: [
          { name: "Channel", value: "Early Access", inline: true },
          { name: "Variants", value: "7 Release APKs", inline: true },
          { name: "Branch", value: ("`" + $branch + "`"), inline: true },
          { name: "Commit", value: ("`" + $commit + "`"), inline: true },
          { name: "Access", value: "Authorized Discord members only", inline: false }
        ],
        footer: { text: "ArchiveTune • Private Build Distribution" },
        timestamp: $published_at
      }
    ],
    components: [
      {
        type: 1,
        components: [
          {
            type: 2,
            style: 5,
            label: "Open Early Access Portal",
            url: $portal_url
          }
        ]
      }
    ],
    allowed_mentions: { parse: [] }
  }' |
  curl --fail-with-body --silent --show-error \
    --retry 4 --retry-delay 3 --retry-all-errors \
    -X POST "https://discord.com/api/v10/channels/${DISCORD_CHANNEL_ID}/messages" \
    -H "Authorization: Bot ${DISCORD_BOT_TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary @- >/dev/null

printf 'Discord rich embed notification sent for %s.\n' "$short_sha"
