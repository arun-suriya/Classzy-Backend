package com.centram.classzy.academic.promotion;

import java.util.List;

import com.centram.classzy.academic.promotion.PromotionDto.OverrideRequest;
import com.centram.classzy.academic.promotion.PromotionDto.PreviewRow;
import com.centram.classzy.academic.promotion.PromotionDto.RuleRequest;
import com.centram.classzy.academic.promotion.PromotionDto.RuleResponse;
import com.centram.classzy.security.Roles;

import jakarta.validation.Valid;

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
@RequestMapping("/api/promotion")
@PreAuthorize(Roles.READ)
public class PromotionController {

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<RuleResponse> listRules(@RequestParam Long academicYearId) {
        return service.listRules(academicYearId).stream().map(RuleResponse::of).toList();
    }

    @PostMapping("/rules")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse createRule(@Valid @RequestBody RuleRequest r) {
        return RuleResponse.of(service.createRule(r));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize(Roles.WRITE)
    public RuleResponse updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest r) {
        return RuleResponse.of(service.updateRule(id, r));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
    }

    @GetMapping("/preview")
    public List<PreviewRow> preview(@RequestParam Long academicYearId) {
        return service.preview(academicYearId);
    }

    @PutMapping("/progressions")
    @PreAuthorize(Roles.WRITE)
    public PreviewRow override(@Valid @RequestBody OverrideRequest r) {
        return service.override(r);
    }
}
