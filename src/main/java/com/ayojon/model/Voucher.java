package com.ayojon.model;

import java.math.BigDecimal;

public record Voucher(String sponsorName, String promoCode, BigDecimal discountPercent) {}
