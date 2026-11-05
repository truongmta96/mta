package vn.mta.shop.service;

import java.util.List;
import java.util.Map;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.dto.ProfileRequest;
import vn.mta.shop.dto.RefundRequest;

public interface ShopService {
  Map<String, Object> health();

  List<Map<String, Object>> products();

  List<Map<String, Object>> campaigns();

  List<Map<String, Object>> list(String email);

  Map<String, Object> detail(long id, String email);

  Map<String, Object> place(String key, PlaceOrderRequest input, String email) throws Exception;

  Map<String, String> cancel(long id, String email);

  Map<String, Object> pay(long id, String email);

  Map<String, Object> refund(long id, RefundRequest input, String email);

  Map<String, Object> customer(String email);

  Map<String, String> profile(ProfileRequest input, String email);
}
