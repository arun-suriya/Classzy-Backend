package com.centram.classzy.billing;

import java.util.List;
import java.util.Map;

import com.centram.classzy.billing.BillingOverviewDto.InvoiceBody;
import com.centram.classzy.billing.BillingOverviewDto.InvoiceRow;
import com.centram.classzy.billing.BillingOverviewDto.Overview;
import com.centram.classzy.billing.BillingOverviewDto.PayBody;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Subscription & Billing overview, invoices and platform billing settings. Super Admin only. */
@RestController
@RequestMapping("/api/billing")
@PreAuthorize(Roles.SUPER)
public class BillingAdminController {

    private final BillingOverviewService overview;
    private final InvoiceService invoices;
    private final BillingSettings settings;
    private final PaymentMethodService methods;

    public BillingAdminController(BillingOverviewService overview, InvoiceService invoices, BillingSettings settings, PaymentMethodService methods) {
        this.methods = methods;
        this.overview = overview;
        this.invoices = invoices;
        this.settings = settings;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(name = "months", defaultValue = "6") int months) { return overview.overview(months); }

    @GetMapping("/settings")
    public Map<String, String> settings() { return settings.get(); }

    @PutMapping("/settings")
    public Map<String, String> saveSettings(@RequestBody Map<String, String> body) { return settings.save(body); }

    @GetMapping("/invoices")
    public List<InvoiceRow> invoices(@RequestParam(name = "status", required = false) String status, @RequestParam(name = "limit", defaultValue = "50") int limit) { return invoices.list(status, limit); }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceRow create(@RequestBody InvoiceBody body) { return invoices.create(body); }

    @GetMapping("/invoices/{id}")
    public BillingOverviewDto.InvoiceDetail invoice(@PathVariable("id") Long id) { return invoices.detail(id); }

    @GetMapping("/payment-methods")
    public List<BillingOverviewDto.MethodRow> methods() { return methods.list(); }

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingOverviewDto.MethodRow addMethod(@RequestBody BillingOverviewDto.MethodBody body) { return methods.create(body); }

    @PostMapping("/payment-methods/{id}/default")
    public List<BillingOverviewDto.MethodRow> defaultMethod(@PathVariable("id") Long id) { return methods.setDefault(id); }

    @DeleteMapping("/payment-methods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMethod(@PathVariable("id") Long id) { methods.delete(id); }

    @PostMapping("/invoices/{id}/mark-paid")
    public InvoiceRow markPaid(@PathVariable("id") Long id, @RequestBody(required = false) PayBody body) { return invoices.markPaid(id, body); }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) { invoices.delete(id); }
}
