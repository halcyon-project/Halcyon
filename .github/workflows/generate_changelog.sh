#!/bin/bash

# Set up variables
CHANGELOG_FILE="CHANGELOG.md"
CONTRIBUTORS_FILE="CONTRIBUTORS.md"

# Generate the changelog
npx conventional-changelog-cli -p angular -i $CHANGELOG_FILE -s

# Generate contributors list
echo "# Contributors" > $CONTRIBUTORS_FILE
echo "" >> $CONTRIBUTORS_FILE
git shortlog -sn --all | awk '{$1=""; print "* "$0}' >> $CONTRIBUTORS_FILE

# Print a success message
echo "Changelog and contributors list updated successfully."
