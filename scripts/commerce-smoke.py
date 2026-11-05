#!/usr/bin/env python3
"""Workflow thật trên sandbox. Tạo dữ liệu thử riêng; không xóa dữ liệu có sẵn.
--database-checks: cần Docker Compose để mô phỏng hết hạn trên đúng đơn thử.
MTA_TEST_WEBHOOK_SECRET: nếu được cấu hình giống PAYMENT_WEBHOOK_SECRET của API,
kiểm tra thêm webhook HTTP có chữ ký. Không dùng khóa thật cho kiểm thử.
"""
import concurrent.futures,json,os,uuid,time,hmac,hashlib,datetime,sys,subprocess
from auth_client import Client, BASE
import urllib.request,urllib.error
run=uuid.uuid4().hex[:10]
password='Commerce@12345'
admin=Client();code,identity=admin.login('admin@mta.local',os.environ.get('MTA_ADMIN_PASSWORD','Admin@12345'),'ADMIN');assert code==200,identity

def req(client,path,body=None,method=None,expected=200,headers=None):
    code,data=client.request(path,body,method,headers)
    assert code==expected,(path,code,data)
    return data

def create_product(stock=5):
    p=dict(name='Workflow '+run,brand='Test',description='Synthetic only',storageGb=128,color='Green',price=100000,onHand=stock)
    row=req(admin,'/admin/products',p);return row['id'],p

def buyer(label):
    c=Client();email=f'commerce-{run}-{label}@example.invalid'
    req(c,'/auth/register',dict(email=email,password=password,name='Commerce '+run+' '+str(label)))
    req(c,'/auth/login',dict(email=email,password=password,portal='CUSTOMER'))
    return c,email

def place(c,sku,campaign=None,expected=200,key=None):
    return req(c,'/orders',dict(skuId=sku,quantity=1,recipient='Commerce '+run,phone='SYNTHETIC',address='Test only',campaignId=campaign),expected=expected,headers={'Idempotency-Key':key or uuid.uuid4().hex})

def sale(sku,start=-60,end=3600):
    now=datetime.datetime.now(datetime.timezone.utc)
    stamp=lambda offset:(now+datetime.timedelta(seconds=offset)).replace(tzinfo=None).isoformat(timespec='seconds')
    return dict(name='Sale '+run,startsAt=stamp(start),endsAt=stamp(end),status='SCHEDULED',items=[dict(skuId=sku,price=80000,quota=3,perCustomerLimit=1)])

def inventory(sku):return next(p for p in req(admin,'/admin/products') if p['id']==sku)
def detail(c,order):return req(c,f'/orders/{order}')
def payment(c,order):return req(c,f'/orders/{order}/payments',{})
def complete(c,attempt,result='SUCCEEDED'):return req(c,f'/payments/sandbox/{attempt}/complete',dict(result=result))

sku,product=create_product()
clients=[buyer(n)[0] for n in range(4)]
campaign= req(admin,'/admin/campaigns',sale(sku))['id']
req(admin,'/admin/campaigns',sale(sku),expected=409)
key=uuid.uuid4().hex
# Nhiều khách tranh 3 suất, tồn vật lý 5. Mỗi khách tối đa 1 máy.
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(lambda c:c.request('/orders',dict(skuId=sku,quantity=1,recipient='Commerce '+run,phone='SYNTHETIC',address='Test only',campaignId=campaign),headers={'Idempotency-Key':uuid.uuid4().hex}),clients))
assert [r[0] for r in results].count(200)==3,results
assert [r[0] for r in results].count(409)==1,results
winners=[(c,r[1]) for c,r in zip(clients,results) if r[0]==200]
c1,o1=winners[0];c2,o2=winners[1];c3,o3=winners[2]
assert o1['total']==80000
place(c1,sku,campaign,expected=409)
assert inventory(sku)['reserved']==3
req(admin,f'/admin/products/{sku}',dict(product,onHand=2),method='PUT',expected=409)
req(admin,f'/admin/campaigns/{campaign}',sale(sku),method='PUT',expected=409)
# Một đơn thành công và webhook trùng không thay đổi kho/quota lần hai.
p1=payment(c1,o1['id']);assert payment(c1,o1['id'])['id']==p1['id']
assert complete(c1,p1['id'])['status']=='PAID'
assert complete(c1,p1['id'])['duplicate'] is True
assert detail(c1,o1['id'])['status']=='PAID'
assert inventory(sku)['reserved']==3
refund=req(c1,f"/orders/{o1['id']}/refunds",dict(reason='Refund test'))
req(admin,f"/admin/refunds/{refund['id']}/decision",dict(approve=True,returnReceived=False))
req(admin,f"/admin/refunds/{refund['id']}/decision",dict(approve=True,returnReceived=False))
assert detail(c1,o1['id'])['status']=='REFUNDED'
assert inventory(sku)['reserved']==2
# Thất bại cho phép thử lại; thanh toán đến sau hủy tự hoàn và không giữ kho lại.
p2=payment(c2,o2['id']);complete(c2,p2['id'],'FAILED')
p2b=payment(c2,o2['id']);assert p2b['id']!=p2['id']
req(c2,f"/orders/{o2['id']}/cancel",{})
assert complete(c2,p2b['id'])['status']=='REFUNDED'
assert detail(c2,o2['id'])['status']=='CANCELLED'
assert inventory(sku)['reserved']==1
# Đơn giao hàng chỉ hoàn khi admin xác nhận nhận lại hàng.
p3=payment(c3,o3['id']);complete(c3,p3['id'])
req(admin,f"/admin/orders/{o3['id']}",dict(status='SHIPPED'),method='PATCH',expected=409)
req(admin,f"/admin/orders/{o3['id']}",dict(status='PROCESSING'),method='PATCH')
req(admin,f"/admin/orders/{o3['id']}",dict(status='SHIPPED'),method='PATCH')
assert inventory(sku)['on_hand']==4 and inventory(sku)['reserved']==0
req(admin,f"/admin/orders/{o3['id']}",dict(status='COMPLETED'),method='PATCH')
r3=req(c3,f"/orders/{o3['id']}/refunds",dict(reason='Return test'))
req(admin,f"/admin/refunds/{r3['id']}/decision",dict(approve=True,returnReceived=False),expected=409)
req(admin,f"/admin/refunds/{r3['id']}/decision",dict(approve=True,returnReceived=True))
assert inventory(sku)['on_hand']==5 and inventory(sku)['reserved']==0
# Quota đã trả; khách có thể đặt lại sau khi hoàn tiền theo chính sách sandbox.
extra=place(c1,sku,campaign)
req(c1,f"/orders/{extra['id']}/cancel",{})
req(admin,f'/admin/campaigns/{campaign}',dict(status='ENDED'),method='PATCH')
place(c1,sku,campaign,expected=409)
# Sale chưa mở bị chặn, kể cả client gọi API trực tiếp.
future=req(admin,'/admin/campaigns',sale(sku,3600,7200))['id']
place(c1,sku,future,expected=409)
# Webhook trực tiếp: chữ ký sai bị chặn. Nếu có khóa thử, kiểm tra amount/time/replay.
anon=Client()
code,_=anon.request('/payments/webhook',dict(eventId='invalid'),headers={'X-Payment-Signature':'00'},csrf=False);assert code==401
plain=place(c1,sku);p=payment(c1,plain['id'])
secret=os.environ.get('MTA_TEST_WEBHOOK_SECRET')
if secret:
    def hook(body,expected=200):
        signature=hmac.new(secret.encode(),json.dumps(body).encode(),hashlib.sha256).hexdigest()
        request=urllib.request.Request(BASE+'/payments/webhook',data=json.dumps(body).encode(),method='POST',headers={'Content-Type':'application/json','X-Payment-Signature':signature})
        try:
            with urllib.request.urlopen(request,timeout=30) as response:code,data=response.status,json.load(response)
        except urllib.error.HTTPError as failure:code,data=failure.code,json.load(failure)
        assert code==expected,(code,data)
        return data
    # provider_reference intentionally queried only for the script's synthetic attempt.
    def sql(query):
        out=subprocess.check_output(['docker','compose','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N mta -e "$1"','sh',query],text=True)
        return out.strip()
    ref=sql(f"SELECT provider_reference FROM payment_attempts WHERE id={p['id']}")
    event=dict(eventId='HTTP-'+run,reference=ref,status='SUCCEEDED',amount=p['amount'],timestamp=int(time.time()))
    hook(None,400)
    hook(dict(event,amount=p['amount']+1),400)
    hook(dict(event,timestamp=0),400)
    hook(event);assert hook(event)['duplicate'] is True
    hook(dict(event,status='FAILED'),409)
    assert detail(c1,plain['id'])['status']=='PAID'
    rr=req(c1,f"/orders/{plain['id']}/refunds",dict(reason='Signed HTTP test'))
    req(admin,f"/admin/refunds/{rr['id']}/decision",dict(approve=True,returnReceived=False))
else:
    req(c1,f"/orders/{plain['id']}/cancel",{})
    print('NOT RUN: signed HTTP webhook amount/time/replay (set MTA_TEST_WEBHOOK_SECRET + matching API test secret)')
if '--database-checks' in sys.argv:
    expiry_sale=req(admin,'/admin/campaigns',sale(sku,-60,1800))['id']
    exp=place(c2,sku,expiry_sale);pe=payment(c2,exp['id'])
    subprocess.run(['docker','compose','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot mta -e "$1"','sh',f"UPDATE orders SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 MINUTE) WHERE id={exp['id']}"],check=True,capture_output=True)
    for _ in range(25):
        if detail(c2,exp['id'])['status']=='EXPIRED':break
        time.sleep(1)
    assert detail(c2,exp['id'])['status']=='EXPIRED','Worker chưa giải phóng đơn hết hạn'
    expiry=next(c for c in req(admin,'/admin/campaigns') if c['id']==expiry_sale)
    assert expiry['items'][0]['reserved']==0
    assert complete(c2,pe['id'])['status']=='REFUNDED'
    req(admin,f'/admin/campaigns/{expiry_sale}',method='DELETE')
    assert detail(c2,exp['id'])['status']=='EXPIRED'
else:print('NOT RUN: database expiry simulation (pass --database-checks)')
# Quản lý khách hàng, xóa mềm và khôi phục tài khoản có lịch sử đơn.
profile=req(c1,'/customer');req(admin,f"/admin/customers/{profile['id']}",dict(name='Updated '+run,phone='SYNTHETIC'),method='PUT')
assert req(c1,'/customer')['full_name']=='Updated '+run
pending=place(c1,sku)
account=req(c1,'/auth/me')
req(c1,'/auth/account',dict(password='wrong-password'),method='DELETE',expected=401)
req(c1,'/auth/account',dict(password=password),method='DELETE')
assert c1.request('/orders')[0]==401
assert req(admin,f"/admin/orders/{pending['id']}")['status']=='CANCELLED'
assert req(admin,f"/admin/orders/{o1['id']}")['status']=='REFUNDED'
req(admin,f"/admin/accounts/{account['id']}/restore",{})
req(c1,'/auth/login',dict(email=account['email'],password=password,portal='CUSTOMER'))
assert req(c1,'/customer')['id']==profile['id']
req(admin,f"/admin/customers/{profile['id']}",method='DELETE')
assert c1.request('/auth/me')[0]==401
req(admin,f"/admin/customers/{profile['id']}/restore",{})
req(admin,f"/admin/accounts/{account['id']}",dict(email=account['email'],name='Restored '+run,password='',role='CUSTOMER',status='ACTIVE'),method='PUT')
req(c1,'/auth/login',dict(email=account['email'],password=password,portal='CUSTOMER'))
# Phân trang/tìm kiếm trên server và quản lý tên/quyền/mật khẩu admin tạo mới.
assert req(admin,'/admin/accounts?page=-1',expected=400)
assert len(req(admin,'/admin/accounts?page=0'))<=50
assert all(a['email'].startswith('commerce-'+run) for a in req(admin,'/admin/accounts?q=commerce-'+run))
managed_email='managed-admin-'+run+'@example.invalid'
managed=req(admin,'/admin/accounts',dict(email=managed_email,name='Managed Admin',password=password,role='ADMIN',status='ACTIVE'))
managed_client=Client();assert req(managed_client,'/auth/login',dict(email=managed_email,password=password,portal='ADMIN'))['name']=='Managed Admin'
req(admin,f"/admin/accounts/{managed['id']}",dict(email=managed_email,name='Updated Admin',password='',role='ADMIN',status='LOCKED'),method='PUT')
assert managed_client.request('/auth/me')[0]==401
assert managed_client.login(managed_email,password)[0]==401
req(admin,f"/admin/accounts/{managed['id']}",method='DELETE')
# Bảo vệ admin đang dùng khỏi tự xóa/hạ quyền.
req(admin,f"/admin/accounts/{identity['id']}",method='DELETE',expected=409)
req(admin,f"/admin/accounts/{identity['id']}",dict(email=identity['email'],name='Admin',password='',role='CUSTOMER',status='ACTIVE'),method='PUT',expected=409)
# Sản phẩm soft delete, restore, rồi ẩn dữ liệu thử khỏi cửa hàng.
req(admin,f'/admin/products/{sku}',method='DELETE');assert sku not in [p['id'] for p in req(c1,'/products')]
req(admin,f'/admin/products/{sku}/restore',{});assert sku in [p['id'] for p in req(c1,'/products')]
req(admin,f'/admin/campaigns/{future}',method='DELETE')
req(admin,f'/admin/campaigns/{campaign}',method='DELETE')
assert inventory(sku)['reserved']==0
req(admin,f'/admin/products/{sku}',method='DELETE')
print('PASS: CRUD, soft deletion/restoration/history, self-delete/admin guard, overlapping/upcoming sales, concurrent quota/stock, per-customer limits, payment retry/replay, late payment, refund, fulfillment/return')
