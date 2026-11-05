package vn.mta.shop.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Đọc trạng thái/quyền từ database, không nhận role hay customerId từ client. */
@Service
public class AccountService implements UserDetailsService {
    private final JdbcTemplate db;
    public AccountService(JdbcTemplate db) { this.db = db; }
    public static String email(String value) { return value.strip().toLowerCase(Locale.ROOT); }

    public Map<String, Object> account(String email) {
        var rows = db.queryForList("SELECT id,email,password_hash,role,status,deleted_at,session_version,display_name FROM accounts WHERE email=?", email(email));
        if (rows.isEmpty()) throw new UsernameNotFoundException("Tài khoản không khả dụng");
        return rows.get(0);
    }
    public boolean active(Map<String,Object> row) {
        return "ACTIVE".equals(row.get("status")) && row.get("deleted_at") == null
            && Set.of("ADMIN", "CUSTOMER").contains(row.get("role"));
    }
    @Override public UserDetails loadUserByUsername(String email) {
        var a = account(email);
        return new AccountUser(a,active(a));
    }
    public static class AccountUser extends User {
        private final int version;
        public AccountUser(Map<String,Object> a,boolean enabled){
            super(a.get("email").toString(),a.get("password_hash").toString(),enabled,true,true,true,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+a.get("role"))));
            version=((Number)a.get("session_version")).intValue();
        }
        public int version(){return version;}
    }
    public long customerId(String email) {
        var rows = db.queryForList("SELECT c.id FROM customers c JOIN accounts a ON a.id=c.account_id WHERE a.email=? AND a.status='ACTIVE' AND a.deleted_at IS NULL AND c.deleted_at IS NULL", email(email));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hồ sơ khách hàng không khả dụng");
        return ((Number) rows.get(0).get("id")).longValue();
    }
    public Map<String,Object> profile(String email) {
        var a = account(email);
        var result = new LinkedHashMap<String,Object>();
        result.put("id",a.get("id")); result.put("email",a.get("email")); result.put("role",a.get("role"));
        var customers = db.queryForList("SELECT full_name FROM customers WHERE account_id=? AND deleted_at IS NULL",a.get("id"));
        result.put("name",customers.isEmpty()?(a.get("display_name")==null?"Quản trị viên":a.get("display_name")):customers.get(0).get("full_name"));
        return result;
    }
}
