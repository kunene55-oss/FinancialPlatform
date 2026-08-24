package com.example.financial.processing.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.example.financial.processing.service.AccountService;
import com.example.financial.processing.dto.CreationRequest;
import com.example.financial.processing.domain.AccountStatus;
import com.example.financial.processing.dto.ErrorResponse;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/accounts")
public class ClientController {
    private final AccountService service;

    @Operation(summary = "Create a new account")
    @PostMapping("createAccount")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Successfully created account",
            content = @Content(
                schema = @Schema(implementation = CreationRequest.class))),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    public void createClient(@RequestBody CreationRequest details) {
        service.createAccount(details.firstName(), details.lastName(), details.idNumber());
    }
    
    @Operation(summary = "Delete an account")
    @DeleteMapping("/deleteAccount/{accountId}")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "204", 
            description = "Account Successfully deleted",
            content = @Content),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid account ID provided",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
        )),
        @ApiResponse(
            responseCode = "404", 
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    public ResponseEntity<Void> deleteAccount(@PathVariable String accountId) {
        log.info("Deleting account {}", accountId);
        service.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Close Client account")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "204", 
            description = "Account Successfully closed",
            content = @Content),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid account ID provided",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
        )),
        @ApiResponse(
            responseCode = "404", 
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    @PutMapping("/closeAccount/{accountId}")
    public ResponseEntity<?> closeAccount(@PathVariable String accountId) {
        if (accountId == null || accountId.isBlank()) {
            log.warn("Account ID is required to deleted account");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        log.info("Deleting account {}", accountId);
        service.updateAccountStatus(accountId, AccountStatus.CLOSED);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Suspend Client account")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "204", 
            description = "Account Successfully suspended",
            content = @Content),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid account ID provided",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
        )),
        @ApiResponse(
            responseCode = "404", 
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    @PutMapping("suspendAccount/{accountId}")
    public ResponseEntity<String> suspendAccount(@PathVariable String accountId) {

        log.info("Deleting account {}", accountId);
        service.updateAccountStatus(accountId, AccountStatus.SUSPENDED);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Freeze Client account")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "204", 
            description = "Account Successfully frozen",
            content = @Content),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid account ID provided",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
        )),
        @ApiResponse(
            responseCode = "404", 
            description = "Account not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    @PutMapping("/freezeAccount/{accountId}")
    public ResponseEntity<Void> freezeAccount(@PathVariable String accountId) {

        log.info("Deleting account {}", accountId);
        service.updateAccountStatus(accountId, AccountStatus.FROZEN);
        return ResponseEntity.noContent().build();
    }
    

}
