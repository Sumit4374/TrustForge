package com.sumit.trustforge.project;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.sumit.trustforge.access.ProjectAccess;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.common.ApiException;
import com.sumit.trustforge.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import com.sumit.trustforge.common.ProjectChanged;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    public record ProjectDto(UUID id,String name,String description,String role,long version,Instant createdAt){}
    public record PageDto<T>(List<T>content,long totalElements,int page,int size){}
    public record MemberDto(UUID userId,String email,String name,String role){}
    private final ProjectRepository projects;private final ProjectAccess access;private final JdbcTemplate jdbc;private final AuditService audit;private final UserRepository users;private final ApplicationEventPublisher events;
    public ProjectService(ProjectRepository projects,ProjectAccess access,JdbcTemplate jdbc,AuditService audit,UserRepository users,ApplicationEventPublisher events){this.projects=projects;this.access=access;this.jdbc=jdbc;this.audit=audit;this.users=users;this.events=events;}
    @Transactional(readOnly=true)public PageDto<ProjectDto> list(int page,int size,String search){
        page=Math.max(0,page);size=Math.min(50,Math.max(1,size));String term="%"+(search==null?"":search.substring(0,Math.min(120,search.length())).toLowerCase(java.util.Locale.ROOT))+"%";
        var list=jdbc.query("select p.id,p.name,p.description,m.role,p.version,p.created_at from projects p join project_members m on m.project_id=p.id where m.user_id=? and lower(p.name) like ? order by p.created_at desc,p.id limit ? offset ?",(r,n)->new ProjectDto(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getTimestamp(6).toInstant()),Actor.require(),term,size,(long)page*size);
        Long count=jdbc.queryForObject("select count(*) from projects p join project_members m on m.project_id=p.id where m.user_id=? and lower(p.name) like ?",Long.class,Actor.require(),term);
        return new PageDto<>(list,count==null?0:count,page,size);
    }
    @Transactional(readOnly=true)public ProjectDto get(UUID id){String role=access.require(id,false).name();Project p=projects.findById(id).orElseThrow(ApiException::notFound);return dto(p,role);}
    @Transactional public ProjectDto create(String name,String description){
        UUID user=Actor.require();Long n=jdbc.queryForObject("select count(*) from projects where created_by=?",Long.class,user);if(n!=null&&n>=50)throw ApiException.invalid("Project limit reached (50)");
        Project p=projects.saveAndFlush(new Project(name.strip(),description,user));jdbc.update("insert into project_members(project_id,user_id,role) values (?,?,'OWNER')",p.id,user);audit.record("PROJECT_CREATE",p.id,"SUCCESS");events.publishEvent(new ProjectChanged(p.id));return dto(p,"OWNER");
    }
    @Transactional public ProjectDto update(UUID id,String name,String description,long version){String role=access.require(id,true).name();Project p=projects.findById(id).orElseThrow(ApiException::notFound);if(p.version!=version)throw new org.springframework.dao.OptimisticLockingFailureException("Version conflict");p.name=name.strip();p.description=description;projects.saveAndFlush(p);audit.record("PROJECT_UPDATE",id,"SUCCESS");events.publishEvent(new ProjectChanged(id));return dto(p,role);}
    @Transactional public void delete(UUID id){access.requireOwner(id);audit.record("PROJECT_DELETE",id,"SUCCESS");events.publishEvent(new ProjectChanged(id));projects.deleteById(id);}
    @Transactional(readOnly=true)public List<MemberDto> members(UUID id){access.require(id,false);return jdbc.query("select u.id,u.email,u.name,m.role from project_members m join users u on u.id=m.user_id where m.project_id=? order by m.created_at,u.id",(r,n)->new MemberDto(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),id);}
    @Transactional public void addMember(UUID id,String email,String role){
        access.requireOwner(id);if(!role.equals("EDITOR")&&!role.equals("VIEWER"))throw ApiException.invalid("Choose EDITOR or VIEWER");
        var u=users.findByEmail(email.strip().toLowerCase(java.util.Locale.ROOT)).filter(a->a.verified).orElseThrow(()->ApiException.invalid("A verified account is required"));
        if(accessRole(id,u.id).equals("OWNER"))throw ApiException.invalid("The owner cannot be changed here");
        jdbc.update("insert into project_members(project_id,user_id,role) values (?,?,?) on conflict(project_id,user_id) do update set role=excluded.role where project_members.role<>'OWNER'",id,u.id,role);events.publishEvent(new ProjectChanged(id));audit.record("MEMBER_UPDATE",id,"SUCCESS");
    }
    @Transactional public void removeMember(UUID id,UUID user){access.requireOwner(id);if(accessRole(id,user).equals("OWNER"))throw ApiException.invalid("The owner cannot be removed");jdbc.update("delete from project_members where project_id=? and user_id=?",id,user);events.publishEvent(new ProjectChanged(id));audit.record("MEMBER_REMOVE",id,"SUCCESS");}
    private String accessRole(UUID id,UUID user){var roles=jdbc.query("select role from project_members where project_id=? and user_id=?",(r,n)->r.getString(1),id,user);return roles.isEmpty()?"":roles.getFirst();}
    private ProjectDto dto(Project p,String role){return new ProjectDto(p.id,p.name,p.description,role,p.version,p.createdAt);}
}
