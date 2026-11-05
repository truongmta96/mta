import React,{useEffect,useState} from 'react';
import {api} from './api';
export default function CustomerPage(){
 const [profile,setProfile]=useState(null),[error,setError]=useState('');
 useEffect(()=>{let active=true;api('/customer').then(p=>{if(active)setProfile(p)}).catch(e=>{if(active)setError(e.message)});return()=>{active=false}},[]);
 return <section className="workspace profile-page"><div className="eyebrow">KHÔNG GIAN CỦA BẠN</div><h1>Thông tin cá nhân</h1><p>Hồ sơ được liên kết với tài khoản đang đăng nhập.</p>{error&&<div role="alert" className="alert">{error}</div>}{profile?<dl>{[['full_name','Họ và tên'],['email','Email'],['phone','Số điện thoại']].map(([k,label])=><div key={k}><dt>{label}</dt><dd>{profile[k]||'Chưa cập nhật'}</dd></div>)}</dl>:!error&&<p>Đang tải hồ sơ...</p>}</section>;
}
