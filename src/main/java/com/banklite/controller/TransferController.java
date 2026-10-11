package com.banklite.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banklite.dto.TransferRequest;
import com.banklite.dto.TransferResponse;
import com.banklite.service.TransferFacade;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferFacade transferFacade;

    public TransferController(TransferFacade transferFacade) {
        this.transferFacade = transferFacade;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferFacade.transfer(request, idempotencyKey));
    }
}
