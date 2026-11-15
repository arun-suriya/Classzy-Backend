package com.centram.classzy.usermgmt;

import java.util.List;

import com.centram.classzy.security.Roles;
import com.centram.classzy.usermgmt.RoleCatalog.RoleView;
import com.centram.classzy.usermgmt.UserMgmtDto.ActivityItem;
import com.centram.classzy.usermgmt.UserMgmtDto.Created;
import com.centram.classzy.usermgmt.UserMgmtDto.ResetBody;
import com.centram.classzy.usermgmt.UserMgmtDto.ResetResult;
import com.centram.classzy.usermgmt.UserMgmtDto.StatusBody;
import com.centram.classzy.usermgmt.UserMgmtDto.Summary;
import com.centram.classzy.usermgmt.UserMgmtDto.UserBody;
import com.centram.classzy.usermgmt.UserMgmtDto.UserDetail;
import com.centram.classzy.usermgmt.UserMgmtDto.UserPage;
import com.centram.classzy.usermgmt.UserMgmtDto.YearOption;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/users")
@PreAuthorize(Roles.WRITE)
public class UserMgmtController {

    private final UserMgmtService service;

    public UserMgmtController(UserMgmtService service) { this.service = service; }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(name = "institutionId", required = false) Long institutionId) { return service.summary(institutionId); }

    @GetMapping("/roles")
    public List<RoleView> roles() { return RoleCatalog.all(); }

    @GetMapping("/modules")
    public List<String> modules() { return RoleCatalog.MODULES; }

    @GetMapping("/academic-years")
    public List<YearOption> years(@RequestParam(name = "institutionId", required = false) Long institutionId) { return service.years(institutionId); }

    @GetMapping
    public UserPage list(@RequestParam(name = "type", required = false) String type, @RequestParam(name = "institutionId", required = false) Long institutionId,
                         @RequestParam(name = "status", required = false) String status, @RequestParam(name = "role", required = false) String role,
                         @RequestParam(name = "q", required = false) String q, @RequestParam(name = "page", defaultValue = "0") int page,
                         @RequestParam(name = "size", defaultValue = "10") int size) {
        return service.list(type, institutionId, status, role, q, page, size);
    }

    @GetMapping("/{id}")
    public UserDetail get(@PathVariable("id") Long id) { return service.get(id); }

    @GetMapping("/{id}/activity")
    public List<ActivityItem> activity(@PathVariable("id") Long id, @RequestParam(name = "kind", defaultValue = "ACTIVITY") String kind) { return service.activity(id, kind); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@RequestBody UserBody body) { return service.create(body); }

    @PutMapping("/{id}")
    public UserDetail update(@PathVariable("id") Long id, @RequestBody UserBody body) { return service.update(id, body); }

    @PostMapping("/{id}/status")
    public UserDetail status(@PathVariable("id") Long id, @RequestBody StatusBody body) { return service.setStatus(id, body.status()); }

    @PostMapping("/{id}/reset-password")
    public ResetResult reset(@PathVariable("id") Long id, @RequestBody(required = false) ResetBody body) { return service.resetPassword(id, body == null ? null : body.password()); }
}
