package com.banklite.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.banklite.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
