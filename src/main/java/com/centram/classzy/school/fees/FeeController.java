package com.centram.classzy.school.fees;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.fees.FeeDto.*;
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

@RestController
@RequestMapping("/api/school/finance")
@PreAuthorize(Roles.WRITE)
public class FeeController {

    private final FeeService s;
    public FeeController(FeeService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/students") public List<StudentHit> students(@RequestParam(name = "q", required = false) String q) { return s.students(q); }
    @GetMapping("/students/{id}/invoices") public List<InvoiceRow> openInvoices(@PathVariable("id") Long id) { return s.openInvoices(id); }

    @GetMapping("/students/{id}/payments") public List<StudentPay> studentPayments(@PathVariable("id") Long id) { return s.studentPayments(id); }

    // fee collection
    @GetMapping("/invoices")
    public InvoicePage invoices(@RequestParam(name = "yearId", required = false) Long y, @RequestParam(name = "termId", required = false) Long t, @RequestParam(name = "classId", required = false) Long c,
                                @RequestParam(name = "status", required = false) String st, @RequestParam(name = "q", required = false) String q,
                                @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.invoices(y, t, c, st, q, p, z); }
    @GetMapping("/invoices/{id}") public InvoiceDetail invoice(@PathVariable("id") Long id) { return s.invoice(id); }
    @PostMapping("/invoices/generate") public GenResult generate(@RequestBody GenBody b) { return s.generate(b); }
    @PostMapping("/payments") @ResponseStatus(HttpStatus.CREATED) public Receipt pay(@RequestBody PayBody b) { return s.pay(b); }
    @GetMapping("/payments/{id}/receipt") public Receipt receipt(@PathVariable("id") Long id) { return s.receipt(id); }

    // fee structure
    @GetMapping("/structures") public List<StructRow> structures(@RequestParam(name = "yearId", required = false) Long y, @RequestParam(name = "classId", required = false) Long c) { return s.structures(y, c); }
    @PostMapping("/structures") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addStructure(@RequestBody StructBody b) { return s.addStructure(b); }
    @PutMapping("/structures/{id}") public Map<String, Object> updateStructure(@PathVariable("id") Long id, @RequestBody StructBody b) { return s.updateStructure(id, b); }
    @DeleteMapping("/structures/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteStructure(@PathVariable("id") Long id) { s.deleteStructure(id); }

    // concessions
    @GetMapping("/concessions")
    public ConcPage concessions(@RequestParam(name = "classId", required = false) Long c, @RequestParam(name = "status", required = false) String st,
                                @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.concessions(c, st, p, z); }
    @PostMapping("/concessions") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> requestConcession(@RequestBody ConcBody b) { return s.requestConcession(b); }
    @PostMapping("/concessions/{id}/decide") public Map<String, Object> decideConcession(@PathVariable("id") Long id, @RequestBody DecisionBody b) { return s.decideConcession(id, b.approve()); }
    @DeleteMapping("/concessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void withdrawConcession(@PathVariable("id") Long id) { s.withdrawConcession(id); }

    // scholarships
    @GetMapping("/scholarships")
    public ScholarPage scholarships(@RequestParam(name = "yearId", required = false) Long y, @RequestParam(name = "status", required = false) String st,
                                    @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.scholarships(y, st, p, z); }
    @PostMapping("/scholarships") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> award(@RequestBody ScholarBody b) { return s.award(b); }
    @PostMapping("/scholarships/{id}/revoke") public Map<String, Object> revoke(@PathVariable("id") Long id) { return s.revoke(id); }

    // refunds
    @GetMapping("/refunds")
    public RefundPage refunds(@RequestParam(name = "from", required = false) String f, @RequestParam(name = "to", required = false) String t, @RequestParam(name = "status", required = false) String st,
                              @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.refunds(f, t, st, p, z); }
    @PostMapping("/refunds") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> requestRefund(@RequestBody RefundBody b) { return s.requestRefund(b); }
    @PostMapping("/refunds/{id}/decide") public Map<String, Object> decideRefund(@PathVariable("id") Long id, @RequestBody DecisionBody b) { return s.decideRefund(id, b.approve()); }
}
