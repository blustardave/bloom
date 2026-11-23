#!/usr/bin/env bash
# Updates one app's line in the "latest" release notes, keeping the other's, e.g.
#   TV app: 0.3.170 (build 253)
#   Mac app: 0.3.168 (build 251)
# Usage: .github/release-notes.sh "TV app" 0.3.170   (needs GH_TOKEN and BLOOM_BUILD)
set -euo pipefail
APP="$1"; VERSION="$2"; REPO="${GITHUB_REPOSITORY}"
OLD=$(gh release view latest -R "$REPO" --json body -q .body 2>/dev/null || true)
LINE="$APP: $VERSION (build $BLOOM_BUILD, $(date -u +'%-d %b %Y %H:%M') UTC)"
{
  for A in "TV app" "Mac app"; do
    if [ "$A" = "$APP" ]; then echo "$LINE"
    else echo "$OLD" | grep -E "^$A: " || true
    fi
  done
} > notes.txt
cat notes.txt
gh release edit latest -R "$REPO" --notes-file notes.txt
