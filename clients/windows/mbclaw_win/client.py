"""MBclaw Windows Desktop Client

A tkinter-based GUI client for Windows.
Connects to MBclaw Server for full functionality.
"""

import json
import os
import sys
import threading
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
from pathlib import Path
from typing import Optional

import requests  # type: ignore

CONFIG_DIR = Path(os.environ.get("APPDATA", Path.home())) / "MBclaw"
CONFIG_FILE = CONFIG_DIR / "config.json"


class MBclawClient:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("MBclaw — AI 长期记忆助手")
        self.root.geometry("900x600")
        self.cfg = self._load_config()
        self._setup_ui()
        self._check_connection()

    # ── config ──

    def _load_config(self) -> dict:
        defaults = {"server_url": "http://localhost:8000", "api_key": "", "user_name": "", "user_id": None}
        if CONFIG_FILE.exists():
            return {**defaults, **json.loads(CONFIG_FILE.read_text(encoding="utf-8"))}
        return defaults

    def _save_config(self):
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        CONFIG_FILE.write_text(json.dumps(self.cfg, indent=2, ensure_ascii=False), encoding="utf-8")

    def _api(self, path: str, method: str = "GET", data: dict = None):
        url = f"{self.cfg['server_url'].rstrip('/')}{path}"
        headers = {"Authorization": f"Bearer {self.cfg['api_key']}"} if self.cfg.get("api_key") else {}
        resp = requests.request(method, url, json=data, headers=headers, timeout=30)
        resp.raise_for_status()
        if resp.status_code == 204:
            return {}
        return resp.json()

    # ── UI ──

    def _setup_ui(self):
        # Menu
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)
        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="设置", command=self._show_settings)
        file_menu.add_command(label="退出", command=self.root.quit)
        menubar.add_cascade(label="文件", menu=file_menu)

        # Main layout
        paned = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)

        # Left panel: projects & sessions
        left = ttk.Frame(paned, width=250)
        paned.add(left, weight=0)

        ttk.Label(left, text="📁 项目", font=("", 12, "bold")).pack(pady=4)
        self.project_list = tk.Listbox(left, height=12)
        self.project_list.pack(fill=tk.BOTH, expand=True, padx=4)
        self.project_list.bind("<<ListboxSelect>>", self._on_project_select)

        ttk.Label(left, text="💬 会话", font=("", 12, "bold")).pack(pady=4)
        self.session_list = tk.Listbox(left, height=12)
        self.session_list.pack(fill=tk.BOTH, expand=True, padx=4)
        self.session_list.bind("<<ListboxSelect>>", self._on_session_select)

        # Right panel: chat
        right = ttk.Frame(paned)
        paned.add(right, weight=1)

        self.chat_display = scrolledtext.ScrolledText(right, state=tk.DISABLED, wrap=tk.WORD)
        self.chat_display.pack(fill=tk.BOTH, expand=True, padx=4, pady=4)

        # Input bar
        input_frame = ttk.Frame(right)
        input_frame.pack(fill=tk.X, padx=4, pady=4)
        self.input_entry = ttk.Entry(input_frame)
        self.input_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self.input_entry.bind("<Return>", self._on_send)
        ttk.Button(input_frame, text="发送", command=self._on_send).pack(side=tk.RIGHT, padx=4)

        # Status bar
        self.status_bar = ttk.Label(self.root, text="就绪", relief=tk.SUNKEN)
        self.status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def _append_chat(self, role: str, text: str):
        self.chat_display.config(state=tk.NORMAL)
        prefix = {"user": "🧑 你", "assistant": "🤖 MBclaw", "system": "⚙️ 系统"}
        self.chat_display.insert(tk.END, f"\n{prefix.get(role, role)}:\n{text}\n")
        self.chat_display.see(tk.END)
        self.chat_display.config(state=tk.DISABLED)

    def _check_connection(self):
        def _check():
            try:
                health = self._api("/health")
                self.status_bar.config(text=f"✅ 已连接 | Status: {health.get('status','?')}")
            except Exception as e:
                self.status_bar.config(text=f"❌ 无法连接服务器: {e}")
        threading.Thread(target=_check, daemon=True).start()

    def _refresh_projects(self):
        try:
            projects = self._api("/projects")
            self.project_list.delete(0, tk.END)
            for p in projects:
                self.project_list.insert(tk.END, f"[{p['id']}] {p['name']}")
        except Exception as e:
            self.status_bar.config(text=f"加载项目失败: {e}")

    def _on_project_select(self, event):
        selection = self.project_list.curselection()
        if not selection:
            return
        text = self.project_list.get(selection[0])
        pid = int(text.split("]")[0][1:])
        try:
            sessions = self._api(f"/projects/{pid}/sessions")
            self.session_list.delete(0, tk.END)
            for s in sessions:
                icon = {"active": "🟢", "completed": "✅", "interrupted": "⏸️"}.get(s.get("status", ""), "❓")
                self.session_list.insert(tk.END, f"{icon} [{s['id']}] {s.get('title','')}")
        except Exception as e:
            self.status_bar.config(text=f"加载会话失败: {e}")

    def _on_session_select(self, event):
        pass

    def _on_send(self, event=None):
        msg = self.input_entry.get().strip()
        if not msg:
            return
        self.input_entry.delete(0, tk.END)
        self._append_chat("user", msg)
        # TODO: Full integration with agent runtime

    def _show_settings(self):
        win = tk.Toplevel(self.root)
        win.title("设置")
        win.geometry("400x300")
        ttk.Label(win, text="服务器地址:").pack(pady=4)
        url_entry = ttk.Entry(win, width=50)
        url_entry.insert(0, self.cfg.get("server_url", ""))
        url_entry.pack(pady=4)
        ttk.Label(win, text="API Key:").pack(pady=4)
        key_entry = ttk.Entry(win, width=50, show="*")
        key_entry.insert(0, self.cfg.get("api_key", ""))
        key_entry.pack(pady=4)

        def _save():
            self.cfg["server_url"] = url_entry.get()
            self.cfg["api_key"] = key_entry.get()
            self._save_config()
            win.destroy()
            self._check_connection()

        ttk.Button(win, text="保存", command=_save).pack(pady=12)


def main():
    root = tk.Tk()
    app = MBclawClient(root)
    root.mainloop()


if __name__ == "__main__":
    main()
