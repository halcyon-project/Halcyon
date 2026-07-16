import re

def generate_changelog(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()

    changelog = []
    for line in lines:
        date, author, subject = re.split(r"\s*\|\s*", line.strip())
        changelog.append(f"- **{date}**: {author} - {subject}")

    with open("formatted_changelog.md", 'w') as f:
        f.write("\n".join(changelog))

if __name__ == '__main__':
    generate_changelog('changelog.md')
