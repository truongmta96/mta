package vn.mta.shop.dto;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record RefundDecisionRequest(boolean approve, boolean returnReceived) {}
