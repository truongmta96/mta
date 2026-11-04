from playwright.sync_api import sync_playwright
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1440,'height':1100},device_scale_factor=1)
 errors=[]
 page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto('http://127.0.0.1:5173',wait_until='networkidle')
 page.locator('.card').first.wait_for()
 assert page.locator('.card').count()==6
 page.get_by_role('button',name='Samsung',exact=True).click()
 assert page.locator('.card').count()==2
 page.get_by_role('button',name='Tất cả',exact=True).click()
 page.screenshot(path='/tmp/mta-desktop.png',full_page=True)
 page.get_by_role('button',name='Mua iPhone 16 Pro',exact=True).click()
 assert page.get_by_role('button',name='Xác nhận đặt hàng').is_visible()
 page.get_by_label('Đóng',exact=True).click()
 page.get_by_role('button',name='Đơn hàng',exact=True).click()
 page.locator('tbody tr').first.wait_for()
 assert page.locator('tbody tr').count()>=9
 page.get_by_role('button',name='Quản lý',exact=True).click()
 assert page.locator('tbody tr').count()==6
 page.set_viewport_size({'width':390,'height':844})
 page.get_by_role('button',name='Khám phá',exact=True).click()
 assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
 page.screenshot(path='/tmp/mta-mobile.png',full_page=True)
 assert not errors,errors
 print('PASS: 6 products, brand filtering, checkout modal, orders, admin inventory, mobile overflow, no JS errors')
 browser.close()
