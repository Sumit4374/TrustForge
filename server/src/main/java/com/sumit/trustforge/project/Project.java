package com.sumit.trustforge.project;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="projects")
public class Project {
    @Id public UUID id;
    @Column(nullable=false,length=120)public String name;
    @Column(nullable=false,length=2000)public String description;
    @Column(name="created_by",nullable=false)public UUID createdBy;
    @Column(name="created_at",nullable=false)public Instant createdAt;
    @Version public long version;
    protected Project(){}
    public Project(String name,String description,UUID user){id=UUID.randomUUID();this.name=name;this.description=description;createdBy=user;createdAt=Instant.now();}
}
