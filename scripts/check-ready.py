#!/usr/bin/env python3
import time,json,urllib.request
for attempt in range(45):
 try:
  with urllib.request.urlopen('http://127.0.0.1:5173/api/health',timeout=3) as r:assert json.load(r)['status']=='UP'
  with urllib.request.urlopen('http://127.0.0.1:5173/api/products',timeout=3) as r:assert len(json.load(r))>0
  print('READY: Nginx → API → MySQL; catalog contains products')
  break
 except Exception:
  if attempt==44:raise
  time.sleep(1)
