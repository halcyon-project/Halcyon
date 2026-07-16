# Get the latest commit hash
git rev-parse HEAD > latest_commit.txt

# Get the last tag
last_tag=$(git describe --tags --abbrev=0 --match="v*" | tail -n 1)

# Generate the changelog
if [ -n "$last_tag" ]; then
    git log --pretty=format:'%ad | %an | %s' --no-merges --since="$last_tag" > changelog.md
else
    echo "No tag found. Generating changelog for all commits."
    git log --pretty=format:'%ad | %an | %s' --no-merges > changelog.md
fi

rm latest_commit.txt

# Format the changelog
python format.py

# Get contributors
git log --format='%aN' | sort -u | tee contributors.txt
