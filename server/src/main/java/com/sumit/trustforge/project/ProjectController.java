package com.sumit.trustforge.project;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    record Create(@NotBlank @Size(max=120)String name,@NotNull @Size(max=2000)String description){}
    record Update(@NotBlank @Size(max=120)String name,@NotNull @Size(max=2000)String description,@Min(0)long version){}
    record Member(@NotBlank @Email @Size(max=254)String email,@Pattern(regexp="EDITOR|VIEWER") @NotNull String role){}
    private final ProjectService service;
    public ProjectController(ProjectService service){this.service=service;}
    @GetMapping ProjectService.PageDto<ProjectService.ProjectDto> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String search){return service.list(page,size,search);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)ProjectService.ProjectDto create(@Valid @RequestBody Create body){return service.create(body.name,body.description);}
    @GetMapping("/{id}")ProjectService.ProjectDto get(@PathVariable UUID id){return service.get(id);}
    @PatchMapping("/{id}")ProjectService.ProjectDto update(@PathVariable UUID id,@Valid @RequestBody Update body){return service.update(id,body.name,body.description,body.version);}
    @DeleteMapping("/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)void delete(@PathVariable UUID id){service.delete(id);}
    @GetMapping("/{id}/members")List<ProjectService.MemberDto> members(@PathVariable UUID id){return service.members(id);}
    @PostMapping("/{id}/members")@ResponseStatus(HttpStatus.NO_CONTENT)void member(@PathVariable UUID id,@Valid @RequestBody Member member){service.addMember(id,member.email,member.role);}
    @DeleteMapping("/{id}/members/{user}")@ResponseStatus(HttpStatus.NO_CONTENT)void remove(@PathVariable UUID id,@PathVariable UUID user){service.removeMember(id,user);}
}
