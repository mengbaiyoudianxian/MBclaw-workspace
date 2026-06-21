#!/usr/bin/env python3
"""MBclaw Linux CLI — 对接 r1 API"""
import json, os, sys, argparse
from pathlib import Path
import urllib.request, urllib.error

CONFIG_DIR = Path.home() / ".mbclaw"
CONFIG_FILE = CONFIG_DIR / "config.json"
DEFAULT = {"server_url": "http://127.0.0.1:8000", "api_key": "", "user_name": ""}

def load(): 
    if CONFIG_FILE.exists(): return {**DEFAULT, **json.loads(CONFIG_FILE.read_text())}
    return DEFAULT
def save(c): CONFIG_DIR.mkdir(parents=True, exist_ok=True); CONFIG_FILE.write_text(json.dumps(c, indent=2))

def api(path, method="GET", data=None):
    c = load()
    url = f"{c['server_url'].rstrip('/')}{path}"
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if data: req.data = json.dumps(data).encode()
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}: {e.reason}"}
    except Exception as e:
        return {"error": str(e)}

def cmd_chat(args):
    print("🦞 MBclaw Chat (/exit to quit, /close to summarize)")
    sid = api("/sessions", "POST", {"title": "CLI Chat"}).get("session_id")
    if not sid: print("Cannot create session"); return
    while True:
        try: msg = input("\nYou: ")
        except (EOFError, KeyboardInterrupt): print("\nBye."); break
        if msg in ("/exit","/quit"): break
        if msg == "/close":
            r = api(f"/sessions/{sid}/close", "POST")
            print(f"Summary: {r.get('summary','')[:200]}")
            print(f"Category: {r.get('category','')}")
            print(f"Keywords: {[k['keyword'] for k in r.get('keywords',[])][:5]}")
            break
        r = api(f"/sessions/{sid}/messages", "POST", {"role":"user","content":msg})
        print(f"MBclaw: (sent, id={r.get('id','?')})")

def cmd_search(args):
    r = api(f"/search/layered?q={args.query}&limit={args.limit or 5}")
    for h in r if isinstance(r,list) else r.get("hits",[]):
        print(f"  [#{h['session_id']}] score={h.get('score',0):.2f} {h['summary'][:120]}")
    if not r: print("No results.")

def cmd_feedback(args):
    r = api("/feedback", "POST", {"session_id": args.sid, "rating": args.rating, "category": args.cat or "general", "comment": args.comment or ""})
    print(r)

def cmd_snapshot(args):
    if args.list:
        for s in api("/snapshots"):
            print(f"  {s['filename']} ({s['size_bytes']} bytes, {s['created_at'][:19]})")
    else:
        r = api("/snapshots", "POST", {"name": args.name, "description": args.desc or ""})
        print(f"Snapshot created: {r.get('filename','?')} ({r.get('size_bytes',0)} bytes)")

def cmd_status(args):
    h = api("/health"); m = api("/metrics"); a = api("/agent/status")
    print(f"Server: {'UP' if h.get('db_ok') else 'DOWN'}")
    print(f"Sessions: {m.get('sessions_created',0)} created / {m.get('sessions_closed',0)} closed")
    print(f"Search hit rate: {m.get('search_hit_rate','N/A')}")
    print(f"LLM error rate: {m.get('llm_error_rate','N/A')}")
    print(f"Agent: {'active' if a.get('active') else 'idle'} (msgs: {a.get('message_count',0)})")

def cmd_config(args):
    c = load()
    if args.set:
        k,v = args.set.split("=",1); c[k] = v; save(c); print(f"Set {k}={v}")
    for k,v in c.items(): print(f"  {k}: {v}")

def cmd_agent(args):
    r = api("/agent/run", "POST", {"message": args.message, "max_turns": args.turns or 3})
    if "error" in r: print(r["error"])
    else: print(r.get("response", str(r)))

def main():
    p = argparse.ArgumentParser(description="MBclaw CLI")
    sp = p.add_subparsers(dest="cmd")
    sp.add_parser("config").add_argument("--set", help="key=value")
    sp.add_parser("status")
    a = sp.add_parser("agent"); a.add_argument("-m","--message",required=True); a.add_argument("-t","--turns",type=int,default=3)
    sp.add_parser("chat")
    s = sp.add_parser("search"); s.add_argument("query"); s.add_argument("-l","--limit",type=int)
    f = sp.add_parser("feedback"); f.add_argument("sid",type=int); f.add_argument("rating",type=int); f.add_argument("-c","--cat"); f.add_argument("-m","--comment")
    sn = sp.add_parser("snapshot"); sn.add_argument("name",nargs="?"); sn.add_argument("-d","--desc"); sn.add_argument("-l","--list",action="store_true")
    args = p.parse_args()
    {"config":cmd_config,"status":cmd_status,"agent":cmd_agent,"chat":cmd_chat,"search":cmd_search,"feedback":cmd_feedback,"snapshot":cmd_snapshot}.get(args.cmd,lambda _:p.print_help())(args)

if __name__ == "__main__": main()
