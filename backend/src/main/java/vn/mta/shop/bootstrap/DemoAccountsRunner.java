package vn.mta.shop.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.mta.shop.service.BootstrapService;

@Component
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
public class DemoAccountsRunner implements ApplicationRunner {
  private final BootstrapService service;
  private final String adminPassword, userPassword;

  public DemoAccountsRunner(
      BootstrapService service,
      @Value("${app.demo-accounts.admin-password}") String adminPassword,
      @Value("${app.demo-accounts.user-password}") String userPassword) {
    this.service = service;
    this.adminPassword = adminPassword;
    this.userPassword = userPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    service.ensureDemoAccounts(adminPassword, userPassword);
  }
}
