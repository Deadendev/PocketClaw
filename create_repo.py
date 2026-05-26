import os
import requests
import sys

def create_github_repo(repo_name, description, visibility, token):
    url = f"https://api.github.com/repos/Deadendev/{repo_name}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    payload = {
        "name": repo_name,
        "description": description,
        "private": visibility == "private",
        "auto_init": True
    }
    response = requests.post(url, json=payload, headers=headers)
    if response.status_code == 201:
        print(f"Repository '{repo_name}' created successfully!")
        return True
    else:
        print(f"Error: {response.status_code} - {response.text}")
        return False

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: python create_repo.py <repo_name> <description> <visibility>")
        sys.exit(1)
    
    repo_name = sys.argv[1]
    description = sys.argv[2]
    visibility = sys.argv[3]  # 'public' or 'private'
    token = os.getenv("GITHUB_TOKEN")
    if not token:
        print("Error: GITHUB_TOKEN environment variable not set")
        sys.exit(1)
    
    create_github_repo(repo_name, description, visibility, token)