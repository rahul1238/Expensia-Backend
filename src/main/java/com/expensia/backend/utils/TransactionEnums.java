package com.expensia.backend.utils;

import lombok.Getter;

public final class TransactionEnums {
  @Getter
  public enum Currency {
    USD("United States Dollar"),
    INR("Indian Rupee"),
    EUR("Euro");

    private final String name;

    Currency(String name) {
      this.name = name;
    }
  }

  @Getter
  public enum TransactionMethod {
    CASH("Cash"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    BANK_TRANSFER("Bank Transfer"),
    UPI("UPI"),
    NET_BANKING("Net Banking"),
    PAYPAL("PayPal"),
    OTHER("Other");

    private final String name;

    TransactionMethod(String name) {
      this.name = name;
    }
  }

  private TransactionEnums(){};
}
