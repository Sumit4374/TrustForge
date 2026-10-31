package com.sumit.trustforge.access;

import java.util.UUID;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccess {
    public enum Role {OWNER,EDITOR,VIEWER}
    private final JdbcTemplate jdbc;
    public ProjectAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Role require(UUID project,boolean write){return require(project,Actor.require(),write);}
    public Role require(UUID project,UUID actor,boolean write){
        var roles=jdbc.query("select role from project_members where project_id=? and user_id=?",(r,n)->Role.valueOf(r.getString(1)),project,actor);
        if(roles.isEmpty())throw ApiException.notFound();
        Role role=roles.getFirst();if(write&&role==Role.VIEWER)throw ApiException.forbidden();return role;
    }
    public void requireOwner(UUID project){if(require(project,false)!=Role.OWNER)throw ApiException.forbidden();}
}
