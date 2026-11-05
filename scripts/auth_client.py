"""HTTP client kiểm thử session cookie + CSRF, dùng thư viện chuẩn Python."""
import http.cookiejar
import json
import os
import urllib.error
import urllib.request

BASE = os.environ.get('MTA_API_URL', 'http://127.0.0.1:8080/api')

class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, path, body=None, method=None, headers=None, csrf=True):
        method = method or ('POST' if body is not None else 'GET')
        headers = dict(headers or {})
        if csrf and method not in ('GET', 'HEAD', 'OPTIONS'):
            status, token = self.request('/auth/csrf')
            assert status == 200, token
            headers[token['headerName']] = token['token']
        data = json.dumps(body).encode() if body is not None else None
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=30) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            content = error.read().decode()
            try: content = json.loads(content)
            except ValueError: pass
            return error.code, content

    def login(self, email, password, portal=None):
        body = {'email': email, 'password': password}
        if portal:
            body['portal'] = portal
        return self.request('/auth/login', body)
