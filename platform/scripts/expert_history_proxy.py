"""Read Octop's original history through SSH, scoped to one app/user/session."""
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import re
import threading
from urllib.parse import quote, urlparse


def start_history_proxy(transport, gateway, gateway_key, port, expert):
    upstream = urlparse(gateway['upstream'])
    if upstream.scheme != 'http' or upstream.hostname not in ('localhost', '127.0.0.1'):
        raise ValueError('History upstream must be a loopback HTTP endpoint on the SSH server')
    if expert not in gateway['apps']['wuxiaozhi']['experts']:
        raise ValueError('Expert is not enabled for this application')

    def request(path, token=None, payload=None):
        connection = http.client.HTTPConnection(upstream.hostname, upstream.port or 80, timeout=30)
        connection.sock = transport().open_channel('direct-tcpip',
                (upstream.hostname, upstream.port or 80), ('127.0.0.1', port), timeout=10)
        connection.sock.settimeout(30)
        try:
            headers = {'Content-Type': 'application/json'}
            if token:
                headers['Authorization'] = 'Bearer ' + token
            connection.request('POST' if payload is not None else 'GET', path,
                               body=json.dumps(payload) if payload is not None else None, headers=headers)
            response = connection.getresponse()
            raw = response.read()
            if response.status not in (200, 201):
                raise OSError('Octop history HTTP ' + str(response.status))
            return json.loads(raw)
        finally:
            connection.close()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_POST(self):
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + gateway_key):
                self.send_error(401)
                return
            if self.path != '/threads':
                self.send_error(404)
                return
            try:
                token = request('/api/auth/login', payload=gateway['octop'])['access_token']
                result = request('/api/agents/' + quote(expert, safe='') + '/threads', token, payload={})
                body = json.dumps({'thread_id': result['thread_id']}).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (OSError, ValueError, KeyError) as error:
                print('Expert thread creation failed:', type(error).__name__, flush=True)
                self.send_error(502)

        def do_GET(self):
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + gateway_key):
                self.send_error(401)
                return
            match = re.fullmatch(r'/history/(thr_[a-zA-Z0-9]+)', self.path)
            if not match:
                self.send_error(404)
                return
            try:
                thread_id = match.group(1)
                token = request('/api/auth/login', payload=gateway['octop'])['access_token']
                prefix = '/api/agents/' + quote(expert, safe='')
                path = prefix + '/threads/' + quote(thread_id, safe='') + '/history'
                messages = []
                offset = 0
                while True:
                    page = request(path + '?limit=200&offset=' + str(offset), token)
                    if page.get('history_loading'):
                        self.send_error(503)
                        return
                    batch = page.get('messages', [])
                    messages = batch + messages
                    if not page.get('has_more') or not batch:
                        break
                    offset += len(batch)
                result = []
                for message in messages:
                    role = message.get('role')
                    if role not in ('user', 'assistant'):
                        continue
                    content = message.get('content', [])
                    content = [{'type': 'text', 'text': content}] if isinstance(content, str) else content
                    text = ''.join(block.get('text', '') for block in content if block.get('type') == 'text')
                    statuses = []
                    for block in content:
                        if block.get('type') in ('thinking', 'reasoning'):
                            statuses.append({'kind': 'thinking', 'number': len(statuses) + 1,
                                             'text': block.get('thinking', block.get('text', ''))})
                    if text or statuses:
                        result.append({'role': 'user' if role == 'user' else 'ai', 'text': text,
                                       'statuses': statuses, 'streaming': False})
                body = json.dumps({'messages': result}, ensure_ascii=False).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json; charset=utf-8')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (OSError, ValueError, KeyError, TypeError) as error:
                print('Expert history request failed:', type(error).__name__, flush=True)
                self.send_error(502)

    server = ThreadingHTTPServer(('127.0.0.1', port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server
