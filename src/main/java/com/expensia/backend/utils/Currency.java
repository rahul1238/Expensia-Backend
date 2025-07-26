package com.expensia.backend.utils;

import lombok.Getter;

@Getter
public enum Currency{
  USD("United States Dollar"),
  INR("Indian Rupee"),
  EUR( "Euro");

  private final String name;

  Currency(String name) {
    this.name = name;
  }
}
