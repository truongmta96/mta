package vn.mta.shop.service;

import vn.mta.shop.dto.AccountRequest;
import vn.mta.shop.dto.CampaignRequest;
import vn.mta.shop.dto.CustomerRequest;
import vn.mta.shop.dto.ProductRequest;

/** Hợp đồng nghiệp vụ; controller và service khác chỉ phụ thuộc interface này. */
public interface ManagementService {
  long product(Long id, ProductRequest input, long actor);

  void productDeleted(long sku, boolean deleted, long actor);

  long account(Long id, AccountRequest input, long actor);

  void accountDeleted(long id, boolean deleted, long actor);

  void customer(long id, CustomerRequest input, long actor);

  void customerDeleted(long id, boolean deleted, long actor);

  void deleteSelf(String email, String password);

  long campaign(Long id, CampaignRequest input, long actor);

  void stopCampaign(long id, String status, long actor);

  void deleteCampaign(long id, long actor);

  void audit(long actor, String action, String entity, long id);
}
