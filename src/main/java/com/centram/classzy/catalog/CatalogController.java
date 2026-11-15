package com.centram.classzy.catalog;

import java.util.List;

import com.centram.classzy.catalog.CatalogDto.ItemBody;
import com.centram.classzy.catalog.CatalogDto.ItemView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** kinds: LIB_CATEGORY, LIB_STATUS, LIB_ISSUE_RULE, LIB_FINE_RULE, CUSTOM_FIELD, INTEGRATION */
@RestController
@RequestMapping("/api/catalog")
@PreAuthorize(Roles.READ)
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/{kind}")
    public List<ItemView> list(@PathVariable("kind") String kind) { return service.list(kind); }

    @PostMapping("/{kind}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView create(@PathVariable("kind") String kind, @RequestBody ItemBody body) { return service.create(kind, body); }

    @PutMapping("/{kind}/{id}")
    @PreAuthorize(Roles.WRITE)
    public ItemView update(@PathVariable("kind") String kind, @PathVariable("id") Long id, @RequestBody ItemBody body) { return service.update(kind, id, body); }

    @DeleteMapping("/{kind}/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("kind") String kind, @PathVariable("id") Long id) { service.delete(kind, id); }

    @PostMapping("/INTEGRATION/{id}/test")
    @PreAuthorize(Roles.WRITE)
    public ItemView test(@PathVariable("id") Long id) { return service.test(id); }
}
