#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd -P)"
configuration="${IOS_CONFIGURATION:-Release}"
distribution="${NUVIO_IOS_DISTRIBUTION:-full}"
configuration_slug="$(printf '%s' "${configuration}" | tr '[:upper:]' '[:lower:]')"

case "${configuration}" in
    Debug|Release) ;;
    *)
        echo "IOS_CONFIGURATION must be 'Debug' or 'Release'." >&2
        exit 1
        ;;
esac

if [[ "${distribution}" != "full" && "${distribution}" != "appstore" ]]; then
    echo "NUVIO_IOS_DISTRIBUTION must be 'full' or 'appstore'." >&2
    exit 1
fi

"${repository_root}/scripts/prepare-ios-dependencies.sh"

if [[ ! -f "${repository_root}/local.properties" ]]; then
    touch "${repository_root}/local.properties"
fi

app_version="$(sed -n 's/^[[:space:]]*MARKETING_VERSION[[:space:]]*=[[:space:]]*//p' \
    "${repository_root}/iosApp/Configuration/Version.xcconfig" | tail -n 1)"
app_version="${app_version:-0.0.0}"

derived_data="${repository_root}/build/ios-derived-${distribution}"

env NUVIO_IOS_DISTRIBUTION="${distribution}" xcodebuild \
    -project "${repository_root}/iosApp/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration "${configuration}" \
    -sdk iphoneos \
    -derivedDataPath "${derived_data}" \
    'PRODUCT_NAME=$(TARGET_NAME)' \
    CODE_SIGNING_ALLOWED=NO \
    build

products_directory="${derived_data}/Build/Products/${configuration}-iphoneos"
app_path="$(find "${products_directory}" -maxdepth 1 -type d -name '*.app' -print -quit)"
if [[ -z "${app_path}" ]]; then
    echo "No app bundle was produced in ${products_directory}." >&2
    exit 1
fi

staging_directory="$(mktemp -d "${TMPDIR:-/tmp}/raif-ipa.XXXXXX")"
trap 'rm -rf "${staging_directory}"' EXIT

mkdir -p "${staging_directory}/Payload"
ditto "${app_path}" "${staging_directory}/Payload/$(basename "${app_path}")"

output_directory="${repository_root}/build/ios-ipa"
mkdir -p "${output_directory}"
ipa_path="${output_directory}/Raif-${app_version}-${distribution}-${configuration_slug}.ipa"
rm -f "${ipa_path}"
(cd "${staging_directory}" && zip --quiet --recurse-paths "${ipa_path}" Payload)

echo "${ipa_path}"
