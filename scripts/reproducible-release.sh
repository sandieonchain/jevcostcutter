#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
work_root=$(mktemp -d "${TMPDIR:-/tmp}/jevopt-repro.XXXXXX")
cleanup() {
  case "$work_root" in
    "${TMPDIR:-/tmp}"/jevopt-repro.*) rm -rf -- "$work_root" ;;
    *) echo "Refusing unsafe cleanup path" >&2; exit 1 ;;
  esac
}
trap cleanup EXIT HUP INT TERM

build_once() {
  pass=$1
  gradle_home="$work_root/gradle-$pass"
  project_cache="$work_root/project-$pass"
  output="$work_root/output-$pass"
  mkdir -p "$gradle_home" "$project_cache" "$output"
  (
    cd "$project_root"
    GRADLE_USER_HOME="$gradle_home" ./gradlew --no-daemon --no-problems-report --project-cache-dir "$project_cache" clean :jevopt-cli:distZip :jevopt-cli:distTar sbom checksums
    cp jevopt-cli/build/distributions/jevopt-*.zip "$output/"
    cp jevopt-cli/build/distributions/jevopt-*.tar "$output/"
    cp build/reports/sbom/jevopt.cdx.json "$output/"
    cp build/reports/checksums/SHA256SUMS "$output/"
  )
}

build_once one
build_once two
diff -ru "$work_root/output-one" "$work_root/output-two"
echo "Reproducible release artifacts: PASS"
