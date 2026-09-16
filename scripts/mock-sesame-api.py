"""検証用の Sesame API モックサーバー（BL-132）。

実資格情報・実 Sesame デバイスを使わずに「施錠/解錠の成功」を作り出し、
ウィジェット操作 → mobile 実行 → DataItem 同期 → ウォッチの Tile 追随、
ウォッチ Tile 操作 → mobile 実行 → ウィジェット追随、の双方向を確認するために使う
（rules/guardrails-unified.v1.md 12.5 が許容するモック限定の疎通確認）。

使い方:

    python scripts/mock-sesame-api.py
    ./gradlew :mobile:installDebug -PsesameApiBaseUrl=http://<PCのIP>:8080/api/sesame2

エンドポイント（本番 API と同じ形）:

- GET  /api/sesame2/<uuid>        → 現在の状態を返す
- POST /api/sesame2/<uuid>/cmd    → cmd 82=施錠 / 83=解錠 を受けて状態を書き換え 200 を返す

uuid は任意の文字列を受け付け、状態はプロセス内メモリにのみ保持する（初期値は施錠中）。
署名(sign)は検証しない。実鍵・実資格情報は一切扱わない。
"""

import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOCK_CMD = 82
UNLOCK_CMD = 83

# uuid -> locked?
STATE: dict[str, bool] = {}
PATH_RE = re.compile(r"^/api/sesame2/([^/]+)(/cmd)?$")


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, payload: dict | None = None) -> None:
        body = json.dumps(payload or {}).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        m = PATH_RE.match(self.path)
        if not m or m.group(2):
            self._send(404, {"message": "not found"})
            return
        uuid = m.group(1)
        locked = STATE.setdefault(uuid, True)
        print(f"[mock] GET  {uuid} -> {'locked' if locked else 'unlocked'}", flush=True)
        self._send(
            200,
            {
                "batteryVoltage": 6.0,
                "position": 0 if locked else 1024,
                "CHSesame2Status": "locked" if locked else "unlocked",
            },
        )

    def do_POST(self) -> None:  # noqa: N802
        m = PATH_RE.match(self.path)
        if not m or not m.group(2):
            self._send(404, {"message": "not found"})
            return
        uuid = m.group(1)
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try:
            cmd = json.loads(raw).get("cmd")
        except json.JSONDecodeError:
            cmd = None
        if cmd == LOCK_CMD:
            STATE[uuid] = True
        elif cmd == UNLOCK_CMD:
            STATE[uuid] = False
        else:
            print(f"[mock] POST {uuid} unknown cmd={cmd}", flush=True)
            self._send(400, {"message": "unknown cmd"})
            return
        print(f"[mock] POST {uuid} cmd={cmd} -> {'locked' if STATE[uuid] else 'unlocked'}", flush=True)
        self._send(200, {"message": "ok"})

    def log_message(self, fmt: str, *args) -> None:  # 既定のアクセスログを抑止
        pass


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8080), Handler)
    print("[mock] listening on 0.0.0.0:8080", flush=True)
    server.serve_forever()
