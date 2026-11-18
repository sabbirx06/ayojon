package com.ayojon.controller;

import com.ayojon.model.UserRepository;
import com.ayojon.model.Voucher;
import com.ayojon.model.VoucherRepository;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {
    private static final String DEMO_CODE = "BKASHAYOJON20";

    private final VoucherRepository vouchers;
    private final UserRepository users;

    public VoucherController(VoucherRepository vouchers, UserRepository users) {
        this.vouchers = vouchers;
        this.users = users;
    }

    @GetMapping("/demo")
    public VoucherRepository.Offer demo(Principal principal) {
        return vouchers.findOffer(DEMO_CODE, currentUserId(principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No demo voucher is available."));
    }

    @PostMapping("/demo/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claim(Principal principal) {
        if (!vouchers.claim(DEMO_CODE, currentUserId(principal))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No demo voucher is available.");
        }
    }

    @GetMapping("/me")
    public List<Voucher> myVouchers(Principal principal) {
        return vouchers.findClaimed(currentUserId(principal));
    }

    private int currentUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."))
                .id();
    }
}
