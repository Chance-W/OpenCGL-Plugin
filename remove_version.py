import os
import re

directory = "/Users/chancew./Documents/Chance/code/self_code/OpenCGL-Plugin-New"

# Matches optional @Override followed by public String version() { ... }
pattern = re.compile(r'(\s*@Override\s*)?^[ \t]*public\s+String\s+version\s*\(\)\s*\{[^{}]*\}', re.MULTILINE)

count = 0
for root, _, files in os.walk(directory):
    if "PluginApiModule" in root:
        continue
    for file in files:
        if file.endswith(".java"):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content, num_subs = pattern.subn('', content)
            if num_subs > 0:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                count += 1
                print(f"Updated {path}")

print(f"Total files updated: {count}")
