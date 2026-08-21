"""Micro-API HTTP standard-library pour le moteur régional WAPI Ads."""

from dataclasses import asdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os

from .engine import AudienceContext, Campaign, rank_campaigns


class Handler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/rank":
            self.send_error(404)
            return
        try:
            length = min(int(self.headers.get("content-length", "0")), 512_000)
            payload = json.loads(self.rfile.read(length))
            context = AudienceContext(**payload["context"])
            campaigns = [Campaign(**item) for item in payload.get("campaigns", [])]
            result = {"campaigns": [asdict(item) for item in rank_campaigns(campaigns, context, payload.get("limit", 3))]}
            encoded = json.dumps(result, ensure_ascii=False).encode()
            self.send_response(200)
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            encoded = json.dumps({"error": str(error)}).encode()
            self.send_response(400)
        self.send_header("content-type", "application/json; charset=utf-8")
        self.send_header("content-length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *_: object) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.getenv("WAPI_ADS_PORT", "8787"))), Handler).serve_forever()
