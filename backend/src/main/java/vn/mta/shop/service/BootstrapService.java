package vn.mta.shop.service;

public interface BootstrapService {
  void ensureDemoAccounts(String adminPassword, String userPassword);
}
