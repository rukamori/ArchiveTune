import os
import subprocess
import requests
import re

def get_git_commit_info():
    commit_author = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%an']).decode('utf-8')
    commit_message = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%s']).decode('utf-8')
    commit_hash = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%H']).decode('utf-8')
    commit_hash_short = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%h']).decode('utf-8')
    return commit_author, commit_message, commit_hash, commit_hash_short

def escape_markdown_v2(text):
    escape_chars = r'_~`#+-=|{}.!'
    return re.sub(r'([%s])' % re.escape(escape_chars), r'\\\1', text)

def escape_parentheses(text):
    return re.sub(r'([()])', r'\\\1', text)

def main():
    bot_token = os.environ['BOT_TOKEN']
    chat_id = os.environ['CHAT_ID']
    topic_id = os.environ.get('TOPIC_ID')

    commit_author, commit_message, commit_hash, commit_hash_short = get_git_commit_info()

    message = (
        f"A new [commit](https://github.com/rukamori/ArchiveTune/commit/{commit_hash}) has been merged to the repository by *{commit_author}*.\n\n"
        f"*What has changed:*\n>{escape_parentheses(commit_message)}\n\n"
        f"I'm currently building it and will send you the APKs here within ~9 mins if the build is successful.\n\n#{commit_hash_short}"
    )

    escaped_message = escape_markdown_v2(message)

    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": escaped_message,
        "parse_mode": "markdownv2",
        "disable_web_page_preview": True
    }
    if topic_id:
        payload["message_thread_id"] = topic_id

    response = requests.post(url, json=payload)
    if response.status_code != 200:
        print(f"Failed to send message: {response.status_code} {response.text}")
    else:
        print("Message sent successfully.")

if __name__ == "__main__":
    main()
