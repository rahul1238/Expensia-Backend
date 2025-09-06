package com.expensia.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BankDomainConfig {

    private final Set<String> domains;

    public BankDomainConfig(
            @Value("${app.bank.domains:hdfcbank.net,icicibank.com,axisbank.com,sbi.co.in,kotak.com,yesbank.in,idfcbank.com,rblbank.com,indusind.com,federalbank.co.in,unionbankofindia.co.in,bobibanking.com,idbi.com,bankofindia.co.in,canarabank.com,kvbmail.com,aubank.in,citi.com,hsbc.co.in,standardchartered.com}")
            String csv
    ) {
        if (csv == null || csv.isBlank()) {
            this.domains = Collections.emptySet();
        } else {
            this.domains = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public Set<String> getDomains() {
        return domains;
    }
}
