"""A one-trick local HTTP server for getting browser-rendered pixels onto disk.

    python tools/save_server.py [port]

POST /save?name=<file>  with a base64 body (raw, or form field "data") (a data-URL payload without its prefix) writes the
decoded bytes to tools/design/<file>. Used with tools/svg_to_vector.py: the designed icon is an
SVG, Android's legacy launcher icons need bitmaps, and a browser is the one SVG rasteriser that is
always to hand -- draw the SVG on a canvas, toDataURL, POST it here. Only ever bind this to
localhost; it writes whatever it is sent, into one directory, with the basename it is given.
"""
import base64
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

DESIGN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "design")


class Handler(BaseHTTPRequestHandler):
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        # GET /files/<name> serves a file from tools/design, so a page on this origin can load the
        # SVG, rasterise it and POST the result back without any cross-origin request.
        url = urlparse(self.path)
        if url.path.startswith("/files/"):
            name = os.path.basename(url.path[len("/files/"):])
            target = os.path.join(DESIGN, name)
            if not name or not os.path.isfile(target):
                self.send_response(404)
                self.end_headers()
                return
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "image/svg+xml" if name.endswith(".svg") else "application/octet-stream")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            with open(target, "rb") as fh:
                self.wfile.write(fh.read())
            return
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "text/html")
        self.end_headers()
        self.wfile.write(b"<!doctype html><title>save_server</title>"
                         b"<p>GET /files/&lt;name&gt; serves tools/design; POST /save?name=&lt;file&gt; with a base64 body writes there.</p>")

    def do_HEAD(self):
        self.send_response(200)
        self.end_headers()

    def do_POST(self):
        url = urlparse(self.path)
        if url.path != "/save":
            self.send_response(404)
            self._cors()
            self.end_headers()
            return
        name = os.path.basename(parse_qs(url.query).get("name", [""])[0])
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        # Either a raw base64 body, or an HTML form post with the base64 in a field called
        # "data" -- the form route exists because a plain navigation is the one kind of request
        # every embedded browser lets a page make without asking.
        if "application/x-www-form-urlencoded" in self.headers.get("Content-Type", ""):
            body = parse_qs(body.decode()).get("data", [""])[0].encode()
        if not name or not body:
            self.send_response(400)
            self._cors()
            self.end_headers()
            return
        os.makedirs(DESIGN, exist_ok=True)
        target = os.path.join(DESIGN, name)
        with open(target, "wb") as fh:
            fh.write(base64.b64decode(body))
        message = f"wrote {target} ({os.path.getsize(target)} bytes)\n"
        print(message, end="", flush=True)
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(message.encode())


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
    print(f"save_server listening on http://localhost:{port}/save -> {DESIGN}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
