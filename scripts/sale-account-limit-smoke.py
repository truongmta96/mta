#!/usr/bin/env python3
"""Kiểm thử giới hạn 1 sản phẩm/tài khoản/toàn chiến dịch, trên API và DB thật."""
import concurrent.futures
import datetime
import os
import subprocess
import sys
import time
import uuid
from auth_client import Client

run = uuid.uuid4().hex[:10]
admin = Client()
assert admin.login('admin@mta.local', os.getenv('MTA_ADMIN_PASSWORD', 'Admin@12345'), 'ADMIN')[0] == 200


def req(client, path, body=None, method=None, expected=200, headers=None):
    status, data = client.request(path, body, method, headers)
    assert status == expected, (path, status, data)
    return data


def buyer(label):
    client = Client()
    email = f'limit-{run}-{label}@example.invalid'
    body = dict(email=email, password='Limit@12345', name='Limit test ' + run)
    req(client, '/auth/register', body)
    assert client.login(email, body['password'], 'CUSTOMER')[0] == 200
    req(client, '/auth/csrf')  # Khởi tạo token trước khi gửi các request song song.
    return client


skus = [req(admin, '/admin/products', dict(name='Limit ' + run, brand='Test',
    description='Synthetic', storageGb=128, color='Black', price=100000, onHand=20))['id'] for _ in range(3)]
now = datetime.datetime.now(datetime.timezone.utc)
stamp = lambda delta: (now + datetime.timedelta(seconds=delta)).replace(tzinfo=None).isoformat(timespec='seconds')
def campaign(items):
    return req(admin, '/admin/campaigns', dict(name='Limit ' + run, startsAt=stamp(-60),
        endsAt=stamp(3600), status='SCHEDULED', items=[dict(skuId=s, price=80000,
        quota=20, perCustomerLimit=1) for s in items]))['id']

sale = campaign(skus[:2])
other_sale = campaign(skus[2:])
c = buyer('one')

def body(sku, quantity=1, campaign_id=sale):
    return dict(skuId=sku, quantity=quantity, recipient='Limit ' + run,
                phone='SYNTHETIC', address='Test', campaignId=campaign_id)

def order(client, payload, key=None, expected=200):
    return req(client, '/orders', payload, expected=expected,
               headers={'Idempotency-Key': key or uuid.uuid4().hex})

# Không thể lách bằng quantity > 1 hoặc bỏ campaignId để API tự áp dụng sale.
order(c, body(skus[0], 2), expected=409)
order(c, body(skus[0], 2, None), expected=409)
requests = [(body(skus[n % 2]), uuid.uuid4().hex) for n in range(8)]
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(lambda item: c.request('/orders', item[0],
        headers={'Idempotency-Key': item[1]}), requests))
assert [status for status, _ in results].count(200) == 1, results
assert [status for status, _ in results].count(409) == 7, results
winner = next(n for n, result in enumerate(results) if result[0] == 200)
payload, key = requests[winner]
first = results[winner][1]
assert order(c, payload, key)['id'] == first['id']  # Retry không tiêu thụ thêm lượt.
order(c, body(skus[1]), expected=409)
attempt = req(c, f"/orders/{first['id']}/payments", {})
req(c, f"/payments/sandbox/{attempt['id']}/complete", dict(result='SUCCEEDED'))
req(c, f"/payments/sandbox/{attempt['id']}/complete", dict(result='SUCCEEDED'))
order(c, body(skus[1]), expected=409)  # Đã thanh toán vẫn bị giới hạn.
refund = req(c, f"/orders/{first['id']}/refunds", dict(reason='Limit test'))
req(admin, f"/admin/refunds/{refund['id']}/decision", dict(approve=True, returnReceived=False))
second = order(c, body(skus[1]))
# Chiến dịch khác và tài khoản khác có lượt riêng.
third = order(c, body(skus[2], campaign_id=other_sale))
c2 = buyer('two')
fourth = order(c2, body(skus[0]))
for client, row in [(c, second), (c, third), (c2, fourth)]:
    req(client, f"/orders/{row['id']}/cancel", {})
fifth = order(c, body(skus[0]))  # Hủy trả lại lượt trên toàn chiến dịch.
req(c, f"/orders/{fifth['id']}/cancel", {})
# Tùy chọn: worker hết hạn trả lượt; callback muộn không giải phóng đơn mới.
if '--database-checks' in sys.argv:
    expiring = order(c, body(skus[0]))
    pending_payment = req(c, f"/orders/{expiring['id']}/payments", {})
    query = f"UPDATE orders SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 MINUTE) WHERE id={int(expiring['id'])}"
    subprocess.run(['docker', 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot mta -e "$1"', 'sh', query],
        check=True, capture_output=True)
    for _ in range(25):
        if req(c, f"/orders/{expiring['id']}")['status'] == 'EXPIRED':
            break
        time.sleep(1)
    assert req(c, f"/orders/{expiring['id']}")['status'] == 'EXPIRED'
    replacement = order(c, body(skus[1]))
    assert req(c, f"/payments/sandbox/{pending_payment['id']}/complete",
               dict(result='SUCCEEDED'))['status'] == 'REFUNDED'
    order(c, body(skus[0]), expected=409)
    req(c, f"/orders/{replacement['id']}/cancel", {})
    print('PASS: expiration restores campaign allowance; late payment preserves new reservation')
else:
    print('NOT RUN: expiration simulation (pass --database-checks)')
for campaign_id in [sale, other_sale]:
    req(admin, f'/admin/campaigns/{campaign_id}', dict(status='ENDED'), method='PATCH')
regular = order(c, body(skus[0], 2, None))  # Mua thường vẫn được số lượng 2.
req(c, f"/orders/{regular['id']}/cancel", {})
for campaign_id in [sale, other_sale]:
    req(admin, f'/admin/campaigns/{campaign_id}', method='DELETE')
for sku in skus:
    req(admin, f'/admin/products/{sku}', method='DELETE')
print('PASS: account-wide campaign limit, cross-SKU concurrency, quantity/auto-sale guard, idempotency, paid/refund/cancel, independent campaigns/accounts, regular quantity')
