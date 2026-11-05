import React, {useEffect, useState} from 'react';
import {Package, ShoppingBag, Users, UserRound, Zap, ShieldCheck} from 'lucide-react';
import {api} from './api';
const sections = [
    ['products','Sản phẩm',Package,[['id','SKU'],['name','Sản phẩm'],['price','Giá bán'],['on_hand','Trong kho'],['reserved','Đang giữ'],['available','Khả dụng']]],
    ['orders','Đơn hàng',ShoppingBag,[['id','Mã đơn'],['customer','Khách hàng'],['status','Trạng thái'],['total','Tổng tiền'],['created_at','Ngày tạo']]],
    ['accounts','Tài khoản',Users,[['id','ID'],['email','Email'],['role','Vai trò'],['status','Trạng thái'],['deleted_at','Ngày xóa']]],
    ['customers','Khách hàng',UserRound,[['id','ID'],['full_name','Họ tên'],['email','Email'],['phone','Điện thoại'],['deleted_at','Ngày xóa']]],
    ['campaigns','Chiến dịch sale',Zap,[['id','ID'],['name','Chiến dịch'],['starts_at','Bắt đầu'],['ends_at','Kết thúc'],['status','Trạng thái']]]
];
export default function AdminPage() {
    const [section,setSection] = useState('products');
    const [rows,setRows] = useState([]);
    const [error,setError] = useState('');
    const [loading,setLoading] = useState(true);
    useEffect(()=>{
        let current = true; setRows([]);setError('');setLoading(true);
        api('/admin/'+section).then(data=>{if(current)setRows(data)}).catch(e=>{if(current)setError(e.message)})
            .finally(()=>{if(current)setLoading(false)});
        return ()=>{current=false};
    },[section]);
    const current=sections.find(s=>s[0]===section);
    function value(key,v) {
        if(v==null)return '—';
        if(['price','total'].includes(key))return new Intl.NumberFormat('vi-VN').format(v)+' ₫';
        if(key.endsWith('_at'))return new Date(v).toLocaleString('vi-VN');
        return String(v);
    }
    return <section className="admin-layout">
        <aside className="admin-nav"><div className="admin-nav-title"><ShieldCheck size={20}/> QUẢN TRỊ</div>{sections.map(([id,name,Icon])=><button key={id} className={id===section?'selected':''} onClick={()=>setSection(id)}><Icon size={18}/>{name}</button>)}<small>Quyền truy cập: ADMIN</small></aside>
        <div className="admin-content"><div className="eyebrow">KHÔNG GIAN QUẢN TRỊ</div><h1>{current[1]}</h1><p>Dữ liệu trực tiếp · Hiển thị tối đa 100 bản ghi mới nhất. Các thao tác thêm/sửa sẽ được bổ sung sau.</p>
        {error&&<div className="alert" role="alert">{error}</div>}
        {loading?<p role="status">Đang tải dữ liệu...</p>:<><div className="table-wrap"><table><thead><tr>{current[3].map(([key,label])=><th key={key}>{label}</th>)}</tr></thead><tbody>{rows.map(row=><tr key={row.id}>{current[3].map(([key])=><td key={key}>{value(key,row[key])}</td>)}</tr>)}</tbody></table></div>{!rows.length&&!error&&<p className="empty">Chưa có dữ liệu.</p>}</>}
        </div>
    </section>;
}
