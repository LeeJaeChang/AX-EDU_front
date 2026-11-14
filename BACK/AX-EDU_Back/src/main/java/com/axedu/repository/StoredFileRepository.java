package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {
}
