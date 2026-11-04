#!/usr/bin/env python3
"""Functional check against a dedicated local demo instance; creates test orders."""
import json, urllib.request, urllib.error, uuid, concurrent.futures
BASE='http://127.0.0.1:8080/api'
def request(path,body=None,key=None):
 data=json.dumps(body).encode() if body is not None else None
 headers={'Content-Type':'application/json'}
 if key:headers['Idempotency-Key']=key
 try:
  with urllib.request.urlopen(urllib.request.Request(BASE+path,data=data,headers=headers),timeout=30) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
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
