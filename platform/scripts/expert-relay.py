"""Local SSH transport for the configured expert gateway. Secrets stay in config/."""
import base64
import json
import os
from pathlib import Path
import select
import socketserver
import threading

import paramiko
from expert_history_proxy import start_history_proxy

root = Path(__file__).resolve().parent.parent
config = json.loads((root / 'config/expert-relay.json').read_text(encoding='utf-8-sig'))
ssh = paramiko.SSHClient()
ssh.load_system_host_keys()
key = paramiko.Ed25519Key.from_type_string('ssh-ed25519', base64.b64decode(config['hostKey']))
ssh.get_host_keys().add(config['host'], key.get_name(), key)
lock = threading.Lock()


def transport():
    with lock:
        active = ssh.get_transport()
        if active is None or not active.is_active():
            ssh.close()
            ssh.connect(config['host'], username=config['username'], password=config['password'],
                        timeout=10, auth_timeout=10, allow_agent=False, look_for_keys=False)
            active = ssh.get_transport()
            active.set_keepalive(30)
        return active


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        try:
            channel = transport().open_channel('direct-tcpip', ('127.0.0.1', config['remotePort']),
                                               self.client_address, timeout=10)
            with channel:
                while True:
                    readable, _, _ = select.select([self.request, channel], [], [], 180)
                    if not readable:
                        return
                    for source in readable:
                        data = source.recv(65536)
                        if not data:
                            return
                        (channel if source is self.request else self.request).sendall(data)
        except (OSError, EOFError, paramiko.SSHException) as error:
            print('Expert relay connection failed:', type(error).__name__, flush=True)


class Relay(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


try:
    transport()
    with ssh.open_sftp() as sftp:
        with sftp.open(config['gatewayKeyPath']) as source:
            gateway_key = source.read().decode().strip()
        with sftp.open(str(Path(config['gatewayKeyPath']).parent).replace('\\', '/') + '/config.json') as source:
            gateway_config = json.loads(source.read().decode('utf-8-sig'))
    if not gateway_key or '\n' in gateway_key or '\r' in gateway_key:
        raise ValueError('Invalid gateway credential')
    with Relay(('127.0.0.1', config['localPort']), Handler) as relay:
        history_port = config.get('historyPort', config['localPort'] + 1)
        history_server = start_history_proxy(transport, gateway_config, gateway_key, history_port,
                                            config.get('expertId', 'wxz-coding-tutor'))
        values = {'octop.preview.enabled': 'true',
                  'octop.preview.base-url': f"http://127.0.0.1:{config['localPort']}",
                  'octop.preview.expert-id': config.get('expertId', 'wxz-coding-tutor'),
                  'octop.preview.gateway-key': gateway_key,
                  'octop.preview.history-url': f'http://127.0.0.1:{history_port}'}
        destination = root / 'config/expert.env'
        pending = destination.with_suffix('.env.tmp')
        pending.write_text(''.join(f'{key}={value}\n' for key, value in values.items()), encoding='utf-8')
        os.replace(pending, destination)
        print('Expert relay ready on localhost:', config['localPort'], flush=True)
        relay.serve_forever()
finally:
    ssh.close()
