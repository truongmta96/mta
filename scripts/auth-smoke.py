#!/usr/bin/env python3
"""Kiểm tra API thật: tạo 2 khách thử riêng, 1 đơn giữ kho; cần tài khoản demo admin."""
import os
import uuid
from auth_client import Client

run = uuid.uuid4().hex[:14]
password = 'Test-Only@12345'
guest, first, second, admin = Client(), Client(), Client(), Client()
assert guest.request('/products')[0] == 200
for path in ['/orders', '/customer', '/admin/products', '/admin/orders', '/admin/accounts', '/admin/customers', '/admin/campaigns']:
    assert guest.request(path)[0] == 401, path
assert guest.request('/auth/login', {'email':'user@mta.local','password':'wrong'}, csrf=False)[0] == 403
assert guest.login('user@mta.local', 'wrong-password')[0] == 401

for suffix, client in [('first', first), ('second', second)]:
    email = f'auth-{run}-{suffix}@example.invalid'
    # Client cố gửi role ADMIN nhưng server luôn gán CUSTOMER khi đăng ký.
    status, body = client.request('/auth/register', {'email':email,'password':password,'name':'Auth Smoke Test','role':'ADMIN'})
    assert status == 200, body
    old_token = client.request('/auth/csrf')[1]
    old_session = next(c.value for c in client.cookies if c.name == 'JSESSIONID')
    status, body = client.login(email, password, 'CUSTOMER')
    assert status == 200 and body['role'] == 'CUSTOMER', body
    new_session = next(c for c in client.cookies if c.name == 'JSESSIONID')
    assert new_session.value != old_session and new_session.has_nonstandard_attr('HttpOnly')
    assert client.request('/orders', {}, headers={old_token['headerName']:old_token['token']}, csrf=False)[0] == 403
    assert client.request('/auth/me')[1]['email'] == email
    assert client.request('/customer')[1]['email'] == email
    for path in ['/admin/products','/admin/orders','/admin/accounts','/admin/customers','/admin/campaigns']:
        assert client.request(path)[0] == 403, path

assert first.request('/orders', {}, csrf=False)[0] == 403
status, products = guest.request('/products')
sku = next(p for p in products if p['available'] > 0)
body = {'skuId':sku['id'],'quantity':1,'recipient':'Auth Smoke Test','phone':'SYNTHETIC','address':'Test only','customerId':1}
key = uuid.uuid4().hex
status, order = first.request('/orders', body, headers={'Idempotency-Key':key})
assert status == 200, order
assert first.request('/orders', body, headers={'Idempotency-Key':key})[1]['id'] == order['id']
assert order['id'] in [o['id'] for o in first.request('/orders')[1]]
assert order['id'] not in [o['id'] for o in second.request('/orders')[1]]
assert second.request(f"/orders/{order['id']}/payments", {})[0] == 404
assert first.request(f"/orders/{order['id']}/payments", {})[0] == 200
assert Client().login(f'auth-{run}-second@example.invalid', password, 'ADMIN')[0] == 403

status, identity = admin.login('admin@mta.local', os.environ.get('MTA_ADMIN_PASSWORD','Admin@12345'), 'ADMIN')
assert status == 200 and identity['role'] == 'ADMIN', identity
for path in ['/admin/products','/admin/orders','/admin/accounts','/admin/customers','/admin/campaigns']:
    status, rows = admin.request(path)
    assert status == 200 and isinstance(rows, list), (path, rows)
    assert all('password_hash' not in row for row in rows)
assert admin.request('/orders')[0] == 403
assert admin.request('/customer')[0] == 403
assert first.request('/auth/logout', method='POST')[0] == 200
assert first.request('/auth/me')[0] == 401
assert first.request('/orders')[0] == 401
print('PASS: guest 401, wrong login, CSRF, registration role enforcement, session rotation/HttpOnly, customer 403, admin access, order ownership, logout')
