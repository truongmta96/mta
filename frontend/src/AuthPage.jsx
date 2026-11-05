import React, {useState} from 'react';
import {ArrowUpRight, ShieldCheck, Smartphone, LockKeyhole, Eye, EyeOff} from 'lucide-react';
import {api} from './api';

export default function AuthPage({mode, onLogin, navigate}) {
    const admin = mode === 'admin-login';
    const register = mode === 'register';
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [visible, setVisible] = useState(false);
    async function submit(event) {
        event.preventDefault(); setBusy(true); setError(''); setSuccess('');
        const form = new FormData(event.currentTarget);
        const email = form.get('email').trim();
        const password = form.get('password');
        try {
            if (register) {
                await api('/auth/register', {method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({email, password, name:form.get('name')})});
                setSuccess('Tài khoản đã sẵn sàng. Chọn Đăng nhập để tiếp tục.');
            } else {
                const account = await api('/auth/login', {method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({email, password, portal:admin?'ADMIN':'CUSTOMER'})});
                onLogin(account);
                navigate(account.role === 'ADMIN' ? 'admin' : 'shop');
            }
        } catch (failure) { setError(failure.message); }
        finally { setBusy(false); }
    }
    return <section className="auth-layout">
        <aside className={'auth-story '+(admin?'admin-story':'')}>
            <div className="eyebrow">MTA MOBILE · {admin?'KHÔNG GIAN QUẢN TRỊ':'KẾT NỐI TRẢI NGHIỆM'}</div>
            <div className="auth-symbol">{admin?<ShieldCheck size={64} strokeWidth={1}/>:<Smartphone size={64} strokeWidth={1}/>}</div>
            <h1>{admin?<>Quản lý rõ ràng.<br/>Vận hành an tâm.</>:<>Một tài khoản.<br/>Trọn trải nghiệm.</>}</h1>
            <p>{admin?'Theo dõi sản phẩm, đơn hàng và khách hàng trong không gian dành riêng cho quản trị viên.':'Đăng nhập để đặt chiếc điện thoại yêu thích và theo dõi những đơn hàng của riêng bạn.'}</p>
            <div className="auth-trust"><LockKeyhole size={16}/> Phiên đăng nhập được bảo vệ</div>
        </aside>
        <div className="auth-panel">
            <div className="auth-switch" aria-label="Loại tài khoản">
                <button className={!admin?'selected':''} onClick={()=>navigate('login')}>Khách hàng</button>
                <button className={admin?'selected':''} onClick={()=>navigate('admin-login')}>Quản trị viên</button>
            </div>
            <div className="eyebrow">{register?'BẮT ĐẦU CÙNG MTA':'CHÀO MỪNG TRỞ LẠI'}</div>
            <h2>{register?'Tạo tài khoản':admin?'Đăng nhập quản trị':'Đăng nhập'}</h2>
            <p>{register?'Tạo tài khoản khách hàng để bắt đầu mua sắm.':admin?'Dành cho tài khoản được cấp quyền ADMIN.':'Tiếp tục hành trình công nghệ của bạn.'}</p>
            <form onSubmit={submit}>
                {register&&<label>Họ và tên<input name="name" autoComplete="name" required maxLength={150} placeholder="Nguyễn Minh Anh"/></label>}
                <label>Email<input name="email" type="email" autoComplete="username" required maxLength={190} placeholder={admin?'admin@example.com':'ban@example.com'}/></label>
                <label>Mật khẩu<div className="password-field"><input name="password" type={visible?'text':'password'} autoComplete={register?'new-password':'current-password'} required minLength={register?10:undefined} maxLength={72} placeholder={register?'Ít nhất 10 ký tự':'Nhập mật khẩu của bạn'}/><button type="button" aria-label={visible?'Ẩn mật khẩu':'Hiện mật khẩu'} onClick={()=>setVisible(!visible)}>{visible?<EyeOff size={18}/>:<Eye size={18}/>}</button></div></label>
                {error&&<div className="auth-error" role="alert">{error}</div>}
                {success&&<div className="notice" role="status">{success}</div>}
                <button className="primary" disabled={busy}>{busy?'Đang xử lý...':register?'Tạo tài khoản':'Đăng nhập'}<ArrowUpRight size={18}/></button>
            </form>
            {!admin&&<p className="auth-bottom">{register?'Đã có tài khoản?':'Chưa có tài khoản?'} <button onClick={()=>navigate(register?'login':'register')}>{register?'Đăng nhập':'Đăng ký ngay'}</button></p>}
            <button className="back-shop" onClick={()=>navigate('shop')}>← Quay lại cửa hàng</button>
        </div>
    </section>;
}
