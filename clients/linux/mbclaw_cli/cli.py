#!/usr/bin/env python3
"""
MBclaw Linux CLI Client

A terminal-based client for interacting with MBclaw Server.
Supports: chat, project management, session review, memory search.

Usage:
    mbclaw chat                    # Start interactive chat
    mbclaw chat "你的问题"         # One-shot query
    mbclaw projects                # List projects
    mbclaw sessions [project_id]   # List sessions
    mbclaw search "关键词"         # Search memory
    mbclaw config                  # Show/change config
    mbclaw snapshot [project_id]   # Create project snapshot
"""

import json
import os
import sys
import argparse
from pathlib import Path
from typing import Optional

import requests

CONFIG_DIR = Path.home() / ".mbclaw"
CONFIG_FILE = CONFIG_DIR / "config.json"
DEFAULT_CONFIG = {
    "server_url": "http://localhost:8000",
    "api_key": "",
    "user_name": "",
    "user_id": None,
}


def load_config() -> dict:
    if CONFIG_FILE.exists():
        return {**DEFAULT_CONFIG, **json.loads(CONFIG_FILE.read_text())}
    return DEFAULT_CONFIG


def save_config(cfg: dict):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(cfg, indent=2, ensure_ascii=False))


def api(path: str, method: str = "GET", data: dict = None) -> dict:
    cfg = load_config()
    url = f"{cfg['server_url'].rstrip('/')}{path}"
    headers = {"Authorization": f"Bearer {cfg['api_key']}"} if cfg["api_key"] else {}
    resp = requests.request(method, url, json=data, headers=headers, timeout=30)
    resp.raise_for_status()
    if resp.status_code == 204:
        return {}
    return resp.json()


def cmd_config(args):
    cfg = load_config()
    if args.set:
        key, value = args.set.split("=", 1)
        cfg[key] = value
        save_config(cfg)
        print(f"✅ {key} = {value}")
    else:
        print(json.dumps(cfg, indent=2, ensure_ascii=False))


def cmd_chat(args):
    message = args.message
    if not message:
        # Interactive mode
        print("MBclaw Chat (输入 /exit 退出, /history 查看历史)")
        cfg = load_config()
        # Ensure user exists
        if not cfg.get("user_name"):
            cfg["user_name"] = input("用户名: ") or "cli-user"
            save_config(cfg)
        user = ensure_user(cfg["user_name"])
        project = ensure_project(user["id"], "default")
        while True:
            try:
                msg = input("\n🧑 > ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\n👋 再见！")
                break
            if not msg:
                continue
            if msg == "/exit":
                break
            if msg == "/history":
                sessions = api(f"/projects/{project['id']}/sessions")
                for s in sessions:
                    print(f"  [{s['id']}] {s.get('title','')} ({s.get('status','')})")
                continue
            # Send to server
            session = api(f"/projects/{project['id']}/sessions", "POST",
                         {"session_number": 1, "title": msg[:50]})
            api(f"/sessions/{session['id']}/messages", "POST",
                {"role": "user", "content": msg})
            print(f"  [会话 {session['id']} 已创建，等待 Agent 处理...]")
            print("  (Agent 执行需在服务端触发 /api/agent/run)")


def ensure_user(name: str) -> dict:
    users = api("/users")
    for u in users:
        if u["name"] == name:
            cfg = load_config()
            cfg["user_id"] = u["id"]
            save_config(cfg)
            return u
    user = api("/users", "POST", {"name": name})
    cfg = load_config()
    cfg["user_id"] = user["id"]
    cfg["user_name"] = name
    save_config(cfg)
    return user


def ensure_project(user_id: int, name: str) -> dict:
    projects = api("/projects")
    for p in projects:
        if p["name"] == name and p["user_id"] == user_id:
            return p
    return api("/projects", "POST", {"user_id": user_id, "name": name})


def cmd_projects(args):
    projects = api("/projects")
    if not projects:
        print("(暂无项目)")
        return
    for p in projects:
        print(f"  [{p['id']}] {p['name']} (user={p['user_id']}) {p.get('created_at','')}")


def cmd_sessions(args):
    project_id = args.project_id
    if not project_id:
        projects = api("/projects")
        if not projects:
            print("请先创建项目")
            return
        project_id = projects[0]["id"]
    sessions = api(f"/projects/{project_id}/sessions")
    if not sessions:
        print("(暂无会话)")
        return
    for s in sessions:
        status_icon = {"active": "🟢", "completed": "✅", "interrupted": "⏸️"}.get(s.get("status",""), "❓")
        print(f"  {status_icon} [{s['id']}] #{s.get('session_number','')} {s.get('title','')} ({s.get('status','')})")


def cmd_search(args):
    results = api(f"/search?q={args.query}")
    if not results:
        print("(无匹配结果)")
        return
    for r in results:
        print(f"  [{r.get('type','')}] {r.get('content','')[:200]}")


def cmd_snapshot(args):
    project_id = args.project_id
    if not project_id:
        projects = api("/projects")
        if not projects:
            print("请先创建项目")
            return
        project_id = projects[0]["id"]
    result = api(f"/projects/{project_id}/snapshots", "POST")
    print(f"✅ 快照已创建: {result}")


def cmd_status(args):
    try:
        health = api("/health")
        print(f"✅ 服务器: {health.get('status','?')}")
        print(f"   Uptime: {health.get('uptime_seconds',0)}s")
    except Exception as e:
        print(f"❌ 无法连接服务器: {e}")

    cfg = load_config()
    print(f"   配置: {cfg['server_url']}")


def main():
    parser = argparse.ArgumentParser(description="MBclaw CLI Client")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("config").add_argument("--set", help="设置配置项, e.g. server_url=http://host:8000")
    chat_p = sub.add_parser("chat")
    chat_p.add_argument("message", nargs="?", default="", help="消息内容（留空进入交互模式）")
    sub.add_parser("projects")
    sess_p = sub.add_parser("sessions")
    sess_p.add_argument("project_id", nargs="?", type=int, help="项目ID")
    search_p = sub.add_parser("search")
    search_p.add_argument("query", help="搜索关键词")
    snap_p = sub.add_parser("snapshot")
    snap_p.add_argument("project_id", nargs="?", type=int, help="项目ID")
    sub.add_parser("status")

    args = parser.parse_args()

    if args.command == "config":
        cmd_config(args)
    elif args.command == "chat":
        cmd_chat(args)
    elif args.command == "projects":
        cmd_projects(args)
    elif args.command == "sessions":
        cmd_sessions(args)
    elif args.command == "search":
        cmd_search(args)
    elif args.command == "snapshot":
        cmd_snapshot(args)
    elif args.command == "status":
        cmd_status(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
