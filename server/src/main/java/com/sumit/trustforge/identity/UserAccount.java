package com.sumit.trustforge.identity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="users")
public class UserAccount {
    @Id public UUID id;
    @Column(nullable=false,unique=true,length=254) public String email;
    @Column(nullable=false,length=100) public String name;
    @Column(name="password_hash",nullable=false) public String passwordHash;
    @Column(nullable=false) public boolean verified;
    @Column(nullable=false) public String role;
    @Column(name="session_generation",nullable=false) public int sessionGeneration;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Version public long version;
    protected UserAccount() {}
    public UserAccount(String email,String name,String hash) {
        id=UUID.randomUUID();this.email=email;this.name=name;passwordHash=hash;verified=false;role="USER";createdAt=Instant.now();
    }
}
