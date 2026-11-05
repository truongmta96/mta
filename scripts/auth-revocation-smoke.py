"""Kiểm tra thu hồi phiên bằng tài khoản thử riêng; cần Docker Compose và database đang chạy."""
import uuid,subprocess
from auth_client import Client
email='revocation-'+uuid.uuid4().hex[:12]+'@example.invalid'
password='Revocation@12345'
c=Client()
assert c.request('/auth/register',dict(email=email,password=password,name='Revocation Test'))[0]==200
assert c.login(email,password)[0]==200

def sql(statement):
 subprocess.run(['docker','compose','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot mta -e "$1"','sh',statement],check=True,capture_output=True,text=True)
try:
 sql(f"UPDATE accounts SET status='LOCKED' WHERE email='{email}'")
 assert c.request('/auth/me')[0]==401
 assert c.login(email,password)[0]==401
 sql(f"UPDATE accounts SET status='ACTIVE' WHERE email='{email}'")
 assert c.login(email,password)[0]==200
 sql(f"UPDATE accounts SET deleted_at=UTC_TIMESTAMP() WHERE email='{email}'")
 assert c.request('/auth/me')[0]==401
 assert c.login(email,password)[0]==401
 sql(f"UPDATE accounts SET deleted_at=NULL WHERE email='{email}'")
 assert c.login(email,password)[0]==200
 sql(f"UPDATE accounts SET role='ADMIN' WHERE email='{email}'")
 assert c.request('/auth/me')[0]==401
finally:
 sql(f"UPDATE accounts SET role='CUSTOMER',status='ACTIVE',deleted_at=NULL WHERE email='{email}'")
print('PASS: lock, soft deletion and role change revoke old sessions; blocked accounts cannot log in')
