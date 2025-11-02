#!/bin/bash

# Script to fix string resource formatting issues
# Clean version without UTF-8 encoding issues

echo "🔧 Start fixing string resource formatting issues..."

# List of strings to fix
declare -a strings_to_fix=(
    "error_eip712_incompatible_network"
    "notification_message_incoming_token"
    "notification_message_incoming_token_with_recipient"
    "link_valid_until"
    "set_price_subtext_abr"
    "developer_override_warning"
    "total_cost_for_x_tickets"
)

# Language directories to fix
declare -a language_dirs=(
    "values"
    "values-zh"
    "values-es"
    "values-fr"
    "values-id"
    "values-my"
    "values-vi"
)

# Fix function
fix_string_resource() {
    local file_path="$1"
    local string_name="$2"

    if [[ -f ${file_path} ]]; then
        # Check if string contains multiple placeholders
        if grep -q "name=\"${string_name}\"" "${file_path}" && grep -q "name=\"${string_name}\"" "${file_path}" | grep -q "%[0-9]*s"; then
            # If string contains multiple placeholders but no formatted="false", add it
            if ! grep -q "name=\"${string_name}\".*formatted=\"false\"" "${file_path}"; then
                echo "  📝 Fix ${string_name} in ${file_path}"
                # Use sed to replace, add formatted="false" attribute
                sed -i '' "s/name=\"${string_name}\">/name=\"${string_name}\" formatted=\"false\">/g" "${file_path}"
            fi
        fi
    fi
}

# Iterate through all language directories
for lang_dir in "${language_dirs[@]}"; do
    strings_file="app/src/main/res/${lang_dir}/strings.xml"

    if [[ -f ${strings_file} ]]; then
        echo "🔍 Check ${strings_file}"

        # Fix each string that needs fixing
        for string_name in "${strings_to_fix[@]}"; do
            fix_string_resource "${strings_file}" "${string_name}"
        done
    fi
done

echo "✅ String resource fix completed"
