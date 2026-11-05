#!/usr/bin/env python3
"""Kiểm tra Redis cache thật. --outages tạm dừng dịch vụ Redis và dùng CLIENT PAUSE để gây timeout.
Chỉ dùng trong môi trường phát triển; luôn khởi động/CLIENT UNPAUSE lại trong finally.
Không dùng FLUSHALL, chỉ thao tác 2 key metadata công khai của ứng dụng.
"""
import json,os,subprocess,sys,time,uuid
from auth_client import Client
KEY='mta:public:products-metadata:v1'
KEYS=[KEY,'mta:public:campaigns-metadata:v1']

def docker(*args):return subprocess.check_output(['docker','compose',*args],text=True,stderr=subprocess.STDOUT).strip()
def redis(*args):return docker('exec','-T','redis','redis-cli','--raw',*map(str,args))
def api(client,path,body=None,method=None):
    code,result=client.request(path,body,method)
    assert code==200,(path,code,result)
    return result

guest,admin,user=Client(),Client(),Client()
api(admin,'/auth/login',dict(email='admin@mta.local',password=os.environ.get('MTA_ADMIN_PASSWORD','Admin@12345'),portal='ADMIN'))
api(user,'/auth/login',dict(email='user@mta.local',password=os.environ.get('MTA_USER_PASSWORD','User@12345'),portal='CUSTOMER'))
assert redis('PING')=='PONG','Redis must be running before this test'
original=dict(name='Cache Probe '+uuid.uuid4().hex[:10],brand='Cache Test',description='Synthetic cache check',storageGb=128,color='Green',price=100000,onHand=5)
sku=api(admin,'/admin/products',original)['id']

def product():return next(p for p in api(guest,'/products') if p['id']==sku)
def await_cache(expected_name,timeout=25):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        p=product()
        value=redis('GET',KEY)
        if value:
            try:
                rows=json.loads(value)['rows']
                if p['name']==expected_name and any(r['id']==sku and r['name']==expected_name for r in rows):return
            except (ValueError,KeyError,TypeError):pass
        time.sleep(.25)
    raise AssertionError('Redis cache did not recover/populate with current metadata')

try:
    # Bước 1: làm ấm cache; có key hợp lệ mới kiểm tra hit/TTL để tránh kết quả giả do chưa bật cache.
    await_cache(original['name'])
    payload=json.loads(redis('GET',KEY));assert payload['schema']==1
    assert all('available' not in p and 'reserved' not in p and 'on_hand' not in p for p in payload['rows'])
    ttl=int(redis('TTL',KEY));assert 0<ttl<=300,ttl
    def hits():
        return int(next(line.split(':')[1] for line in redis('INFO','stats').splitlines() if line.startswith('keyspace_hits:')))
    before=hits();assert product()['available']==5;assert hits()>before
    # Metadata cache hit vẫn phản ánh tồn kho mới từ database.
    code,order=user.request('/orders',dict(skuId=sku,quantity=1,recipient='Cache Probe',phone='SYNTHETIC',address='Test only'),headers={'Idempotency-Key':uuid.uuid4().hex});assert code==200,order
    assert product()['available']==4
    api(user,f"/orders/{order['id']}/cancel",{})
    assert product()['available']==5
    # Sau commit, tên mới phải hiện ngay mà không chờ TTL.
    renamed=dict(original,name=original['name']+' Updated')
    api(admin,'/admin/products/'+str(sku),renamed,'PUT')
    assert product()['name']==renamed['name']
    await_cache(renamed['name'])
    # JSON cache hỏng vẫn trả dữ liệu MySQL, không phải 500.
    # Bước 2: cố ý làm hỏng đúng key public, kiểm tra service quay về DB; không FLUSHALL.
    redis('SET',KEY,'not-json','EX','10')
    assert product()['name']==renamed['name']
    redis('DEL',*KEYS)
    await_cache(renamed['name'])
    print('PASS: Redis hit + TTL, live stock on cache hit, post-commit invalidation, corrupt cache fallback')
    if '--outages' not in sys.argv:
        print('NOT RUN: stopped/paused Redis tests (pass --outages in development)')
    else:
        stopped=False
        try:
            # Bước 3: mất Redis thật; API vẫn phải đọc DB và đặt/hủy đơn được.
            docker('stop','redis');stopped=True
            start=time.monotonic();assert product()['name']==renamed['name'];elapsed=time.monotonic()-start
            assert elapsed<3,('Redis outage response too slow',elapsed)
            during=dict(renamed,name=original['name']+' During outage')
            api(admin,'/admin/products/'+str(sku),during,'PUT')
            assert product()['name']==during['name']
            assert api(guest,'/campaigns') is not None
            code,held=user.request('/orders',dict(skuId=sku,quantity=1,recipient='Cache Probe',phone='SYNTHETIC',address='Test only'),headers={'Idempotency-Key':uuid.uuid4().hex});assert code==200,held
            assert product()['available']==4
            api(user,f"/orders/{held['id']}/cancel",{})
            print(f'PASS: Redis stopped → HTTP 200 + primary DB reads/writes/orders; first read {elapsed:.3f}s')
        finally:
            if stopped:docker('start','redis')
        await_cache(during['name'])
        paused=False
        try:
            # TCP vẫn mở nhưng Redis không xử lý lệnh: buộc command timeout thật.
            # Bước 4: kết nối còn mở nhưng Redis tạm không xử lý lệnh -> command timeout thật.
            # 3000 ms tự hết hạn; finally vẫn UNPAUSE sớm để khôi phục môi trường.
            redis('CLIENT','PAUSE','3000','ALL');paused=True
            start=time.monotonic();assert product()['name']==during['name'];elapsed=time.monotonic()-start
            assert elapsed<3,('Redis timeout response too slow',elapsed)
            assert api(guest,'/campaigns') is not None
            print(f'PASS: Redis CLIENT PAUSE → command timeout → HTTP 200 from DB; first read {elapsed:.3f}s')
        finally:
            if paused:redis('CLIENT','UNPAUSE')
        await_cache(during['name'])
        # Không restart API: cache phải tự hoạt động lại khi hết backoff và probe thành công.
        print('PASS: Redis recovery re-enables cache without API restart')
finally:
    # Chỉ ẩn sản phẩm thử đã tạo ở trên; không xóa sản phẩm người dùng hoặc lịch sử đơn.
    code,result=admin.request('/admin/products/'+str(sku),method='DELETE')
    assert code==200,result
