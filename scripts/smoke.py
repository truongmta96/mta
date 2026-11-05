#!/usr/bin/env python3
"""Functional check against a dedicated local demo instance; creates test orders."""
import uuid, concurrent.futures
import os
from auth_client import Client
client=Client()
status, identity=client.login('user@mta.local',os.environ.get('MTA_USER_PASSWORD','User@12345'),'CUSTOMER')
assert status==200,identity
def request(path,body=None,key=None):
 return client.request(path,body,headers={'Idempotency-Key':key} if key else {})
assert request('/health')[0]==200
products=request('/products')[1];assert len(products)==6
sku=products[-1];before=sku['available']
body={'skuId':sku['id'],'quantity':1,'recipient':'Smoke Test','phone':'SYNTHETIC','address':'Test only'}
key=str(uuid.uuid4())
a=request('/orders',body,key);b=request('/orders',body,key)
assert a[0]==b[0]==200 and a[1]['id']==b[1]['id'],(a,b)
assert request('/orders',dict(body,quantity=2),key)[0]==409
assert request('/orders',dict(body,quantity=0),str(uuid.uuid4()))[0]==400
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
 results=list(pool.map(lambda _:request('/orders',body,str(uuid.uuid4())),range(8)))
assert all(r[0]==200 for r in results),results
assert request('/products')[1][-1]['available']==before-9
assert request('/orders/'+str(a[1]['id'])+'/payments',{})[0]==501
print('PASS: health, catalog, idempotency, conflict, validation, concurrent stock reservations, payment fail-closed')
