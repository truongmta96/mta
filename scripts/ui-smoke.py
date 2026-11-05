"""Kiểm tra giao diện thật; cần backend/Compose và tài khoản demo đang chạy."""
import os
import shutil
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    executable = os.environ.get('PLAYWRIGHT_CHROMIUM') or shutil.which('chromium')
    browser = p.chromium.launch(**({'executable_path':executable} if executable else {}), headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width':1440,'height':1000}, device_scale_factor=1)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    base = os.environ.get('MTA_WEB_URL', 'http://127.0.0.1:5173')
    page.goto(base, wait_until='networkidle')
    page.locator('.card').first.wait_for()
    assert page.locator('.card').count() == 6
    assert page.get_by_role('button',name='Quản lý',exact=True).count() == 0
    page.get_by_role('button',name='Samsung',exact=True).click()
    assert page.locator('.card').count() == 2
    page.get_by_role('button',name='Tất cả',exact=True).click()
    page.goto(base+'/#/admin')
    page.get_by_role('heading',name='Vui lòng đăng nhập',exact=True).wait_for()
    page.get_by_role('button',name='Đến trang đăng nhập',exact=True).click()
    page.get_by_role('heading',name='Đăng nhập quản trị',exact=True).wait_for()
    page.locator('input[name=email]').fill('user@mta.local')
    page.locator('input[name=password]').fill(os.environ.get('MTA_USER_PASSWORD','User@12345'))
    page.locator('form button.primary').click()
    page.locator('.auth-error').wait_for()
    assert 'không có quyền' in page.locator('.auth-error').inner_text()
    page.get_by_role('button',name='Khách hàng',exact=True).click()
    page.locator('input[name=email]').fill('user@mta.local')
    page.locator('input[name=password]').fill(os.environ.get('MTA_USER_PASSWORD','User@12345'))
    page.locator('form button.primary').click()
    page.get_by_role('button',name='Đơn hàng',exact=True).wait_for()
    assert page.get_by_role('button',name='Quản lý',exact=True).count() == 0
    page.reload(wait_until='networkidle')
    page.get_by_role('button',name='Đơn hàng',exact=True).wait_for()
    page.get_by_role('button',name='Mua iPhone 16 Pro',exact=True).click()
    page.get_by_role('button',name='Xác nhận đặt hàng',exact=True).wait_for()
    page.get_by_label('Đóng',exact=True).click()
    page.get_by_role('button',name='Đơn hàng',exact=True).click()
    page.locator('tbody tr').first.wait_for()
    page.get_by_role('button',name='Hồ sơ',exact=True).click()
    page.locator('dd').filter(has_text='user@mta.local').wait_for()
    page.goto(base+'/#/admin', wait_until='networkidle')
    page.get_by_role('heading',name='Bạn không có quyền truy cập',exact=True).wait_for()
    assert page.evaluate("fetch('/api/admin/accounts').then(r=>r.status)") == 403
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Quản trị',exact=True).wait_for()
    page.get_by_role('button',name='Quản trị',exact=True).click()
    page.locator('input[name=email]').fill('admin@mta.local')
    page.locator('input[name=password]').fill(os.environ.get('MTA_ADMIN_PASSWORD','Admin@12345'))
    page.locator('form button.primary').click()
    page.locator('.admin-layout tbody tr').first.wait_for()
    assert page.locator('tbody tr').count() == 6
    page.get_by_role('button',name='Tài khoản',exact=True).click()
    page.locator('td').filter(has_text='admin@mta.local').wait_for()
    page.get_by_role('button',name='Khách hàng',exact=True).click()
    page.locator('tbody tr').first.wait_for()
    page.screenshot(path='/tmp/mta-admin.png',full_page=True)
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    page.get_by_role('heading',name='Đăng nhập',exact=True).wait_for()
    page.screenshot(path='/tmp/mta-login.png',full_page=True)
    page.set_viewport_size({'width':390,'height':844})
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.screenshot(path='/tmp/mta-login-mobile.png',full_page=True)
    assert not errors, errors
    print('PASS: guest/admin route guard, wrong portal, customer login/menu/refresh/profile/orders, API 403, admin tables, logout, mobile, no JS errors')
    browser.close()
