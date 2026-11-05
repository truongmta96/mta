package vn.mta.shop.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Chỉ tạo tài khoản mẫu khi chủ động bật chế độ demo. Không đổi mật khẩu tài khoản đã có. */
@Component @ConditionalOnProperty(name="app.demo-accounts.enabled",havingValue="true")
public class DemoAccounts implements ApplicationRunner {
    private final JdbcTemplate db; private final PasswordEncoder encoder;
    private final String adminPassword,userPassword;
    public DemoAccounts(JdbcTemplate db,PasswordEncoder encoder,
        @Value("${app.demo-accounts.admin-password}") String adminPassword,
        @Value("${app.demo-accounts.user-password}") String userPassword) {
        this.db=db;this.encoder=encoder;this.adminPassword=adminPassword;this.userPassword=userPassword;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        create("admin@mta.local",adminPassword,"ADMIN","Quản trị viên");
        create("user@mta.local",userPassword,"CUSTOMER","Nguyễn Minh Anh");
    }
    private void create(String email,String password,String role,String name) {
        if(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?",Integer.class,email)>0)return;
        if(password.length()<10||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Mật khẩu tài khoản demo cần từ 10 ký tự, tối đa 72 bytes UTF-8");
        db.update("INSERT INTO accounts(email,password_hash,role) VALUES(?,?,?)",email,encoder.encode(password),role);
        Long id=db.queryForObject("SELECT id FROM accounts WHERE email=?",Long.class,email);
        if(role.equals("CUSTOMER"))db.update("INSERT INTO customers(account_id,full_name) VALUES(?,?)",id,name);
    }
}
