package vn.mta.shop.service;

import java.util.List;
import java.util.Map;
import vn.mta.shop.dto.AccountRequest;
import vn.mta.shop.dto.CampaignRequest;
import vn.mta.shop.dto.CustomerRequest;
import vn.mta.shop.dto.OrderStatusRequest;
import vn.mta.shop.dto.ProductRequest;
import vn.mta.shop.dto.RefundDecisionRequest;
import vn.mta.shop.dto.RefundRequest;

public interface AdminService {
  List<Map<String, Object>> products(int page, String q);

  Map<String, Long> addProduct(ProductRequest input, String email);

  Map<String, Long> editProduct(long id, ProductRequest input, String email);

  Map<String, String> deleteProduct(long id, String email);

  Map<String, String> restoreProduct(long id, String email);

  List<Map<String, Object>> listOrders(int page, String q);

  Map<String, Object> detail(long id);

  Map<String, String> progress(long id, OrderStatusRequest input);

  Map<String, String> cancel(long id);

  Map<String, Object> refund(long id, RefundRequest input);

  List<Map<String, Object>> accountList(int page, String q);

  Map<String, Long> addAccount(AccountRequest input, String email);

  Map<String, Long> editAccount(long id, AccountRequest input, String email);

  Map<String, String> deleteAccount(long id, String email);

  Map<String, String> restoreAccount(long id, String email);

  List<Map<String, Object>> customers(int page, String q);

  Map<String, String> customer(long id, CustomerRequest input, String email);

  Map<String, String> deleteCustomer(long id, String email);

  Map<String, String> restoreCustomer(long id, String email);

  List<Map<String, Object>> campaigns(int page, String q);

  Map<String, Long> addSale(CampaignRequest input, String email);

  Map<String, Long> editSale(long id, CampaignRequest input, String email);

  Map<String, String> stopSale(long id, OrderStatusRequest input, String email);

  Map<String, String> deleteSale(long id, String email);

  List<Map<String, Object>> refunds(int page, String q);

  Map<String, String> decide(long id, RefundDecisionRequest input);

  List<Map<String, Object>> audit(int page, String q);
}
