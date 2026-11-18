#!/usr/bin/env bash
# measure.sh — reproducible baseline / verification measurements for Karma Launcher perf audit.
# Run UNCHANGED before and after fixes, on the same machine, data and settings.
# POSIX sh / bash. On Windows without a shell, the equivalent checks are run via PowerShell
# (see OPTIMIZE_REPORT.md, section "What I could not measure or got wrong").
set -uo pipefail

cd "$(dirname "$0")"

echo "============================================================"
echo "KARMA LAUNCHER - PERFORMANCE MEASUREMENT"
echo "Run at: $(date -u +'%Y-%m-%dT%H:%M:%SZ') (UTC)"
echo "============================================================"

# --- 1. Environment / tool versions -----------------------------------------
echo "--- Environment ---"
echo "machine:        $(uname -srm 2>/dev/null || echo unknown)"
echo "git_commit:     $(git rev-parse HEAD 2>/dev/null || echo n/a)"
echo "git_branch:     $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo n/a)"
echo "git status:     $(git status --porcelain 2>/dev/null | wc -l | tr -d ' ') changed file(s)"
echo "git --version:  $(git --version 2>/dev/null || echo n/a)"
echo "java -version:  $(java -version 2>&1 | head -n 1 || echo n/a)"
echo "python3:        $(python3 --version 2>/dev/null || echo n/a)"
echo "ANDROID_HOME:   ${ANDROID_HOME:-<unset>}"
echo "ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT:-<unset>}"
echo "local.properties: $([ -f local.properties ] && echo present || echo absent)"

# --- 2. Source fingerprints (prove no unintended behaviour change) ----------
# git blob hashes are stable fingerprints of file content. "HEAD" is the
# pre-audit baseline; "WORKTREE" is the current state under measurement.
echo "--- Source fingerprints (sha1 via git hash-object; HEAD = pre-audit baseline) ---"
FINGERPRINT_FILES="
build.gradle
gradle.properties
lawnchair/src/app/lawnchair/search/algorithms/engine/provider/apps/AppMatcher.kt
lawnchair/src/app/lawnchair/search/algorithms/engine/provider/apps/AppSearchProvider.kt
lawnchair/src/app/lawnchair/search/algorithms/SearchUtils.kt
lawnchair/src/app/lawnchair/search/algorithms/engine/provider/SettingsSearchProvider.kt
lawnchair/src/app/lawnchair/search/algorithms/engine/provider/ContactsSearchProvider.kt
lawnchair/src/app/lawnchair/search/algorithms/engine/provider/CalculatorSearchProvider.kt
lawnchair/src/app/lawnchair/backup/NovaBackupConverter.kt
.github/workflows/ci.yml
"
for f in $FINGERPRINT_FILES; do
  [ -f "$f" ] || continue
  head_h=$(git rev-parse "HEAD:$f" 2>/dev/null || echo "<missing>")
  work_h=$(git hash-object "$f" 2>/dev/null || echo "<missing>")
  if [ "$head_h" = "$work_h" ]; then state=UNCHANGED; else state=CHANGED; fi
  printf '  %-90s %s\n' "$f" "$state"
  printf '      HEAD=%s\n      WORK=%s\n' "$head_h" "$work_h"
done

# --- 3. Static performance configuration checks -----------------------------
echo "--- Static config checks ---"
locale_dirs=$(find res lawnchair/res -maxdepth 1 -type d -name 'values-*' 2>/dev/null | wc -l | tr -d ' ')
echo "  locale resource dirs (values-*):        $locale_dirs"
echo "  resourceConfigurations line:            $(grep -n 'resourceConfigurations' build.gradle | head -n 1 || echo none)"
echo "  minifyEnabled / shrinkResources:        $(grep -nE 'minifyEnabled|shrinkResources' build.gradle | tr '\n' ' ')"
echo "  datastore.preferences decls in build.gradle: $(grep -c 'libs.androidx.datastore.preferences' build.gradle)"
echo "  incremental flags in gradle.properties: $(grep -cE 'kotlin.incremental|ksp.incremental' gradle.properties)"
echo "  CI build variants:                      $(grep -n 'assembleLawnWithQuickstep' .github/workflows/ci.yml | head -n 1 | tr -d ' ')"

# --- 4. Build + APK size (only when toolchain is available) -----------------
echo "--- Build / APK size ---"
sdk_ok=false
if { [ -n "${ANDROID_HOME:-}" ] || [ -f local.properties ]; } && java -version 2>&1 | head -n 1 | grep -qE '"(21|22|23|24|25)' ; then
  sdk_ok=true
fi
if [ "$sdk_ok" = true ]; then
  echo "Building release variant (timed)..."
  /usr/bin/time -f 'build_wall_seconds=%e' ./gradlew assembleLawnWithQuickstepNightlyRelease --no-daemon
  echo "APK outputs:"
  find build/outputs/apk -name '*.apk' -printf '%s %p\n' 2>/dev/null | sort -nr
else
  echo "SKIPPED: no Android SDK (ANDROID_HOME/local.properties) and/or JDK != 21."
  echo "         Build time, per-route JS size, Lighthouse LCP/TBT/CLS and APK size are NOT measured here."
  echo "         Run this script on CI or a machine with the Android 16 SDK + JDK 21 to populate them."
fi

echo "============================================================"
echo "END OF MEASUREMENT"
echo "============================================================"
